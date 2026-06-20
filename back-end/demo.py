"""
CLAiM demo — matches partner invoice appendix against TK records per patient and service code.

Usage:
  python -X utf8 demo.py <partner_invoice.pdf|json> <partner_appendix.pdf|json> --tk <tk_data.json> [--csv output.csv]

Examples:
  python -X utf8 demo.py tests/selfmadetest/Ida-Tallinna_Keskhaigla_arve.pdf tests/selfmadetest/Ida-Tallinna_Keskhaigla_arve_LISA.pdf --tk tk_data.json
  python -X utf8 demo.py tests/synthetic/scenario_2_arves_rohkem/arve.json tests/synthetic/scenario_2_arves_rohkem/lisa.json --tk tests/synthetic/scenario_2_arves_rohkem/tk.json --csv result.csv
"""
from __future__ import annotations
import argparse
import io
import sys
from pathlib import Path

if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

import pdfplumber
from claim.parsers.arve_json import parse_invoice_json
from claim.parsers.arve_pdf import parse_invoice_synlab_pdf
from claim.parsers.arve_haigla_pdf import parse_invoice_hospital_pdf
from claim.parsers.lisa_json import parse_appendix_json
from claim.parsers.lisa_pdf import parse_appendix_pdf
from claim.parsers.tk_json import parse_tk_json
from claim.matching import reconcile
from claim.report import print_report, export_csv
from claim.models import Invoice


def _is_synlab_pdf(path: str) -> bool:
    with pdfplumber.open(path) as pdf:
        text = pdf.pages[0].extract_text() or ""
    return "Kaubakood" in text


def _parse_invoice(path: Path) -> Invoice:
    if path.suffix.lower() == ".pdf":
        return parse_invoice_synlab_pdf(str(path)) if _is_synlab_pdf(str(path)) else parse_invoice_hospital_pdf(str(path))
    if path.suffix.lower() == ".json":
        return parse_invoice_json(path)
    print(f"Unknown invoice format: {path.suffix}")
    sys.exit(1)


def _parse_appendix(path: Path):
    if path.suffix.lower() == ".pdf":
        return parse_appendix_pdf(str(path))
    if path.suffix.lower() == ".json":
        return parse_appendix_json(path)
    print(f"Unknown appendix format: {path.suffix}")
    sys.exit(1)


def main() -> None:
    parser = argparse.ArgumentParser(description="CLAiM — partner invoice reconciliation tool")
    parser.add_argument("invoice", help="Partner invoice file (.pdf or .json)")
    parser.add_argument("appendix", help="Partner invoice appendix file (.pdf or .json)")
    parser.add_argument("--tk", metavar="FILE", help="TK data file (.json)")
    parser.add_argument("--csv", metavar="FILE", help="Export full comparison table as CSV")
    args = parser.parse_args()

    partner_invoice = _parse_invoice(Path(args.invoice))
    partner_appendix = _parse_appendix(Path(args.appendix))

    if not args.tk:
        print("\n  [!] TK data missing (--tk argument required for reconciliation).\n")
        sys.exit(1)

    tk_data = parse_tk_json(Path(args.tk))
    discrepancies = reconcile(partner_appendix, tk_data)

    print_report(partner_invoice, partner_appendix, discrepancies)

    if args.csv:
        export_csv(partner_invoice, partner_appendix, discrepancies, args.csv)


if __name__ == "__main__":
    main()
