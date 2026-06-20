import { useEffect, useState, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { CheckCircle2 } from 'lucide-react';
import Wordmark from '../components/Wordmark';
import LanguageSwitcher from '../components/LanguageSwitcher';

type StepStatus = 'pending' | 'running' | 'done';

export default function Reconciling() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const fileName = (location.state as { fileName?: string })?.fileName ?? 'partner_invoice.pdf';

  const STEPS = [
    { label: t('reconciling.step1Label'), description: t('reconciling.step1Desc') },
    { label: t('reconciling.step2Label'), description: t('reconciling.step2Desc') },
    { label: t('reconciling.step3Label'), description: t('reconciling.step3Desc') },
    { label: t('reconciling.step4Label'), description: t('reconciling.step4Desc') },
  ];

  const [stepIdx, setStepIdx] = useState(-1);
  const [overallPct, setOverallPct] = useState(0);
  const [done, setDone] = useState(false);
  const doneRef = useRef(false);

  useEffect(() => {
    const durations = [1400, 2000, 1800, 1200];
    let cancelled = false;
    let timeout: ReturnType<typeof setTimeout>;

    function tick(i: number) {
      if (cancelled) return;
      setStepIdx(i);
      timeout = setTimeout(() => {
        if (cancelled) return;
        if (i + 1 < STEPS.length) {
          tick(i + 1);
        } else {
          doneRef.current = true;
          setDone(true);
          setOverallPct(100);
          timeout = setTimeout(() => {
            if (!cancelled) navigate('/invoices/i1');
          }, 900);
        }
      }, durations[i] ?? 1200);
    }

    tick(0);

    const pctInterval = setInterval(() => {
      if (doneRef.current) return;
      setOverallPct((p) => Math.min(p + 5, 93));
    }, 300);

    return () => {
      cancelled = true;
      clearTimeout(timeout);
      clearInterval(pctInterval);
    };
  }, [navigate]);

  const getStatus = (i: number): StepStatus => {
    if (i < stepIdx) return 'done';
    if (i === stepIdx) return 'running';
    return 'pending';
  };

  const iconFor = (i: number) => {
    const s = getStatus(i);
    if (s === 'done') return <CheckCircle2 size={18} className="text-[#3C7355]" />;
    if (s === 'running') return <LoaderIcon />;
    return <span className="w-[18px] h-[18px] rounded-full border-2 border-[#D9D2C0]" />;
  };

  return (
    <div className="min-h-screen bg-cream font-sans text-ink flex flex-col">
      <div className="absolute top-4 right-4">
        <LanguageSwitcher />
      </div>
      <div className="flex-1 flex items-center justify-center px-6 py-12">
        <div className="w-[560px] max-w-full">
          <div className="text-center mb-9">
            <Wordmark className="text-[32px]" />
            <p className="text-[15px] text-muted mt-2.5">{t('reconciling.title')}</p>
          </div>

          <div className="bg-cream-card border border-line rounded-2xl shadow-[0_1px_3px_rgba(40,30,10,0.06),0_14px_30px_-20px_rgba(40,30,10,0.2)] overflow-hidden">
            <div className="px-[26px] pt-[26px] pb-5 border-b border-[#ECE6D8]">
              <div className="text-[18px] font-bold">{t('reconciling.inProgress')}</div>
              <div className="text-[13px] text-muted mt-0.5 font-mono truncate">
                {fileName}
              </div>
            </div>

            <div className="px-[26px] py-6">
              {STEPS.map((step, i) => {
                const status = getStatus(i);
                return (
                  <div key={step.label} className="flex items-start gap-3.5" style={{ marginBottom: i < STEPS.length - 1 ? 18 : 0 }}>
                    <div className="mt-0.5 shrink-0">
                      {iconFor(i)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className={`text-[14px] font-semibold transition-colors duration-300 ${
                        status === 'done' ? 'text-[#3C7355]' : status === 'running' ? 'text-ink' : 'text-[#B5AF9E]'
                      }`}>
                        {step.label}
                      </div>
                      <div className={`text-[12.5px] mt-0.5 transition-colors duration-300 ${
                        status === 'done' ? 'text-[#6E9E7A]' : status === 'running' ? 'text-muted' : 'text-[#C5BFAD]'
                      }`}>
                        {step.description}
                      </div>
                    </div>
                    {status === 'done' && (
                      <span className="text-[11.5px] text-[#6E9E7A] font-mono mt-0.5">{t('reconciling.ok')}</span>
                    )}
                    {status === 'running' && (
                      <span className="text-[11.5px] text-gold font-mono mt-0.5 animate-pulse">…</span>
                    )}
                  </div>
                );
              })}

              <div className="mt-6 h-[5px] bg-[#EFEADB] rounded-full overflow-hidden">
                <div
                  className="h-full bg-gold rounded-full transition-all duration-300"
                  style={{ width: `${Math.min(overallPct, 100)}%` }}
                />
              </div>
              <div className="flex justify-between mt-2 text-[11px] font-mono text-[#B5AF9E]">
                <span>{overallPct}{t('reconciling.percentComplete')}</span>
                <span>
                  {done ? `847 / 847 ${t('reconciling.linesProcessed')}` : `${Math.floor(overallPct * 8.47)} / 847`}
                </span>
              </div>

              {done && (
                <div className="mt-4 bg-[#E6EFE8] border border-[#C8DBCB] rounded-[10px] px-4 py-3 flex items-center gap-2.5 text-[13.5px] text-[#3C7355] font-semibold">
                  <CheckCircle2 size={17} />
                  {t('reconciling.complete', { count: 14 })}
                </div>
              )}
            </div>
          </div>

          <div className="flex items-center gap-2 justify-center mt-[22px] opacity-70">
            <Wordmark className="text-[13px]" />
            <span className="text-[11.5px] text-[#9A9484] font-mono">{t('nav.automatedInvoiceReconciliation')}</span>
          </div>
        </div>
      </div>
    </div>
  );
}

function LoaderIcon() {
  return (
    <svg className="animate-spin" width="18" height="18" viewBox="0 0 18 18" fill="none">
      <circle cx="9" cy="9" r="7" stroke="#E6DFCF" strokeWidth="2.5" />
      <path d="M9 2a7 7 0 0 1 7 7" stroke="#9A7E45" strokeWidth="2.5" strokeLinecap="round" />
    </svg>
  );
}
