import type { Flat, Requirement } from "./onboarding.ts";
export type Option = { code: string; label: string };
export type Issuer = {
  scopeKey: string;
  label: string;
  issuingCountry: string | null;
};
export type FormOptions = {
  clientType: "INDIVIDUAL" | "ORGANIZATION";
  countryCode: string | null;
  catalogStatus: "AVAILABLE" | "COUNTRY_REQUIRED" | "UNAVAILABLE";
  organizationTypes: Option[];
  businessModels: Option[];
  identifierTypes: (Option & { issuers: Issuer[] })[];
  requirementsStatus: string;
  requirements: Requirement[];
};
// 只有用户明确修改父字段时才清理其依赖值；刷新目录不会重写现有资料。
export function changeCatalogField(
  values: Flat,
  key: string,
  value: string,
  options: FormOptions | null,
): Flat {
  if ((values[key] ?? "") === value) return values;
  const next = { ...values, [key]: value };
  if (key === "organization.incorporationCountry")
    next["organization.organizationType"] = "";
  if (key === "identifier.identifierType") {
    next["identifier.issuerScopeKey"] = "";
    next["identifier.issuingCountry"] = "";
  }
  if (key === "identifier.issuerScopeKey") {
    const issuer = options?.identifierTypes
      .find((i) => i.code === values["identifier.identifierType"])
      ?.issuers.find((i) => i.scopeKey === value);
    next["identifier.issuingCountry"] = issuer?.issuingCountry ?? "";
  }
  return next;
}
export function catalogChoices(
  key: string,
  values: Flat,
  options: FormOptions | null,
): Option[] {
  if (!options) return [];
  switch (key) {
    case "organization.organizationType":
      return options.organizationTypes;
    case "business.businessModelCode":
      return options.businessModels;
    case "identifier.identifierType":
      return options.identifierTypes;
    case "identifier.issuerScopeKey":
      return (
        options.identifierTypes
          .find((i) => i.code === values["identifier.identifierType"])
          ?.issuers.map((i) => ({
            code: i.scopeKey,
            label:
              i.label +
              (i.issuingCountry ? "（" + i.issuingCountry + "）" : "（全球）"),
          })) ?? []
      );
    default:
      return [];
  }
}
