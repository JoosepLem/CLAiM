/** @type {import('tailwindcss').Config} */
// Merge `theme.extend` into your existing tailwind.config — these are the only
// additions the CLAiM screens rely on. Everything else uses arbitrary values.
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: '#21303F',     // primary navy
        gold: '#9A7E45',    // brand accent
        danger: '#B0492F',  // money-at-risk / missing
        muted: '#8C8676',
        line: '#E2DCCD',    // hairline borders
        cream: {
          DEFAULT: '#E6E1D4', // page background
          card: '#FBFAF5',    // card surface
          panel: '#FDFCF8',   // raised panel
        },
      },
      fontFamily: {
        sans: ['"Hanken Grotesk"', 'system-ui', 'sans-serif'],
        mono: ['"IBM Plex Mono"', 'ui-monospace', 'monospace'],
      },
    },
  },
  plugins: [],
};
