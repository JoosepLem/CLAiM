from __future__ import annotations
from .models import Arve, Lisa, Lahknevus


def vorrle(arve: Arve, lisa: Lisa) -> list[Lahknevus]:
    """
    Compares quantities per kood between the invoice (arve) and the referral list (lisa).
    Returns discrepancies — rows where counts don't match.
    """
    arve_kogused = arve.kogus_koodide_jargi()
    lisa_kogused = lisa.kogus_koodide_jargi()
    kirjeldused = arve.kirjeldus_koodide_jargi()
    hinnad = arve.hind_koodide_jargi()

    koodid = sorted(set(arve_kogused) | set(lisa_kogused))
    lahknevused: list[Lahknevus] = []

    for kood in koodid:
        arve_k = arve_kogused.get(kood, 0.0)
        lisa_k = float(lisa_kogused.get(kood, 0))
        if arve_k == lisa_k:
            continue
        erinevus = arve_k - lisa_k
        hind = hinnad.get(kood, 0.0)
        lahknevused.append(Lahknevus(
            kood=kood,
            kirjeldus=kirjeldused.get(kood, "—"),
            arves_kogus=arve_k,
            lisas_kogus=lisa_k,
            erinevus=erinevus,
            uhiku_hind=hind,
            rahaline_erinevus_eur=round(erinevus * hind, 2),
        ))

    return lahknevused
