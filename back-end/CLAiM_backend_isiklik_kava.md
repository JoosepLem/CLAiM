# CLAiM — Backend-arendaja isiklik kava (esimese prototüübini)

**Roll:** Arendaja 1 — backend / andmed
**Kontekst:** koostatud 17. juuni 2026 · ametlik ehitus algab 6. juulil · piloot 1. september
**Eesmärk:** jõuda demoni, mis tõestab kogu kontseptsiooni — kaks arvefaili sisse, katmata teenuste nimekiri + € välja.

---

## 0. Demo siht (mis on "esimene prototüüp")

Üks püstloodis viil otspunktist otspunkti, mis töötab **sünteetiliste andmete** peal:

> Kaks sisendit (Tervisekassasse arveldatu + partneri arve) → normaliseerimine → matching isikukood + teenuskood + kuupäev (± tolerants) → **katmata teenuste nimekiri koos rahalise hinnanguga**.

Demo vormid, eskaleeruvas järjekorras:
1. **CLI** — `python -m claim.demo tk.json partner.json` → tabel terminali. (Kiireim viis ennast tõestada.)
2. **Õhuke API** — FastAPI: üleslaadimise endpoint + tulemuste pärimine. (Selle otsa saab frontend-arendaja ehitada ja teistele päriselt näidata.)
3. **CSV-eksport** — sama tulemus failina, mille kliinik saaks edasi töödelda.

**Definition of done demo jaoks:** suvaline tiimiliige käivitab ühe käsu, saab nähtava katmata teenuste nimekirja + kogusumma, ja saab tulemuse CSV-na alla laadida.

---

## Juhtprintsiip: sünteetilised andmed kõigepealt

Sa **ei sõltu** Medisofti ega partnerite failidest selleks, et ehitada andmemudel ja matching-loogika — ehk süsteemi tegelik tuum. Generee fake-andmed teadaolevate "aukudega" ja ehita kogu loogika nende peal valmis. Kui pärisfailid saabuvad, on need lihtsalt uued **adapterid** sama normaliseeritud mudeli otsa — mitte ümberkirjutamine.

See on ühtlasi roadmap'i "country as configuration" printsiip parseri tasandil: uus formaat = uus adapter, mitte uus loogika.

---

## Sprint 0 — Vundament (17.–20. juuni, ~2 päeva)

- [ ] **Repo + struktuur** GitHubis (CI/CD tuleb niikuinii — pane Git kohe korda).
  - `claim/` (pakett), `tests/`, `pyproject.toml`, `docker-compose.yml` (lokaalne Postgres).
- [ ] **Keskkond:** Python 3.12, venv, `ruff` (linter), `pytest`.
- [ ] **Andmemudel mustandina** (Pydantic v2, *enne DB-d*):
  - `Partner`, `ServiceCode`, `Invoice`, `InvoiceRow`, `MatchResult`.
  - Defineeri väljad nii nagu äriloogika nõuab; DB-skeem tuleb hiljem nende järgi.
  - **NB:** lõpliku andmemudeli kinnitab arhitekt N1 lõpuks — aga sina pead esitama mustandi, sest see on sinu töö blokeer. Vii see arhitektiga kokku enne kui DB peale lähed.
- [ ] **`CLAUDE.md`** repo juurde: arhitektuur, andmemudel, konventsioonid. Maksab end ära nii tiimi kui ka hilisema Claude Code'i jaoks.

---

## Sprint 1 — Süda: normaliseerimine + matching (21.–27. juuni, ~4 päeva)

See on kriitiline tee. Ei vaja ühtegi välist faili.

- [ ] **Sünteetilised testandmed** — generaator, mis teeb:
  - TK-poolse arvete komplekti (isikukood, teenuskood, kuupäev, summa),
  - partneri arve komplekti, milles on **teadaolevad katmata read** (partner tegi, TK-sse arveldamata) ja teadaolevad duplikaadid + serva-juhtumid.
  - Teadaolev "õige vastus" → saad oma loogikat valideerida.
- [ ] **Normaliseerimiskiht** — ühtne sisemine esitus (`Invoice` + `InvoiceRow`), kuhu mõlemad allikad mappivad. Defineeri **parseri liides** (nt `Parser.parse(raw) -> list[Invoice]`), et XML- ja PDF-parserid oleksid hiljem ainult adapterid.
- [ ] **Matching-loogika** (tuumfunktsioon):
  - Võti: `(isikukood, teenuskood, kuupäev ± N päeva)`.
  - Iga partneri rea kohta otsi vaste TK-komplektist.
  - Vaste puudub → **katmata** (partner arveldas, TK-sse mitte) → liida € summa.
  - Edge case'id, mis tuleb kohe sisse: duplikaadid, sama isik + sama päev mitu teenust, tolerantsi-akna kattuvused (deterministlik prioriteedireegel vaste valikul), null-/puuduvad väljad.
- [ ] **Ühiktestid** matching-moodulile sünteetiliste andmete peal — "õige vastuse" vastu.

**Sprint 1 tulem:** matching töötab fake-andmetel ja annab õige katmata-nimekirja + summa. Kontseptsioon tõestatud.

---

## Sprint 2 — Demoni: väljund + õhuke API (28. juuni – 4. juuli, ~4 päeva)

- [ ] **CLI-väljund** — katmata teenuste tabel + kogusumma terminali. (Esimene demo on valmis.)
- [ ] **CSV-eksport** — sama nimekiri failina.
- [ ] **Õhuke FastAPI:**
  - `POST /upload` (TK-fail + partneri fail),
  - `GET /results` (filtrid: kuupäev, partner, teenuskood),
  - automaatne OpenAPI-doc on demoks ja frontend-arendaja jaoks kohe olemas.
- [ ] **DB-persistents (skeleton)** — SQLAlchemy + Alembic, Postgres Dockeris. Salvesta arved + match-tulemused. (Täielik audit trail tuleb kiirendi N2-s; pane alus paika juba nüüd.)

**Sprint 2 tulem:** demotav prototüüp — käivitad ühe käsu / lööd kahe failiga API-t, näed katmata teenuseid + €, laed CSV alla.

---

## Pärisandmete sissetoomine (kui saabuvad)

- **~25. juuni — Medisofti näidisfail:** kirjuta **Medisoft XML-parser** kui esimene pärisadapter normaliseeritud mudeli otsa (lxml). Asenda sünteetiline TK-allikas pärisega. See on ühtlasi kiirendi N1 esimene ametlik tulem — sa oled siis ees.
- **~6. juuli — partnerite PDF-id:** deterministlikud PDF-parserid (pdfplumber) sama liidese taha. Üks adapter formaadi kohta.

---

## Tehnoloogiavalikud (soovituslik)

| Kiht | Valik | Põhjus |
|---|---|---|
| Mudelid / valideerimine | Pydantic v2 | Tervishoiu andmevalideerimine, selge skeem |
| API | FastAPI | Async, auto-OpenAPI, demoks ideaalne |
| DB / migratsioonid | SQLAlchemy + Alembic, Postgres | Stack on juba Postgres |
| XML | lxml | Medisoft-parser |
| PDF | pdfplumber | Deterministlik tekstieraldus |
| Testid | pytest | — |
| Lint | ruff | — |
| Lokaalne DB | Docker Compose | Kiire kohalik Postgres |

---

## Mida mitte teha veel

- Ära oota Medisofti/partnerite faile, et alustada — sünteetilised andmed unblock'ivad kõik tuuma.
- Ära ehita täielikku audit trail'i / krüpteerimist enne demo't — pane skeleton, lihvi kiirendi N2-s.
- Ära aja andmemudelit lukku üksi — vii mustand arhitektiga kokku enne DB-tööd.

---

## Kontrollnimekiri demoks valmis

- [ ] Üks käsk / API-kõne annab katmata teenuste nimekirja + kogusumma
- [ ] Sünteetilised andmed teadaoleva õige vastusega, mille vastu loogika valideeritud
- [ ] CSV-eksport töötab
- [ ] Medisofti näidisfail (kui saabunud) loeb pärisandmeid sama loogikaga
- [ ] README, et tiimiliige saab demo ise käivitada
