"""CLAiM FastAPI backend — serves reconciliation results to the frontend."""
from __future__ import annotations

import os
import tempfile
import uuid
from collections import defaultdict
from datetime import date
from pathlib import Path

import pdfplumber
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware

from claim.matching import reconcile
from claim.parsers.arve_haigla_pdf import parse_invoice_hospital_pdf
from claim.parsers.arve_pdf import parse_invoice_synlab_pdf
from claim.parsers.lisa_pdf import parse_appendix_pdf
from claim.parsers.tk_json import parse_tk_json

app = FastAPI(title="CLAiM API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

TK_PATH = Path(__file__).parent / "tests/tervisekassa_invoices/tervisekassa_invoices.json"
tk_data = parse_tk_json(TK_PATH)

# In-memory store: invoice_id → {summary, discrepancies, matched, meta}
store: dict[str, dict] = {}

_MONTHS_SHORT = ["Jan", "Feb", "Mar", "Apr", "May", "Jun",
                 "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
_MONTHS_LONG = ["January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"]


def _is_synlab(path: str) -> bool:
    with pdfplumber.open(path) as pdf:
        text = pdf.pages[0].extract_text() or ""
    return "Kaubakood" in text


def _fmt_iso_date(d: str) -> str:
    """Convert 'YYYY-MM-DD' to '01 Jan'. Returns '—' on failure."""
    if not d or len(d) < 10:
        return "—"
    try:
        y, m, day = d[:10].split("-")
        return f"{int(day):02d} {_MONTHS_SHORT[int(m) - 1]}"
    except Exception:
        return "—"


def _to_year_month(d: str) -> str:
    """Return 'YYYY-MM' from either 'YYYY-MM-DD' or 'DD.MM.YYYY'."""
    if not d:
        return "2026-05"
    d = d.strip()
    if len(d) >= 10 and d[4] == "-":
        return d[:7]
    if len(d) >= 10 and d[2] == "." and d[5] == ".":
        return f"{d[6:10]}-{d[3:5]}"
    return "2026-05"


@app.post("/api/reconcile")
async def do_reconcile(
    arve: UploadFile = File(..., description="Partner invoice PDF"),
    lisa: UploadFile = File(..., description="Partner invoice appendix PDF"),
):
    """Parse both PDFs, run reconciliation against TK data, return summary."""
    arve_tmp = lisa_tmp = None
    try:
        with tempfile.NamedTemporaryFile(suffix=".pdf", delete=False) as f:
            f.write(await arve.read())
            arve_tmp = f.name
        with tempfile.NamedTemporaryFile(suffix=".pdf", delete=False) as f:
            f.write(await lisa.read())
            lisa_tmp = f.name

        invoice = (
            parse_invoice_synlab_pdf(arve_tmp)
            if _is_synlab(arve_tmp)
            else parse_invoice_hospital_pdf(arve_tmp)
        )
        appendix = parse_appendix_pdf(lisa_tmp)
        discs = reconcile(appendix, tk_data)
    finally:
        for p in (arve_tmp, lisa_tmp):
            if p:
                try:
                    os.unlink(p)
                except OSError:
                    pass

    # Build TK date lookup: (patient_id, service_code) → earliest date
    tk_dates: dict[tuple[str, str], str] = {}
    for rec in tk_data.records:
        key = (rec.patient_id, rec.service_code)
        if key not in tk_dates or rec.date < tk_dates[key]:
            tk_dates[key] = rec.date

    # Aggregate appendix quantities and metadata
    appendix_qty: dict[tuple[str, str], int] = defaultdict(int)
    appendix_meta: dict[tuple[str, str], tuple[str, float]] = {}
    for referral in appendix.referrals:
        for svc in referral.services:
            key = (referral.patient_id, svc.code)
            appendix_qty[key] += svc.quantity
            if key not in appendix_meta:
                appendix_meta[key] = (svc.service, svc.unit_price)

    # Code-level name+price lookup (for TK-only entries that aren't in appendix_meta)
    code_name: dict[str, str] = {}
    code_price: dict[str, float] = {}
    for (_, code), (name, price) in appendix_meta.items():
        code_name[code] = name
        code_price[code] = price

    tk_qty_map = tk_data.qty_by_patient_and_code()

    # Build discrepancy list in frontend shape.
    # Three cases:
    #   missing  — partner billed, TK has no record (pq > 0, ptq == 0)
    #   qty      — partner billed different quantity than TK (pq > ptq > 0)
    #   tk_only  — TK has a record but partner didn't include in invoice (pq == 0, ptq > 0)
    disc_list = []
    for i, d in enumerate(discs):
        key = (d.patient_id, d.service_code)
        if d.appendix_qty == 0 and d.tk_qty > 0:
            disc_type = "tk_only"
            name = code_name.get(d.service_code, d.description)
            price = code_price.get(d.service_code, 0.0)
        elif d.tk_qty == 0:
            disc_type = "missing"
            name = d.description
            price = d.unit_price
        else:
            disc_type = "qty"
            name = d.description
            price = d.unit_price
        disc_list.append({
            "id": f"D{i + 1}",
            "type": disc_type,
            "dos": _fmt_iso_date(tk_dates.get(key, "")),
            "patient": d.patient_id,
            "code": d.service_code,
            "name": name,
            "price": price,
            "pq": d.appendix_qty,
            "ptq": d.tk_qty,
            "page": 0,
            "line": i,
        })

    # Build matched lines (appendix_qty == tk_qty and > 0)
    matched_list = []
    j = 0
    for key, a_qty in appendix_qty.items():
        t_qty = tk_qty_map.get(key, 0)
        if a_qty == t_qty and t_qty > 0:
            patient_id, code = key
            name, price = appendix_meta[key]
            matched_list.append({
                "id": f"M{j + 1}",
                "dos": _fmt_iso_date(tk_dates.get(key, "")),
                "patient": patient_id,
                "code": code,
                "name": name,
                "price": price,
                "pq": a_qty,
                "line": len(disc_list) + j,
            })
            j += 1

    # totalLines = unique (patient, code) pairs in the appendix — same denominator
    # as matched_list + partner-side discrepancies, so the progress bar adds up.
    total_lines = len(appendix_qty)
    # Financial risk items only (partner billed more than TK knows about)
    open_discs = [d for d in disc_list if d["pq"] > d["ptq"]]
    risk = sum(d["price"] * (d["pq"] - d["ptq"]) for d in open_discs)

    period = _to_year_month(invoice.invoice_date or "")
    try:
        _y, _m = period.split("-")
        period_label = f"{_MONTHS_LONG[int(_m) - 1]} {_y}"
    except Exception:
        period_label = period

    inv_id = str(uuid.uuid4())[:8]
    uploaded = date.today().strftime("%-d %b %Y")

    summary = {
        "id": inv_id,
        "partner": invoice.partner,
        "invoiceNo": invoice.invoice_nr,
        "period": period,
        "uploaded": uploaded,
        "lines": total_lines,
        "disc": len(open_discs),
        "risk": round(risk, 2),
        "status": "review" if open_discs else "reconciled",
    }

    store[inv_id] = {
        "summary": summary,
        "discrepancies": disc_list,
        "matched": matched_list,
        "meta": {
            "partner": invoice.partner,
            "invoiceNo": invoice.invoice_nr,
            "period": period_label,
            "uploaded": uploaded,
            "totalLines": total_lines,
            "baselineReconciled": len(matched_list),
        },
    }

    return summary


@app.get("/api/invoices")
def list_invoices():
    return [v["summary"] for v in store.values()]


@app.get("/api/invoices/{inv_id}")
def get_invoice(inv_id: str):
    if inv_id not in store:
        raise HTTPException(404, "Invoice not found")
    return store[inv_id]
