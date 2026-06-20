from __future__ import annotations
import csv
from pathlib import Path
from .models import Invoice, Appendix, TKData, Discrepancy


def _row(cols: list[str], widths: list[int]) -> str:
    return "  ".join(str(c).ljust(w) for c, w in zip(cols, widths))


def print_report(
    partner_invoice: Invoice,
    appendix: Appendix,
    discrepancies: list[Discrepancy],
) -> None:
    print(f"\n{'='*80}")
    print(f"  Partnerarve võrdlusaruanne (lisa vs TK)")
    print(f"  Arve: {partner_invoice.invoice_nr}  |  Kuupäev: {partner_invoice.invoice_date}  |  Partner: {partner_invoice.partner}")
    print(f"  Saatekirjad lisas: {len(appendix.referrals)}")
    print(f"{'='*80}")

    if not discrepancies:
        print("\n  Kõik kirjed klapivad täpselt. Lahknevusi ei leitud.\n")
        return

    widths = [13, 20, 8, 10, 8, 8, 8, 14]
    headers = ["Isikukood", "Saatekiri", "Kood", "Kirjeldus", "Lisas tk", "TK-s tk", "Erinevus", "Rahaline €"]
    print()
    print("  " + _row(headers, widths))
    print("  " + "-" * (sum(widths) + 2 * len(widths)))

    total_eur = 0.0
    sorted_disc = sorted(discrepancies, key=lambda d: abs(d.financial_diff_eur), reverse=True)
    for d in sorted_disc:
        sign = "+" if d.difference > 0 else ""
        cols = [
            d.patient_id,
            d.referral_nr,
            d.service_code,
            d.description[:9],
            str(d.appendix_qty),
            str(d.tk_qty),
            f"{sign}{d.difference}",
            f"{sign}{d.financial_diff_eur:.2f}",
        ]
        print("  " + _row(cols, widths))
        total_eur += d.financial_diff_eur

    print("  " + "-" * (sum(widths) + 2 * len(widths)))
    sign = "+" if total_eur >= 0 else ""
    print(f"\n  Kokku lahknevusi: {len(discrepancies)}  |  Rahaline mõju: {sign}{total_eur:.2f} €")
    print(f"  (+ = lisas rohkem kui TK-s  |  - = TK-s rohkem kui lisas)\n")


def export_csv(
    partner_invoice: Invoice,
    appendix: Appendix,
    discrepancies: list[Discrepancy],
    path: str | Path,
) -> None:
    """
    Exports full patient-level comparison as CSV.
    Uses utf-8-sig encoding (BOM) for Excel compatibility.
    """
    disc_keys = {(d.patient_id, d.service_code): d for d in discrepancies}

    fieldnames = [
        "Isikukood",
        "Saatekiri nr",
        "Teenuskood",
        "Teenus",
        "Lisas (tk)",
        "TK-s (tk)",
        "Erinevus (tk)",
        "Ühiku hind (€)",
        "Rahaline erinevus (€)",
        "Staatus",
    ]

    csv_rows = []

    # All appendix entries
    for referral in appendix.referrals:
        for service in referral.services:
            key = (referral.patient_id, service.code)
            d = disc_keys.get(key)
            if d:
                tk_qty = d.tk_qty
                diff = d.difference
                fin_diff = d.financial_diff_eur
                if tk_qty == 0:
                    status = "Puudub TK-s"
                else:
                    status = "Koguse erinevus"
            else:
                tk_qty = service.quantity
                diff = 0
                fin_diff = 0.0
                status = "Klapib"

            csv_rows.append({
                "Isikukood": referral.patient_id,
                "Saatekiri nr": referral.referral_nr,
                "Teenuskood": service.code,
                "Teenus": service.service,
                "Lisas (tk)": service.quantity,
                "TK-s (tk)": tk_qty,
                "Erinevus (tk)": diff,
                "Ühiku hind (€)": service.unit_price,
                "Rahaline erinevus (€)": fin_diff,
                "Staatus": status,
            })

    # TK-only entries (not in appendix)
    appendix_keys = {
        (ref.patient_id, s.code)
        for ref in appendix.referrals
        for s in ref.services
    }
    for d in discrepancies:
        if d.referral_nr == "—":
            csv_rows.append({
                "Isikukood": d.patient_id,
                "Saatekiri nr": "—",
                "Teenuskood": d.service_code,
                "Teenus": "—",
                "Lisas (tk)": 0,
                "TK-s (tk)": d.tk_qty,
                "Erinevus (tk)": d.difference,
                "Ühiku hind (€)": 0.0,
                "Rahaline erinevus (€)": 0.0,
                "Staatus": "Ainult TK-s",
            })

    csv_rows.sort(key=lambda r: (r["Staatus"] == "Klapib", -abs(r["Rahaline erinevus (€)"])))

    with open(path, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames, delimiter=";")
        writer.writeheader()
        writer.writerows(csv_rows)

    print(f"  CSV salvestatud: {path}")
