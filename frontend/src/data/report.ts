// Per-invoice reconciliation sample data (Synlab, SYN-05-2026).
// In production this is fetched per invoice id; here it is a static sample.

export type DiscType = 'missing' | 'qty' | 'tk_only';

export interface Discrepancy {
  id: string;
  type: DiscType;
  /** Date of service, e.g. "02 May". */
  dos: string;
  /** Patient reference id. */
  patient: string;
  code: string;
  name: string;
  price: number;
  /** Partner quantity. */
  pq: number;
  /** Patient-invoice quantity. */
  ptq: number;
  page: number;
  line: number;
}

export interface MatchedLine {
  id: string;
  dos: string;
  patient: string;
  code: string;
  name: string;
  price: number;
  pq: number;
  line: number;
}

export const REPORT_META = {
  partner: 'Synlab',
  invoiceNo: 'SYN-05-2026',
  period: 'May 2026',
  uploaded: '01 Jun 2026',
  totalLines: 847,
  /** Lines that matched on the first pass (before any manual resolves). */
  baselineReconciled: 833,
};

export const DISCREPANCIES: Discrepancy[] = [
  { id: 'D1',  type: 'missing', dos: '02 May', patient: 'P-10482', code: 'VITD-25', name: 'Vitamin D, 25-Hydroxy',            price: 47.2,  pq: 1, ptq: 0, page: 1, line: 42 },
  { id: 'D2',  type: 'missing', dos: '03 May', patient: 'P-10515', code: 'CMP-140', name: 'Comprehensive Metabolic Panel',   price: 39.0,  pq: 1, ptq: 0, page: 1, line: 58 },
  { id: 'D3',  type: 'qty',     dos: '05 May', patient: 'P-10533', code: 'LP-2010', name: 'Lipid Panel',                     price: 42.0,  pq: 2, ptq: 1, page: 2, line: 119 },
  { id: 'D4',  type: 'missing', dos: '06 May', patient: 'P-10547', code: 'HCV-510', name: 'Hepatitis C Antibody',            price: 52.0,  pq: 1, ptq: 0, page: 2, line: 147 },
  { id: 'D5',  type: 'qty',     dos: '08 May', patient: 'P-10562', code: 'URC-005', name: 'Urinalysis, Complete',            price: 12.5,  pq: 2, ptq: 1, page: 2, line: 188 },
  { id: 'D6',  type: 'missing', dos: '09 May', patient: 'P-10579', code: 'TSH-330', name: 'Thyroid Stimulating Hormone',     price: 33.75, pq: 1, ptq: 0, page: 3, line: 206 },
  { id: 'D7',  type: 'qty',     dos: '12 May', patient: 'P-10588', code: 'CBC-100', name: 'Complete Blood Count',            price: 18.0,  pq: 3, ptq: 2, page: 3, line: 241 },
  { id: 'D8',  type: 'missing', dos: '13 May', patient: 'P-10604', code: 'PSA-080', name: 'Prostate Specific Antigen',       price: 36.4,  pq: 1, ptq: 0, page: 3, line: 267 },
  { id: 'D9',  type: 'missing', dos: '15 May', patient: 'P-10622', code: 'FER-210', name: 'Ferritin',                       price: 24.9,  pq: 1, ptq: 0, page: 4, line: 301 },
  { id: 'D10', type: 'qty',     dos: '16 May', patient: 'P-10641', code: 'A1C-440', name: 'Hemoglobin A1c',                  price: 28.5,  pq: 2, ptq: 1, page: 4, line: 330 },
  { id: 'D11', type: 'qty',     dos: '19 May', patient: 'P-10663', code: 'CRP-150', name: 'C-Reactive Protein',              price: 21.3,  pq: 2, ptq: 1, page: 4, line: 362 },
  { id: 'D12', type: 'missing', dos: '21 May', patient: 'P-10688', code: 'GLU-010', name: 'Glucose, Fasting',                price: 9.75,  pq: 1, ptq: 0, page: 5, line: 401 },
  { id: 'D13', type: 'missing', dos: '23 May', patient: 'P-10702', code: 'CMP-140', name: 'Comprehensive Metabolic Panel',   price: 39.0,  pq: 1, ptq: 0, page: 5, line: 438 },
  { id: 'D14', type: 'missing', dos: '26 May', patient: 'P-10729', code: 'VITD-25', name: 'Vitamin D, 25-Hydroxy',            price: 47.2,  pq: 1, ptq: 0, page: 6, line: 474 },
];

export const MATCHED: MatchedLine[] = [
  { id: 'M1', dos: '02 May', patient: 'P-10488', code: 'CBC-100', name: 'Complete Blood Count',           price: 18.0,  pq: 1, line: 31 },
  { id: 'M2', dos: '04 May', patient: 'P-10521', code: 'CMP-140', name: 'Comprehensive Metabolic Panel',  price: 39.0,  pq: 1, line: 96 },
  { id: 'M3', dos: '07 May', patient: 'P-10550', code: 'GLU-010', name: 'Glucose, Fasting',               price: 9.75,  pq: 1, line: 165 },
  { id: 'M4', dos: '10 May', patient: 'P-10581', code: 'TSH-330', name: 'Thyroid Stimulating Hormone',    price: 33.75, pq: 1, line: 223 },
  { id: 'M5', dos: '14 May', patient: 'P-10610', code: 'FER-210', name: 'Ferritin',                      price: 24.9,  pq: 1, line: 288 },
  { id: 'M6', dos: '17 May', patient: 'P-10648', code: 'LP-2010', name: 'Lipid Panel',                    price: 42.0,  pq: 1, line: 345 },
  { id: 'M7', dos: '20 May', patient: 'P-10675', code: 'A1C-440', name: 'Hemoglobin A1c',                 price: 28.5,  pq: 1, line: 380 },
  { id: 'M8', dos: '24 May', patient: 'P-10710', code: 'URC-005', name: 'Urinalysis, Complete',           price: 12.5,  pq: 1, line: 455 },
];

/** Money at risk for a single discrepancy (0 for tk_only entries). */
export function riskOf(d: Discrepancy): number {
  if (d.type === 'tk_only') return 0;
  return d.type === 'missing' ? d.price * d.pq : d.price * (d.pq - d.ptq);
}

export function noteOf(d: Discrepancy): string {
  if (d.type === 'missing') return 'Billed by the partner but never invoiced to the insurer.';
  if (d.type === 'tk_only') return 'TK has a record of this service but the partner did not include it in their invoice.';
  return `Partner billed ${d.pq}, patient invoice billed ${d.ptq}.`;
}

/** Three recent patient-invoice dates for a patient, ending on the service date. */
export function recentInvoices(dos: string): string[] {
  const M: Record<string, number> = {
    Jan: 0, Feb: 1, Mar: 2, Apr: 3, May: 4, Jun: 5,
    Jul: 6, Aug: 7, Sep: 8, Oct: 9, Nov: 10, Dec: 11,
  };
  const MN = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  const [dd, mon] = dos.split(' ');
  const base = new Date(2026, M[mon], parseInt(dd, 10));
  const fmt = (dt: Date) => `${String(dt.getDate()).padStart(2, '0')} ${MN[dt.getMonth()]}`;
  const a = new Date(base); a.setDate(a.getDate() - 16);
  const b = new Date(base); b.setDate(b.getDate() - 37);
  return [dos, fmt(a), fmt(b)];
}
