from __future__ import annotations
import csv
from pathlib import Path
from .models import Arve, Lisa, Lahknevus


def _row(cols: list[str], widths: list[int]) -> str:
    return "  ".join(str(c).ljust(w) for c, w in zip(cols, widths))


def print_report(arve: Arve, lisa: Lisa, lahknevused: list[Lahknevus]) -> None:
    print(f"\n{'='*72}")
    print(f"  Partnerarve võrdlusaruanne")
    print(f"  Arve: {arve.arve_nr}  |  Kuupäev: {arve.arve_kuupaev}  |  Partner: {arve.partner}")
    print(f"  Saatekirjad lisas: {len(lisa.saatekirjad)}")
    print(f"{'='*72}")

    if not lahknevused:
        print("\n  Kõik koodid klapivad täpselt. Lahknevusi ei leitud.\n")
        return

    widths = [7, 32, 10, 10, 8, 10, 14]
    headers = ["Kood", "Kirjeldus", "Arves tk", "Lisas tk", "Erinevus", "€/tk", "Rahaline €"]
    print()
    print("  " + _row(headers, widths))
    print("  " + "-" * (sum(widths) + 2 * len(widths)))

    total_eur = 0.0
    for l in sorted(lahknevused, key=lambda x: abs(x.rahaline_erinevus_eur), reverse=True):
        sign = "+" if l.erinevus > 0 else ""
        cols = [
            l.kood,
            l.kirjeldus[:31],
            f"{l.arves_kogus:.0f}",
            f"{l.lisas_kogus:.0f}",
            f"{sign}{l.erinevus:.0f}",
            f"{l.uhiku_hind:.2f}",
            f"{sign}{l.rahaline_erinevus_eur:.2f}",
        ]
        print("  " + _row(cols, widths))
        total_eur += l.rahaline_erinevus_eur

    print("  " + "-" * (sum(widths) + 2 * len(widths)))
    sign = "+" if total_eur >= 0 else ""
    print(f"\n  Kokku lahknevusi: {len(lahknevused)}  |  Rahaline mõju: {sign}{total_eur:.2f} €")
    print(f"  (+ = arves rohkem kui lisas  |  - = lisas rohkem kui arves)\n")


def export_csv(arve: Arve, lisa: Lisa, lahknevused: list[Lahknevus], path: str | Path) -> None:
    """
    Kirjutab täieliku võrdlustabeli CSV-na (kõik koodid, mitte ainult lahknevused).
    Avamiseks Excelis: utf-8-sig kodeering (BOM) on automaatselt lisatud.
    """
    arve_kogused = arve.kogus_koodide_jargi()
    lisa_kogused = lisa.kogus_koodide_jargi()
    kirjeldused_arve = arve.kirjeldus_koodide_jargi()
    kirjeldused_lisa = lisa.kirjeldus_koodide_jargi()
    hinnad = arve.hind_koodide_jargi()
    lahk_by_kood = {l.kood: l for l in lahknevused}

    koodid = sorted(set(arve_kogused) | set(lisa_kogused))

    fieldnames = [
        "Kood",
        "Teenus (arve)",
        "Teenus (lisa)",
        "Nimed klapivad",
        "Arves (tk)",
        "Lisas (tk)",
        "Erinevus (tk)",
        "Ühiku hind (€)",
        "Rahaline erinevus (€)",
        "Staatus",
    ]

    rows = []
    for kood in koodid:
        arve_k = arve_kogused.get(kood, 0.0)
        lisa_k = float(lisa_kogused.get(kood, 0))
        erinevus = arve_k - lisa_k
        hind = hinnad.get(kood, 0.0)
        teenus_arve = kirjeldused_arve.get(kood, "—")
        teenus_lisa = kirjeldused_lisa.get(kood, "—")

        if erinevus == 0:
            staatus = "Klapib"
        elif lisa_k == 0:
            staatus = "Ainult arves"
        elif arve_k == 0:
            staatus = "Ainult lisas"
        elif erinevus > 0:
            staatus = "Arves rohkem"
        else:
            staatus = "Lisas rohkem"

        rows.append({
            "Kood": kood,
            "Teenus (arve)": teenus_arve,
            "Teenus (lisa)": teenus_lisa,
            "Nimed klapivad": "Jah" if teenus_arve.lower() == teenus_lisa.lower() else "EI",
            "Arves (tk)": arve_k,
            "Lisas (tk)": lisa_k,
            "Erinevus (tk)": erinevus,
            "Ühiku hind (€)": hind,
            "Rahaline erinevus (€)": round(erinevus * hind, 2),
            "Staatus": staatus,
        })

    # Sort: discrepancies first by absolute financial impact, then matching rows
    rows.sort(key=lambda r: (r["Staatus"] == "Klapib", -abs(r["Rahaline erinevus (€)"])))

    with open(path, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames, delimiter=";")
        writer.writeheader()
        writer.writerows(rows)

    print(f"  CSV salvestatud: {path}")
