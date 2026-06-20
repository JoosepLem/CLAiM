import { useState, useRef, useCallback, useEffect, type DragEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Upload as UploadIcon, FileText, X, Info, Trash2, ArrowRight, CheckCircle2, AlertTriangle } from 'lucide-react';
import Wordmark from '../components/Wordmark';

interface StoredDoc {
  id: string;
  fileName: string;
  partner: string;
  invoiceNo: string;
  period: string;
  uploadedAt: string;
  discrepancies: number;
  moneyAtRisk: number;
}

const STORAGE_KEY = 'claim_docs';

function loadDocs(): StoredDoc[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

function saveDocs(docs: StoredDoc[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(docs));
}

const PARTNERS = ['Synlab', 'PERH', 'Medicover', 'Quattromed'];
const INVOICE_NOS: Record<string, string> = {
  Synlab: 'SYN-05-2026',
  PERH: 'PERH-2026-05',
  Medicover: 'MED-05-2026',
  Quattromed: 'QM-2026-05',
};

function randomPick<T>(arr: readonly T[]): T {
  return arr[Math.floor(Math.random() * arr.length)];
}

export default function Upload() {
  const navigate = useNavigate();
  const [file, setFile] = useState<File | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [docs, setDocs] = useState<StoredDoc[]>(loadDocs);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    saveDocs(docs);
  }, [docs]);

  const handleFile = useCallback((f: File) => {
    if (f.type === 'application/pdf' || f.name.endsWith('.pdf')) {
      setFile(f);
    }
  }, []);

  const onDrop = useCallback((e: DragEvent) => {
    e.preventDefault();
    setDragOver(false);
    const f = e.dataTransfer.files?.[0];
    if (f) handleFile(f);
  }, [handleFile]);

  const onDragOver = useCallback((e: DragEvent) => {
    e.preventDefault();
    setDragOver(true);
  }, []);

  const onDragLeave = useCallback((e: DragEvent) => {
    e.preventDefault();
    setDragOver(false);
  }, []);

  const onInputChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (f) handleFile(f);
  }, [handleFile]);

  const startReconciliation = () => {
    if (!file) return;
    const partner = randomPick(PARTNERS);
    const newDoc: StoredDoc = {
      id: Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
      fileName: file.name,
      partner,
      invoiceNo: INVOICE_NOS[partner],
      period: 'May 2026',
      uploadedAt: new Date().toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' }),
      discrepancies: Math.floor(Math.random() * 15) + 2,
      moneyAtRisk: Math.round((Math.random() * 400 + 50) * 10) / 10,
    };
    setDocs((prev) => [newDoc, ...prev]);
    navigate('/reconciling', { state: { fileName: file.name } });
  };

  const deleteDoc = (id: string) => {
    setDocs((prev) => prev.filter((d) => d.id !== id));
  };

  const clearFile = () => setFile(null);

  return (
    <div className="min-h-screen bg-cream font-sans text-ink px-6 pt-9 pb-16">
      <div className="max-w-[960px] mx-auto">
        <div className="flex items-center justify-between mb-9">
          <Wordmark className="text-[25px]" />
          <span className="text-[13px] text-muted">Invoice Reconciliation</span>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-[1fr_1.1fr] gap-6 items-start">
          <div className="bg-cream-card border border-line rounded-2xl shadow-[0_1px_3px_rgba(40,30,10,0.06),0_14px_30px_-20px_rgba(40,30,10,0.2)] overflow-hidden">
            <div className="px-[22px] pt-[20px] pb-4 border-b border-[#ECE6D8]">
              <div className="text-[17px] font-bold">Upload partner invoice</div>
              <div className="text-[13px] text-muted mt-0.5">
                Partner and period are detected automatically.
              </div>
            </div>
            <div className="p-[22px]">
              {!file ? (
                <label
                  onDrop={onDrop}
                  onDragOver={onDragOver}
                  onDragLeave={onDragLeave}
                  className={`block border-2 border-dashed rounded-xl px-5 py-[34px] text-center cursor-pointer transition-colors ${
                    dragOver
                      ? 'border-gold bg-[#F3EBD7]'
                      : 'border-[#D7C9A8] bg-[#F6EFDD] hover:border-gold hover:bg-[#F4ECD5]'
                  }`}
                >
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept=".pdf,application/pdf"
                    className="hidden"
                    onChange={onInputChange}
                  />
                  <div className="w-12 h-12 rounded-xl bg-ink text-cream-card flex items-center justify-center mx-auto mb-3.5">
                    <UploadIcon size={22} />
                  </div>
                  <div className="text-[14px] font-semibold">
                    Drag &amp; drop your partner PDF
                  </div>
                  <div className="text-[12.5px] text-muted mt-1">
                    or <span className="text-gold font-semibold underline">browse your files</span>
                  </div>
                  <div className="text-[11px] text-[#A89F88] mt-3 font-mono">
                    PDF · up to 25 MB
                  </div>
                </label>
              ) : (
                <div className="border border-[#D9D2C0] bg-white rounded-xl px-4 py-3.5">
                  <div className="flex items-center gap-3">
                    <div className="w-11 h-11 rounded-lg bg-[#F4ECE2] flex items-center justify-center shrink-0">
                      <FileText size={20} className="text-ink" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="text-[14px] font-semibold truncate">{file.name}</div>
                      <div className="text-[11.5px] text-muted mt-0.5 font-mono">
                        {(file.size / 1024).toFixed(0)} KB · PDF
                      </div>
                    </div>
                    <button onClick={clearFile} className="text-[#9A9484] hover:text-ink p-1">
                      <X size={17} />
                    </button>
                  </div>
                </div>
              )}
              <div className="flex items-start gap-2.5 mt-3.5 bg-[#FBF9F3] border border-[#E6DFCF] rounded-[10px] px-3.5 py-2.5">
                <Info size={14} className="text-[#6E6A5E] shrink-0 mt-0.5" />
                <span className="text-[12px] text-[#6E6A5E] leading-snug">
                  Reconciliation starts immediately after upload. Every line is cross-referenced against insurer billing records.
                </span>
              </div>
            </div>
            <div className="flex items-center justify-end gap-2.5 px-[22px] py-3.5 border-t border-[#ECE6D8] bg-cream-panel">
              <button
                onClick={startReconciliation}
                disabled={!file}
                className="inline-flex items-center gap-2 bg-ink text-cream-card rounded-[9px] px-[18px] py-2.5 text-[13.5px] font-semibold hover:bg-[#2C3E4F] transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
              >
                Start reconciliation
              </button>
            </div>
          </div>

          <div>
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-[16px] font-bold">Recent documents</h2>
              {docs.length > 0 && (
                <button
                  onClick={() => setDocs([])}
                  className="text-[12.5px] text-[#B5AF9E] hover:text-danger font-medium transition-colors"
                >
                  Clear all
                </button>
              )}
            </div>

            {docs.length === 0 ? (
              <div className="bg-cream-card border border-line rounded-xl px-5 py-[38px] text-center">
                <div className="w-10 h-10 rounded-lg bg-[#F3EFE4] flex items-center justify-center mx-auto mb-3">
                  <FileText size={18} className="text-[#C2B89F]" />
                </div>
                <div className="text-[13.5px] text-[#A89F88] font-medium">No documents yet</div>
                <div className="text-[12px] text-[#C5BFAD] mt-1">
                  Upload a partner invoice to get started.
                </div>
              </div>
            ) : (
              <div className="flex flex-col gap-2.5">
                {docs.map((doc) => (
                  <div
                    key={doc.id}
                    className="bg-cream-card border border-line rounded-xl px-4 py-3.5 flex items-center gap-3.5 group hover:border-[#D7C9A8] transition-colors"
                  >
                    <div className="w-[38px] h-[38px] rounded-[9px] bg-ink text-cream-card flex items-center justify-center font-bold text-[12px] tracking-[0.04em] shrink-0">
                      {doc.partner.slice(0, 2).toUpperCase()}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <span className="text-[14px] font-semibold truncate">{doc.fileName}</span>
                        <span className="inline-flex items-center gap-1 bg-[#E6EFE8] text-[#3C7355] text-[10.5px] font-semibold px-[7px] py-[2px] rounded-full whitespace-nowrap">
                          <CheckCircle2 size={10} /> Reconciled
                        </span>
                      </div>
                      <div className="flex items-center gap-3 mt-1 text-[11.5px] text-muted font-mono">
                        <span>{doc.partner}</span>
                        <span className="text-[#D9D2C0]">·</span>
                        <span>{doc.invoiceNo}</span>
                        <span className="text-[#D9D2C0]">·</span>
                        <span>{doc.uploadedAt}</span>
                      </div>
                      <div className="flex items-center gap-3 mt-1.5">
                        <span className="inline-flex items-center gap-1 text-[11.5px] text-danger font-semibold">
                          <AlertTriangle size={11} />
                          {doc.discrepancies} discrepancies
                        </span>
                        <span className="text-[11.5px] text-muted font-mono">
                          € {doc.moneyAtRisk.toFixed(2)} at risk
                        </span>
                      </div>
                    </div>
                    <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button
                        onClick={() => navigate('/invoices/i1')}
                        className="p-1.5 text-ink hover:bg-[#F0EAD8] rounded-lg transition-colors"
                        title="View report"
                      >
                        <ArrowRight size={16} />
                      </button>
                      <button
                        onClick={() => deleteDoc(doc.id)}
                        className="p-1.5 text-[#C2B89F] hover:text-danger hover:bg-[#F4E7E0] rounded-lg transition-colors"
                        title="Delete"
                      >
                        <Trash2 size={15} />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        <div className="flex items-center gap-2 justify-center mt-[26px] opacity-70">
          <Wordmark className="text-[13px]" />
          <span className="text-[11.5px] text-[#9A9484] font-mono">Automated invoice reconciliation</span>
        </div>
      </div>
    </div>
  );
}
