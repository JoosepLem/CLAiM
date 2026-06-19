"""Parser for SYNLAB-format partner invoice PDF."""
from __future__ import annotations
import re
import pdfplumber

from ..models import Arve, ArveRida


def _to_float(s: str) -> float:
    return float(s.replace(",", "."))


_HEADER_WORDS = {"Kaubakood", "Kirjeldus", "Kogus", "Ühik", "Ühiku", "hind", "Ah.", "Summa", "KM"}
_FOOTER_WORDS = {"Reg.nr.", "IBAN", "SWIFT", "pank", "SEB", "Swedbank", "Luminor", "Tel.", "E-post", "Web:"}

_CODE_RE = re.compile(r"^(\d{5})\s+(.+)$")
_TAIL_RE = re.compile(r"([\d]+[,\.][\d]+)\s+tk\s+([\d]+[,\.][\d]+)\s+([\d]+[,\.][\d]+)\s+([\d]+[,\.][\d]+)\s*$")
_META_ARVE_NR = re.compile(r"Arve\s+(\d+)")
_META_KUUPAEV = re.compile(r"Arve kuupäev\s+([\d]{2}\.[\d]{2}\.[\d]{4})")
_META_PARTNER = re.compile(r"^([A-ZÜÕÖÄ][A-ZÜÕÖÄa-züõöä\s&]+OÜ|AS\s.+?)$")


def _is_noise(line: str) -> bool:
    return any(w in line for w in _FOOTER_WORDS) or not line.strip()


def parse_arve_pdf(path: str) -> Arve:
    read: list[ArveRida] = []
    arve_nr = ""
    arve_kuupaev = ""
    partner = "SYNLAB Eesti OÜ"
    maksja = ""

    pending_kood: str | None = None
    pending_desc: str = ""

    def flush(kogus: float, uhiku_hind: float, ah: float, summa: float) -> None:
        if pending_kood:
            read.append(ArveRida(
                kaubakood=pending_kood,
                kirjeldus=pending_desc.strip(),
                kogus=kogus,
                uhiku_hind=uhiku_hind,
                allahindlus_protsent=ah,
                summa=summa,
            ))

    with pdfplumber.open(path) as pdf:
        for page in pdf.pages:
            text = page.extract_text() or ""
            for raw_line in text.splitlines():
                line = raw_line.strip()
                if not line or _is_noise(line):
                    continue

                # Extract metadata from any page
                if not arve_nr:
                    m = _META_ARVE_NR.search(line)
                    if m:
                        arve_nr = m.group(1)
                if not arve_kuupaev:
                    m = _META_KUUPAEV.search(line)
                    if m:
                        arve_kuupaev = m.group(1)

                # Skip header rows
                if any(w in line for w in _HEADER_WORDS) and not _CODE_RE.match(line):
                    continue

                # Check if line ends with numeric tail (qty tk price disc sum)
                tail_m = _TAIL_RE.search(line)
                code_m = _CODE_RE.match(line)

                if code_m and tail_m:
                    # Complete single-line row
                    flush_kood = code_m.group(1)
                    flush_desc = line[:tail_m.start()].replace(flush_kood, "", 1).strip()
                    kogus, uhiku_hind, ah, summa = [_to_float(x) for x in tail_m.groups()]
                    read.append(ArveRida(
                        kaubakood=flush_kood,
                        kirjeldus=flush_desc,
                        kogus=kogus,
                        uhiku_hind=uhiku_hind,
                        allahindlus_protsent=ah,
                        summa=summa,
                    ))
                    pending_kood = None
                    pending_desc = ""

                elif code_m and not tail_m:
                    # Start of a multi-line row
                    pending_kood = code_m.group(1)
                    pending_desc = code_m.group(2)

                elif tail_m and pending_kood:
                    # Tail line closes a multi-line row
                    kogus, uhiku_hind, ah, summa = [_to_float(x) for x in tail_m.groups()]
                    flush(kogus, uhiku_hind, ah, summa)
                    pending_kood = None
                    pending_desc = ""

                elif pending_kood and not code_m:
                    # Continuation of multi-line description
                    pending_desc += " " + line

    return Arve(
        arve_nr=arve_nr or "TUNDMATU",
        arve_kuupaev=arve_kuupaev or "",
        partner=partner,
        maksja=maksja or "Tundmatu",
        read=read,
    )
