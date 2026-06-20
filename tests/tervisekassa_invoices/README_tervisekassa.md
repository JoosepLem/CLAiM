# Tervisekassa raviarve testandmed — Sinilille Tervisekeskus, mai 2026

## Failid

**`tervisekassa_invoices.json`** — peamine testandmestik (167 kirjet). See on see, mille vastu matching
logic peab töötama. Sisaldab tahtlikult lünki ja mõned valepositiivsed kirjed (vt allpool) —
need EI OLE märgistatud failis endas, vaid ainult ground truth failis.

**`matching_groundtruth.json`** — vastuste võti. Ei kuulu sisendandmete hulka, ainult tulemuste
kontrollimiseks. Sisaldab täpset jaotust, mis on tahtlikult välja jäetud ja millised kirjed on
valepositiivsed.

## tervisekassa_invoices.json struktuur

Üks kirje = üks patsiendi raviarve, kõik tema mai partnerteenused (sõltumata sellest, mitu
erinevat partnerit teenust osutas) koos ühel arvel — samamoodi nagu päris Terviseportaali
näidetes (Karulaugu Tervisekeskus OÜ kontonäidetes), kus ühel arvel olid koos perearstiabi,
labor ja röntgen.

| Väli | Selgitus |
|---|---|
| `arve_id` | PA-vormingus arve number |
| `saadetud_tervisekassale` | bool — kas arve on Tervisekassale juba esitatud |
| `saatmise_staatus` | "Avatud" (koostamisel, pole veel saadetud) / "Saadetud Tervisekassale" |
| `staatus` | "Avatud" / "Tasutud" |
| `arve_kuupaev` | arve koostamise kuupäev |
| `raviteenuse_periood` | algus/lõpp — patsiendi tegelike teenuste kuupäevavahemik mais (NB: nagu päris näidetes, **ei ole iga teenuse rea kohta eraldi kuupäeva** — ainult arve tasemel periood) |
| `patsient.nimi`, `patsient.isikukood` | checksum-valid Eesti isikukoodid |
| `asutus` | väljastav kliinik — alati Sinilille Tervisekeskus OÜ (kõik partneriteenused arveldatakse Tervisekassale perearstikeskuse enda raviarvel) |
| `teenused[].kood`, `.nimetus`, `.hind` | Partnerteenused_1.xlsx ametlikust koodinimekirjast — **NB: sama kood võib esineda mitmel real**, kui mitu erinevat alamuuringut kuulub sama koondkoodi alla (täpselt nagu päris näidetes nähtud, nt "SÕELUURINGUD, HORMOONUURINGUD" 2 korda) |
| `arve_summa_kokku` | rea hindade summa |

**26 arvet on "Avatud"** (koostatud, aga Tervisekassale veel saatmata) — need ei tohiks
tõenäoliselt minna automaatselt "leitud lekkena" eskalatsiooni, sest perearst on need juba
oma süsteemis järjekorda pannud.

## Mida andmed katavad

Kõik 8 partnerarve (Synlab + 3 haiglat + Confido + Fertilitas + Mammograaf + Tallinna
Eriarstikeskus) teenused on kaetud, **välja arvatud ligikaudu 500€ ulatuses, mis on
tahtlikult välja jäetud matching logica testimiseks**:

- **51,51 € — struktuurne lünk, mitte viga.** Synlabi Glükoos ja Uriini ribaanalüüs
  teenustel pole Partnerteenused.xlsx's vastavat Tervisekassa koodi — neid ei saagi
  Tervisekassale eraldi arveldada. Need ON katmata kõigil arvetel, kuid see ei ole midagi,
  mida perearst "unustas" — pigem peaks matching logic need eraldi kategoriseerima
  ("ei ole arveldatav", mitte "puudub").

- **457,98 € — tahtlik puudu jäetud arveldamine.** 95 teenust on hajutatud 95 erineva
  patsiendi vahel (max 1 patsiendi kohta), simuleerimaks realistlikku unustatud
  arveldamist. See on see, mida matching logic peaks leidma.

Kokku reaalne lõhe: **509,49 €** (lähedal soovitud ~500 € sihile).

## Valepositiivsed (false positives)

`matching_groundtruth.json` → `false_positives` sisaldab 5 näidet kahes vormis:

1. **2 täiesti väljamõeldud arvet** kahele patsiendile, kellel pole üldse partnerarvetel
   ühtegi kirjet sel perioodil (Heli Mänd, Rasmus Tobias).
2. **2 sisestatud lisarida** muidu täiesti reaalsetes arvetes (Indrek Raud, Triin Raud) —
   testimaks, kas matching logic suudab leida valepositiivse ka siis, kui ülejäänud arve
   on korrektne.

## Teadaolevad piirangud

- Hinnad on Partnerteenused.xlsx ametlikud hinnad, mis enamasti (aga mitte alati 1:1)
  kattuvad partnerite endi arvetel kasutatud hindadega.
- `asutus.reg_nr` ja arstide litsentsinumbrid on väljamõeldud, mitte päris registriandmed.
