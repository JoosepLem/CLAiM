"""Parser for hospital invoice PDFs (ITK, PERH format)."""
from __future__ import annotations
import re
import pdfplumber

from ..models import Invoice, InvoiceRow

_INVOICE_NR = re.compile(r"Arve\s+(?:number|nr)\s+([\w/\-]+)")
_INVOICE_DATE = re.compile(r"(?:Koostamise\s+kuupäev|Arve\s+kuupäev|Kuupaev)\s+([\d]{2}\.[\d]{2}\.[\d]{4}|[\d]{4}-[\d]{2}-[\d]{2})")
_PAYER = re.compile(r"Maksja\s*\n(.+)")
_TEXT_ROW = re.compile(r"^(\d{4,5})\s+(.+?)\s+(\d+)\s+([\d]+,[\d]+)\s+([\d]+,[\d]+)\s*$")

_SKIP_WORDS = {"Teenuse kood", "Arve summa", "Käibemaksuvaba", "Reg.nr", "Tel ", "E-post", "www."}


def _to_float(s: str) -> float:
    return float(s.replace(",", "."))


def parse_invoice_hospital_pdf(path: str) -> Invoice:
    rows: list[InvoiceRow] = []
    all_text = ""
    all_tables: list = []

    with pdfplumber.open(path) as pdf:
        for page in pdf.pages:
            all_text += (page.extract_text() or "") + "\n"
            all_tables.extend(page.extract_tables())

    m = _INVOICE_NR.search(all_text)
    invoice_nr = m.group(1) if m else "TUNDMATU"
    m = _INVOICE_DATE.search(all_text)
    invoice_date = m.group(1) if m else ""
    partner = all_text.splitlines()[0].strip() if all_text.strip() else ""
    payer = ""
    pm = _PAYER.search(all_text)
    if pm:
        payer = pm.group(1).strip()

    service_tables = [t for t in all_tables if t and t[0] and t[0][0] == "Teenuse kood"]

    if service_tables:
        for table in service_tables:
            for row in table[1:]:
                if not row or not row[0] or not str(row[0]).isdigit():
                    continue
                try:
                    rows.append(InvoiceRow(
                        service_code=str(row[0]),
                        description=str(row[1]).replace("\n", " "),
                        quantity=_to_float(str(row[2])),
                        unit_price=_to_float(str(row[3])),
                        discount_pct=0.0,
                        amount=_to_float(str(row[4])),
                    ))
                except (ValueError, TypeError, IndexError):
                    pass
    else:
        for line in all_text.splitlines():
            if any(w in line for w in _SKIP_WORDS):
                continue
            m2 = _TEXT_ROW.match(line.strip())
            if m2:
                rows.append(InvoiceRow(
                    service_code=m2.group(1),
                    description=m2.group(2),
                    quantity=_to_float(m2.group(3)),
                    unit_price=_to_float(m2.group(4)),
                    discount_pct=0.0,
                    amount=_to_float(m2.group(5)),
                ))

    return Invoice(
        invoice_nr=invoice_nr,
        invoice_date=invoice_date,
        partner=partner,
        payer=payer or "Tundmatu",
        rows=rows,
    )
