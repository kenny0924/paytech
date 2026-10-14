import { test } from "node:test";
import assert from "node:assert/strict";
import {
  changeCatalogField,
  catalogChoices,
  issuingCountryMode,
} from "./catalog.ts";
import type { FormOptions } from "./catalog.ts";
const options: FormOptions = {
  clientType: "ORGANIZATION",
  catalogStatus: "AVAILABLE",
  organizationTypes: [{ code: "FIXTURE_ORG", label: "合成组织" }],
  businessModels: [{ code: "FIXTURE_MODEL", label: "合成模式" }],
  identifierTypes: [
    { code: "FIXTURE_ID", label: "合成证明", issuingCountryMode: "REQUIRED" },
    { code: "FIXTURE_GLOBAL", label: "合成全球标识", issuingCountryMode: "NONE" },
  ],
  requirementsStatus: "AVAILABLE",
  requirements: [],
};
test("changing incorporation country keeps the generic organization type", () => {
  const before = {
    "organization.incorporationCountry": "AE",
    "organization.organizationType": "FIXTURE_ORG",
  };
  assert.deepEqual(
    changeCatalogField(
      before,
      "organization.incorporationCountry",
      "GB",
      options,
    ),
    { ...before, "organization.incorporationCountry": "GB" },
  );
});
test("issuing country is chosen freely and cleared only for global identifiers", () => {
  const before = {
    "individual.residenceCountry": "AE",
    "identifier.identifierType": "FIXTURE_ID",
    "identifier.issuingCountry": "GB",
  };
  assert.equal(issuingCountryMode(before, options), "REQUIRED");
  const global = changeCatalogField(
    before,
    "identifier.identifierType",
    "FIXTURE_GLOBAL",
    options,
  );
  assert.equal(global["identifier.issuingCountry"], "");
  assert.equal(global["individual.residenceCountry"], "AE");
  assert.equal(issuingCountryMode(global, options), "NONE");
  const back = changeCatalogField(
    { ...global, "identifier.issuingCountry": "" },
    "identifier.identifierType",
    "FIXTURE_ID",
    options,
  );
  assert.equal(back["identifier.issuingCountry"], "");
  assert.equal(
    changeCatalogField(
      before,
      "identifier.identifierType",
      "FIXTURE_ID",
      options,
    ),
    before,
  );
});
test("missing catalogs provide no invented choices and do not rewrite existing draft", () => {
  assert.deepEqual(catalogChoices("identifier.identifierType", {}, null), []);
  assert.equal(issuingCountryMode({ "identifier.identifierType": "OLD" }, options), null);
  const before = {
    "identifier.identifierType": "OLD_CODE",
    "identifier.issuingCountry": "GB",
    "individual.givenName": "Fixture",
  };
  assert.deepEqual(
    changeCatalogField(before, "identifier.identifierType", "NEW_CODE", null),
    { ...before, "identifier.identifierType": "NEW_CODE" },
  );
});
