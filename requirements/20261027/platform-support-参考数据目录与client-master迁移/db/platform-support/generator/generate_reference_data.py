#!/usr/bin/env python3
"""Platform reference-data generator (dev machine only).

Reads the pinned source snapshots in ./sources (never executes them) and writes:
  ../generated/change-backfill.sql       data part of the change script for existing environments
  ../generated/initial-data-blocks.sql   blocks for platform-support-initial-data.sql (fresh environments)
  ../generated/summary.json              counts
  ../generated/expected.json             values for auto tests
  <requirement>/release/i18n-bundles/    refdata namespace bundles for client-console and operations-console

Run with:  /usr/bin/python3 -I generate_reference_data.py
Standard library only; output is deterministic (no timestamps, sorted inputs).
See README.md for sources, licenses and decisions.
"""
import csv
import hashlib
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCES = os.path.join(HERE, "sources")
GENERATED = os.path.normpath(os.path.join(HERE, "..", "generated"))
REQ_ROOT = os.path.normpath(os.path.join(HERE, "..", "..", ".."))
I18N_OUT = os.path.join(REQ_ROOT, "release", "i18n-bundles")

CLDR_VERSION = "48.2.0"
CLDR_XML_TAG = "release-48-2"
LIBPHONENUMBER_VERSION = "v9.0.42"
MCC_COMMIT = "df792275c567946119a8903d36dbd33e44a248bf"

ACTIVE = "ACTIVE"
SEED_TIME = "@ref_data_seed_time"
COUNTRY_SEED_TIME = "@country_region_seed_time"
I18N_APPS = ("client-console", "operations-console")
I18N_NAMESPACE = "refdata"


# ---------------------------------------------------------------------------------------------------------------------
# Fixed tables
# ---------------------------------------------------------------------------------------------------------------------

# Currencies kept as rows: code -> fixed id. USD 1001 already exists; the other three are inserted.
CURRENCY_ROWS = [("USD", 1001), ("AED", 1002), ("SAR", 1003), ("EGP", 1004)]
CURRENCY_MINOR_UNIT = {"USD": 2, "AED": 2, "SAR": 2, "EGP": 2}

# ISO 3166-2 subdivisions. Latin names as ISO 3166-2 lists them (romanization with diacritics), verified against the
# cached Wikipedia "ISO 3166-2:AE/SA/EG" wikitext in ./sources (which states names are listed as in ISO 3166-2).
# local_name: Arabic names from Unicode CLDR common/subdivisions/ar.xml (tag release-48-2, same CLDR version as the JSON
# sources; cached as sources/cldr.common.subdivisions.ar.xml). ISO 3166-2 does not publish Arabic-script names for
# AE/SA/EG. User decision 2026-10-27 (E-2): use CLDR as-is; all 47 entries are CLDR draft="provisional".
# Categories: AE emirate; SA region (ISO OBP changed SA from province to region on 2018-11-26); EG governorate.
SUBDIVISIONS = {
    "AE": ("EMIRATE", [
        ("AE-AJ", "‘Ajmān"),
        ("AE-AZ", "Abū Z̧aby"),
        ("AE-DU", "Dubayy"),
        ("AE-FU", "Al Fujayrah"),
        ("AE-RK", "Ra’s al Khaymah"),
        ("AE-SH", "Ash Shāriqah"),
        ("AE-UQ", "Umm al Qaywayn"),
    ]),
    "SA": ("REGION", [
        ("SA-01", "Ar Riyāḑ"),
        ("SA-02", "Makkah al Mukarramah"),
        ("SA-03", "Al Madīnah al Munawwarah"),
        ("SA-04", "Ash Sharqīyah"),
        ("SA-05", "Al Qaşīm"),
        ("SA-06", "Ḩā'il"),
        ("SA-07", "Tabūk"),
        ("SA-08", "Al Ḩudūd ash Shamālīyah"),
        ("SA-09", "Jāzān"),
        ("SA-10", "Najrān"),
        ("SA-11", "Al Bāḩah"),
        ("SA-12", "Al Jawf"),
        ("SA-14", "'Asīr"),
    ]),
    "EG": ("GOVERNORATE", [
        ("EG-ALX", "Al Iskandarīyah"),
        ("EG-ASN", "Aswān"),
        ("EG-AST", "Asyūţ"),
        ("EG-BA", "Al Baḩr al Aḩmar"),
        ("EG-BH", "Al Buḩayrah"),
        ("EG-BNS", "Banī Suwayf"),
        ("EG-C", "Al Qāhirah"),
        ("EG-DK", "Ad Daqahlīyah"),
        ("EG-DT", "Dumyāţ"),
        ("EG-FYM", "Al Fayyūm"),
        ("EG-GH", "Al Gharbīyah"),
        ("EG-GZ", "Al Jīzah"),
        ("EG-IS", "Al Ismā'īlīyah"),
        ("EG-JS", "Janūb Sīnā'"),
        ("EG-KB", "Al Qalyūbīyah"),
        ("EG-KFS", "Kafr ash Shaykh"),
        ("EG-KN", "Qinā"),
        ("EG-LX", "Al Uqşur"),
        ("EG-MN", "Al Minyā"),
        ("EG-MNF", "Al Minūfīyah"),
        ("EG-MT", "Maţrūḩ"),
        ("EG-PTS", "Būr Sa‘īd"),
        ("EG-SHG", "Sūhāj"),
        ("EG-SHR", "Ash Sharqīyah"),
        ("EG-SIN", "Shamāl Sīnā'"),
        ("EG-SUZ", "As Suways"),
        ("EG-WAD", "Al Wādī al Jadīd"),
    ]),
}
SUBDIVISION_COUNTRY_ORDER = ("AE", "SA", "EG")
EXPECTED_SUBDIVISION_COUNTS = {"AE": 7, "SA": 13, "EG": 27}

# zh-CN for SA (no existing translation); AE / EG come from the existing onboarding-schema zh catalog.
SUBDIVISION_ZH_EXTRA = {
    "SA-01": "利雅得省", "SA-02": "麦加省", "SA-03": "麦地那省", "SA-04": "东部省", "SA-05": "卡西姆省",
    "SA-06": "哈伊勒省", "SA-07": "塔布克省", "SA-08": "北部边疆省", "SA-09": "吉赞省", "SA-10": "纳季兰省",
    "SA-11": "巴哈省", "SA-12": "焦夫省", "SA-14": "阿西尔省",
}

# Industry two-level draft: technique.md section 7. (code, en name, zh name, en description, default MCC)
INDUSTRIES = [
    ("RETAIL", "Retail", None,
     "Physical goods sold to consumers online or in store. Excludes digital goods, software and food service.", [
         ("RETAIL_APPAREL", "Apparel & accessories", "服装与配饰",
          "Clothing, shoes, bags, jewellery and fashion accessories.", "5651"),
         ("RETAIL_ELECTRONICS", "Electronics", "电子产品",
          "Phones, computers, consumer electronics and their accessories. Excludes software sold separately.", "5732"),
         ("RETAIL_HOME", "Home & furniture", "家居与家具",
          "Furniture, home furnishings, decor and household goods. Excludes major appliances sold alone.", "5712"),
         ("RETAIL_BEAUTY", "Beauty & cosmetics", "美妆与个护",
          "Cosmetics, skincare, fragrance and personal care products. Excludes pharmacies and prescription products.",
          "5977"),
         ("RETAIL_GENERAL", "General merchandise", "综合百货",
          "Stores selling a broad mix of everyday goods across several categories.", "5399"),
     ]),
    ("DIGITAL_GOODS", "Digital goods", None,
     "Products delivered digitally, such as games, in-game items, e-books, music and video. Excludes software "
     "subscriptions and apps (Software & SaaS) and gambling.", [
         ("DIGITAL_GAMES", "Games & in-game items", "游戏与游戏内物品",
          "Digital games, in-game currency and items, and game passes. Excludes gambling, betting and skill games "
          "with cash prizes.", "5816"),
         ("DIGITAL_MEDIA", "Books, music & video", "图书、音乐与视频",
          "Downloadable or streamed e-books, audiobooks, music, movies and other media. Excludes ongoing streaming "
          "subscriptions (Media & entertainment).", "5815"),
         ("DIGITAL_OTHER", "Other digital goods", "其他数字商品",
          "Other digital products, such as templates, stock assets and digital collectibles. Excludes cryptocurrency.",
          "5818"),
     ]),
    ("SOFTWARE_SAAS", "Software & SaaS", None,
     "Software licences, subscriptions, apps and IT services. Excludes games (Digital goods).", [
         ("SAAS_SUBSCRIPTION", "SaaS subscriptions", "SaaS 订阅",
          "Recurring access to hosted business or consumer software. Excludes one-off app downloads.", "5734"),
         ("SOFTWARE_APPS", "Apps & software downloads", "应用与软件下载",
          "One-off purchases of apps and downloadable software. Excludes games.", "5817"),
         ("IT_SERVICES", "IT & development services", "IT 与开发服务",
          "Software development, systems integration, data processing and IT support services.", "7372"),
     ]),
    ("PROFESSIONAL_SERVICES", "Professional services", None,
     "Business services provided by professionals, such as consulting, marketing, accounting and legal. Excludes IT "
     "services (Software & SaaS) and education.", [
         ("PRO_CONSULTING", "Consulting", "咨询",
          "Management, business and other consulting services not covered by a more specific category.", "8999"),
         ("PRO_MARKETING", "Marketing & advertising", "营销与广告",
          "Advertising, digital marketing, design and promotion services.", "7311"),
         ("PRO_ACCOUNTING", "Accounting & bookkeeping", "会计与记账",
          "Accounting, auditing, bookkeeping and tax preparation services.", "8931"),
         ("PRO_LEGAL", "Legal services", "法律服务",
          "Law firms, attorneys and other legal services.", "8111"),
     ]),
    ("EDUCATION", "Education", None,
     "Schools, universities and online learning. Excludes educational books and media sold as products.", [
         ("EDU_ONLINE_COURSES", "Online courses", "在线课程",
          "Online courses, tutoring, training and certification programmes.", "8299"),
         ("EDU_SCHOOLS", "Schools", "中小学",
          "Primary and secondary schools.", "8211"),
         ("EDU_HIGHER", "Colleges & universities", "高等院校",
          "Colleges, universities and professional schools.", "8220"),
     ]),
    ("TRAVEL", "Travel", None,
     "Travel agencies, tours, lodging and transportation services. Excludes airlines and car rental companies that "
     "use their own brand MCCs.", [
         ("TRAVEL_AGENCY", "Travel agencies & tours", "旅行社与旅游团",
          "Travel agencies, tour operators and travel booking services.", "4722"),
         ("TRAVEL_LODGING", "Hotels & lodging", "酒店与住宿",
          "Hotels, resorts, serviced apartments and other lodging.", "7011"),
         ("TRAVEL_TRANSPORT", "Transportation services", "交通运输服务",
          "Ground transport, transfers and other transportation services. Excludes airlines.", "4789"),
     ]),
    ("FOOD_BEVERAGE", "Food & beverage", None,
     "Restaurants, fast food and grocery. Excludes stores that sell only alcohol or tobacco.", [
         ("FB_RESTAURANT", "Restaurants", "餐厅",
          "Sit-down restaurants, cafes and catering.", "5812"),
         ("FB_FAST_FOOD", "Fast food", "快餐",
          "Fast food and quick-service restaurants, including delivery-only kitchens.", "5814"),
         ("FB_GROCERY", "Grocery", "生鲜杂货",
          "Grocery stores, supermarkets and online grocery delivery.", "5411"),
     ]),
    ("MEDIA_ENTERTAINMENT", "Media & entertainment", None,
     "Streaming services, events, ticketing and publishing. Excludes gambling and betting.", [
         ("MEDIA_STREAMING", "Streaming & subscriptions", "流媒体与订阅",
          "Video, music and other media subscription services.", "4899"),
         ("MEDIA_EVENTS", "Events & tickets", "活动与票务",
          "Concerts, theatre, events and ticket sales.", "7922"),
         ("MEDIA_PUBLISHING", "Publishing", "出版",
          "Publishers of books, newspapers, magazines and other content.", "2741"),
     ]),
    ("NON_PROFIT", "Non-profit", None,
     "Charities, religious and membership organizations that accept donations or dues.", [
         ("NP_CHARITY", "Charities", "慈善机构",
          "Registered charities and social service organizations accepting donations.", "8398"),
         ("NP_RELIGIOUS", "Religious organizations", "宗教组织",
          "Religious organizations accepting donations.", "8661"),
         ("NP_MEMBERSHIP", "Membership organizations", "会员组织",
          "Clubs, associations and other membership organizations collecting dues.", "8699"),
     ]),
    ("OTHER", "Other", None,
     "Businesses not covered by another category. Operations confirm the final MCC during review.", [
         ("OTHER_SERVICES", "Other services", "其他服务",
          "Services not covered by another category.", "7299"),
         ("OTHER_RETAIL", "Other retail", "其他零售",
          "Specialty retail not covered by another category.", "5999"),
     ]),
]

# MCCs excluded from the generic list: brand-specific airline, car rental and lodging codes.
MCC_BRAND_RANGE = ("3000", "3999")

# Card-network high-risk MCCs (network_high_risk = 1). Sources and per-code notes in README.md.
HIGH_RISK_MCC = ["4816", "5122", "5912", "5962", "5966", "5967", "5993", "6051", "7273", "7800", "7801", "7802",
                 "7994", "7995"]

# Corrections of obvious typos / incomplete wording in the source list (applied to name and description).
MCC_TEXT_OVERRIDES = {
    "4789": "Transportation Services (Not Elsewhere Classified)",
    "4899": "Cable, Satellite, and Other Pay Television and Radio Services",
    "5399": "Miscellaneous General Merchandise",
    "5712": "Furniture, Home Furnishings, and Equipment Stores, Except Appliances",
    "5732": "Electronics Stores",
    "5812": "Eating Places and Restaurants",
    "7299": "Miscellaneous Personal Services (Not Elsewhere Classified)",
    "8220": "Colleges, Universities, Professional Schools, and Junior Colleges",
}

# zh-CN for MCCs used as an industry default MCC (the original S-09 scope). Since 2026-10-27 every MCC is translated in
# sources/mcc-zh-CN.json; these 32 must stay identical there (checked in load_data).
MCC_ZH = {
    "2741": "其他出版与印刷", "4722": "旅行社与旅游经营", "4789": "其他交通运输服务",
    "4899": "有线、卫星及其他付费电视与广播服务", "5399": "其他综合百货", "5411": "杂货店与超市",
    "5651": "家庭服装店", "5712": "家具与家居用品店（不含家电）", "5732": "电子产品商店", "5734": "计算机软件商店",
    "5812": "餐馆", "5814": "快餐店", "5815": "数字商品：媒体、图书、电影与音乐", "5816": "数字商品：游戏",
    "5817": "数字商品：应用程序（不含游戏）", "5818": "数字商品：大型数字商品商户", "5977": "化妆品店",
    "5999": "其他专门零售店", "7011": "酒店、汽车旅馆与度假村", "7299": "其他个人服务", "7311": "广告服务",
    "7372": "计算机编程、系统集成与数据处理服务", "7922": "演出制作与票务代理", "8111": "法律服务与律师",
    "8211": "中小学", "8220": "大学、学院与专业学校", "8299": "其他学校与教育服务", "8398": "慈善与社会服务组织",
    "8661": "宗教组织", "8699": "其他会员组织", "8931": "会计、审计与簿记服务", "8999": "其他专业服务",
}


# ---------------------------------------------------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------------------------------------------------

def fail(message):
    sys.stderr.write("ASSERTION FAILED: " + message + "\n")
    sys.exit(1)


def check(condition, message):
    if not condition:
        fail(message)


def load_cldr_subdivision_names(name):
    """CLDR common/subdivisions/<locale>.xml → {unicode subdivision id (e.g. aedu): name}; alt variants ignored."""
    root = ET.parse(os.path.join(SOURCES, name)).getroot()
    return {node.get("type"): node.text.strip() for node in root.iter("subdivision") if not node.get("alt") and node.text}


def load_json(name):
    with open(os.path.join(SOURCES, name), encoding="utf-8") as handle:
        return json.load(handle)


def sql_literal(value):
    if value is None:
        return "NULL"
    if isinstance(value, int):
        return str(value)
    text = str(value)
    check("\\" not in text, "backslash in SQL literal: " + text)
    check("\x00" not in text, "NUL in SQL literal")
    return "'" + text.replace("'", "''") + "'"


def sql_row(values):
    return "  (" + ", ".join(v if isinstance(v, RawSql) else sql_literal(v) for v in values) + ")"


class RawSql(str):
    """SQL expression emitted verbatim (variables, functions)."""


def insert_sql(table, columns, rows):
    head = "INSERT INTO {} ({}) VALUES\n".format(table, ", ".join(columns))
    return head + ",\n".join(sql_row(r) for r in rows) + ";\n"


def clean_text(text):
    text = re.sub(r"\s+", " ", text).strip()
    text = text.replace("( ", "(").replace(" )", ")")
    return text


def truncate_words(text, limit):
    if len(text) <= limit:
        return text
    cut = text[:limit - 1]
    space = cut.rfind(" ")
    if space > limit // 2:
        cut = cut[:space]
    return cut.rstrip(" ,;:-") + "…"


def icu_escape(text):
    """ICU MessageFormat literal: braces quoted, an apostrophe that would start quoting is doubled.

    Mirrors the existing bundles, which keep plain apostrophes (You're, Druggist's) as-is because ICU treats an
    apostrophe as literal unless it precedes a syntax character or another apostrophe."""
    out = []
    for i, ch in enumerate(text):
        nxt = text[i + 1] if i + 1 < len(text) else ""
        if ch == "'" and nxt in ("{", "}", "'", "#", "|"):
            out.append("''")
        elif ch in "{}":
            out.append("'" + ch + "'")
        else:
            out.append(ch)
    return "".join(out)


def write_text(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(content)


def write_json(path, data, indent=2):
    write_text(path, json.dumps(data, ensure_ascii=False, indent=indent) + "\n")


def sha256_file(path):
    with open(path, "rb") as handle:
        return hashlib.sha256(handle.read()).hexdigest()


# ---------------------------------------------------------------------------------------------------------------------
# Loaders
# ---------------------------------------------------------------------------------------------------------------------

def load_existing_countries():
    pattern = re.compile(r"^\s*\((\d+), '([A-Z]{2})', '([A-Z_]+)', @country_region_seed_time, @country_region_seed_time\)")
    rows = []
    with open(os.path.join(SOURCES, "existing-country-region-seed.sql"), encoding="utf-8") as handle:
        for line in handle:
            match = pattern.match(line)
            if match:
                rows.append((int(match.group(1)), match.group(2), match.group(3)))
    check(len(rows) == 249, "existing country seed rows: %d" % len(rows))
    check([r[0] for r in rows] == list(range(2001, 2250)), "existing country ids are not 2001..2249")
    check(len({r[1] for r in rows}) == 249, "duplicate country codes in existing seed")
    return rows


def load_calling_codes():
    tree = ET.parse(os.path.join(SOURCES, "libphonenumber.PhoneNumberMetadata.xml"))
    codes = {}
    for territory in tree.getroot().iter("territory"):
        region = territory.get("id")
        code = territory.get("countryCode")
        if region and re.fullmatch(r"[A-Z]{2}", region) and code:
            check(code.isdigit() and len(code) <= 8, "bad calling code %s %s" % (region, code))
            codes[region] = code
    return codes


def active_currency_by_region(region_data):
    """region -> first currency with no _to and _tender != false (CLDR order); and the set of all such currencies."""
    first = {}
    active = set()
    for region, entries in region_data.items():
        for entry in entries:
            (code, attrs), = entry.items()
            if "_to" in attrs or attrs.get("_tender") == "false":
                continue
            active.add(code)
            first.setdefault(region, code)
    return first, active


def load_currency_iso(active_codes, code_mappings, en_currencies):
    rows = {}
    for code in sorted(active_codes):
        numeric = code_mappings.get(code, {}).get("_numeric")
        if not numeric:
            continue
        numeric = numeric.zfill(3)
        info = en_currencies.get(code, {})
        name = info.get("displayName") or code
        symbol = info.get("symbol") or code
        check(re.fullmatch(r"[0-9]{3}", numeric) is not None, "currency numeric " + code)
        check(len(name) <= 128, "currency name too long " + code)
        check(len(symbol) <= 8, "currency symbol too long %s %r" % (code, symbol))
        rows[code] = {"numeric_code": numeric, "name": name, "symbol": symbol}
    return rows


def load_mcc():
    rows = []
    with open(os.path.join(SOURCES, "mcc_codes.csv"), encoding="utf-8", newline="") as handle:
        for record in csv.DictReader(handle):
            code = record["mcc"].strip()
            check(re.fullmatch(r"[0-9]{4}", code) is not None, "MCC not 4 digits: %r" % code)
            if MCC_BRAND_RANGE[0] <= code <= MCC_BRAND_RANGE[1]:
                continue
            short = clean_text(record["edited_description"])
            full = clean_text(record["combined_description"]) or short
            if code in MCC_TEXT_OVERRIDES:
                short = full = MCC_TEXT_OVERRIDES[code]
            check(short != "", "empty MCC name " + code)
            rows.append({
                "mcc_code": code,
                "name": truncate_words(short, 128),
                "description": truncate_words(full, 512),
                "network_high_risk": 1 if code in HIGH_RISK_MCC else 0,
            })
    rows.sort(key=lambda r: r["mcc_code"])
    codes = [r["mcc_code"] for r in rows]
    check(len(codes) == len(set(codes)), "duplicate MCC codes")
    for code in HIGH_RISK_MCC:
        check(code in set(codes), "high-risk MCC missing from list: " + code)
    for index, row in enumerate(rows):
        row["id"] = 5001 + index
    return rows


def verify_subdivisions_against_wikipedia():
    for country, (_, rows) in SUBDIVISIONS.items():
        with open(os.path.join(SOURCES, "wikipedia.ISO_3166-2_%s.wikitext" % country), encoding="utf-8") as handle:
            text = handle.read()
        current = text.split("==Current codes==", 1)[1].split("|}", 1)[0]
        wiki_codes = sorted(set(re.findall(r"\{\{mono\|(%s-[A-Z0-9]+)\}\}" % country, current)))
        check(wiki_codes == sorted(r[0] for r in rows), "subdivision codes differ from Wikipedia for " + country)
        for code, name in rows:
            check(name in current, "subdivision Latin name not found in Wikipedia table: %s %s" % (code, name))


# ---------------------------------------------------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------------------------------------------------

def build():
    code_mappings = load_json("cldr-core.codeMappings.json")["supplemental"]["codeMappings"]
    currency_data = load_json("cldr-core.currencyData.json")["supplemental"]["currencyData"]
    territories = load_json("cldr-localenames-full.en.territories.json")["main"]["en"]["localeDisplayNames"]["territories"]
    en_currencies = load_json("cldr-numbers-full.en.currencies.json")["main"]["en"]["numbers"]["currencies"]
    existing_zh = load_json("existing-zh.onboarding-schema-catalog.json")["entries"]

    region_currency, active_codes = active_currency_by_region(currency_data["region"])
    currency_iso = load_currency_iso(active_codes, code_mappings, en_currencies)
    for code, _ in CURRENCY_ROWS:
        check(code in currency_iso, "kept currency missing from ISO list: " + code)

    # Countries
    calling = load_calling_codes()
    countries = []
    for country_id, code, region_code in load_existing_countries():
        mapping = code_mappings.get(code, {})
        alpha3 = mapping.get("_alpha3")
        numeric = mapping.get("_numeric")
        name = territories.get(code)
        check(alpha3 is not None and re.fullmatch(r"[A-Z]{3}", alpha3) is not None, "alpha3 missing " + code)
        check(numeric is not None, "numeric missing " + code)
        numeric = numeric.zfill(3)
        check(re.fullmatch(r"[0-9]{3}", numeric) is not None, "numeric bad " + code)
        check(name is not None and 0 < len(name) <= 128, "name missing/too long " + code)
        currency = region_currency.get(code)
        check(currency is None or re.fullmatch(r"[A-Z]{3}", currency) is not None, "currency bad " + code)
        countries.append({
            "id": country_id, "country_code": code, "alpha3_code": alpha3, "numeric_code": numeric, "name": name,
            "region_code": region_code, "calling_code": calling.get(code), "default_currency_code": currency,
        })
    check(len(countries) == 249, "country count")
    check(len({c["alpha3_code"] for c in countries}) == 249, "duplicate alpha3")
    check(len({c["numeric_code"] for c in countries}) == 249, "duplicate numeric")

    # Subdivisions
    verify_subdivisions_against_wikipedia()
    cldr_ar = load_cldr_subdivision_names("cldr.common.subdivisions.ar.xml")
    subdivisions = []
    next_id = 3001
    for country in SUBDIVISION_COUNTRY_ORDER:
        category, rows = SUBDIVISIONS[country]
        check(len(rows) == EXPECTED_SUBDIVISION_COUNTS[country], "subdivision count " + country)
        for code, name in sorted(rows, key=lambda r: r[0]):
            check(code.startswith(country + "-") and len(code) <= 6, "subdivision code " + code)
            local_name = cldr_ar.get(code.lower().replace("-", ""))
            check(local_name, "CLDR ar subdivision name missing for " + code)
            check(len(name) <= 128 and len(local_name) <= 128, "subdivision name length " + code)
            subdivisions.append({"id": next_id, "subdivision_code": code, "country_code": country,
                                 "category": category, "name": name, "local_name": local_name})
            next_id += 1

    # MCC
    mccs = load_mcc()
    mcc_by_code = {m["mcc_code"]: m for m in mccs}
    for m in mccs:
        check(len(m["name"]) <= 128 and len(m["description"]) <= 512, "MCC text length " + m["mcc_code"])

    # Industries
    industries = []
    next_id = 4001
    check(len(INDUSTRIES) == 10, "top-level industry count")
    for top_index, (code, name, _, description, subs) in enumerate(INDUSTRIES):
        industries.append({"id": next_id, "industry_code": code, "parent_industry_code": None, "industry_level": 1,
                           "name": name, "description": description, "default_mcc_code": None,
                           "sort_no": (top_index + 1) * 10})
        next_id += 1
        for sub_index, (sub_code, sub_name, _, sub_description, mcc) in enumerate(subs):
            check(mcc in mcc_by_code, "default MCC %s of %s missing from MCC list" % (mcc, sub_code))
            industries.append({"id": next_id, "industry_code": sub_code, "parent_industry_code": code,
                               "industry_level": 2, "name": sub_name, "description": sub_description,
                               "default_mcc_code": mcc, "sort_no": (sub_index + 1) * 10})
            next_id += 1
    check(sum(1 for i in industries if i["industry_level"] == 2) == 32, "sub-industry count")
    check(len({i["industry_code"] for i in industries}) == 42, "duplicate industry code")
    for industry in industries:
        check(len(industry["name"]) <= 128 and len(industry["description"]) <= 512, "industry text length")
    check(set(MCC_ZH) == {i["default_mcc_code"] for i in industries if i["default_mcc_code"]},
          "MCC_ZH must cover exactly the default MCCs")

    # zh translations
    industry_zh = {}
    for code, _, _, _, subs in INDUSTRIES:
        key = "onboarding-schema.catalog.INDUSTRY." + code
        check(key in existing_zh, "existing zh missing for top-level industry " + code)
        industry_zh[code] = existing_zh[key]
        for sub in subs:
            industry_zh[sub[0]] = sub[2]
    mcc_zh = load_json("mcc-zh-CN.json")["entries"]
    check(set(mcc_zh) == {m["mcc_code"] for m in mccs}, "mcc-zh-CN.json must cover exactly the generated MCC list")
    check(all(mcc_zh[code] == text for code, text in MCC_ZH.items()), "mcc-zh-CN.json changed a default-MCC translation")
    check(all(text.strip() and len(text) <= 128 for text in mcc_zh.values()), "empty or over-long MCC zh name")
    subdivision_zh = {}
    for row in subdivisions:
        key = "onboarding-schema.catalog.STATE_OR_PROVINCE." + row["subdivision_code"].replace("-", "_")
        subdivision_zh[row["subdivision_code"]] = existing_zh.get(key) or SUBDIVISION_ZH_EXTRA.get(row["subdivision_code"])
        check(subdivision_zh[row["subdivision_code"]], "zh missing for subdivision " + row["subdivision_code"])

    return {
        "currency_iso": currency_iso, "countries": countries, "subdivisions": subdivisions, "industries": industries,
        "mccs": mccs, "industry_zh": industry_zh, "subdivision_zh": subdivision_zh, "mcc_zh": mcc_zh,
    }


# ---------------------------------------------------------------------------------------------------------------------
# SQL output
# ---------------------------------------------------------------------------------------------------------------------

CURRENCY_COLUMNS = ["id", "currency_code", "numeric_code", "name", "symbol", "minor_unit", "rounding_mode",
                    "logo_file_address", "status", "version", "create_time", "update_time"]
COUNTRY_COLUMNS = ["id", "country_code", "alpha3_code", "numeric_code", "name", "region_code", "calling_code",
                   "default_currency_code", "logo_file_address", "status", "version", "create_time", "update_time"]
SUBDIVISION_COLUMNS = ["id", "subdivision_code", "country_code", "parent_subdivision_code", "category", "name",
                       "local_name", "status", "version", "create_time", "update_time"]
INDUSTRY_COLUMNS = ["id", "industry_code", "parent_industry_code", "industry_level", "name", "description",
                    "default_mcc_code", "logo_file_address", "sort_no", "status", "version", "create_time",
                    "update_time"]
MCC_COLUMNS = ["id", "mcc_code", "name", "description", "network_high_risk", "status", "version", "create_time",
               "update_time"]

HEADER = """-- GENERATED FILE - do not edit by hand. Regenerate with:
--   /usr/bin/python3 -I db/platform-support/generator/generate_reference_data.py
-- Sources (see generator/README.md): Unicode CLDR {cldr} (cldr-core, cldr-localenames-full, cldr-numbers-full;
-- Unicode License v3), libphonenumber {lpn} PhoneNumberMetadata.xml (Apache-2.0), greggles/mcc-codes @{mcc}
-- (Unlicense; brand codes 3000-3999 excluded), ISO 3166-2 AE/SA/EG names verified against Wikipedia, industry draft
-- technique.md section 7. Review only; never applied automatically.
""".format(cldr=CLDR_VERSION, lpn=LIBPHONENUMBER_VERSION, mcc=MCC_COMMIT)


def currency_row(code, currency_id, info, time_expr):
    return [currency_id, code, info["numeric_code"], info["name"], info["symbol"], CURRENCY_MINOR_UNIT[code],
            "HALF_UP", None, ACTIVE, 1, RawSql(time_expr), RawSql(time_expr)]


def subdivision_rows(data, time_expr):
    return [[s["id"], s["subdivision_code"], s["country_code"], None, s["category"], s["name"], s["local_name"],
             ACTIVE, 1, RawSql(time_expr), RawSql(time_expr)] for s in data["subdivisions"]]


def industry_rows(data, time_expr):
    return [[i["id"], i["industry_code"], i["parent_industry_code"], i["industry_level"], i["name"], i["description"],
             i["default_mcc_code"], None, i["sort_no"], ACTIVE, 1, RawSql(time_expr), RawSql(time_expr)]
            for i in data["industries"]]


def mcc_rows(data, time_expr):
    return [[m["id"], m["mcc_code"], m["name"], m["description"], m["network_high_risk"], ACTIVE, 1,
             RawSql(time_expr), RawSql(time_expr)] for m in data["mccs"]]


def reference_inserts(data, time_expr):
    parts = [
        "-- Subdivisions (ISO 3166-2): AE 7 emirates, SA 13 regions, EG 27 governorates; ids 3001+.\n",
        insert_sql("t_ref_country_subdivision", SUBDIVISION_COLUMNS, subdivision_rows(data, time_expr)),
        "\n-- Industries (technique.md section 7): 10 top-level + 32 sub-industries; ids 4001+.\n",
        insert_sql("t_ref_industry", INDUSTRY_COLUMNS, industry_rows(data, time_expr)),
        "\n-- MCC (ISO 18245 generic codes, brand codes 3000-3999 excluded); ids 5001+ ascending by code.\n",
        insert_sql("t_ref_mcc", MCC_COLUMNS, mcc_rows(data, time_expr)),
    ]
    return "".join(parts)


def change_backfill_sql(data):
    iso = data["currency_iso"]
    out = [HEADER, """--
-- change-backfill.sql: data part of changes/2026-10-27-reference-data.sql for EXISTING environments.
-- Preconditions (owned by the caller): the DDL rename/add-column step has run (new currency/country columns still
-- NULLable), the transaction is open, @ref_data_seed_time = UTC_TIMESTAMP(6) is set, and the precheck confirmed that
-- t_ref_country_subdivision, t_ref_industry and t_ref_mcc are empty. This file emits no START TRANSACTION / COMMIT.

-- a) Currency backfill: every currently active ISO 4217 currency CLDR has a numeric code for, so rows created through
--    the operations console with any real ISO code are backfilled too.
CREATE TEMPORARY TABLE tmp_ref_currency_iso (
  currency_code CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  numeric_code CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  name VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  symbol VARCHAR(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
"""]
    out.append(insert_sql("tmp_ref_currency_iso", ["currency_code", "numeric_code", "name", "symbol"],
                          [[c, v["numeric_code"], v["name"], v["symbol"]] for c, v in sorted(iso.items())]))
    out.append("""UPDATE t_ref_currency c JOIN tmp_ref_currency_iso i ON i.currency_code = c.currency_code
SET c.numeric_code = i.numeric_code, c.name = i.name, c.symbol = i.symbol
WHERE c.numeric_code IS NULL;

-- b) AED / SAR / EGP: insert when absent (fixed ids 1002-1004), then activate any existing non-ACTIVE row.
""")
    for code, currency_id in CURRENCY_ROWS[1:]:
        values = currency_row(code, currency_id, iso[code], SEED_TIME)
        out.append("INSERT INTO t_ref_currency ({})\nSELECT {} FROM DUAL\nWHERE NOT EXISTS (SELECT 1 FROM t_ref_currency "
                   "WHERE currency_code = {});\n".format(
                       ", ".join(CURRENCY_COLUMNS),
                       ", ".join(v if isinstance(v, RawSql) else sql_literal(v) for v in values), sql_literal(code)))
    out.append("UPDATE t_ref_currency SET status = 'ACTIVE', version = version + 1, update_time = @ref_data_seed_time\n"
               "WHERE currency_code IN ('AED', 'SAR', 'EGP') AND status <> 'ACTIVE';\n")
    out.append("""
-- c) Country backfill: 249 rows (ISO 3166-1 alpha-3 / numeric from CLDR codeMappings, CLDR English short name,
--    libphonenumber calling code, CLDR current legal tender). default_currency_code is an ISO code and need not
--    exist in t_ref_currency.
CREATE TEMPORARY TABLE tmp_ref_country_iso (
  country_code CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  alpha3_code CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  numeric_code CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  name VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  calling_code VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NULL,
  default_currency_code CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NULL
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
""")
    out.append(insert_sql("tmp_ref_country_iso",
                          ["country_code", "alpha3_code", "numeric_code", "name", "calling_code",
                           "default_currency_code"],
                          [[c["country_code"], c["alpha3_code"], c["numeric_code"], c["name"], c["calling_code"],
                            c["default_currency_code"]] for c in data["countries"]]))
    out.append("""UPDATE t_ref_country c JOIN tmp_ref_country_iso i ON i.country_code = c.country_code
SET c.alpha3_code = i.alpha3_code, c.numeric_code = i.numeric_code, c.name = i.name,
    c.calling_code = i.calling_code, c.default_currency_code = i.default_currency_code
WHERE c.alpha3_code IS NULL;

-- d) New reference tables.
""")
    out.append(reference_inserts(data, SEED_TIME))
    out.append("""
-- e) Clean up.
DROP TEMPORARY TABLE tmp_ref_currency_iso;
DROP TEMPORARY TABLE tmp_ref_country_iso;
""")
    return "".join(out)


def initial_data_blocks_sql(data):
    iso = data["currency_iso"]
    out = [HEADER, """--
-- initial-data-blocks.sql: reference data for FRESH environments created by the final schema
-- (platform-support-schema.sql). Paste each block into platform-support-initial-data.sql at the noted place.

-- =====================================================================================================================
-- BLOCK "CURRENCY" - replaces the existing USD INSERT INTO t_currency_spec (D41) in the initial-data file.
-- =====================================================================================================================
-- 全平台参考数据（D41）：与 finance-initial-data.sql 相同的 USD 币种规则；固定 ID 1001 低于 Snowflake 范围。
"""]
    usd = currency_row("USD", 1001, iso["USD"], "UTC_TIMESTAMP(6)")
    out.append("INSERT INTO `t_ref_currency` ({})\nVALUES ({});\n".format(
        ", ".join("`%s`" % c for c in CURRENCY_COLUMNS),
        ", ".join(v if isinstance(v, RawSql) else sql_literal(v) for v in usd)))
    out.append("""
-- =====================================================================================================================
-- BLOCK "COUNTRY" - replaces the rows of the existing country seed block (SET @country_region_seed_time ... ;
-- INSERT INTO t_country_region ...). Ids 2001-2249 and region codes are unchanged.
-- =====================================================================================================================
SET @country_region_seed_time = UTC_TIMESTAMP(6);
""")
    out.append(insert_sql("t_ref_country", COUNTRY_COLUMNS,
                          [[c["id"], c["country_code"], c["alpha3_code"], c["numeric_code"], c["name"],
                            c["region_code"], c["calling_code"], c["default_currency_code"], None, ACTIVE, 1,
                            RawSql(COUNTRY_SEED_TIME), RawSql(COUNTRY_SEED_TIME)] for c in data["countries"]]))
    out.append("""
-- =====================================================================================================================
-- BLOCK "REFERENCE DATA" - new block after the country seed: AED / SAR / EGP, subdivisions, industries, MCC.
-- Rollback: DELETE FROM t_ref_mcc WHERE id BETWEEN 5001 AND 5999; DELETE FROM t_ref_industry WHERE id BETWEEN 4001
-- AND 4999; DELETE FROM t_ref_country_subdivision WHERE id BETWEEN 3001 AND 3999; DELETE FROM t_ref_currency WHERE
-- id IN (1002, 1003, 1004);
-- =====================================================================================================================
SET @ref_data_seed_time = UTC_TIMESTAMP(6);
-- Currencies AED / SAR / EGP (fixed ids 1002-1004, ACTIVE).
""")
    out.append(insert_sql("t_ref_currency", CURRENCY_COLUMNS,
                          [currency_row(code, cid, iso[code], SEED_TIME) for code, cid in CURRENCY_ROWS[1:]]))
    out.append("\n")
    out.append(reference_inserts(data, SEED_TIME))
    return "".join(out)


# ---------------------------------------------------------------------------------------------------------------------
# JSON / i18n output
# ---------------------------------------------------------------------------------------------------------------------

def summary_json(data):
    kept = {code for code, _ in CURRENCY_ROWS}
    countries = data["countries"]
    per_country = {}
    for s in data["subdivisions"]:
        per_country[s["country_code"]] = per_country.get(s["country_code"], 0) + 1
    other_currencies = sorted({c["default_currency_code"] for c in countries
                               if c["default_currency_code"] and c["default_currency_code"] not in kept})
    return {
        "sources": {"cldr": CLDR_VERSION, "cldrSubdivisionsXml": CLDR_XML_TAG, "libphonenumber": LIBPHONENUMBER_VERSION, "mccCodesCommit": MCC_COMMIT},
        "currencyIsoTempRows": len(data["currency_iso"]),
        "currencyRows": [code for code, _ in CURRENCY_ROWS],
        "countries": len(countries),
        "countriesWithCallingCode": sum(1 for c in countries if c["calling_code"]),
        "countriesWithoutCallingCode": [c["country_code"] for c in countries if not c["calling_code"]],
        "countriesWithDefaultCurrency": sum(1 for c in countries if c["default_currency_code"]),
        "countriesWithoutDefaultCurrency": [c["country_code"] for c in countries if not c["default_currency_code"]],
        "subdivisionsPerCountry": {k: per_country[k] for k in SUBDIVISION_COUNTRY_ORDER},
        "industriesPerLevel": {"1": sum(1 for i in data["industries"] if i["industry_level"] == 1),
                               "2": sum(1 for i in data["industries"] if i["industry_level"] == 2)},
        "mccs": len(data["mccs"]),
        "highRiskMcc": [m["mcc_code"] for m in data["mccs"] if m["network_high_risk"]],
        "countryDefaultCurrenciesNotInCurrencyTable": len(other_currencies),
        "countryDefaultCurrenciesNotInCurrencyTableCodes": other_currencies,
    }


def expected_json(data):
    country = {c["country_code"]: c for c in data["countries"]}
    iso = data["currency_iso"]
    return {
        "subIndustryDefaultMcc": {i["industry_code"]: i["default_mcc_code"] for i in data["industries"]
                                  if i["industry_level"] == 2},
        "highRiskMcc": [m["mcc_code"] for m in data["mccs"] if m["network_high_risk"]],
        "countries": {code: {k: country[code][k] for k in ("id", "alpha3_code", "numeric_code", "name", "calling_code",
                                                            "default_currency_code", "region_code")}
                      for code in ("AE", "SA", "EG", "US")},
        "currencies": {code: dict(iso[code], id=cid, minor_unit=CURRENCY_MINOR_UNIT[code])
                       for code, cid in CURRENCY_ROWS},
        "subdivisionCodes": {k: [s["subdivision_code"] for s in data["subdivisions"] if s["country_code"] == k]
                             for k in SUBDIVISION_COUNTRY_ORDER},
    }


def i18n_outputs(data):
    # en of refdata.subdivision.* uses common English names (sources/subdivision-en.json); DB name stays ISO Latin.
    subdivision_en = load_json("subdivision-en.json")["entries"]
    check(set(subdivision_en) == {s["subdivision_code"] for s in data["subdivisions"]},
          "subdivision-en.json must cover exactly the subdivision list")
    # refdata.industry.<CODE>.description: en = DB description, zh-CN from sources/industry-description-zh-CN.json.
    description_zh = load_json("industry-description-zh-CN.json")["entries"]
    check(set(description_zh) == {i["industry_code"] for i in data["industries"]},
          "industry-description-zh-CN.json must cover exactly the industry list")
    check(all(i["description"] for i in data["industries"]), "every industry needs an English description")
    en = {}
    for i in data["industries"]:
        en["refdata.industry." + i["industry_code"]] = icu_escape(i["name"])
        en["refdata.industry." + i["industry_code"] + ".description"] = icu_escape(i["description"])
    for s in data["subdivisions"]:
        en["refdata.subdivision." + s["subdivision_code"]] = icu_escape(subdivision_en[s["subdivision_code"]])
    for m in data["mccs"]:
        en["refdata.mcc." + m["mcc_code"]] = icu_escape(m["name"])
    zh = {}
    for code, text in data["industry_zh"].items():
        zh["refdata.industry." + code] = icu_escape(text)
    for code, text in description_zh.items():
        check(text.strip(), "empty industry description zh " + code)
        zh["refdata.industry." + code + ".description"] = icu_escape(text)
    for code, text in data["subdivision_zh"].items():
        zh["refdata.subdivision." + code] = icu_escape(text)
    for code, text in data["mcc_zh"].items():
        zh["refdata.mcc." + code] = icu_escape(text)
    check(set(zh) == set(en), "zh-CN must cover every refdata key")
    en = dict(sorted(en.items()))
    zh = dict(sorted(zh.items()))
    files = []
    for app in I18N_APPS:
        en_name = "%s.refdata.en.json" % app
        zh_name = "%s.refdata.zh-CN.json" % app
        write_json(os.path.join(I18N_OUT, en_name),
                   {"format": "code-mor-i18n-source/1", "application": app, "namespaces": {I18N_NAMESPACE: en}})
        write_json(os.path.join(I18N_OUT, zh_name),
                   {"format": "code-mor-i18n-translation/1", "application": app, "locale": "zh-CN",
                    "namespaces": {I18N_NAMESPACE: zh}})
        plan = {
            "app": app,
            "currentRelease": None,
            "liveNamespaces": None,
            "repoNamespaces": None,
            "newNamespaces": [I18N_NAMESPACE],
            "repoKeys": None,
            "liveActiveKeys": None,
            "added": len(en),
            "restored": 0,
            "sourceChanged": 0,
            "missingInRepo": None,
            "zhImportKeys": len(zh),
            "zhMissingForImport": 0,
            "keepLiveZh": None,
            "deprecations": [],
            "note": "refdata namespace only; null fields depend on the live release and are filled by the stage-6 "
                    "release plan. onboarding-schema catalog.INDUSTRY/CURRENCY/STATE_OR_PROVINCE deprecations are "
                    "planned with the frontend change, not here.",
            "sha256": {en_name: sha256_file(os.path.join(I18N_OUT, en_name)),
                       zh_name: sha256_file(os.path.join(I18N_OUT, zh_name))},
        }
        write_json(os.path.join(I18N_OUT, "%s.refdata.plan.json" % app), plan, indent=1)
        files += [en_name, zh_name, "%s.refdata.plan.json" % app]
    return len(en), len(zh), files


def main():
    data = build()
    write_text(os.path.join(GENERATED, "change-backfill.sql"), change_backfill_sql(data))
    write_text(os.path.join(GENERATED, "initial-data-blocks.sql"), initial_data_blocks_sql(data))
    summary = summary_json(data)
    en_keys, zh_keys, _ = i18n_outputs(data)
    summary["i18nKeysPerApp"] = {"en": en_keys, "zh-CN": zh_keys}
    write_json(os.path.join(GENERATED, "summary.json"), summary)
    write_json(os.path.join(GENERATED, "expected.json"), expected_json(data))
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
