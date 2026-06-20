from __future__ import annotations
from pydantic import BaseModel


class InvoiceRow(BaseModel):
    service_code: str
    description: str
    quantity: float
    unit_price: float
    discount_pct: float
    amount: float


class Invoice(BaseModel):
    invoice_nr: str
    invoice_date: str
    partner: str
    payer: str
    rows: list[InvoiceRow]

    def qty_by_code(self) -> dict[str, float]:
        result: dict[str, float] = {}
        for r in self.rows:
            result[r.service_code] = result.get(r.service_code, 0.0) + r.quantity
        return result

    def description_by_code(self) -> dict[str, str]:
        result: dict[str, str] = {}
        for r in self.rows:
            if r.service_code not in result:
                result[r.service_code] = r.description
        return result

    def price_by_code(self) -> dict[str, float]:
        result: dict[str, float] = {}
        for r in self.rows:
            if r.service_code not in result:
                result[r.service_code] = r.unit_price
        return result


class ServiceRow(BaseModel):
    service: str
    code: str
    quantity: int
    unit_price: float
    amount: float


class Referral(BaseModel):
    referral_nr: str
    patient_id: str
    services: list[ServiceRow]


class Appendix(BaseModel):
    invoice_nr: str
    referrals: list[Referral]

    def qty_by_code(self) -> dict[str, int]:
        result: dict[str, int] = {}
        for ref in self.referrals:
            for s in ref.services:
                result[s.code] = result.get(s.code, 0) + s.quantity
        return result

    def description_by_code(self) -> dict[str, str]:
        result: dict[str, str] = {}
        for ref in self.referrals:
            for s in ref.services:
                if s.code not in result:
                    result[s.code] = s.service
        return result


class TKRecord(BaseModel):
    patient_id: str
    service_code: str
    date: str
    status: str  # "open" or "sent" — both count as valid matches
    quantity: int
    amount: float


class TKData(BaseModel):
    period_start: str
    period_end: str
    records: list[TKRecord]

    def qty_by_patient_and_code(self) -> dict[tuple[str, str], int]:
        result: dict[tuple[str, str], int] = {}
        for r in self.records:
            key = (r.patient_id, r.service_code)
            result[key] = result.get(key, 0) + r.quantity
        return result


class Discrepancy(BaseModel):
    patient_id: str
    referral_nr: str
    service_code: str
    description: str
    appendix_qty: int    # what partner's appendix says
    tk_qty: int          # what TK has (0 if missing)
    # positive = appendix has more than TK, negative = TK has more than appendix
    difference: int
    unit_price: float
    financial_diff_eur: float
