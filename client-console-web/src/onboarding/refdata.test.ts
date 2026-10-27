import { test } from "node:test";
import assert from "node:assert/strict";
import { readSchema } from "./model.ts";
import { addressPathsToClear } from "./schema.ts";
import {
  REF_COUNTRY_SUBDIVISION, REF_CURRENCY, countryName, countryOptions, currencyName, currencyOptions, filterOptions, safeLogo,
  subdivisionKey, subdivisionName, subdivisionOptions,
} from "./refdata.ts";

const countries = [
  { code: "AE", name: "United Arab Emirates", logoPublicUrl: "https://cdn.example.test/ae.svg", callingCode: "971" },
  { code: "SA", name: "Saudi Arabia", logoPublicUrl: null, callingCode: "966" },
  { code: "OM", name: "Oman", logoPublicUrl: null, callingCode: "968" },
];
const subdivisions = [
  { itemCode: "AE-DU", labelKey: "", name: "Dubai", countryCode: "AE" },
  { itemCode: "AE-AZ", labelKey: "", name: "Abu Dhabi", countryCode: "AE" },
  { itemCode: "EG-C", labelKey: "", name: "Cairo", countryCode: "EG" },
];

test("country options: names follow the interface language, sorted by that name; flags come only from Platform", () => {
  const en = countryOptions(countries, "en");
  assert.deepEqual(en.map((option) => option.code), ["OM", "SA", "AE"]);
  assert.equal(en.find((option) => option.code === "AE")?.logo, "https://cdn.example.test/ae.svg");
  const zh = countryOptions(countries, "zh-CN");
  assert.equal(zh.find((option) => option.code === "AE")?.label, new Intl.DisplayNames(["zh-CN"], { type: "region" }).of("AE"));
  assert.notEqual(zh.find((option) => option.code === "AE")?.label, "United Arab Emirates");
});

test("names fall back to the backend English name, then the code (B7)", () => {
  assert.equal(countryName("XX", "en", "Nowhere"), "Nowhere");
  assert.equal(countryName("XX", "en", null), "XX");
  assert.equal(countryName(null, "en"), "");
  assert.equal(currencyName("XXQ", "en", "Test Coin"), "Test Coin");
  assert.equal(currencyName("XXQ", "en"), "XXQ");
  const lookup = (key: string) => (key === subdivisionKey("AE-DU") ? "迪拜" : null);
  assert.equal(subdivisionName(subdivisions[0], lookup), "迪拜");
  assert.equal(subdivisionName(subdivisions[1], lookup), "Abu Dhabi");
  assert.equal(subdivisionName({ itemCode: "AE-SH", name: null }, lookup), "AE-SH");
});

test("search matches the localized name, the English name or the code; code-prefix matches come first", () => {
  const zh = countryOptions(countries, "zh-CN");
  const arabic = new Intl.DisplayNames(["zh-CN"], { type: "region" }).of("AE")!.slice(0, 2);
  assert.ok(filterOptions(zh, arabic).some((option) => option.code === "AE"));
  assert.deepEqual(filterOptions(zh, "saudi").map((option) => option.code), ["SA"]);
  assert.deepEqual(filterOptions(zh, "om")[0].code, "OM");
  assert.deepEqual(filterOptions(zh, "  ").length, 3);
  assert.deepEqual(filterOptions(zh, "zzz"), []);
});

test("currency options list the localized name, show '<localized name> (CODE)' when chosen, and keep the backend order", () => {
  const items = [{ itemCode: "AED", labelKey: "", name: "UAE Dirham", logoPublicUrl: "https://cdn.example.test/aed.svg" },
    { itemCode: "USD", labelKey: "", name: "US Dollar" }];
  const en = currencyOptions(items, "en");
  assert.deepEqual(en.map((option) => option.code), ["AED", "USD"]);
  assert.equal(en[1].label, new Intl.DisplayNames(["en"], { type: "currency" }).of("USD"));
  assert.equal(en[1].display, new Intl.DisplayNames(["en"], { type: "currency" }).of("USD") + " (USD)");
  assert.equal(en[0].logo, "https://cdn.example.test/aed.svg");
  assert.equal(currencyOptions(items, "zh-CN")[1].display, new Intl.DisplayNames(["zh-CN"], { type: "currency" }).of("USD") + " (USD)");
});

test("subdivision options are scoped to the address country; a country without subdivisions has none", () => {
  assert.deepEqual(subdivisionOptions(subdivisions, "AE", "en", () => null).map((option) => option.code), ["AE-AZ", "AE-DU"]);
  assert.deepEqual(subdivisionOptions(subdivisions, "GB", "en", () => null), []);
  assert.deepEqual(subdivisionOptions(subdivisions, null, "en", () => null), []);
});

test("logo addresses: only http(s) or same-site absolute paths are rendered", () => {
  assert.equal(safeLogo("https://cdn.example.test/a.svg"), "https://cdn.example.test/a.svg");
  assert.equal(safeLogo("/files/public/a.svg"), "/files/public/a.svg");
  for (const value of ["javascript:alert(1)", "data:image/svg+xml,<svg/>", "//evil.test/a.svg", "", null, 3]) assert.equal(safeLogo(value), null);
});

test("BFF page model decodes: reference catalog items keep name, logo and country; malformed countries are dropped", () => {
  const schema = readSchema({
    schemaId: "1", versionNo: 1, sections: [],
    catalogs: {
      [REF_CURRENCY]: [{ code: "AED", labelKey: null, name: "UAE Dirham", logoPublicUrl: "javascript:x", countryCode: null }],
      [REF_COUNTRY_SUBDIVISION]: [{ code: "AE-DU", labelKey: null, name: "Dubai", logoPublicUrl: null, countryCode: "AE" }],
    },
    countries: [...countries, { code: "ae", name: "bad" }, { code: null }, null],
  }, "x");
  assert.deepEqual(schema.catalogs[REF_CURRENCY], [{ itemCode: "AED", labelKey: "", name: "UAE Dirham", logoPublicUrl: null, countryCode: null }]);
  assert.equal(schema.catalogs[REF_COUNTRY_SUBDIVISION][0].countryCode, "AE");
  assert.deepEqual(schema.countries.map((country) => country.code), ["AE", "SA", "OM"]);
});

test("changing the address country clears the chosen subdivision, also when the layout stays the same", () => {
  const format = { code: "LOCAL", components: [
    { component: "stateOrProvince", required: true, labelKey: "k", order: 10, catalogCode: REF_COUNTRY_SUBDIVISION },
    { component: "city", required: true, labelKey: "k", order: 20 },
    { component: "countryCode", required: true, labelKey: "k", order: 30 },
  ] };
  const schema = readSchema({ schemaId: "1", versionNo: 1, sections: [], catalogs: {}, addressFormat: format, fallbackAddressFormat: format }, "x");
  const before = { "address.countryCode": "AE", "address.stateOrProvince": "AE-DU", "address.city": "Dubai" };
  assert.deepEqual(addressPathsToClear(schema, before, { ...before, "address.countryCode": "EG" }), ["address.stateOrProvince"]);
  assert.deepEqual(addressPathsToClear(schema, before, { ...before, "address.city": "Deira" }), []);
  assert.deepEqual(addressPathsToClear(schema, { ...before, "address.stateOrProvince": "" }, { ...before, "address.countryCode": "EG" }), []);
});
