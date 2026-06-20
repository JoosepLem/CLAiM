import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ArrowLeft, Download } from 'lucide-react';
import Wordmark from '../components/Wordmark';
import LanguageSwitcher from '../components/LanguageSwitcher';
import {
  DISCREPANCIES, MATCHED, REPORT_META,
  riskOf, recentInvoices, type Discrepancy,
} from '../data/report';
import { INVOICES } from '../data/invoices';
import { money, num } from '../lib/format';

const TYPE_CONFIG: Record<Discrepancy['type'], { color: string; bg: string }> = {
  missing: { color: '#B0492F', bg: '#F4E7E0' },
  qty: { color: '#8A6A1E', bg: '#F3EBD7' },
};

const microLabel = 'font-mono text-[10.5px] tracking-[0.07em] uppercase';

interface AllRow {
  id: string; dos: string; patient: string; code: string; name: string;
  qty: number; unit: string; total: string;
  statusType: 'missing' | 'qty' | 'matched'; flagged: boolean; barColor: string; line: number;
}

function statusBg(statusType: string) {
  if (statusType === 'missing') return '#F4E7E0';
  if (statusType === 'qty') return '#F3EBD7';
  return '#E6EFE8';
}
function statusColor(statusType: string) {
  if (statusType === 'missing') return '#B0492F';
  if (statusType === 'qty') return '#8A6A1E';
  return '#3C7355';
}

export default function ReconciliationReport() {
  const { t } = useTranslation();
  const { invoiceId } = useParams();
  const meta = INVOICES.find((i) => i.id === invoiceId);
  const partner = meta?.partner ?? REPORT_META.partner;
  const invoiceNo = meta?.invoiceNo ?? REPORT_META.invoiceNo;
  const uploaded = meta?.uploaded ?? REPORT_META.uploaded;

  const [resolved, setResolved] = useState<Set<string>>(new Set());
  const [expandedId, setExpandedId] = useState<string | null>('D1');
  const [tab, setTab] = useState<'disc' | 'all'>('disc');

  const toggleResolve = (id: string) =>
    setResolved((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });

  const kpi = useMemo(() => {
    const open = DISCREPANCIES.filter((d) => !resolved.has(d.id));
    const moneyAtRisk = open.reduce((s, d) => s + riskOf(d), 0);
    const remaining = open.length;
    const total = DISCREPANCIES.length;
    const resolvedCount = total - remaining;
    const reconLines = REPORT_META.baselineReconciled + resolvedCount;
    const reconPct = Math.round((reconLines / REPORT_META.totalLines) * 100);
    return { moneyAtRisk, remaining, total, resolvedCount, reconLines, reconPct };
  }, [resolved]);

  const orderedRows = useMemo<AllRow[]>(() => {
    const disc = DISCREPANCIES.map((d) => ({
      id: d.id, dos: d.dos, patient: d.patient, code: d.code, name: d.name,
      qty: d.pq, unit: money(d.price), total: money(d.price * d.pq),
      statusType: d.type, flagged: true, barColor: TYPE_CONFIG[d.type].color, line: d.line,
    }));
    const matched = MATCHED.map((m) => ({
      id: m.id, dos: m.dos, patient: m.patient, code: m.code, name: m.name,
      qty: m.pq, unit: money(m.price), total: money(m.price * m.pq),
      statusType: 'matched' as const, flagged: false, barColor: 'transparent', line: m.line,
    }));
    return [...disc, ...matched].sort((a, b) => a.line - b.line);
  }, []);

  const tabCls = (active: boolean) =>
    'inline-flex items-center gap-2 px-3.5 py-3 text-[14px] cursor-pointer border-b-2 ' +
    (active ? 'text-ink font-semibold border-gold' : 'text-muted font-medium border-transparent');

  return (
    <div className="min-h-screen bg-cream font-sans text-ink px-6 py-10">
      <div className="max-w-[1080px] mx-auto">
        <Link to="/" className="inline-flex items-center gap-1.5 text-[13.5px] font-semibold text-[#6E6A5E] hover:text-ink mb-3.5 ml-0.5 no-underline">
          <ArrowLeft size={15} /> {t('report.backToInvoices')}
        </Link>

        <div className="bg-cream-card border border-line rounded-[14px] shadow-[0_1px_3px_rgba(40,30,10,0.07),0_12px_28px_-16px_rgba(40,30,10,0.18)] overflow-hidden">
          {/* Top bar */}
          <div className="flex items-center justify-between px-6 py-4 border-b border-[#ECE6D8] bg-cream-panel">
            <div className="flex items-center gap-3.5">
              <Wordmark className="text-[21px]" />
              <div className="w-px h-5 bg-line" />
              <span className="text-[14px] text-[#6E6A5E] font-medium">{t('report.reconciliationReport')}</span>
            </div>
            <div className="flex items-center gap-2.5">
              <LanguageSwitcher />
              <span className="inline-flex items-center gap-1.5 bg-[#F3EBD7] text-[#8A6A1E] px-3 py-1.5 rounded-full font-semibold text-[12.5px]">
                <span className="w-[7px] h-[7px] rounded-full bg-gold inline-block" /> {t('report.needsReview')}
              </span>
            </div>
          </div>

          {/* Partner + meta */}
          <div className="flex items-start justify-between gap-6 px-6 pt-[22px] pb-5">
            <div className="flex-1">
              <div className="flex items-center gap-3.5">
                <div className="w-[46px] h-[46px] rounded-[11px] bg-ink text-cream-card flex items-center justify-center font-bold text-[15px] tracking-[0.04em]">
                  {partner.slice(0, 2).toUpperCase()}
                </div>
                <div>
                  <div className="text-[23px] font-bold tracking-[-0.015em] leading-none">{partner}</div>
                  <div className="text-[13px] text-muted mt-0.5">{t('report.laboratoryServices')}</div>
                </div>
              </div>
              <div className="flex gap-[34px] mt-5">
                {[
                  [t('report.invoiceNo'), invoiceNo],
                  [t('report.period'), REPORT_META.period],
                  [t('report.uploaded'), uploaded],
                  [t('report.lines'), String(REPORT_META.totalLines)],
                ].map(([k, v]) => (
                  <div key={k}>
                    <div className={`${microLabel} text-[#9A9484]`}>{k}</div>
                    <div className="text-[14px] font-mono mt-1">{v}</div>
                  </div>
                ))}
              </div>
            </div>
            <button className="inline-flex items-center gap-2 bg-ink text-cream-card rounded-[10px] px-[17px] py-[11px] text-[14px] font-semibold whitespace-nowrap hover:bg-[#2C3E4F] transition-colors">
              <Download size={15} /> {t('report.downloadPdf')}
            </button>
          </div>

          {/* KPIs */}
          <div className="grid grid-cols-[1.25fr_1fr_1fr] gap-3.5 px-6 pb-[22px]">
            <div className="bg-[#FBF1EC] border border-[#EBD7CC] rounded-xl px-[18px] py-[15px]">
              <div className={`${microLabel} text-danger`}>{t('report.moneyAtRisk')}</div>
              <div className="font-mono text-[30px] font-semibold text-danger mt-1.5 tracking-[-0.01em]">{money(kpi.moneyAtRisk)}</div>
              <div className="text-[12.5px] text-[#A06B52] mt-0.5">
                {t('report.acrossOpenDiscrepancies', { count: kpi.remaining })}
              </div>
            </div>
            <div className="bg-white border border-line rounded-xl px-[18px] py-[15px]">
              <div className={`${microLabel} text-[#9A9484]`}>{t('report.discrepanciesLeft')}</div>
              <div className="font-mono text-[30px] font-semibold mt-1.5 tracking-[-0.01em]">{kpi.remaining}</div>
              <div className="text-[12.5px] text-muted mt-0.5">
                {t('report.ofFlaggedWithResolved', { total: kpi.total, resolved: kpi.resolvedCount })}
              </div>
            </div>
            <div className="bg-white border border-line rounded-xl px-[18px] py-[15px]">
              <div className={`${microLabel} text-[#9A9484]`}>{t('report.linesReconciled')}</div>
              <div className="font-mono text-[30px] font-semibold mt-1.5 tracking-[-0.01em]">
                {num(kpi.reconLines)}<span className="text-[#B5AF9E] text-[16px] font-medium"> / {num(REPORT_META.totalLines)}</span>
              </div>
              <div className="h-1.5 bg-[#EFEADB] rounded-full mt-2.5 overflow-hidden">
                <div className="h-full bg-gold rounded-full transition-[width] duration-300" style={{ width: `${kpi.reconPct}%` }} />
              </div>
            </div>
          </div>

          {/* Tabs */}
          <div className="flex gap-0.5 px-6 border-b border-[#ECE6D8]">
            <button className={tabCls(tab === 'disc')} onClick={() => setTab('disc')}>
              {t('report.tabDiscrepancies')}
              <span className="font-mono text-[11px] bg-[#F4E7E0] text-danger px-[7px] py-0.5 rounded-full font-semibold">{kpi.remaining}</span>
            </button>
            <button className={tabCls(tab === 'all')} onClick={() => setTab('all')}>
              {t('report.tabAllLines')}
              <span className="font-mono text-[11px] text-[#9A9484]">{REPORT_META.totalLines}</span>
            </button>
          </div>

          {/* Discrepancy list */}
          {tab === 'disc' && (
            <div className="px-[18px] pt-3.5 pb-5 flex flex-col gap-2.5 bg-[#F6F3EA]">
              {DISCREPANCIES.map((d) => (
                <DiscCard
                  key={d.id}
                  d={d}
                  expanded={expandedId === d.id}
                  resolved={resolved.has(d.id)}
                  onToggle={() => setExpandedId((id) => (id === d.id ? null : d.id))}
                  onResolve={() => toggleResolve(d.id)}
                  t={t}
                  partner={partner}
                />
              ))}
            </div>
          )}

          {/* All lines */}
          {tab === 'all' && (
            <div className="px-[18px] pt-3.5 pb-5 bg-[#F6F3EA]">
              <div className="text-[12.5px] text-muted px-1 pb-3">
                {t('report.showingLines', { shown: orderedRows.length, total: REPORT_META.totalLines })}
              </div>
              <div className="border border-line rounded-[10px] overflow-hidden bg-white">
                <div className="grid grid-cols-[118px_64px_84px_86px_1fr_44px_84px_92px] gap-2 px-3.5 py-2.5 bg-[#F3EFE4] border-b border-[#E6DFCF] font-mono text-[10px] tracking-[0.06em] uppercase text-[#9A9484]">
                  <span>{t('report.colStatus')}</span><span>{t('report.colDate')}</span><span>{t('report.colPatient')}</span><span>{t('report.code')}</span><span>{t('report.colDescription')}</span>
                  <span className="text-right">{t('report.colQty')}</span><span className="text-right">{t('report.colUnit')}</span><span className="text-right">{t('report.colTotal')}</span>
                </div>
                {orderedRows.map((r) => {
                  const statusLabel = r.statusType === 'missing' ? t('report.missing')
                    : r.statusType === 'qty' ? t('report.qtyMismatch')
                    : t('report.matched');
                  return (
                    <div
                      key={r.id}
                      className="grid grid-cols-[118px_64px_84px_86px_1fr_44px_84px_92px] gap-2 px-3.5 py-2.5 border-b border-[#F2EDE0] items-center"
                      style={{ background: r.flagged ? '#FDF8F1' : '#fff', borderLeft: `3px solid ${r.barColor}` }}
                    >
                      <span><span className="inline-block font-mono text-[10px] font-semibold px-2 py-[3px] rounded-[5px] whitespace-nowrap" style={{ background: statusBg(r.statusType), color: statusColor(r.statusType) }}>{statusLabel}</span></span>
                      <span className="font-mono text-[12.5px] text-[#6E6A5E]">{r.dos}</span>
                      <span className="font-mono text-[12.5px] text-[#6E6A5E]">{r.patient}</span>
                      <span className="font-mono text-[12.5px]">{r.code}</span>
                      <span className="text-[13px] truncate min-w-0">{r.name}</span>
                      <span className="font-mono text-[12.5px] text-right">{r.qty}</span>
                      <span className="font-mono text-[12.5px] text-[#6E6A5E] text-right">{r.unit}</span>
                      <span className="font-mono text-[12.5px] text-right font-semibold">{r.total}</span>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function DiscCard({
  d, expanded, resolved, onToggle, onResolve, t, partner,
}: {
  d: Discrepancy;
  expanded: boolean;
  resolved: boolean;
  onToggle: () => void;
  onResolve: () => void;
  t: (key: string, options?: Record<string, unknown>) => string;
  partner: string;
}) {
  const meta = TYPE_CONFIG[d.type];
  const typeLabel = d.type === 'missing' ? t('report.missing') : t('report.qtyMismatch');
  const present = d.type !== 'missing';
  const risk = riskOf(d);
  const pdfRef = `p.${d.page} · line ${d.line}`;
  const visits = recentInvoices(d.dos);

  return (
    <div
      className="rounded-[11px] bg-white overflow-hidden transition-opacity"
      style={{ border: `1px solid ${expanded ? '#D9CFB4' : '#ECE6D8'}`, opacity: resolved ? 0.6 : 1 }}
    >
      <div className="flex items-center gap-3.5 px-3.5 py-3.5">
        <div className="w-1 self-stretch rounded-full" style={{ background: meta.color }} />
        <span className="font-mono text-[11px] font-semibold px-2 py-1 rounded-md whitespace-nowrap" style={{ background: meta.bg, color: meta.color }}>
          {typeLabel}
        </span>
        <div className="min-w-0 flex-1 cursor-pointer" onClick={onToggle}>
          <div className="text-[14px] font-semibold">{d.code} · {d.name}</div>
          <div className="text-[12px] text-muted mt-0.5 font-mono">{d.patient} · {d.dos} · {pdfRef}</div>
        </div>
        <div className="text-right mr-0.5">
          <div className="font-mono text-[15px] font-semibold" style={{ color: resolved ? '#A39C8A' : '#B0492F', textDecoration: resolved ? 'line-through' : 'none' }}>
            {money(risk)}
          </div>
          <div className="font-mono text-[10.5px] text-[#A39C8A] tracking-[0.04em] uppercase">{t('report.atRisk')}</div>
        </div>
        <button
          onClick={onResolve}
          className="font-sans text-[12.5px] font-semibold px-[13px] py-[7px] rounded-lg whitespace-nowrap"
          style={resolved
            ? { border: '1px solid #CFC7B4', background: '#fff', color: '#6E6A5E' }
            : { border: '1px solid #21303F', background: '#21303F', color: '#F1EEE4' }}
        >
          {resolved ? t('report.reopen') : t('report.markResolved')}
        </button>
      </div>

      {expanded && (
        <div className="border-t border-[#ECE6D8] bg-[#FBF9F3] p-4 grid grid-cols-2 gap-3.5">
          {present && (
            <>
              <CompareCard title={`${t('report.partnerInvoice')} — ${partner}`} rows={[
                [t('report.service'), d.name], [t('report.code'), d.code], [t('report.quantity'), String(d.pq)], [t('report.lineTotal'), money(d.price * d.pq)],
              ]} t={t} />
              <CompareCard title={t('report.patientInvoice')} rows={[
                [t('report.service'), d.name], [t('report.code'), d.code], [t('report.quantity'), String(d.ptq)], [t('report.lineTotal'), money(d.price * d.ptq)],
              ]} highlightQty t={t} />
            </>
          )}

          {/* Patient locator */}
          <div className="col-span-2 border border-[#E2D5B6] bg-[#FAF4E6] rounded-[10px] px-4 py-3.5 flex items-center gap-5 flex-wrap">
            <div className="min-w-[150px]">
              <div className="font-mono text-[10.5px] tracking-[0.06em] uppercase text-[#8A6A1E] font-semibold">{t('report.addToPatientInvoice')}</div>
              <div className="text-[12px] text-[#8C7B57] mt-0.5 leading-snug max-w-[210px]">{t('report.patientInvoiceHint')}</div>
            </div>
            <div className="flex items-start gap-6 ml-auto flex-wrap">
              <div>
                <div className="font-mono text-[9.5px] tracking-[0.06em] uppercase text-[#A88F5A]">{t('report.patientId')}</div>
                <div className="font-mono text-[15px] font-semibold mt-0.5">{d.patient}</div>
              </div>
              <div>
                <div className="font-mono text-[9.5px] tracking-[0.06em] uppercase text-[#A88F5A]">{t('report.recentPatientInvoices')}</div>
                <div className="flex gap-1.5 mt-1.5">
                  {visits.map((v, i) => (
                    <span key={v} className="font-mono text-[12px] px-[9px] py-[3px] rounded-md"
                      style={i === 0 ? { background: '#F0E2C0', color: '#8A6A1E', fontWeight: 600 } : { background: '#fff', color: '#6E6A5E', border: '1px solid #E6DFCF' }}>
                      {v}
                    </span>
                  ))}
                </div>
              </div>
            </div>
          </div>

          {/* Source PDF snippet */}
          <div className="col-span-2 bg-ink rounded-[10px] px-3.5 py-3">
            <div className="flex justify-between items-center mb-2.5">
              <span className="font-mono text-[10.5px] tracking-[0.06em] uppercase text-[#A9B4BF]">{t('report.source')} · {partner} PDF — {pdfRef}</span>
              <span className="text-[12.5px] text-[#C9A95F] font-semibold cursor-pointer">{t('report.openFullPdf')}</span>
            </div>
            <div className="bg-[#F6EFDD] rounded-md px-3 py-2.5 grid grid-cols-[54px_92px_1fr_44px_92px] gap-2.5 font-mono text-[12.5px] items-center" style={{ borderLeft: '3px solid #9A7E45' }}>
              <span className="text-[#8A6A1E]">L{d.line}</span>
              <span>{d.code}</span>
              <span className="truncate">{d.name}</span>
              <span>×{d.pq}</span>
              <span className="text-right font-semibold">{money(d.price * d.pq)}</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function CompareCard({
  title, rows, highlightQty = false, t,
}: {
  title: string;
  rows: [string, string][];
  highlightQty?: boolean;
  t: (key: string, options?: Record<string, unknown>) => string;
}) {
  const qtyKey = t('report.quantity');
  const totalKey = t('report.lineTotal');
  return (
    <div className="border border-[#E6DFCF] rounded-[10px] bg-white px-[15px] py-3.5">
      <div className="font-mono text-[10.5px] tracking-[0.07em] uppercase font-semibold mb-2.5">{title}</div>
      {rows.map(([k, v], i) => (
        <div key={k} className="flex justify-between text-[13px] py-1.5" style={{ borderBottom: i < rows.length - 1 ? '1px solid #F0EADC' : 'none' }}>
          <span className="text-muted">{k}</span>
          <span className={`text-right ${k === qtyKey || k === totalKey ? 'font-mono font-semibold' : ''}`}
            style={highlightQty && k === qtyKey ? { color: '#B0492F' } : undefined}>
            {v}
          </span>
        </div>
      ))}
    </div>
  );
}
