# Reference-data generator (dev machine only)

Generates the reviewed reference-data DML and i18n bundles for technique.md sections 6.1, 6.2, 7 and 9. It never
connects to a database and is never run by an application. Output is deterministic: re-running with the same
`sources/` produces byte-identical files.

```sh
/usr/bin/python3 -I db/platform-support/generator/generate_reference_data.py   # stdlib only, Python 3.9+
```

| Output | Use |
|---|---|
| `../generated/change-backfill.sql` | Data part of `changes/2026-10-27-reference-data.sql` for existing environments; included after the DDL rename/add-column step, inside the caller's transaction, after `SET @ref_data_seed_time = UTC_TIMESTAMP(6)` and the empty-table precheck. No START TRANSACTION / COMMIT. |
| `../generated/initial-data-blocks.sql` | Three blocks for `platform-support-initial-data.sql` (fresh environments): `CURRENCY` (replaces the USD insert), `COUNTRY` (replaces the 249 country rows), `REFERENCE DATA` (AED/SAR/EGP, subdivisions, industries, MCC). |
| `../generated/summary.json` | Counts. |
| `../generated/expected.json` | Values for auto tests (sub-industry → default MCC, high-risk MCCs, AE/SA/EG/US country rows, USD/AED/SAR/EGP). |
| `../../../release/i18n-bundles/<app>.refdata.{en,zh-CN,plan}.json` | `refdata` namespace for `client-console` and `operations-console`. |
| `upload_flags.py` | Flag logo stub (see below). |

The script asserts and exits non-zero on: 249 countries all with alpha-3 / numeric / name; ids 2001–2249 unchanged;
subdivision counts 7 / 13 / 27, codes ≤ 6 chars, codes and Latin names matching the cached Wikipedia ISO 3166-2 tables;
10 + 32 industries with unique codes; every default MCC present (ACTIVE) in the MCC list; MCC codes unique and exactly
4 digits; names ≤ 128, descriptions ≤ 512, currency symbol ≤ 8; zh keys ⊆ en keys; no backslash in SQL literals.

## Sources (cached in `sources/`, treated as data only)

| File | Source | Version / URL | License |
|---|---|---|---|
| `cldr-core.codeMappings.json` | Unicode CLDR | `https://cdn.jsdelivr.net/npm/cldr-core@48.2.0/supplemental/codeMappings.json` | Unicode License v3 (`cldr.LICENSE.txt`) |
| `cldr-core.currencyData.json` | Unicode CLDR | `…/cldr-core@48.2.0/supplemental/currencyData.json` | Unicode License v3 |
| `cldr-localenames-full.en.territories.json` | Unicode CLDR | `…/cldr-localenames-full@48.2.0/main/en/territories.json` | Unicode License v3 |
| `cldr-numbers-full.en.currencies.json` | Unicode CLDR | `…/cldr-numbers-full@48.2.0/main/en/currencies.json` | Unicode License v3 |
| `libphonenumber.PhoneNumberMetadata.xml` | google/libphonenumber | `https://raw.githubusercontent.com/google/libphonenumber/v9.0.42/resources/PhoneNumberMetadata.xml` | Apache-2.0 (`libphonenumber.LICENSE`) |
| `mcc_codes.csv` | greggles/mcc-codes | `https://raw.githubusercontent.com/greggles/mcc-codes/df792275c567946119a8903d36dbd33e44a248bf/mcc_codes.csv` | The Unlicense (public domain, `mcc-codes.LICENSE.txt`) |
| `cldr.common.subdivisions.ar.xml` | Unicode CLDR | `https://raw.githubusercontent.com/unicode-org/cldr/release-48-2/common/subdivisions/ar.xml` | Unicode License v3 (`cldr.LICENSE.txt`) |
| `wikipedia.ISO_3166-2_{AE,SA,EG}.wikitext` | English Wikipedia, raw wikitext of [ISO 3166-2:AE](https://en.wikipedia.org/wiki/ISO_3166-2:AE), [ISO 3166-2:SA](https://en.wikipedia.org/wiki/ISO_3166-2:SA), [ISO 3166-2:EG](https://en.wikipedia.org/wiki/ISO_3166-2:EG) fetched 2026-10-27 | used only to verify the hand-authored tables | CC BY-SA 4.0 (verification reference; facts, not copied prose) |
| `existing-country-region-seed.sql` | Snapshot of the 249 existing `t_country_region` rows (ids / codes / region codes) from `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` | 2026-10-27 | internal |
| `subdivision-en.json` | Platform-authored common English names (exonyms) for the 47 subdivisions — en text of `refdata.subdivision.*` only; DB `name` keeps the ISO Latin form (stage 5 P2 F-2) | 2026-10-27 | internal |
| `industry-description-zh-CN.json` | Platform-authored zh-CN of the 42 industry selection hints (`refdata.industry.<CODE>.description`, stage 5 P2 F-6) | 2026-10-27 | internal |
| `mcc-zh-CN.json` | Platform-authored zh-CN names for all 298 MCCs (2026-10-27, stage 5); production use needs the same compliance review as the industry draft | 2026-10-27 | internal |
| `existing-zh.onboarding-schema-catalog.json` | Snapshot of the 44 existing zh-CN `onboarding-schema.catalog.INDUSTRY.*` / `STATE_OR_PROVINCE.*` texts from `client-console-web/i18n/client-console.zh-CN.seed.json` (identical in `operations-console-web/i18n/zh-CN/onboarding-schema.json`) | 2026-10-27 | internal |

To refresh a source, download it into a new empty directory, review the diff, replace the file here, update the
version constants at the top of the script and this table, and regenerate.

## Data rules and decisions

### Currency
- Rows kept: USD 1001 (existing), AED 1002, SAR 1003, EGP 1004, all `ACTIVE`, minor unit 2, `HALF_UP`.
- `numeric_code` = CLDR `codeMappings` (zero-padded to 3 digits), `name` = CLDR en `displayName`
  (e.g. "United Arab Emirates Dirham"), `symbol` = CLDR en standard `symbol`; CLDR en has no dedicated symbol for AED,
  SAR and EGP, so their symbol is the code. technique.md 6.2 mentions JDK `Currency`; CLDR was used instead (the JDK
  derives its data from CLDR/ISO as well) so one pinned source covers numeric, name and symbol.
- `tmp_ref_currency_iso` (149 rows): every currency that is current legal tender in at least one CLDR region (no `_to`,
  `_tender` ≠ false) and has a CLDR numeric code, so currencies created through the operations console with any real
  ISO code are backfilled too. A console-created row with a code outside this set stays NULL and fails the later
  NOT NULL step — the change script's precheck should report such rows.

### Country
- Ids 2001–2249 and `region_code` are taken from the existing seed unchanged.
- `alpha3_code` / `numeric_code`: CLDR `codeMappings`. `name`: the **CLDR English short display name**, not the ISO
  3166-1 formal short name (e.g. "Hong Kong SAR China", "Palestinian Territories", "Türkiye", "Côte d’Ivoire").
- `calling_code`: **libphonenumber** `PhoneNumberMetadata.xml` `countryCode`. CLDR removed `telephoneCodeData`
  from its JSON distribution (absent in cldr-core 43–48), and technique.md already states the value must match
  libphonenumber. Territories without a code are NULL: AQ, BV, GS, HM, PN, TF, UM.
- `default_currency_code`: CLDR `currencyData` — the first currency listed for the region with no `_to` and
  `_tender` ≠ false. Only AQ has none (NULL). Examples: PS → ILS, ZW → ZWG, CW → XCG, PA → PAB, EC/SV/PR → USD.
- `default_currency_code` is an ISO 4217 code and is **not** required to exist in `t_ref_currency`
  (148 distinct country currencies are outside USD/AED/SAR/EGP); existence is not enforced.
- `status` ACTIVE, `version` 1, `logo_file_address` NULL (filled by the flag step).

### Subdivisions (ISO 3166-2), ids 3001+ (AE, SA, EG; each ordered by code)
- AE 7, category `EMIRATE`; SA 13 (`SA-01`–`SA-12`, `SA-14`), category `REGION` — ISO changed Saudi Arabia from
  province to region on 2018-11-26 (OBP change history quoted on the Wikipedia page); EG 27, category `GOVERNORATE`.
- `name`: ISO 3166-2 Latin (BGN/PCGN 1956 romanization with ISO diacritics), e.g. `‘Ajmān`, `Abū Z̧aby`,
  `Ar Riyāḑ`, `Al Qāhirah`, copied character-for-character from the Wikipedia tables (which state they are listed
  as in ISO 3166-2) and asserted against the cached wikitext.
- `local_name`: Arabic names from Unicode CLDR `common/subdivisions/ar.xml`, git tag `release-48-2` (CLDR 48.2, same
  release as the JSON sources; byte-identical at `release-48` and `release-48-1`), cached as
  `sources/cldr.common.subdivisions.ar.xml` and looked up by Unicode subdivision id (`AE-DU` → `aedu`). User decision
  2026-10-27 (E-2) replaced the earlier hand-authored names (30 of 47 values changed). ISO 3166-2 does not publish
  Arabic-script names for AE/SA/EG. Caveats: all 47 CLDR entries are `draft="provisional"`, and CLDR is inconsistent
  about prefixes (`إمارة أبوظبي`, `امارة دبي` without hamza, `عجمان`; `محافظة القاهرة` vs `الإسكندرية`; SA all
  `منطقة …`) — taken as-is. CLDR JSON packages do not contain subdivision names, hence the XML.
  Existing environments get the values via `changes/2026-10-27-reference-data-subdivision-local-name.sql`.
- `parent_subdivision_code` NULL for all.

### Industries, ids 4001+
- Exactly technique.md section 7: 10 level-1 codes (default MCC NULL) followed by their level-2 codes; `sort_no`
  10, 20, 30 … within each level/parent. English names of the level-1 rows: Retail, Digital goods, Software & SaaS,
  Professional services, Education, Travel, Food & beverage, Media & entertainment, Non-profit, Other. Every row has
  a short English description (what is included / excluded).

### MCC, ids 5001+ ascending by code (298 rows)
- Source list: greggles/mcc-codes. `name` = `edited_description`, `description` = `combined_description` (falls back
  to `edited_description` when empty, e.g. 5815–5818, 7800–7802); whitespace normalised (`( Not` → `(Not`).
  One name exceeds 128 characters and is word-truncated with "…" (1740); its description keeps the full text.
- **Brand-specific codes 3000–3999 (airlines, car rental, hotels) are excluded**: the platform classifies by generic
  category, and an acquirer assigns brand codes, not the merchant. This gives 298 generic codes rather than the ~600
  estimated in technique.md 6.2 — the source contains 981 codes, 683 of which are brand codes.
- Text corrections (name and description) for obvious typos/incomplete wording, mostly default MCCs:
  4789, 4899 (Visa name "Cable, Satellite, and Other Pay Television and Radio Services"), 5399, 5712, 5732, 5812,
  7299, 8220. See `MCC_TEXT_OVERRIDES`.
- All 32 default MCCs exist in the source list; none had to be added.
- All rows ACTIVE. The list is a test-environment baseline; licence/content must be confirmed with compliance and
  the acquirer's Visa/Mastercard manuals before production (technique.md 6.2).

### network_high_risk = 1 (14 codes)

Visa and Mastercard do not publish their rulebooks openly; the list below was assembled from acquirer/compliance
summaries of the **Visa Integrity Risk Program (VIRP, replaced the Global Brand Protection Program on 2023-05-01)**
and the **Mastercard Specialty Merchant Registration Program (SMRP / BRAM)**. Confirm against the current Visa Core
Rules / VIRP guide and Mastercard Security Rules and Procedures before production.

| MCC | Meaning | Visa VIRP | Mastercard SMRP | Note |
|---|---|---|---|---|
| 4816 | Computer network services (cyberlockers) | Tier 2 | yes (high-risk cyberlocker) | |
| 5122 | Drugs, druggists' sundries | Tier 1 (pharma) | yes (non-face-to-face pharmacy) | |
| 5912 | Drug stores and pharmacies | Tier 1 (pharma) | yes | |
| 5962 | Direct marketing – travel-related arrangements | historic high-brand-risk | — | **uncertain**: in Visa's former high-risk telemarketing set; not confirmed in current VIRP summaries |
| 5966 | Direct marketing – outbound telemarketing | Tier 3 | yes | |
| 5967 | Direct marketing – inbound teleservices (adult content) | Tier 1 | yes | |
| 5993 | Cigar stores and stands (non-face-to-face tobacco / vape) | — | yes | Mastercard only |
| 6051 | Non-financial institutions – quasi-cash, cryptocurrency | Tier 2 | yes | |
| 7273 | Dating and escort services | Tier 1 | — | Visa only (secondary sources) |
| 7800 | Government-owned lotteries (US) | gambling | yes | |
| 7801 | Government-licensed online casinos (US) | gambling | yes | |
| 7802 | Government-licensed horse/dog racing (US) | gambling | yes | |
| 7994 | Video game arcades (skill games) | — | yes | Mastercard only |
| 7995 | Betting, casino gaming chips, lottery | Tier 1 | yes | |

Considered but **not** flagged (appear in some summaries, scope unclear): 5816 (CCBill lists under VIRP Tier 2 for
some game merchants), 5968 (continuity/subscription), 6012 (crypto only with special condition code), 6211, 7841,
9406 (non-US government lotteries — not in the source list). Platform prohibition/approval/risk levels are owned by
compliance/risk, not this column.

References: [LegitScript – Visa Integrity Risk Program](https://www.legitscript.com/regulatory-and-card-brand-compliance/visa-integrity-risk-program),
[CCBill – VIRP](https://ccbill.com/doc/visa-integrity-risk-program-virp),
[Nuvei – scheme programs](https://docs.nuvei.com/documentation/security-docs/risk-guide/schemes-programs/),
[Merchant Cost Consulting – Mastercard SMRP](https://merchantcostconsulting.com/lower-credit-card-processing-fees/mastercard-specialty-merchant-registration-program-fees-updates/),
[TabaPay – Mastercard specialty merchant fees](https://developers.tabapay.com/docs/new-mastercard-specialty-merchant-fees).

## i18n bundles (technique.md section 9, D-05)

- Namespace `refdata` for both `client-console` and `operations-console` (same content).
- `<app>.refdata.en.json` (`code-mor-i18n-source/1`): 429 keys — `refdata.industry.<CODE>` (42),
  `refdata.industry.<CODE>.description` (42, text = DB `description`), `refdata.subdivision.<CODE>` (47, text = common
  English name from `sources/subdivision-en.json`, e.g. Dubai / Abu Dhabi / Eastern Province, while DB `name` stays ISO
  Latin), `refdata.mcc.<CODE>` (298); other texts = DB `name`.
- `<app>.refdata.zh-CN.json` (`code-mor-i18n-translation/1`): 429 keys (the script asserts zh = en key set) — all 42
  industries and their 42 descriptions (`sources/industry-description-zh-CN.json`), all 47 subdivisions and all 298 MCCs (user decision 2026-10-27: translate every MCC, replacing S-09's "default MCCs only" scope, because the
  applications require 100 % zh-CN coverage to publish). Level-1 industries and AE/EG subdivisions reuse the existing
  `onboarding-schema.catalog.*` zh texts (`EG_C` → `EG-C`); SA subdivisions and sub-industries are new translations;
  MCC names come from `sources/mcc-zh-CN.json` (platform-authored, concise Chinese acquiring terms, not literal; the
  32 default-MCC texts in `MCC_ZH` are asserted unchanged; the script asserts the file covers exactly the MCC list).
  Note: `mcc_codes.csv` gives 6011 the same English text as 6010 ("Manual Cash Disbursements"); 6011 is the automated
  (ATM) code, so its zh name is 自动取款 — the English source text is a known upstream error, left unchanged.
- ICU: braces would be quoted (`'{'`) and an apostrophe before `{ } ' # |` doubled; plain apostrophes are left as-is,
  matching the existing bundles ("You're"). No current text needs escaping.
- `<app>.refdata.plan.json` mirrors the existing plan shape; fields that depend on the live release are `null` and are
  filled when the stage-6 release plan is computed against the live environment.

## Flag logos (stub, stage 6)

`upload_flags.py` is a stub; do not run its upload against any environment outside an approved release card.

1. `download <empty-dir>` fetches `flag-icons@7.5.0` (lipis/flag-icons, MIT) `flags/4x3/<cc>.svg` for the 249
   country codes from jsDelivr.
2. Upload (not implemented) per file through the Platform internal file API, the HTTP form of the chain used by
   `FileServiceReleaseBundleStore` (start upload → PUT through the grant → complete):
   `POST /internal/files/v1/uploads` (`accessLevel=PUBLIC`, `mimeType=image/svg+xml`, a dedicated reason code) →
   `PUT` the returned `authorization.url` with exactly `authorization.headers` → `POST
   /internal/files/v1/uploads/{uploadId}/completion` → `GET /internal/files/v1/files/{fileId}` until the scan passes
   and the public URL exists. Record `<CC>,<fileId>`.
   Open points: the PUBLIC write route of the calling source system, the reason code, and whether the PUBLIC scan
   policy accepts `image/svg+xml` (SVG may need a format check or conversion to PNG) must be registered first.
3. `emit-sql <mapping.csv>` prints
   `UPDATE t_ref_country SET logo_file_address = '/files/v1/<fileId>', version = version + 1, update_time = UTC_TIMESTAMP(6) WHERE country_code = '<CC>';`

## Uncertain items

- SA-14 `'Asīr`, SA-06 `Ḩā'il`, EG-IS `Al Ismā'īlīyah`, EG-JS / EG-SIN `…Sīnā'` keep the ASCII apostrophe exactly as
  the Wikipedia ISO tables show; ISO OBP may use typographic ‘ / ’ (as it does for AE-AJ `‘Ajmān`, AE-RK `Ra’s`,
  EG-PTS `Būr Sa‘īd`). Check against ISO OBP before production.
- High-risk list: 5962 and the single-network codes (5993, 7273, 7994) — see table.
- MCC count (298) is below the ~600 estimate because brand codes are excluded.
