"""Unit tests for patient-level reconciliation logic."""
from pathlib import Path

from claim.parsers.lisa_json import parse_appendix_json
from claim.parsers.tk_json import parse_tk_json
from claim.matching import reconcile

SYNTHETIC = Path(__file__).parent / "synthetic"


def load(scenario: str):
    base = SYNTHETIC / scenario
    appendix = parse_appendix_json(base / "lisa.json")
    tk = parse_tk_json(base / "tk.json")
    return appendix, tk


class TestScenario1FullMatch:
    def test_no_discrepancies(self):
        appendix, tk = load("scenario_1_taielik_vaste")
        assert reconcile(appendix, tk) == []


class TestScenario2AppendixHasMore:
    def test_discrepancies_exist(self):
        appendix, tk = load("scenario_2_arves_rohkem")
        discs = reconcile(appendix, tk)
        assert len(discs) > 0

    def test_missing_entries_flagged(self):
        appendix, tk = load("scenario_2_arves_rohkem")
        discs = reconcile(appendix, tk)
        # P002/66101 is in appendix but not in TK
        match = next((d for d in discs if d.patient_id == "P002" and d.service_code == "66101"), None)
        assert match is not None
        assert match.tk_qty == 0
        assert match.difference == 2

    def test_qty_mismatch_flagged(self):
        appendix, tk = load("scenario_2_arves_rohkem")
        discs = reconcile(appendix, tk)
        # P003/66102: appendix=2, TK=1
        match = next((d for d in discs if d.patient_id == "P003" and d.service_code == "66102"), None)
        assert match is not None
        assert match.appendix_qty == 2
        assert match.tk_qty == 1
        assert match.difference == 1

    def test_financial_impact_positive(self):
        appendix, tk = load("scenario_2_arves_rohkem")
        total = sum(d.financial_diff_eur for d in reconcile(appendix, tk))
        assert total > 0


class TestScenario3TKHasMore:
    def test_discrepancies_exist(self):
        appendix, tk = load("scenario_3_lisas_rohkem")
        assert len(reconcile(appendix, tk)) > 0

    def test_tk_excess_flagged(self):
        appendix, tk = load("scenario_3_lisas_rohkem")
        discs = reconcile(appendix, tk)
        # P003/66101: appendix=1, TK=3
        match = next((d for d in discs if d.patient_id == "P003" and d.service_code == "66101"), None)
        assert match is not None
        assert match.difference == -2

    def test_financial_impact_negative(self):
        appendix, tk = load("scenario_3_lisas_rohkem")
        appendix_driven = [d for d in reconcile(appendix, tk) if d.referral_nr != "—"]
        assert all(d.difference < 0 for d in appendix_driven)
