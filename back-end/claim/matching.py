from __future__ import annotations
from collections import defaultdict
from .models import Appendix, TKData, Discrepancy


def reconcile(appendix: Appendix, tk_data: TKData) -> list[Discrepancy]:
    """
    Matches partner appendix services against TK records by (patient_id, service_code).

    Aggregates quantities across all referrals — a patient can have the same billing code
    in multiple referrals or multiple service rows within one referral (different test names
    mapped to the same code). The comparison is always total appendix qty vs total TK qty.

    Flags:
    - Service in appendix not found in TK → missing from TK
    - Service found in TK but total quantity differs → quantity mismatch
    - TK has a (patient_id, service_code) not present in appendix → only in TK
    """
    tk_qty = tk_data.qty_by_patient_and_code()

    # Aggregate appendix quantities by (patient_id, service_code)
    appendix_qty: dict[tuple[str, str], int] = defaultdict(int)
    # Keep first-seen metadata for reporting
    appendix_meta: dict[tuple[str, str], tuple[str, str, float]] = {}  # → (referral_nr, description, unit_price)

    for referral in appendix.referrals:
        for service in referral.services:
            key = (referral.patient_id, service.code)
            appendix_qty[key] += service.quantity
            if key not in appendix_meta:
                appendix_meta[key] = (referral.referral_nr, service.service, service.unit_price)

    discrepancies: list[Discrepancy] = []

    for key, a_qty in appendix_qty.items():
        patient_id, service_code = key
        t_qty = tk_qty.get(key, 0)
        if a_qty == t_qty:
            continue
        referral_nr, description, unit_price = appendix_meta[key]
        diff = a_qty - t_qty
        discrepancies.append(Discrepancy(
            patient_id=patient_id,
            referral_nr=referral_nr,
            service_code=service_code,
            description=description,
            appendix_qty=a_qty,
            tk_qty=t_qty,
            difference=diff,
            unit_price=unit_price,
            financial_diff_eur=round(diff * unit_price, 2),
        ))

    # Codes that appear anywhere in this appendix — defines the scope for this partner
    appendix_codes = {service_code for _, service_code in appendix_qty}

    # Flag TK entries with no matching appendix entry, scoped to this partner's code space.
    # TK records with codes outside the appendix (e.g. hospital radiology codes when reconciling
    # a lab partner) belong to a different partner's reconciliation run and are skipped here.
    for (patient_id, service_code), qty in tk_qty.items():
        if (patient_id, service_code) not in appendix_qty and service_code in appendix_codes:
            discrepancies.append(Discrepancy(
                patient_id=patient_id,
                referral_nr="—",
                service_code=service_code,
                description="—",
                appendix_qty=0,
                tk_qty=qty,
                difference=-qty,
                unit_price=0.0,
                financial_diff_eur=0.0,
            ))

    return discrepancies
