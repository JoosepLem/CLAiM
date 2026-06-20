"""Parser for SYNLAB-format partner invoice PDF."""
from __future__ import annotations
import re
import pdfplumber

from ..models import Invoice, InvoiceRow


def _to_float(s: str) -> float:
    return float(s.replace(",", "."))


_HEADER_WORDS = {"Kaubakood", "Kirjeldus", "Kogus", "Ühik", "Ühiku", "hind", "Ah.", "Summa", "KM"}
_FOOTER_WORDS = {"Reg.nr.", "IBAN", "SWIFT", "pank", "SEB", "Swedbank", "Luminor", "Tel.", "E-post", "Web:"}

_CODE_RE = re.compile(r"^(\d{5})\s+(.+)$")
_TAIL_RE = re.compile(r"([\d]+[,\.][\d]+)\s+tk\s+([\d]+[,\.][\d]+)\s+([\d]+[,\.][\d]+)\s+([\d]+[,\.][\d]+)\s*$")
_META_INVOICE_NR = re.compile(r"Arve\s+(\d+)")
_META_DATE = re.compile(r"Arve kuupäev\s+([\d]{2}\.[\d]{2}\.[\d]{4})")


def _is_noise(line: str) -> bool:
    return any(w in line for w in _FOOTER_WORDS) or not line.strip()


def parse_invoice_synlab_pdf(path: str) -> Invoice:
    rows: list[InvoiceRow] = []
    invoice_nr = ""
    invoice_date = ""
    partner = "SYNLAB Eesti OÜ"
    payer = ""

    pending_code: str | None = None
    pending_description: str = ""

    def flush(quantity: float, unit_price: float, discount: float, amount: float) -> None:
        if pending_code:
            rows.append(InvoiceRow(
                service_code=pending_code,
                description=pending_description.strip(),
                quantity=quantity,
                unit_price=unit_price,
                discount_pct=discount,
                amount=amount,
            ))

    with pdfplumber.open(path) as pdf:
        for page in pdf.pages:
            text = page.extract_text() or ""
            for raw_line in text.splitlines():
                line = raw_line.strip()
                if not line or _is_noise(line):
                    continue

                if not invoice_nr:
                    m = _META_INVOICE_NR.search(line)
                    if m:
                        invoice_nr = m.group(1)
                if not invoice_date:
                    m = _META_DATE.search(line)
                    if m:
                        invoice_date = m.group(1)

                if any(w in line for w in _HEADER_WORDS) and not _CODE_RE.match(line):
                    continue

                tail_m = _TAIL_RE.search(line)
                code_m = _CODE_RE.match(line)

                if code_m and tail_m:
                    flush_code = code_m.group(1)
                    flush_desc = line[:tail_m.start()].replace(flush_code, "", 1).strip()
                    quantity, unit_price, discount, amount = [_to_float(x) for x in tail_m.groups()]
                    rows.append(InvoiceRow(
                        service_code=flush_code,
                        description=flush_desc,
                        quantity=quantity,
                        unit_price=unit_price,
                        discount_pct=discount,
                        amount=amount,
                    ))
                    pending_code = None
                    pending_description = ""

                elif code_m and not tail_m:
                    pending_code = code_m.group(1)
                    pending_description = code_m.group(2)

                elif tail_m and pending_code:
                    quantity, unit_price, discount, amount = [_to_float(x) for x in tail_m.groups()]
                    flush(quantity, unit_price, discount, amount)
                    pending_code = None
                    pending_description = ""

                elif pending_code and not code_m:
                    pending_description += " " + line

    return Invoice(
        invoice_nr=invoice_nr or "TUNDMATU",
        invoice_date=invoice_date or "",
        partner=partner,
        payer=payer or "Tundmatu",
        rows=rows,
    )
