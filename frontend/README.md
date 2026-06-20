# CLAiM — React export (TypeScript + Tailwind + React Router)

Two screens converted from the HTML design prototypes into drop-in React components:

- **Dashboard** — partner-invoice list with partner + date-range filters, aggregate KPIs, and an upload modal.
- **Reconciliation Report** — per-invoice discrepancy-first report with expandable verification, patient locator, source-PDF snippet, and resolve/reopen.

Everything is plain React + hooks. No state library, no CSS-in-JS — just Tailwind utility classes (with arbitrary values for the exact brand colors) and a little inline `style` for the handful of truly dynamic values (progress-bar width, status tints).

---

## Run it standalone (quickstart)

This folder is a complete, runnable **Vite + React + TypeScript + Tailwind** project — you don't need to wire it into anything to see it:

```bash
npm install
npm run dev      # open the printed http://localhost:5173
```

`npm run build` then `npm run preview` for a production build. That's it — everything below (sections 1–5) is only relevant if you'd rather **merge these screens into your own existing project** instead of running this one.

---

## 1. Install dependencies

```bash
npm install react-router-dom lucide-react
```

Both are free/open-source (MIT). Fonts are **Hanken Grotesk** + **IBM Plex Mono** (SIL OFL), loaded via Google Fonts in `index.css`.

You also need Tailwind set up. If you don't have it yet:

```bash
npm install -D tailwindcss postcss autoprefixer
npx tailwindcss init -p
```

## 2. Copy files into your project

```
src/
  App.tsx                       ← example router (merge into yours)
  index.css                     ← @import fonts + @tailwind layers (merge into yours)
  components/
    Wordmark.tsx
  data/
    invoices.ts                 ← dashboard sample data + types
    report.ts                   ← per-invoice sample data + helpers
  lib/
    format.ts                   ← money / number / date-range helpers
  pages/
    Dashboard.tsx
    ReconciliationReport.tsx
tailwind.config.js              ← merge `theme.extend` into yours
```

## 3. Tailwind config

Merge the `theme.extend` block from `tailwind.config.js` into your own config. It adds:

- **Colors:** `ink` (navy `#21303F`), `gold` (`#9A7E45`), `danger` (`#B0492F`), `muted`, `line`, and `cream` / `cream-card` / `cream-panel`.
- **Fonts:** `font-sans` → Hanken Grotesk, `font-mono` → IBM Plex Mono.

Make sure your `content` globs include the files above so the arbitrary-value classes (e.g. `bg-[#F4E7E0]`) are generated.

## 4. Global CSS

Merge `src/index.css` into your global stylesheet — it has the Google Fonts `@import`, the `@tailwind` layers, a `body` base style, and a themed scrollbar. Import it once at your app entry (e.g. `main.tsx`).

## 5. Routing

`App.tsx` shows the two routes:

| Path | Screen |
| --- | --- |
| `/` | Dashboard |
| `/invoices/:invoiceId` | Reconciliation Report |

The dashboard navigates with `navigate('/invoices/' + id)`; the report reads `useParams().invoiceId` and uses it for the header meta. Drop these routes into your existing router if you already have one.

---

## Wiring up real data (replace the stubs)

These are faithful UI shells with **sample data**. To make them live:

- **`src/data/invoices.ts`** — replace `INVOICES` with your fetched list (`InvoiceSummary[]`). `PARTNERS`, `PRESETS`, and `DEFAULT_RANGE` are config you can tune.
- **`src/data/report.ts`** — replace `DISCREPANCIES` / `MATCHED` / `REPORT_META` with per-invoice data fetched by `invoiceId`. `riskOf`, `noteOf`, and `recentInvoices` are pure helpers you can keep or swap.
- **Download / Open PDF buttons** — currently no-ops; wire to your partner-PDF URL and line-anchored deep links.
- **Upload modal** — visual only; connect the dropzone + "Start reconciliation" to your ingest pipeline.
- **Resolve** — kept in local component state (a `Set` of resolved ids). Persist it to your backend in `toggleResolve`.
- **Patient locator** — shows Patient ID + recent patient invoices so an assistant can find the encounter in the separate billing service. Add a deep link there once that service is reachable.

## Notes

- The "today" reference in the sample date presets is **2026-06-20** (so "Last month" = May 2026). Adjust `PRESETS` / `DEFAULT_RANGE` for real dates, or compute them from `new Date()`.
- Money is formatted in euros with thin-space thousands separators (`lib/format.ts`).
- Tailwind can only see class strings that appear **literally** in source — if you refactor status colors into a runtime map, use inline `style` (as done for the all-lines status chips) rather than building `bg-[${...}]` strings dynamically.
