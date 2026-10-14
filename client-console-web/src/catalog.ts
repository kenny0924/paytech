import type { Flat, Requirement } from "./onboarding.ts";
export type Option = { code: string; label: string };
// REQUIRED：须选择签发国；NONE：全球通用标识，无签发国。
export type IssuingCountryMode = "REQUIRED" | "NONE";
export type FormOptions = {
  clientType: "INDIVIDUAL" | "ORGANIZATION";
  catalogStatus: "AVAILABLE" | "UNAVAILABLE";
  organizationTypes: Option[];
  businessModels: Option[];
  identifierTypes: (Option & { issuingCountryMode: IssuingCountryMode })[];
  requirementsStatus: string;
  requirements: Requirement[];
};
// 当前证件类型的签发国要求；目录缺失或旧代码时返回 null，不猜测。
export function issuingCountryMode(
  values: Flat,
  options: FormOptions | null,
): IssuingCountryMode | null {
  return (
    options?.identifierTypes.find(
      (i) => i.code === values["identifier.identifierType"],
    )?.issuingCountryMode ?? null
  );
}
// 只有用户明确修改父字段时才清理其依赖值；刷新目录不会重写现有资料。
export function changeCatalogField(
  values: Flat,
  key: string,
  value: string,
  options: FormOptions | null,
): Flat {
  if ((values[key] ?? "") === value) return values;
  const next = { ...values, [key]: value };
  if (
    key === "identifier.identifierType" &&
    issuingCountryMode(next, options) === "NONE"
  )
    next["identifier.issuingCountry"] = "";
  return next;
}
export function catalogChoices(
  key: string,
  _values: Flat,
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
    default:
      return [];
  }
}
