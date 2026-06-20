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
  { id: 'i1', partner: 'Synlab',     invoiceNo: 'SYN-06-2026',  period: '2026-06', uploaded: '01 Jun 2026', lines: 847,  disc: 14, risk: 451.5,  status: 'review' },
  { id: 'i2', partner: 'PERH',       invoiceNo: 'PERH-2026-06', period: '2026-06', uploaded: '02 Jun 2026', lines: 1204, disc: 4,  risk: 312.4,  status: 'review' },
  { id: 'i3', partner: 'Medicover',  invoiceNo: 'MED-06-2026',  period: '2026-06', uploaded: '03 Jun 2026', lines: 312,  disc: 1,  risk: 48.0,   status: 'review' },
  { id: 'i4', partner: 'Quattromed', invoiceNo: 'QM-2026-06',   period: '2026-06', uploaded: '02 Jun 2026', lines: 188,  disc: 0,  risk: 0,      status: 'reconciled' },
  { id: 'i5', partner: 'Synlab',     invoiceNo: 'SYN-05-2026',  period: '2026-05', uploaded: '02 May 2026', lines: 821,  disc: 0,  risk: 0,      status: 'reconciled' },
  { id: 'i6', partner: 'PERH',       invoiceNo: 'PERH-2026-05', period: '2026-05', uploaded: '03 May 2026', lines: 1150, disc: 3,  risk: 201.1,  status: 'review' },
  { id: 'i7', partner: 'Medicover',  invoiceNo: 'MED-05-2026',  period: '2026-05', uploaded: '02 May 2026', lines: 298,  disc: 1,  risk: 33.0,   status: 'review' },
];

export interface DateRangePreset {
  label: string;
  from: string;
  to: string;
}

function isoDate(d: Date) {
  return d.toISOString().slice(0, 10);
}

function buildPresets(): DateRangePreset[] {
  const now = new Date();
  const y = now.getFullYear();
  const m = now.getMonth(); // 0-indexed

  const thisMonthStart = isoDate(new Date(y, m, 1));
  const thisMonthEnd   = isoDate(new Date(y, m + 1, 0));

  const lastMonthStart = isoDate(new Date(y, m - 1, 1));
  const lastMonthEnd   = isoDate(new Date(y, m, 0));

  const threeMonthsAgo = isoDate(new Date(y, m - 2, 1));
  const ytdStart       = isoDate(new Date(y, 0, 1));
  const today          = isoDate(now);

  return [
    { label: 'This month',    from: thisMonthStart, to: thisMonthEnd },
    { label: 'Last month',    from: lastMonthStart, to: lastMonthEnd },
    { label: 'Last 3 months', from: threeMonthsAgo, to: thisMonthEnd },
    { label: 'Year to date',  from: ytdStart,       to: today },
    { label: 'All time',      from: '1900-01-01',   to: '2999-12-31' },
  ];
}

export const PRESETS: DateRangePreset[] = buildPresets();

const _now = new Date();
export const DEFAULT_RANGE = {
  from: isoDate(new Date(_now.getFullYear(), _now.getMonth(), 1)),
  to:   isoDate(new Date(_now.getFullYear(), _now.getMonth() + 1, 0)),
};
