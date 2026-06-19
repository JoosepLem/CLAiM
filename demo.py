"""
CLAiM demo — võrdleb partnerarvet ja selle lisa.

Kasutus:
  python -X utf8 demo.py <arve.json|arve.pdf> <lisa.json> [--csv väljund.csv]

Näidised:
  python -X utf8 demo.py tests/synthetic/scenario_1_taielik_vaste/arve.json tests/synthetic/scenario_1_taielik_vaste/lisa.json
  python -X utf8 demo.py tests/synthetic/scenario_2_arves_rohkem/arve.json   tests/synthetic/scenario_2_arves_rohkem/lisa.json --csv tulemus.csv
  python -X utf8 demo.py "Partnerarve näidis_peidetud.pdf" tests/real_test/lisa.json --csv real_tulemus.csv
"""
from __future__ import annotations
import argparse
import io
import sys
from pathlib import Path

if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

from claim.parsers.arve_json import parse_arve_json
from claim.parsers.arve_pdf import parse_arve_pdf
from claim.parsers.lisa_json import parse_lisa_json
from claim.matching import vorrle
from claim.report import print_report, export_csv


def main() -> None:
    parser = argparse.ArgumentParser(description="CLAiM — partnerarve võrdlustööriist")
    parser.add_argument("arve", help="Arve fail (.pdf või .json)")
    parser.add_argument("lisa", help="Partnerarve lisa fail (.json)")
    parser.add_argument("--csv", metavar="FAIL", help="Salvesta täielik võrdlustabel CSV-na")
    args = parser.parse_args()

    arve_path = Path(args.arve)
    lisa_path = Path(args.lisa)

    if arve_path.suffix.lower() == ".pdf":
        arve = parse_arve_pdf(str(arve_path))
    elif arve_path.suffix.lower() == ".json":
        arve = parse_arve_json(arve_path)
    else:
        print(f"Tundmatu arve formaat: {arve_path.suffix}")
        sys.exit(1)

    lisa = parse_lisa_json(lisa_path)
    lahknevused = vorrle(arve, lisa)
    print_report(arve, lisa, lahknevused)

    if args.csv:
        export_csv(arve, lisa, lahknevused, args.csv)


if __name__ == "__main__":
    main()
