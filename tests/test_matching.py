"""Unit tests for matching logic against all three synthetic scenarios."""
from pathlib import Path
import pytest

from claim.parsers.arve_json import parse_arve_json
from claim.parsers.lisa_json import parse_lisa_json
from claim.matching import vorrle

SYNTHETIC = Path(__file__).parent / "synthetic"


def load(scenario: str):
    base = SYNTHETIC / scenario
    return parse_arve_json(base / "arve.json"), parse_lisa_json(base / "lisa.json")


class TestScenario1TaielikVaste:
    def test_no_discrepancies(self):
        arve, lisa = load("scenario_1_taielik_vaste")
        lahknevused = vorrle(arve, lisa)
        assert lahknevused == [], f"Oodati 0 lahknevust, sain: {lahknevused}"

    def test_code_totals_match(self):
        arve, lisa = load("scenario_1_taielik_vaste")
        assert arve.kogus_koodide_jargi() == {"66101": 5, "66102": 3, "66202": 4, "66706": 2}
        assert lisa.kogus_koodide_jargi() == {"66101": 5, "66102": 3, "66202": 4, "66706": 2}


class TestScenario2ArvesRohkem:
    def test_three_discrepancies(self):
        arve, lisa = load("scenario_2_arves_rohkem")
        lahknevused = vorrle(arve, lisa)
        assert len(lahknevused) == 3

    def test_all_positive(self):
        arve, lisa = load("scenario_2_arves_rohkem")
        for l in vorrle(arve, lisa):
            assert l.erinevus > 0, f"Kood {l.kood}: oodati arves rohkem"

    def test_specific_discrepancies(self):
        arve, lisa = load("scenario_2_arves_rohkem")
        by_kood = {l.kood: l for l in vorrle(arve, lisa)}
        assert by_kood["66101"].erinevus == 2   # 7 arves, 5 lisas
        assert by_kood["66706"].erinevus == 2   # 4 arves, 2 lisas
        assert by_kood["66112"].erinevus == 2   # 2 arves, 0 lisas

    def test_financial_impact(self):
        arve, lisa = load("scenario_2_arves_rohkem")
        total = sum(l.rahaline_erinevus_eur for l in vorrle(arve, lisa))
        assert total > 0


class TestScenario3LisasRohkem:
    def test_two_discrepancies(self):
        arve, lisa = load("scenario_3_lisas_rohkem")
        lahknevused = vorrle(arve, lisa)
        assert len(lahknevused) == 2

    def test_all_negative(self):
        arve, lisa = load("scenario_3_lisas_rohkem")
        for l in vorrle(arve, lisa):
            assert l.erinevus < 0, f"Kood {l.kood}: oodati lisas rohkem"

    def test_specific_discrepancies(self):
        arve, lisa = load("scenario_3_lisas_rohkem")
        by_kood = {l.kood: l for l in vorrle(arve, lisa)}
        assert by_kood["66101"].erinevus == -2  # 3 arves, 5 lisas
        assert by_kood["66102"].erinevus == -1  # 2 arves, 3 lisas
