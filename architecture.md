# CLAiM partnerarvete kontrolli toote arhitektuur

> **Taust:** 10 000 USD AWS krediiti (hosting), 25 000 USD Google Cloud krediiti (kehtib 2 aastat), 500 EUR Swedbank stardiraha (plaan kulutada Authentigate + agentic development).

---

## 1. GDPR raamistik ja mõju CLAiM arhitektuurile

### 1.1 Vastutava ja volitatud töötleja roll

CLAiM on **volitatud töötleja** (data processor). Iga kliinik on **vastutav töötleja** (data controller) oma patsientide andmete suhtes.

**Praktilised tagajärjed arendajale:**

- Kliinik otsustab, mis eesmärgil andmeid töödeldakse, kui kaua säilitatakse, kellele edastatakse. CLAiM ei otsusta nende küsimuste üle.
- CLAiM töötleb andmeid **AINULT** kliiniku juhiste alusel. Juhised on dokumenteeritud DPA-s ja teenuselepingus.
- CLAiM **ei tohi** kasutada andmeid oma eesmärkidel — sh ei tohi treenida ML-mudeleid, koguda statistikat müügi jaoks, jagada andmeid partneritega.
- Iga uus andmekasutuse stsenaarium (nt aggregeeritud analytics dashboard, võrdlemine teiste kliinikutega) on õiguslikult uus töötlemine, mis nõuab kliiniku eraldi juhist või lepingu muudatust.

See on aluseks kogu arhitektuuri otsustele — CLAiM on rakendus, mis töötleb teiste organisatsioonide andmeid nende organisatsioonide nimel, mitte rakendus, mis kogub oma andmeid.

### 1.2 Eriliigilised andmed (art 9)

GDPR art 9 lg 1 keelab "eriliigiliste andmete" töötlemise, välja arvatud lõikes 2 toodud aluste alusel. Terviseandmed on eriliigiliste andmete hulgas.

**CLAiM töötleb terviseandmeid, mille hulka kuuluvad:**

- Tervishoiuteenuse osutamise fakt (kes mis teenuse sai)
- Teenuse osutamise kuupäev ja kood (TIS kood)

**Mida CLAiM ei töötle:**

- Diagnoosi (RHK kood), patsiendi nime partnerarvel — kuigi see võib olla raviarve Excelis, kustutatakse sissetuleku punktis
- Kliinilisi märkmeid, anamneesi, suunamise põhjusi

See piiritlemine on kategooriline. Iga andmeväli, mida me lisame, peab läbima kontrolli: kas see on vajalik lubatud funtsionaalsuse täitmiseks? Kui ei, eemaldame sissetuleku punktis. See piirang teeb mõjuhinnangu argumendi tugevamaks ja vähendab andmerikke ulatust.

### 1.3 Õiguslik alus

GDPR nõuab töötlemiseks õiguslikku alust. Eriliigiliste andmete puhul peab olema kaks alust:

- Art 6 alus (üldine töötlemise alus)
- Art 9 lg 2 alus (eriliigiliste andmete täiendav alus)

**CLAiMi kontekstis:**

- **Art 6 lg 1 punkt c** — töötlemine on vajalik vastutava töötleja seadusjärgse kohustuse täitmiseks. Konkreetselt: ravikindlustuse seaduse § 35–36 kohustab kliinikut esitama Tervisekassale arveid osutatud tervishoiuteenuste eest.
- **Art 9 lg 2 punkt b** — eriliigiliste andmete töötlemine on vajalik sotsiaalkindlustusalase kohustuse täitmiseks. Eesti ravikindlustus on sotsiaalkindlustuse osa (sotsiaalseadustiku üldosa seadus § 4).

CLAiM ise ei oma iseseisvat õiguslikku alust. Töötleme andmeid kliiniku õigusliku aluse all, volitatud töötlejana. Kui keegi küsib "kas saame neid andmeid kasutada X jaoks?", on vastus enamasti **EI**, välja arvatud kui X on otseselt reconciliation.

### 1.4 GDPR-i põhiprintsiibid CLAiMi kontekstis

GDPR art 5 lg 1 sätestab 6 põhiprintsiipi, mida iga töötlemine peab järgima:

| Põhimõte                                   | Rakendumine CLAiMi arhitektuurile                                                                                                                                                                |
| ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **(a) Seaduslikkus, õiglus, läbipaistvus** | Kliinik teavitab patsiente isikuandmete töötlemisest oma andmekaitsetingimustes. CLAiM ei suhtle patsiendiga otse — patsiendil ei ole CLAiMis kontot, ei vaata midagi CLAiMi rakenduses.         |
| **(b) Eesmärgi piirang**                   | Andmeid kasutatakse ainult reconciliation jaoks. Mitte mudelite treenimiseks, mitte analytics jaoks (välja arvatud kliinikule endale, aggregeeritud kujul), mitte ühegi muu eesmärgi jaoks.      |
| **(c) Andmete minimeerimine**              | Ainult need väljad, mida reconciliation vajab. Whitelist-põhine sissetulek (vt ptk 2.10). Iga lisaväli on lisarisk ja lisakoormus mõjuhinnangule.                                                 |
| **(d) Õigsus**                             | Andmeid ei muudeta CLAiMis pärast sisestamist. Kui kliinik annab vale andmestiku, parandab seda kliiniku allikas, mitte CLAiM. CLAiM võib märkida andmeid "tühistatud", aga ei muuda originaali. |
| **(e) Säilitamise piirang**                | Andmeid säilitatakse minimaalse vajaliku aja jooksul. Konkreetne lahendus vt 2.9.                                                                                                                |
| **(f) Terviklikkus ja konfidentsiaalsus**  | Krüpteerimine, pseudonümiseerimine, access control, audit log. Kõik nõutavad meetmed peavad olema rakendatud ja dokumenteeritud.                                                                 |

Lisaks: **vastutus (art 5 lg 2)** — CLAiM peab suutma tõendada, et järgime kõiki ülaltoodud printsiipe. See tähendab dokumentatsiooni, audit-loge, kirjalikke poliitikaid, mitte ainult sõnu mõjuhinnangus.

### 1.5 Andmesubjekti õigused

GDPR annab andmesubjektile (patsiendile) õigused, mida vastutav töötleja (kliinik) peab täitma. CLAiM kui volitatud töötleja peab abistama kliinikut nende õiguste täitmisel.

| Õigus                                | Rakendumine CLAiMis                                                                                                                                                                                                 |
| ------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Tutvumisõigus (art 15)**           | Kui patsient küsib kliinikult, milliseid andmeid tema kohta töödeldakse, peab kliinik suutma CLAiMist väljastatud andmestiku näidata. CLAiM peab pakkuma eksporti isikukoodi alusel (vt 2.15).                      |
| **Kustutamisõigus (art 17)**         | Piiratud tervisedata kontekstis (art 17 lg 3 punkt b, c — säilitamiskohustus seaduse alusel). Tavaliselt CLAiMis ei realiseeru. **[JAARIKA KINNITUS VAJALIK: kas kustutamise mehhanism on piloodi jaoks vajalik?]** |
| **Parandamise õigus (art 16)**       | Kui andmed on valed, peab parandama. Kuid CLAiMis ei tohi keegi andmeid muuta — parandus tehakse kliinikus allikas, seejärel uus sissevedu CLAiMi.                                                                  |
| **Vastuväiteõigus, ülekandmisõigus** | Õigusliku aluse (art 6 lg 1 c) iseloomu tõttu enamasti ei realiseeru.                                                                                                                                               |

### 1.6 Andmerikke käsitlemine

GDPR art 33 nõuab andmerikke teavitamist Andmekaitse Inspektsioonile 72 tunni jooksul rikke avastamisest. Art 34 nõuab andmesubjektide teavitamist, kui rikke tagajärjed on suure riskiga.

CLAiMi DPA kohustab CLAiMi teavitama kliinikut **24 tunni jooksul** rikke avastamisest, et kliinik saaks omakorda 72-tunnise tähtaja jooksul teavitada AKI-d.

**Mida see arendajale tähendab:**

- Monitooring ja alarmid peavad olema piisavad, et avastada rikkumisi kiiresti (mitte alles auditi käigus)
- Incident response protseduur peab olema dokumenteeritud
- Kasutusvalmiduse harjutus tehakse vähemalt kord aastas (mõjuhinnangu nõue)

---

## 2. Arhitektuurilised otsused

### 2.1 Autentimine — Authentigate Starter MFA

**Otsustatud:** Authentigate Starter (€29/kuus) 

*Põhjendus:* Tagab tuvlise riiklikel isikut tõendavatel dokumentidel põhineva mitmefaktorilise autentimise. Eriliigilisi andmeid töötlevatel rakendustel on kohustus kasutada MFA-d. Authentigate on sobilik, kuna toote lähiaja plaanitav kasutajaskond on Eesti perearstikeskused. Authentigate on ka tehniliselt võrdlemisi lihtsasti implementeeritav - ühe API kaudu juurdepääsu ID-kaardile, Mobiil-ID-le ja Smart-ID-le. Üks integratsioon ühe pakkuja API kaudu. Lahenduse maksumus on hetkel tasulistest pakkujatest turu odavaim.

**Välistatud alternatiivid:**

- **Otse SK ID Solutions AS-iga** — odavam pikemas perspektiivis, kuid nõuab subscriber agreement'i, IP whitelistingu protsessi (2–4 nädalat), eraldi production-application'i ja iga eID-meetodi eraldi integreerimist. Piloodi käivitamise ajakavasse ei mahu.
- **Ainult ID-kaardi tugi** — odavam, kuid Smart-ID on Eestis nii laialt levinud, et selle puudumine on UX-i puudus.

Implementatsioon: Kasutades Authentigate dokumentatsiooni implementeerida OIDC lahendus kasutades Spring Security sisseehitatud OAuth2 client tuge.

### 2.2 Kasutajasessioonid

**Otsustatud:** lühiajaline JWT + refresh token turvalises brauseriküpsises

*Põhjendus:* Tagab efektiivse skaleeruva sessionide halduse ning võimaldab hoida JWT eluea lühiajalisena. Samuti on selle lahenduse puhul võimalik vajadusel kasutaja sessioon kahtetuks muuta.

**Välistatud alternatiivid:**

- **Ühekordne JWT** — lihtne implementeerida, kuid tokeni eluiga peaks olema sel juhul kasutajamugavuse tagamiseks pikk ja puuduks võimalus serveripoolselt sessioon katkestada.
- **Sessiooni ID** — turvaline, kuid vähem efektiivne kui JWT põhine lahendus, kuna vajalik teha rohkem andmebaasi päringuid või cache-päringuid, et sessiooni valideerida.

Implementatsioon: Spring Security JWT valideerimiseks, genereerimiseks jne. Refresh tokeni elutsükkel eraldi service klassina ja talletatud postgres andmebaasis public schemas.

### 2.3 Rollipõhine ligipääsukontroll

**Piloodis:** 1-2 rolli kliinikus + 1 roll CLAiMi poolel (support/admin).

| Roll                                          | Õigused                                                                                                                                          |
| --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Haldusjuht** *(kliiniku-sisene super-user)* | Näeb kõiki keskuse arveid; kontrollib, kinnitab, vaidlustab; haldab partnereid ja hinnakirju; ekspordib andmeid                                  |
| **Admin (CLAiM)** *(teenusepakkuja)*          | Loob ja seadistab kliiniku konto; lisab haldusjuhi; näeb süsteemi-tasemel audit logi; **ei näe** päris arve-sisu ilma eraldi logitud ligipääsuta |

### 2.4 Hosting ja andmete asukoht — AWS

**Otsustatud:** AWS (EL-i piirkond). Kasutatav piirkond eu-north-1 (Stockholm)

*Põhjendus:* Võimaldab hoida kogu töötlemine EL-i piires. Kiirendist saadud 10 000 eurot krediiti võimaldab järgmised kaks aastat hoida infrastruktuuri kulud nullilähedased. Hea IaC tugi (terraform). Tiimi sees rohkem kogemust, kui muude pilveteenusepakkujatega + LLM-id oskavad hästi aidata. 

**Välistatud alternatiivid:**

- **Google Cloud** — kuni siin on ka 25 000 USD krediiti 2 aastaks, siis võime osa tükke siin hostida tulevikus. AWS valisime piloodiks, sest tiimil rohkem kogemust.
- **Digital Ocean** — AWSis on 10 000$ krediiti, DOs ei ole.
- **Eesti pilvepakkujad** — Piiratud toodete portfell, pole krediiti.

### 2.5 Andmebaas — PostgreSQL AWS RDS, kliiniku-spetsiifiline skeem

**Otsustatud:** PostgreSQL andmebaas AWS RDS-is, iga kliinik eraldi skeemis (schema-per-tenant). Rakenduse tasemel päringud on alati skeemiga seotud.

*Põhjendus:* Kliinikute andmed on selgepiiriliselt eraldatud. Võimaldab hiljem vajadusel üle minna database-per-tenant lahendusele. Võimaldab lihtsasti andmeid töödelda (kustutada, kliinikutele edastada)

**Välistatud alternatiivid:**

- **Eraldi andmebaas kliiniku kohta** — tugevaim isolatsioon, kuid halduskulu skaleerub liiga kiiresti.
- **Sama skeem, tenant_id veerg** — lihtsaim, kuid SQL injection korral vahetu leke ning inimeksimus on liialt tõenäoline (kõik pärisngud peavad sisaldama tenant_id, ununemise või bugi korral kohene andmeleke).
- **Row-level security (RLS)** — policy-de haldus on keerukas ja error-mode raskem catch'ida.

Implementatsioon: Päringupõhine vahefilter tuvastab päringu sooritaja kliiniku id ning seda kasutatakse iga päringu tegemisel search_pathi seadistamiseks.

### 2.6 Parserid

**Otsustatud:** Reeglipõhised parserid PDF-ide ja CSV-de jaoks + arve struktuuri muutumise alertid — rakendusse saabub alert, kui mingid väljad ei leia või sisu on vale.

**Välistatud alternatiivid:**

- **Lokaalne agent (Google Cloud)** — vajalik ainult pilt-arve puhul, praegused partnerid on teksti-PDFid.
- **LLM-põhine parsimine (Claude API)** — eeldab andmete edastamist USA-sse, lisab alltöötleja DPA-sse, hallutsinatsioonirisk.

Implementatsioon: TBD

### 2.7 Krüpteerimise alustase

**Otsustatud:**

- TLS 1.2+ kõikidel võrguliikluse punktidel
- AWS RDS at-rest krüpteerimine (AES-256, tervisedata tööstusstandard)
- HTTPS-i sertifikaadid (Let's Encrypt või sarnane)

Võrguliikluse ja kettatasemel andmed on krüpteeritud automaatselt — need on infrastruktuuri tasemel paigas. Kombinatsioon TLS + at-rest krüpteering + rakenduse-tasemel pseudonümiseerimine (vt 2.11) katab GDPR art 32 nõuded.

### 2.8 Audit-logi sisu ja säilitusaeg

**Otsustatud:** iga rakenduse päring, mis puudutab patsiendi andmeid, logitakse audit-logisse. Audit-log on append-only. Säilitusaeg on **12 kuud**. Iga endpoint, mis tagastab patsiendi andmeid, peab läbima logimise wrapper'i.

#### Logi kategooriad

**Kategooria 1: Andmesubjekti isikuandmete vaatamine ja töötlemine** *(kriitiline)*

Iga sündmus, mille käigus isikustatud andmed dekrüpteeritakse või töödeldakse:

- Dekrüpteerimine kuvamiseks
- Ajatempel
- Kasutaja ID (Authentigate)
- Kliiniku ID (skeem)
- Kirje ID (milline patsient)
- Millised väljad dekrüpteeriti (isikukood, nimi, mõlemad)
- Kontekst (workbench_view, export, andmesubjekti taotlus)
- Sessiooni ID
- IP-aadress

**Kategooria 2: Autentimine ja autoriseerimine** *(kriitiline)*

- Edukas sisselogimine (kasutaja ID, ajatempel, IP, autentimismeetod)
- Ebaõnnestunud sisselogimise katse (põhjus)
- Sessiooni loomine ja lõpetamine
- Rolli/õiguste muutused
- Volituste kontrolli ebaõnnestumised (turvasündmus)

**Kategooria 3: Andmete elutsükkel**

- Andmete sissetulek (kes, mis fail, kliinik, mitu kirjet)
- Andmete kustutamine (kasutaja või süsteem, kirjete arv, kliinik, põhjus)
- Andmete asendus üleslaadimise korral (asendatud kirjete arv)

**Kategooria 4: Süsteemi konfiguratsiooni muudatused**

- Uue kliiniku lisamine
- Krüpteerimisvõtmete rotatsioon
- Access control policy muudatused
- Alltöötlejate lisamine

**Kategooria 5: Turvasündmused/anomaaliad**

- Ebatavaliselt suur päringute arv
- Andmete massiline eksport
- Volituste kontrolli ebaõnnestumised
- Süsteemi vead, mis võivad viidata rünnakule

### 2.9 Säilitustähtajad — rolling 6-kuuline aken

**Otsustatud:** Raviarve ja partnerarve andmeid säilitatakse 6 kuu pikkuses rolling-aknas. Aken arvestab teenuse osutamise kuust (mitte üleslaadimise kuupäevast). Andmed asenduvad uue üleslaadimise korral; lisaks käivitub iga öö automaatne cleanup-task.

**Mida tähendab arendajale:**

- Iga raviarve ja partnerarve kirje kannab `teenuse_kuu` välja (parsitud allikast)
- Nightly cron-task: `DELETE WHERE teenuse_kuu < CURRENT_DATE - INTERVAL '6 months'` iga kliiniku skeemis
- Üleslaadimise loogika: uus fail asendab täielikult olemasoleva poole
- Cleanup-sündmus kantakse audit-logi
- Agregeeritud match-tulemused on eraldi entiteet, mida cleanup ei puuduta — säilivad lepingu kestuse jooksul

**Õiguslik põhjendus:**

- Saatekirja kliiniline kehtivusaeg on 6 kuud (Tervisekassa praktika)
- 6-kuuline aken on minimaalne aeg, mis tagab matchimise võimekuse
- Automaatne kustutamine ilma kasutaja sekkumiseta tõendab vastutust (art 5 lg 2)

### 2.10 Andmete minimeerimine — mida partnerarvelt loeme

**Otsustatud:** Partnerarvelt loeme isikukood, teenused, teenuste osutamise kuupäevad, hinnad, kogused. Patsiendi nimi loetakse raviarve Excelist (Tervisekassa poolt), kus see on niikuinii olemas ja matchimist ei mõjuta.

**Põhjendus:**

- Matchimiseks pole nime vaja — matching toimub isikukoodi HMAC-i alusel
- Kuvamiseks on nimi olemas raviarve poolelt
- Iga loetud väli suurendab andmerikke ulatust ja mõjuhinnangu koormust

### 2.11 Rakenduse tasemel krüpteerimine ja pseudonümiseerimine

**Kokkuvõte:**

- Andmebaas on infrastruktuuri tasemel krüpteeritud (2.7)
- Rakenduse tasemel tundlikud väljad (isikukood) on at-rest krüpteeritud, kasutades envelope encyption meetodit. 
- Master key asub AWS KMS-is ning ei välju seal, kasutatakse vaid kliiniku-spetsiifilise krüpteerimisvõtme dekrüpterimiseks. Kliiniku krüpteerimisvõti on puhkeolekus salvestatud andmebaasi krüpteerituna ja seda kasutatakse kõikide kliiniku patsientide tundliku info krüpteerimiseks
- Isikukood matchimiseks: **HMAC-SHA256** kliiniku-spetsiifilise võtmega (ühesuunaline)
- Isikukood kuvamiseks: **AES-256-GCM** kliiniku-spetsiifilise võtmega (pööratav)
- Andmebaasi otsevaatamine ei näita isikukoodi ega nime
- Prototüübi ehitamise käigus valideeritud, et lahendus on teostatav ning hea jõudlusega.

**Andmerikke korral:** teavitamise kohustus on tõenäoliselt välditav, kuna pseudonümiseeritud andmed on GDPR art 34 lg 3 mõttes "kaitsemeetmega, mis muudab tuvastamise ebatõenäoliseks".

### 2.12 Võtmehoidla — AWS-i võtmehoidla teenus

**Otsustatud:** AWS-i võtmehoidla teenus krüpteerimisvõtmete hoidmiseks.

Võti on eraldi teenuses (AWS võtmehoidla), mille juurdepääsukontroll on IAM-i tasemel eraldi rakenduse enda service account'ist. 

### 2.13 Backup — AWS-i standardlahendus

**Otsustatud:** AWS RDS-i standardlahendus (point-in-time backup).

- AWS teeb automaatselt point-in-time backup-e
- Backup-id sisaldavad pseudonümiseeritud andmeid (vt 2.11), mis on juba kaitstud
- Backup-ide säilitusaeg AWS-i standardpoliitika on 7 päeva

### 2.14 Mõjuhinnang partnerarvete kontrollimisele

**Otsustatud:** Andmekaitse mõjuhinnang partnerarvete kontrollimisele koostatakse enne pärisandmete töötlemist. CLAiM annab kogu tehnilise sisendi. Mõjuhinnang peab katma:

- Mis eesmärgi saavutamiseks, mis alusel, mis isikuandmeid, mis meetoditega töödeldakse
- Kuidas töötlemiseks kavandatud valikud on eesmärgi saavutamiseks vajalikud ja kohased
- Ohtude analüüs, sh nende taseme määramine (kõrge, keskmine, madal)
- Milliseid tagatisi ja meetmeid ohtude suhtes rakendatakse

Käesolev dokument on mõjuhinnangu tehnilise sisendi peamine allikas.

### 2.15 Muud lukustatud põhimõtted

- **Monitooring ja alarmid** peavad olema piisavad, et avastada rikkumisi kiiresti.
- **Incident response** protseduur peab olema dokumenteeritud (läheb ptk 4).
- **Andmesubjekti taotluste eksport** — kliinik sisestab isikukoodi, süsteem arvutab HMAC-i kliiniku võtmega, otsib andmebaasist, dekrüpteerib kuvamiseks, logib audit-logi.
- **AI-first development** — arenduses kasutame AI-tööriistu (Claude Code).
- **Mitmekeelsuse tugi** peab olema olemas.

---

### 2.16 Audit-logi ja manipulatsiooni-avastamise tehnika (viide 2.8)

**Küsimus:** kuidas tagame, et logi ei saa tagantjärele muuta?

- **CloudTrail log-integrity validation** — infrastruktuuri-tasemel, igatahes vaja.
- **Hash-chain audit-logi** — rakenduse tasemel, iga logikirje sisaldab eelmise kirje räsi. Sõltumatu pilvepakkujast.
- Audit logid asuvad andmebaasitabelitena kliiniku schemas

### 2.17 Mis teemadest peaks lahendama nüüd ja mis võib minna 4. peatükki?

**Lahendada nüüd:**

- CI/CD ja deploy-protsess
- Andmete importimise/eksportimise formaadid
- Test-andmed ja test-keskkond
- Konfiguratsioon vs saladus (sekretid võtmehoidlas, konfiguratsioon eraldi)
- Rikketuvastus, monitooring, alerting
- Andmesubjekti õiguste tehniline realisatsioon
- Parseri-konfiguratsioonide hoidmine (Git vs andmebaas vs S3)

