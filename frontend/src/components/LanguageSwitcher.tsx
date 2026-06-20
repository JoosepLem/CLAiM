import { useTranslation } from 'react-i18next';

export default function LanguageSwitcher() {
  const { i18n } = useTranslation();
  const current = i18n.language;

  return (
    <button
      onClick={() => i18n.changeLanguage(current === 'en' ? 'et' : 'en')}
      className="inline-flex items-center gap-1.5 font-mono text-[12px] font-semibold uppercase tracking-[0.04em] text-muted hover:text-ink bg-cream-card border border-[#D9D2C0] rounded-[8px] px-2.5 py-1.5 transition-colors"
      title={current === 'en' ? 'Switch to Estonian' : 'Switch to English'}
    >
      <span className={current === 'en' ? 'text-ink' : 'text-[#C2B89F]'}>EN</span>
      <span className="text-[#D9D2C0]">/</span>
      <span className={current === 'et' ? 'text-ink' : 'text-[#C2B89F]'}>ET</span>
    </button>
  );
}
