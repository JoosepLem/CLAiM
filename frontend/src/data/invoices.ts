// Dashboard sample data — partner invoices aggregated across the practice.
// Replace with data from your backend; the shapes are what the UI expects.

export type InvoiceStatus = 'review' | 'reconciled' | 'processing';

export interface InvoiceSummary {
  id: string;
  partner: string;
  invoiceNo: string;
  /** Service period as "YYYY-MM". */
  period: string;
  uploaded: string;
  lines: number | null;
  /** Discrepancies left (open). */
  disc: number | null;
  /** Money at risk in euros. */
  risk: number | null;
  status: InvoiceStatus;
}

export const PARTNERS = ['Synlab', 'PERH', 'Medicover', 'Quattromed'] as const;

export const MONOGRAM: Record<string, string> = {
  Synlab: 'SY',
  PERH: 'PE',
  Medicover: 'ME',
  Quattromed: 'QM',
};

export const INVOICES: InvoiceSummary[] = [
  { id: 'i1', partner: 'Synlab',     invoiceNo: 'SYN-05-2026',  period: '2026-05', uploaded: '01 Jun 2026', lines: 847,  disc: 14, risk: 451.5,  status: 'review' },
  { id: 'i2', partner: 'PERH',       invoiceNo: 'PERH-2026-05', period: '2026-05', uploaded: '02 Jun 2026', lines: 1204, disc: 4,  risk: 312.4,  status: 'review' },
  { id: 'i3', partner: 'Medicover',  invoiceNo: 'MED-05-2026',  period: '2026-05', uploaded: '03 Jun 2026', lines: 312,  disc: 1,  risk: 48.0,   status: 'review' },
  { id: 'i4', partner: 'Quattromed', invoiceNo: 'QM-2026-05',   period: '2026-05', uploaded: '02 Jun 2026', lines: 188,  disc: 0,  risk: 0,      status: 'reconciled' },
  { id: 'i5', partner: 'Synlab',     invoiceNo: 'SYN-04-2026',  period: '2026-04', uploaded: '02 May 2026', lines: 821,  disc: 0,  risk: 0,      status: 'reconciled' },
  { id: 'i6', partner: 'PERH',       invoiceNo: 'PERH-2026-04', period: '2026-04', uploaded: '03 May 2026', lines: 1150, disc: 3,  risk: 201.1,  status: 'review' },
  { id: 'i7', partner: 'Medicover',  invoiceNo: 'MED-04-2026',  period: '2026-04', uploaded: '02 May 2026', lines: 298,  disc: 1,  risk: 33.0,   status: 'review' },
  { id: 'i8', partner: 'Synlab',     invoiceNo: 'SYN-06-2026',  period: '2026-06', uploaded: '18 Jun 2026', lines: null, disc: null, risk: null, status: 'processing' },
  { id: 'i9', partner: 'PERH',       invoiceNo: 'PERH-2026-06', period: '2026-06', uploaded: '19 Jun 2026', lines: null, disc: null, risk: null, status: 'processing' },
];

export interface DateRangePreset {
  label: string;
  from: string;
  to: string;
}

// "Today" in the sample context is 2026-06-20.
export const PRESETS: DateRangePreset[] = [
  { label: 'Last month',    from: '2026-05-01', to: '2026-05-31' },
  { label: 'This month',    from: '2026-06-01', to: '2026-06-30' },
  { label: 'Last 3 months', from: '2026-04-01', to: '2026-06-30' },
  { label: 'Year to date',  from: '2026-01-01', to: '2026-06-20' },
  { label: 'All time',      from: '1900-01-01', to: '2999-12-31' },
];

export const DEFAULT_RANGE = { from: '2026-05-01', to: '2026-05-31' };
