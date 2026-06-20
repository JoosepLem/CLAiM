"""Parser for partner appendix PDFs — Synlab, ITK, LTK, and PERH formats."""
from __future__ import annotations
import re
import pdfplumber

from ..models import Appendix, Referral, ServiceRow

_INVOICE_NR = re.compile(r"Arve\s+([\w/\-]+)\s+lisa", re.IGNORECASE)

# ITK format: "Patsiendi nimi: ... Isikukood: XXXXXXXXXXX"
_ITK_PATIENT = re.compile(r"Patsiendi nimi:\s+.+?\s+Isikukood:\s+(\d{11})")
_ITK_REFERRAL = re.compile(r"Kuupäev:\s+[\d.]+\s+Saatekirja nr:\s+(\S+)")
# ITK service row: "{description} {4-5-digit code} {int qty} {price,comma} {total,comma}"
_ITK_SERVICE_ROW = re.compile(r"^(.+?)\s+(\d{4,5})\s+(\d+)\s+([\d]+,[\d]+)\s+([\d]+,[\d]+)\s*$")

# LTK format: "PATSIENT: Name ISIKUKOOD: XXXXXXXXXXX" (uppercase)
_LTK_PATIENT = re.compile(r"PATSIENT:\s+.+?\s+ISIKUKOOD:\s+(\d{11})")
_LTK_REFERRAL = re.compile(r"SAATEKIRI:\s+(\S+)")
# LTK service row ends with "{4-5-digit code} {int qty} {price.dot} {total.dot}"
_LTK_SERVICE_ROW = re.compile(r"(\d{4,5})\s+(\d+)\s+([\d]+\.[\d]+)\s+([\d]+\.[\d]+)\s*$")

# PERH/Synlab format: "Patsient: Name Isikukood: XXXXXXXXXXX" (mixed case)
_SYNLAB_PERH_PATIENT = re.compile(r"Patsient:\s+.+?\s+Isikukood:\s+(\d{11})")
# Synlab referral: "Teenuse kuupäev: ... Saatekiri: SK-..."
_SYNLAB_REFERRAL = re.compile(r"Saatekiri:\s+(\S+)")
# PERH referral: "Kuupäev: ... Saatekiri: SK-..."
_PERH_REFERRAL = re.compile(r"Kuupäev:\s+[\d.]+\s+Saatekiri:\s+(\S+)")
# Synlab service row: "{description} {4-5-digit code} {decimal,comma qty} {price,comma} {total,comma}"
_SYNLAB_SERVICE_ROW = re.compile(r"^(.+?)\s+(\d{4,5})\s+(\d+,\d+)\s+([\d,]+)\s+([\d,]+)\s*$")

_HEADER_WORDS = {"Teenus", "Kood", "Kogus", "Ühiku hind", "Summa"}


def _to_float_comma(s: str) -> float:
    return float(s.replace(",", "."))


def _to_float_dot(s: str) -> float:
    return float(s)


def _parse_itk(all_text: str, invoice_nr: str) -> Appendix:
    referrals = []
    for block in all_text.split("Vahesumma:"):
        pm = _ITK_PATIENT.search(block)
        km = _ITK_REFERRAL.search(block)
        if not pm or not km:
            continue
        services = []
        for line in block.splitlines():
            line = line.strip()
            if not line or any(w in line for w in _HEADER_WORDS):
                continue
            sm = _ITK_SERVICE_ROW.match(line)
            if sm:
                services.append(ServiceRow(
                    service=sm.group(1),
                    code=sm.group(2),
                    quantity=int(sm.group(3)),
                    unit_price=_to_float_comma(sm.group(4)),
                    amount=_to_float_comma(sm.group(5)),
                ))
        if services:
            referrals.append(Referral(
                referral_nr=km.group(1),
                patient_id=pm.group(1),
                services=services,
            ))
    return Appendix(invoice_nr=invoice_nr, referrals=referrals)


def _parse_ltk(all_text: str, invoice_nr: str) -> Appendix:
    referrals = []
    for block in all_text.split("Vahesumma:"):
        pm = _LTK_PATIENT.search(block)
        km = _LTK_REFERRAL.search(block)
        if not pm or not km:
            continue
        services = []
        prev_line = ""
        for line in block.splitlines():
            line = line.strip()
            if not line or any(w in line for w in _HEADER_WORDS):
                prev_line = line
                continue
            sm = _LTK_SERVICE_ROW.search(line)
            if sm:
                # Description is text before the code on this line; fall back to prev line
                inline_desc = line[:sm.start()].strip()
                description = inline_desc if inline_desc else prev_line
                services.append(ServiceRow(
                    service=description,
                    code=sm.group(1),
                    quantity=int(sm.group(2)),
                    unit_price=_to_float_dot(sm.group(3)),
                    amount=_to_float_dot(sm.group(4)),
                ))
            prev_line = line
        if services:
            referrals.append(Referral(
                referral_nr=km.group(1),
                patient_id=pm.group(1),
                services=services,
            ))
    return Appendix(invoice_nr=invoice_nr, referrals=referrals)


def _parse_synlab(all_text: str, invoice_nr: str) -> Appendix:
    referrals = []
    for block in all_text.split("Vahesumma:"):
        pm = _SYNLAB_PERH_PATIENT.search(block)
        km = _SYNLAB_REFERRAL.search(block)
        if not pm or not km:
            continue
        services = []
        for line in block.splitlines():
            line = line.strip()
            if not line or any(w in line for w in _HEADER_WORDS):
                continue
            sm = _SYNLAB_SERVICE_ROW.match(line)
            if sm:
                services.append(ServiceRow(
                    service=sm.group(1),
                    code=sm.group(2),
                    quantity=int(round(_to_float_comma(sm.group(3)))),
                    unit_price=_to_float_comma(sm.group(4)),
                    amount=_to_float_comma(sm.group(5)),
                ))
        if services:
            referrals.append(Referral(
                referral_nr=km.group(1),
                patient_id=pm.group(1),
                services=services,
            ))
    return Appendix(invoice_nr=invoice_nr, referrals=referrals)


def _parse_perh(all_text: str, all_tables: list, invoice_nr: str) -> Appendix:
    patients: list[tuple[str, str]] = []
    for block in all_text.split("Vahesumma:"):
        pm = _SYNLAB_PERH_PATIENT.search(block)
        km = _PERH_REFERRAL.search(block)
        if pm and km:
            patients.append((pm.group(1), km.group(1)))

    service_tables = [t for t in all_tables if t and t[0] and t[0][0] == "Teenus"]

    referrals = []
    for i, (patient_id, referral_nr) in enumerate(patients):
        if i >= len(service_tables):
            break
        services = []
        for row in service_tables[i][1:]:
            if not row or not row[1]:
                continue
            try:
                services.append(ServiceRow(
                    service=str(row[0]).replace("\n", " "),
                    code=str(row[1]),
                    quantity=int(row[2]),
                    unit_price=_to_float_comma(str(row[3])),
                    amount=_to_float_comma(str(row[4])),
                ))
            except (ValueError, TypeError, IndexError):
                pass
        if services:
            referrals.append(Referral(
                referral_nr=referral_nr,
                patient_id=patient_id,
                services=services,
            ))
    return Appendix(invoice_nr=invoice_nr, referrals=referrals)


def parse_appendix_pdf(path: str) -> Appendix:
    all_text = ""
    all_tables: list = []

    with pdfplumber.open(path) as pdf:
        for page in pdf.pages:
            all_text += (page.extract_text() or "") + "\n"
            all_tables.extend(page.extract_tables())

    m = _INVOICE_NR.search(all_text)
    invoice_nr = m.group(1) if m else "TUNDMATU"

    if "Patsiendi nimi:" in all_text:
        return _parse_itk(all_text, invoice_nr)

    service_tables = [t for t in all_tables if t and t[0] and t[0][0] == "Teenus"]
    if service_tables:
        return _parse_perh(all_text, all_tables, invoice_nr)

    if "PATSIENT:" in all_text and "SAATEKIRI:" in all_text:
        return _parse_ltk(all_text, invoice_nr)

    if "Patsient:" in all_text and "Saatekiri:" in all_text:
        return _parse_synlab(all_text, invoice_nr)

    return Appendix(invoice_nr=invoice_nr, referrals=[])
