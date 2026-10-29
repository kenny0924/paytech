import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { RequestFailure } from "../auth.ts";
import { flatten } from "../onboarding.ts";
import type { Flat } from "../onboarding.ts";
import { enumLabel } from "../enum-label.ts";
import { formatDateTime } from "../i18n/index.ts";
import { useLocale, useOptionalT, useT } from "../i18n/react.tsx";
import { appPath, taskPath, useApplication } from "./context.tsx";
import { api, problemErrors, readCheck } from "./model.ts";
import type { FormSchema, RequirementError, SchemaField } from "./model.ts";
import { declared, locate } from "./progress.ts";
import type { Anchor } from "./progress.ts";
import { activeAddressFormat, addressPath, availableTasks, draftPath, identifierPaths, isIdentifierLead, isPersonRole, isVisible, selectedIdentifier, splitCodeList, taskBlocks } from "./schema.ts";
import type { TaskKey } from "./schema.ts";
import { errorText } from "./draft.ts";
import { inputId } from "./FormTask.tsx";
import { personName, relationTypes } from "./PeopleTask.tsx";
import { taskLabelKey } from "./Shell.tsx";
import { Busy, ErrorSummary, Failure, useSchemaLookup, useSchemaText } from "./ui.tsx";
import { REF_COUNTRY_SUBDIVISION, REF_CURRENCY, REFDATA_NAMESPACE, countryName, currencyName, subdivisionName } from "./refdata.ts";

/** 字段的展示值：目录 / 国家显示名称，证件只显示掩码，日期按当前语言。 */
export function useDisplayValue() {
  const label = useSchemaText(), lookup = useSchemaLookup(), { locale } = useLocale(), t = useT("onboarding"), refdata = useOptionalT(REFDATA_NAMESPACE);
  /** 目录项名称：参考数据按 B7（币种 Intl、行政区 refdata 文案，再回退英文名与编码），自有目录按文案键。 */
  const catalogText = (schema: FormSchema, catalogCode: string | null | undefined, value: string): string => {
    const item = catalogCode ? schema.catalogs[catalogCode]?.find((entry) => entry.itemCode === value) : undefined;
    if (catalogCode === REF_CURRENCY) return currencyName(value, locale, item?.name) + " (" + value + ")";
    if (catalogCode === REF_COUNTRY_SUBDIVISION) return subdivisionName(item ?? { itemCode: value, name: null }, refdata);
    return item ? label(item.labelKey, value) : value;
  };
  const country = (schema: FormSchema, value: string) => countryName(value, locale, schema.countries.find((entry) => entry.code === value)?.name);
  return (field: SchemaField, values: Flat, schema: FormSchema): string => {
    if (field.widget === "ADDRESS") return (activeAddressFormat(schema, values)?.components ?? []).map((component) => {
      const value = values[addressPath(component)];
      if (!value) return "";
      if (/^country(code)?$/i.test(component.component)) return country(schema, value);
      return catalogText(schema, component.catalogCode, value);
    }).filter(Boolean).join(", ");
    if (field.widget === "IDENTIFIER") {
      const type = selectedIdentifier(field, schema, values);
      const number = values[identifierPaths.masked] || (values[identifierPaths.value] ? t("review.identifier_entered") : "");
      return number ? [type ? label(type.labelKey, type.code) : "", number].filter(Boolean).join(" \u00b7 ") : "";
    }
    const path = draftPath(field), value = path ? values[path] ?? "" : "";
    if (!value) return "";
    if (field.widget === "COUNTRY") return country(schema, value);
    if (field.widget === "BOOLEAN") return t(value.toUpperCase() === "TRUE" ? "form.yes" : "form.no");
    if (field.widget === "DATE") { const date = new Date(value + "T00:00:00Z"); return Number.isFinite(date.getTime()) ? new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" }).format(date) : value; }
    if (field.widget === "MULTI_SELECT") {
      // 按目录顺序列出名称（没有译文或不在目录时显示编码），用当前语言的列表连接方式。
      const codes = splitCodeList(value), catalog = (field.catalogCode ? schema.catalogs[field.catalogCode] : undefined) ?? [];
      const ordered = [...catalog.map((item) => item.itemCode).filter((code) => codes.includes(code)), ...codes.filter((code) => !catalog.some((item) => item.itemCode === code))];
      return new Intl.ListFormat(locale, { type: "conjunction" }).format(ordered.map((code) => {
        const item = catalog.find((entry) => entry.itemCode === code);
        return item ? lookup(item.labelKey) ?? code : code;
      }));
    }
    if (field.widget === "SELECT" && field.catalogCode?.startsWith("REF_")) return catalogText(schema, field.catalogCode, value);
    if (field.widget === "SELECT") {
      const item = (field.catalogCode ? schema.catalogs[field.catalogCode]?.find((entry) => entry.itemCode === value) : undefined)
        ?? schema.organizationTypes.map((entry) => ({ itemCode: entry.code, labelKey: entry.labelKey })).find((entry) => /organization_?type$/i.test(field.fieldKey) && entry.itemCode === value);
      return item ? label(item.labelKey, value) : value;
    }
    return value;
  };
}

/** 需求级错误 → 汇总链接：任务名 · 定位 + 原因；点击进入对应任务并聚焦字段 / 人员 / 材料 / 声明。 */
export function useErrorLinks() {
  const { app, schema } = useApplication(), t = useT("onboarding"), navigate = useNavigate();
  return (errors: RequirementError[]) => errors.map((error, index) => {
    const anchor = locate(error.requirementKey, error.subjectPartyId, null, app, schema);
    const id = anchorId(anchor, app.subjectPartyId);
    return { id: id ?? "error-" + index, text: t("form.summary_item", { label: t(taskLabelKey(anchor.task, app.clientType)), message: errorText(error.code) }),
      onOpen: () => navigate(taskPath(app.applicationId, anchor.task), { state: { focus: id } }) };
  });
}
export function anchorId(anchor: Anchor, subjectPartyId: string): string | null {
  if (anchor.declarationType) return "declaration-" + anchor.declarationType;
  if (anchor.requirementCode) return "slot-" + (anchor.personId ?? subjectPartyId) + "-" + anchor.requirementCode;
  if (anchor.personId) return "person-" + anchor.personId;
  if (anchor.fieldKey) return inputId("f", anchor.fieldKey);
  return null;
}

/**
 * 核对并提交（C3）：汇总页即提交页；每段"编辑"回到对应任务
 * <li>提交前先跑同源核对（CM-9）；有问题时页顶错误汇总逐条跳转，不提交。</li>
 * <li>提交成功后进入"审核中"；422 同样按需求级错误展示。</li>
 */
export function ReviewTask() {
  const { app, schema, request, reload } = useApplication(), t = useT("onboarding"), label = useSchemaText(), { locale } = useLocale();
  const display = useDisplayValue(), links = useErrorLinks(), navigate = useNavigate();
  const [busy, setBusy] = useState(false), [errors, setErrors] = useState<RequirementError[]>([]), [error, setError] = useState<Error | null>(null);
  const values = flatten(app.values), tasks = availableTasks(schema);
  async function submit() {
    setBusy(true); setError(null); setErrors([]);
    try {
      const check = readCheck(await request(api.submissionCheck(app.applicationId)), t("submit.check_unavailable"));
      if (!check.ready) { setErrors(check.errors); return; }
      await request(api.submissions(app.applicationId), { method: "POST" });
      await reload();
      navigate(appPath(app.applicationId), { replace: true });
    } catch (failure) {
      const listed = failure instanceof RequestFailure ? problemErrors(failure.problem) : [];
      if (listed.length) setErrors(listed);
      else if (failure instanceof RequestFailure && failure.code === "SUBMISSION_INCOMPLETE") {
        // 正式提交的 422 可能不带逐项问题：重新核对一次取需求级错误。
        try { setErrors(readCheck(await request(api.submissionCheck(app.applicationId)), t("submit.check_unavailable")).errors); }
        catch (again) { setError(again as Error); }
      } else setError(failure as Error);
    } finally { setBusy(false); }
  }
  const section = (task: TaskKey, rows: [string, string][]) => <section className="ob-summary-section" key={task}>
    <header><h2>{t(taskLabelKey(task, app.clientType))}</h2>
      {app.editable && <Link to={taskPath(app.applicationId, task)}>{t("review.edit")}</Link>}</header>
    <dl>{rows.map(([name, value], index) => <div key={name + index}><dt>{name}</dt><dd>{value || <span className="ob-muted">{t("review.not_provided")}</span>}</dd></div>)}</dl>
  </section>;
  const fieldRows = (task: TaskKey): [string, string][] => taskBlocks(schema, task, "PRIMARY").flatMap((block) => block.fields)
    .filter((field) => isVisible(field, values, schema) && isIdentifierLead(field, schema))
    .map((field) => [field.widget === "IDENTIFIER" && field.requirementGroup ? t("identifier.type") : label(field.labelKey, field.fieldKey), display(field, values, schema)]);
  const types = relationTypes(schema);
  const companySlots = schema.materials.filter((slot) => !isPersonRole(slot.subjectRole));
  const uploaded = companySlots.filter((slot) => app.documents.some((doc) => doc.requirementCode === slot.requirementCode && doc.subjectPartyId === app.subjectPartyId)).length;
  const confirmed = schema.declarations.filter((item) => declared(app, item.declarationType));
  const lastDeclared = app.declarations.map((item) => item.declaredAt).filter(Boolean).sort().pop();
  return <>
    <ErrorSummary items={links(errors)} />
    <h1>{t("task.review")}</h1>
    <p className="ob-intro">{app.responsiblePartnerName ? t("task_intro.review_partner", { name: app.responsiblePartnerName }) : t("task_intro.review")}</p>
    <Failure error={error} />
    {tasks.includes("profile") && section("profile", fieldRows("profile"))}
    {tasks.includes("address") && section("address", fieldRows("address"))}
    {tasks.includes("people") && section("people", app.people.map((person) => {
      const roles = app.relations.filter((relation) => relation.relatedPartyId === person.partyId && !relation.effectiveTo);
      const share = roles.find((relation) => relation.ownershipPercentage)?.ownershipPercentage;
      const names = new Intl.ListFormat(locale, { style: "long", type: "conjunction" }).format(
        roles.map((relation) => label(types.find((type) => type.code === relation.relationType)?.labelKey, enumLabel("relation_type", relation.relationType) ?? relation.relationType)));
      return [personName(person) || t("people.unnamed"), [names, share ? share + "%" : ""].filter(Boolean).join(" \u00b7 ")] as [string, string];
    }))}
    {tasks.includes("materials") && section("materials", [[t("review.uploaded"), t("review.count", { done: uploaded, total: companySlots.length })]])}
    {tasks.includes("declarations") && section("declarations", [[t("review.confirmed"), [t("review.count", { done: confirmed.length, total: schema.declarations.length }),
      lastDeclared ? formatDateTime(lastDeclared) : ""].filter(Boolean).join(" · ")]])}
    {app.editable && <div className="ob-submit-row">
      <button type="button" className="primary" disabled={busy} onClick={() => void submit()}><Busy active={busy} label={t("submit.submitting")}>{t("submit.action")}</Busy></button>
    </div>}
  </>;
}
