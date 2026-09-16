import { test } from "node:test";
import assert from "node:assert/strict";
import { changeCatalogField, catalogChoices } from "./catalog.ts";
import type { FormOptions } from "./catalog.ts";
const options: FormOptions = {
  clientType: "ORGANIZATION",
  countryCode: "AE",
  catalogStatus: "AVAILABLE",
  organizationTypes: [{ code: "FIXTURE_ORG", label: "合成组织" }],
  businessModels: [{ code: "FIXTURE_MODEL", label: "合成模式" }],
  identifierTypes: [
    {
      code: "FIXTURE_ID",
      label: "合成证明",
      issuers: [
        {
          scopeKey: "FIXTURE_GB_REGISTRY",
          label: "合成机构",
          issuingCountry: "GB",
        },
        { scopeKey: "GLOBAL", label: "合成全球机构", issuingCountry: null },
      ],
    },
  ],
  requirementsStatus: "COUNTRY_REQUIRED",
  requirements: [],
};
test("changing incorporation country clears only the dependent organization type", () => {
  const before = {
    "organization.incorporationCountry": "AE",
    "organization.organizationType": "FIXTURE_ORG",
    "organization.legalName": "Fixture",
  };
  assert.deepEqual(
    changeCatalogField(
      before,
      "organization.incorporationCountry",
      "GB",
      options,
    ),
    {
      ...before,
      "organization.incorporationCountry": "GB",
      "organization.organizationType": "",
    },
  );
  assert.deepEqual(
    changeCatalogField(
      before,
      "organization.incorporationCountry",
      "AE",
      options,
    ),
    before,
  );
});
test("issuer selection derives exact country and never substitutes residence country", () => {
  const before = {
    "individual.residenceCountry": "AE",
    "identifier.identifierType": "FIXTURE_ID",
  };
  const next = changeCatalogField(
    before,
    "identifier.issuerScopeKey",
    "FIXTURE_GB_REGISTRY",
    options,
  );
  assert.equal(next["identifier.issuingCountry"], "GB");
  assert.equal(next["individual.residenceCountry"], "AE");
  assert.equal(
    changeCatalogField(next, "identifier.issuerScopeKey", "GLOBAL", options)[
      "identifier.issuingCountry"
    ],
    "",
  );
  assert.equal(
    changeCatalogField(next, "identifier.identifierType", "", options)[
      "identifier.issuerScopeKey"
    ],
    "",
  );
  assert.equal(before["identifier.identifierType"], "FIXTURE_ID");
});
test("missing catalogs provide no invented choices and do not rewrite existing draft", () => {
  assert.deepEqual(catalogChoices("identifier.identifierType", {}, null), []);
  const before = {
    "identifier.issuerScopeKey": "OLD_CODE",
    "identifier.issuingCountry": "GB",
    "individual.givenName": "Fixture",
  };
  assert.deepEqual(
    changeCatalogField(before, "individual.givenName", "New", null),
    { ...before, "individual.givenName": "New" },
  );
  assert.deepEqual(
    catalogChoices(
      "identifier.issuerScopeKey",
      { "identifier.identifierType": "UNKNOWN" },
      options,
    ),
    [],
  );
});
