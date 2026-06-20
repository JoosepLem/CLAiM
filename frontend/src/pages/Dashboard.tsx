import { useMemo, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Upload, ChevronDown, ArrowRight, Check, X, Info, Trash2 } from 'lucide-react';
import Wordmark from '../components/Wordmark';
import {
  INVOICES, PARTNERS, MONOGRAM, PRESETS, DEFAULT_RANGE,
  type InvoiceSummary, type InvoiceStatus,
} from '../data/invoices';
import { money, num, rangeLabel, periodInRange, MONTHS } from '../lib/format';

const STATUS: Record<InvoiceStatus, { label: string; pill: string; dot: string }> = {
  review:     { label: 'Needs review', pill: 'bg-[#F3EBD7] text-[#8A6A1E]', dot: 'bg-gold' },
  reconciled: { label: 'Reconciled',   pill: 'bg-[#E6EFE8] text-[#3C7355]', dot: 'bg-[#3C7355]' },
  processing: { label: 'Reconciling',  pill: 'bg-[#E9EBF3] text-[#4A5A8C]', dot: 'bg-[#4A5A8C]' },
};

const microLabel = 'font-mono text-[10px] tracking-[0.06em] uppercase text-[#9A9484]';
const fieldBtn =
  'inline-flex items-center gap-2.5 bg-cream-card border border-[#D9D2C0] rounded-[10px] ' +
  'px-3.5 py-2.5 text-[13.5px] font-medium text-ink cursor-pointer';

function menuItemCls(selected: boolean) {
  return (
    'flex items-center gap-2.5 px-2.5 py-2 rounded-lg text-[13.5px] cursor-pointer ' +
    (selected ? 'bg-[#F6EFDD] text-ink font-semibold' : 'text-[#4A5568] hover:bg-[#F6F3EA]')
  );
}

const periodLabelOf = (period: string) => {
  const [y, m] = period.split('-').map(Number);
  return `${MONTHS[m - 1]} ${y}`;
};

const STORAGE_KEY = 'claim_invoices';

function loadInvoices(): InvoiceSummary[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [...INVOICES];
    return JSON.parse(raw) as InvoiceSummary[];
  } catch {
    return [...INVOICES];
  }
}

function saveInvoices(list: InvoiceSummary[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(list));
}

export default function Dashboard() {
  const navigate = useNavigate();
  const [partner, setPartner] = useState('all');
  const [from, setFrom] = useState(DEFAULT_RANGE.from);
  const [to, setTo] = useState(DEFAULT_RANGE.to);
  const [draftFrom, setDraftFrom] = useState(DEFAULT_RANGE.from);
  const [draftTo, setDraftTo] = useState(DEFAULT_RANGE.to);
  const [openMenu, setOpenMenu] = useState<'partner' | 'period' | null>(null);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [uploadedFile, setUploadedFile] = useState<File | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [invoices, setInvoices] = useState<InvoiceSummary[]>(loadInvoices);

  const filtered = useMemo(() => {
    const rank = (i: InvoiceSummary) =>
      i.status === 'review' ? 0 : i.status === 'processing' ? 1 : 2;
    return invoices.filter(
      (i) => (partner === 'all' || i.partner === partner) && periodInRange(i.period, from, to),
    ).sort((a, b) => rank(a) - rank(b) || (b.risk ?? 0) - (a.risk ?? 0));
  }, [partner, from, to, invoices]);

  const agg = useMemo(() => {
    return {
      money: filtered.reduce((s, i) => s + (i.risk ?? 0), 0),
      disc: filtered.reduce((s, i) => s + (i.disc ?? 0), 0),
      needsReview: filtered.filter((i) => i.status === 'review').length,
      reconciled: filtered.filter((i) => i.status === 'reconciled').length,
      processing: filtered.filter((i) => i.status === 'processing').length,
    };
  }, [filtered]);

  const periodLabel = rangeLabel(from, to);
  const filtersActive =
    partner !== 'all' || from !== DEFAULT_RANGE.from || to !== DEFAULT_RANGE.to;

  const openPeriod = () => {
    setDraftFrom(from);
    setDraftTo(to);
    setOpenMenu((m) => (m === 'period' ? null : 'period'));
  };
  const applyRange = () => {
    let a = draftFrom || '1900-01-01';
    let b = draftTo || '2999-12-31';
    if (a > b) [a, b] = [b, a];
    setFrom(a);
    setTo(b);
    setDraftFrom(a);
    setDraftTo(b);
    setOpenMenu(null);
  };
  const reset = () => {
    setPartner('all');
    setFrom(DEFAULT_RANGE.from);
    setTo(DEFAULT_RANGE.to);
    setOpenMenu(null);
  };

  const deleteInvoice = (id: string) => {
    setInvoices((prev) => {
      const next = prev.filter((i) => i.id !== id);
      saveInvoices(next);
      return next;
    });
  };

  const addInvoiceAndReconcile = () => {
    const partner = PARTNERS[Math.floor(Math.random() * PARTNERS.length)];
    const newInvoice: InvoiceSummary = {
      id: 'u' + Date.now(),
      partner,
      invoiceNo: partner === 'Synlab' ? 'SYN-05-2026' : partner === 'PERH' ? 'PERH-2026-05' : partner === 'Medicover' ? 'MED-05-2026' : 'QM-2026-05',
      period: '2026-05',
      uploaded: new Date().toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' }),
      lines: Math.floor(Math.random() * 800 + 200),
      disc: Math.floor(Math.random() * 15) + 2,
      risk: Math.round((Math.random() * 400 + 50) * 10) / 10,
      status: 'review',
    };
    const updated = [newInvoice, ...invoices];
    setInvoices(updated);
    saveInvoices(updated);
    setUploadOpen(false);
    navigate('/reconciling', { state: { fileName: uploadedFile?.name ?? 'partner_invoice.pdf' } });
  };

  const partnerOptions = [{ val: 'all', label: 'All partners' }, ...PARTNERS.map((p) => ({ val: p, label: p }))];

  return (
    <div className="min-h-screen bg-cream font-sans text-ink px-6 pt-9 pb-16">
      <div className="max-w-[1160px] mx-auto">
        {openMenu && <div className="fixed inset-0 z-40" onClick={() => setOpenMenu(null)} />}

        {/* Nav */}
        <div className="flex items-center justify-between px-0.5 pb-[26px]">
          <Wordmark className="text-[25px]" />
          <div className="flex items-center gap-3.5">
            <span className="text-[13px] text-muted">Reconciliation</span>
            <div className="w-[34px] h-[34px] rounded-full bg-ink text-cream-card flex items-center justify-center text-[12px] font-semibold font-mono">
              AK
            </div>
          </div>
        </div>

        {/* Header */}
        <div className="flex items-end justify-between gap-6 mb-[22px]">
          <div>
            <h1 className="text-[29px] font-bold tracking-[-0.02em]">Invoices</h1>
            <p className="text-[14.5px] text-muted mt-1">
              Reconcile partner invoices against patient claims, all in one place.
            </p>
          </div>
          <button
            onClick={() => { setUploadOpen(true); setOpenMenu(null); }}
            className="inline-flex items-center gap-2 bg-ink text-cream-card rounded-[10px] px-[18px] py-3 text-[14px] font-semibold whitespace-nowrap hover:bg-[#2C3E4F] transition-colors"
          >
            <Upload size={16} /> Upload invoice
          </button>
        </div>

        {/* Filters */}
        <div className="flex items-center justify-between gap-4 mb-4 flex-wrap">
          <div className="flex items-center gap-2.5">
            {/* Partner */}
            <div className="relative z-50">
              <button className={fieldBtn} onClick={() => setOpenMenu((m) => (m === 'partner' ? null : 'partner'))}>
                <span className={microLabel}>Partner</span>
                {partner === 'all' ? 'All partners' : partner}
                <ChevronDown size={13} className="text-[#9A9484]" />
              </button>
              {openMenu === 'partner' && (
                <div className="absolute top-[calc(100%_+_6px)] left-0 min-w-[210px] bg-white border border-line rounded-xl shadow-[0_12px_30px_-12px_rgba(40,30,10,0.3)] p-1.5 z-50">
                  {partnerOptions.map((o) => (
                    <div key={o.val} className={menuItemCls(partner === o.val)} onClick={() => { setPartner(o.val); setOpenMenu(null); }}>
                      {o.label}
                      {partner === o.val && <Check size={14} className="ml-auto text-gold" />}
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Period (date range) */}
            <div className="relative z-50">
              <button className={fieldBtn} onClick={openPeriod}>
                <span className={microLabel}>Period</span>
                {periodLabel}
                <ChevronDown size={13} className="text-[#9A9484]" />
              </button>
              {openMenu === 'period' && (
                <div className="absolute top-[calc(100%_+_6px)] left-0 w-[300px] bg-white border border-line rounded-xl shadow-[0_12px_30px_-12px_rgba(40,30,10,0.3)] p-2.5 z-50">
                  <div className={`${microLabel} px-[7px] pt-1 pb-1.5`}>Quick ranges</div>
                  {PRESETS.map((p) => {
                    const sel = from === p.from && to === p.to;
                    return (
                      <div key={p.label} className={menuItemCls(sel)} onClick={() => { setFrom(p.from); setTo(p.to); setDraftFrom(p.from); setDraftTo(p.to); setOpenMenu(null); }}>
                        {p.label}
                        {sel && <Check size={14} className="ml-auto text-gold" />}
                      </div>
                    );
                  })}
                  <div className="h-px bg-line my-2 mx-1" />
                  <div className={`${microLabel} px-[7px] pt-0.5 pb-2`}>Custom range</div>
                  <div className="flex gap-2 px-[5px]">
                    <label className="flex-1 block">
                      <span className="block text-[11px] text-muted mb-1">From</span>
                      <input
                        type="date"
                        value={draftFrom}
                        onChange={(e) => setDraftFrom(e.target.value)}
                        className="w-full font-sans text-[13px] text-ink border border-[#D9D2C0] rounded-lg px-2.5 py-2 bg-cream-card"
                      />
                    </label>
                    <label className="flex-1 block">
                      <span className="block text-[11px] text-muted mb-1">To</span>
                      <input
                        type="date"
                        value={draftTo}
                        onChange={(e) => setDraftTo(e.target.value)}
                        className="w-full font-sans text-[13px] text-ink border border-[#D9D2C0] rounded-lg px-2.5 py-2 bg-cream-card"
                      />
                    </label>
                  </div>
                  <button onClick={applyRange} className="w-[calc(100%_-_10px)] mx-[5px] mt-2.5 mb-1 bg-ink text-cream-card rounded-[9px] py-2.5 text-[13.5px] font-semibold">
                    Apply range
                  </button>
                </div>
              )}
            </div>

            {filtersActive && (
              <button onClick={reset} className="text-[13px] text-gold font-semibold px-1 py-2">
                Reset
              </button>
            )}
          </div>
          <div className="text-[13px] text-muted">
            {filtered.length} invoices · {periodLabel}
          </div>
        </div>

        {/* KPIs */}
        <div className="grid grid-cols-[1.25fr_1fr_1fr] gap-3.5 mb-5">
          <div className="bg-[#FBF1EC] border border-[#EBD7CC] rounded-[13px] px-5 py-[17px]">
            <div className="font-mono text-[10.5px] tracking-[0.07em] uppercase text-danger">Money at risk</div>
            <div className="font-mono text-[32px] font-semibold text-danger mt-1.5 tracking-[-0.01em]">{money(agg.money)}</div>
            <div className="text-[13px] text-[#A06B52] mt-0.5">across {agg.disc} open discrepancies</div>
          </div>
          <div className="bg-cream-card border border-line rounded-[13px] px-5 py-[17px]">
            <div className="font-mono text-[10.5px] tracking-[0.07em] uppercase text-[#9A9484]">Discrepancies left</div>
            <div className="font-mono text-[32px] font-semibold text-ink mt-1.5 tracking-[-0.01em]">{agg.disc}</div>
            <div className="text-[13px] text-muted mt-0.5">{agg.needsReview} invoices need review</div>
          </div>
          <div className="bg-cream-card border border-line rounded-[13px] px-5 py-[17px]">
            <div className="font-mono text-[10.5px] tracking-[0.07em] uppercase text-[#9A9484]">Invoices in period</div>
            <div className="font-mono text-[32px] font-semibold text-ink mt-1.5 tracking-[-0.01em]">{filtered.length}</div>
            <div className="text-[13px] text-muted mt-0.5">{agg.reconciled} reconciled · {agg.processing} processing</div>
          </div>
        </div>

        {/* Table */}
        <div className="bg-cream-card border border-line rounded-[14px] shadow-[0_1px_3px_rgba(40,30,10,0.06),0_14px_30px_-20px_rgba(40,30,10,0.2)] overflow-hidden">
          <div className="grid grid-cols-[1.7fr_150px_84px_132px_132px_142px_22px] gap-3.5 px-[22px] py-3.5 bg-[#F3EFE4] border-b border-[#E6DFCF] font-mono text-[10px] tracking-[0.06em] uppercase text-[#9A9484]">
            <span>Partner / Invoice</span>
            <span>Period</span>
            <span className="text-right">Lines</span>
            <span className="text-center">Discrepancies</span>
            <span className="text-right">Money at risk</span>
            <span>Status</span>
            <span />
          </div>

          {filtered.map((inv) => {
            const st = STATUS[inv.status];
            const clickable = inv.status !== 'processing';
            const disc =
              inv.status === 'processing'
                ? { text: '—', cls: 'bg-[#EEEADD] text-[#A89F88]' }
                : (inv.disc ?? 0) > 0
                ? { text: `${inv.disc} open`, cls: 'bg-[#F4E7E0] text-danger' }
                : { text: '✓ Clear', cls: 'bg-[#E6EFE8] text-[#3C7355]' };
            const riskCell =
              inv.status === 'processing'
                ? { text: '—', cls: 'text-[#B5AF9E]' }
                : (inv.risk ?? 0) > 0
                ? { text: money(inv.risk as number), cls: 'text-danger' }
                : { text: '—', cls: 'text-[#B5AF9E]' };
            return (
              <div
                key={inv.id}
                onClick={clickable ? () => navigate(`/invoices/${inv.id}`) : undefined}
                className={`grid grid-cols-[1.7fr_150px_84px_132px_132px_142px_22px] gap-3.5 px-[22px] py-[15px] border-b border-[#F0EADC] items-center group ${clickable ? 'cursor-pointer hover:bg-[#F6F2E8]' : 'cursor-default'}`}
              >
                <div className="flex items-center gap-3 min-w-0">
                  <div className="w-[38px] h-[38px] rounded-[9px] bg-ink text-cream-card flex items-center justify-center font-bold text-[12px] tracking-[0.04em] shrink-0">
                    {MONOGRAM[inv.partner] ?? inv.partner.slice(0, 2).toUpperCase()}
                  </div>
                  <div className="min-w-0">
                    <div className="text-[14.5px] font-semibold">{inv.partner}</div>
                    <div className="text-[12px] text-[#9A9484] font-mono mt-px">{inv.invoiceNo}</div>
                  </div>
                </div>
                <div>
                  <div className="text-[13.5px]">{periodLabelOf(inv.period)}</div>
                  <div className="text-[11.5px] text-[#9A9484] mt-px">uploaded {inv.uploaded}</div>
                </div>
                <div className="text-right font-mono text-[13.5px] text-[#6E6A5E]">
                  {inv.lines == null ? '—' : num(inv.lines)}
                </div>
                <div className="text-center">
                  <span className={`inline-block font-mono text-[12px] font-semibold px-2.5 py-1 rounded-[7px] whitespace-nowrap ${disc.cls}`}>
                    {disc.text}
                  </span>
                </div>
                <div className={`text-right font-mono text-[14.5px] font-semibold ${riskCell.cls}`}>
                  {riskCell.text}
                </div>
                <div>
                  <span className={`inline-flex items-center gap-1.5 text-[12px] font-semibold px-[11px] py-[5px] rounded-full whitespace-nowrap ${st.pill}`}>
                    <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${st.dot}`} />
                    {st.label}
                  </span>
                </div>
                <div className="text-right text-[#C2B89F] flex items-center justify-end gap-0.5">
                  <button
                    onClick={(e) => { e.stopPropagation(); deleteInvoice(inv.id); }}
                    className="p-1 opacity-0 group-hover:opacity-100 hover:text-danger hover:bg-[#F4E7E0] rounded-md transition-all"
                    title="Delete invoice"
                  >
                    <Trash2 size={14} />
                  </button>
                  {clickable && <ArrowRight size={15} className="group-hover:opacity-0 transition-opacity" />}
                </div>
              </div>
            );
          })}

          {filtered.length === 0 && (
            <div className="py-[54px] px-6 text-center">
              <div className="text-[15px] text-[#6E6A5E] font-semibold">No invoices match these filters</div>
              <div className="text-[13.5px] text-[#9A9484] mt-1.5">Try a different partner or period, or upload a new invoice.</div>
            </div>
          )}
        </div>

        {/* Footer brand */}
        <div className="flex items-center gap-2 justify-center mt-[26px] opacity-70">
          <Wordmark className="text-[14px]" />
          <span className="text-[12px] text-[#9A9484] font-mono">Automated invoice reconciliation</span>
        </div>
      </div>

      {/* Upload modal */}
      {uploadOpen && (
        <div className="fixed inset-0 bg-[rgba(28,22,10,0.42)] z-[60] flex items-center justify-center p-6" onClick={() => setUploadOpen(false)}>
          <div className="w-[540px] max-w-full bg-cream-card border border-line rounded-2xl shadow-[0_30px_70px_-20px_rgba(20,14,4,0.5)] overflow-hidden" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between px-[22px] py-5 border-b border-[#ECE6D8]">
              <div>
                <div className="text-[18px] font-bold">Upload partner invoice</div>
                <div className="text-[13px] text-muted mt-0.5">We'll detect the partner and period automatically.</div>
              </div>
              <button onClick={() => setUploadOpen(false)} className="text-[#9A9484] p-1"><X size={20} /></button>
            </div>
            <div className="p-[22px]">
              <div className="border-2 border-dashed border-[#D7C9A8] bg-[#F6EFDD] rounded-xl px-6 py-[38px] text-center">
                <div className="w-12 h-12 rounded-xl bg-ink text-cream-card flex items-center justify-center mx-auto mb-3.5">
                  <Upload size={22} />
                </div>
                {uploadedFile ? (
                  <div>
                    <div className="text-[14px] font-semibold truncate max-w-[420px] mx-auto">{uploadedFile.name}</div>
                    <div className="text-[12px] text-muted mt-1 font-mono">{(uploadedFile.size / 1024).toFixed(0)} KB</div>
                    <button onClick={() => setUploadedFile(null)} className="text-[11.5px] text-gold font-semibold mt-2 underline">Choose different file</button>
                  </div>
                ) : (
                  <>
                    <div className="text-[15px] font-semibold">Drag &amp; drop the partner PDF here</div>
                    <div className="text-[13px] text-muted mt-1">
                      or{' '}
                      <span
                        className="text-gold font-semibold underline cursor-pointer"
                        onClick={() => fileInputRef.current?.click()}
                      >
                        browse your files
                      </span>
                    </div>
                    <div className="text-[11.5px] text-[#A89F88] mt-3 font-mono">PDF · up to 25 MB</div>
                  </>
                )}
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".pdf,application/pdf"
                  className="hidden"
                  onChange={(e) => { const f = e.target.files?.[0]; if (f) setUploadedFile(f); }}
                />
              </div>
              <div className="flex items-center gap-2.5 mt-4 bg-white border border-[#ECE6D8] rounded-[10px] px-3.5 py-[11px]">
                <Info size={15} className="text-[#6E6A5E] shrink-0" />
                <span className="text-[13px] text-[#6E6A5E]">Reconciliation against patient claims starts as soon as the upload finishes.</span>
              </div>
            </div>
            <div className="flex items-center justify-end gap-2.5 px-[22px] py-4 border-t border-[#ECE6D8] bg-cream-panel">
              <button onClick={() => setUploadOpen(false)} className="bg-white border border-[#D9D2C0] rounded-[9px] px-4 py-2.5 text-[13.5px] font-semibold text-[#4A5568]">Cancel</button>
              <button onClick={addInvoiceAndReconcile} className="bg-ink text-cream-card rounded-[9px] px-[18px] py-2.5 text-[13.5px] font-semibold">Start reconciliation</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
