"""Parser for TK (Health Insurance) data in JSON format.

Handles two formats:
  - Real TK format: list of patient invoices with nested 'teenused', 'patsient.isikukood', integer 'kood'
  - Synthetic flat format (unit tests): {"period_start": ..., "period_end": ..., "records": [...]}
"""
from __future__ import annotations
import json
from pathlib import Path

from ..models import TKData, TKRecord


def parse_tk_json(path: str | Path) -> TKData:
    data = json.loads(Path(path).read_text(encoding="utf-8"))

    # Synthetic flat format used in unit tests
    if isinstance(data, dict):
        return TKData.model_validate(data)

    # Real TK format: list of patient invoices
    records: list[TKRecord] = []
    dates: list[str] = []

    for invoice in data:
        patient_id = invoice["patsient"]["isikukood"]
        invoice_date = invoice.get("arve_kuupaev", "")
        if invoice_date:
            dates.append(invoice_date)
        status = invoice.get("saatmise_staatus", invoice.get("staatus", ""))
        for service in invoice.get("teenused", []):
            records.append(TKRecord(
                patient_id=str(patient_id),
                service_code=str(service["kood"]),
                date=invoice_date,
                status=status,
                quantity=int(service.get("kogus", 1)),
                amount=float(service.get("hind", 0.0)),
            ))

    dates.sort()
    return TKData(
        period_start=dates[0] if dates else "",
        period_end=dates[-1] if dates else "",
        records=records,
    )
