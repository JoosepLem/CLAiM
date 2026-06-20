// The CLAiM wordmark, reproduced in type (CL navy · Ai gold · M navy).
export default function Wordmark({
  className = '',
  light = false,
}: {
  className?: string;
  light?: boolean;
}) {
  const base = light ? 'text-cream-card' : 'text-ink';
  return (
    <span className={`font-bold tracking-[-0.015em] ${className}`}>
      <span className={base}>CL</span>
      <span className={light ? 'text-[#C9A95F]' : 'text-gold'}>Ai</span>
      <span className={base}>M</span>
    </span>
  );
}
