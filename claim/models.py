from __future__ import annotations
from pydantic import BaseModel


class ArveRida(BaseModel):
    kaubakood: str
    kirjeldus: str
    kogus: float
    uhiku_hind: float
    allahindlus_protsent: float
    summa: float


class Arve(BaseModel):
    arve_nr: str
    arve_kuupaev: str
    partner: str
    maksja: str
    read: list[ArveRida]

    def kogus_koodide_jargi(self) -> dict[str, float]:
        result: dict[str, float] = {}
        for r in self.read:
            result[r.kaubakood] = result.get(r.kaubakood, 0.0) + r.kogus
        return result

    def kirjeldus_koodide_jargi(self) -> dict[str, str]:
        # first occurrence wins
        result: dict[str, str] = {}
        for r in self.read:
            if r.kaubakood not in result:
                result[r.kaubakood] = r.kirjeldus
        return result

    def hind_koodide_jargi(self) -> dict[str, float]:
        result: dict[str, float] = {}
        for r in self.read:
            if r.kaubakood not in result:
                result[r.kaubakood] = r.uhiku_hind
        return result


class TeenusRida(BaseModel):
    teenus: str
    kood: str
    kogus: int
    uhiku_hind: float
    summa: float


class Saatekiri(BaseModel):
    saatekiri_nr: str
    patsient_id: str
    teenused: list[TeenusRida]


class Lisa(BaseModel):
    arve_nr: str
    saatekirjad: list[Saatekiri]

    def kogus_koodide_jargi(self) -> dict[str, int]:
        result: dict[str, int] = {}
        for sk in self.saatekirjad:
            for t in sk.teenused:
                result[t.kood] = result.get(t.kood, 0) + t.kogus
        return result

    def kirjeldus_koodide_jargi(self) -> dict[str, str]:
        result: dict[str, str] = {}
        for sk in self.saatekirjad:
            for t in sk.teenused:
                if t.kood not in result:
                    result[t.kood] = t.teenus
        return result


class Lahknevus(BaseModel):
    kood: str
    kirjeldus: str
    arves_kogus: float
    lisas_kogus: float
    # positive = arves rohkem (ülearve), negative = lisas rohkem (alarve)
    erinevus: float
    uhiku_hind: float
    rahaline_erinevus_eur: float
