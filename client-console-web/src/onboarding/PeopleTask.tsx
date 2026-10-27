import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { flatten } from "../onboarding.ts";
import { enumLabel } from "../enum-label.ts";
import { useLocale, useT } from "../i18n/react.tsx";
import { appPath, taskPath, useApplication } from "./context.tsx";
import { api } from "./model.ts";
import type { Application, FormSchema, Person, Relation } from "./model.ts";
import { declared } from "./progress.ts";
import { today } from "./documentDates.ts";
import { drawerErrors, ownershipRole, personSlots, relationTypes } from "./people.ts";
import { RequestFailure } from "../auth.ts";
import { errorText } from "./draft.ts";
export { personSlots, relationTypes } from "./people.ts";
import { availableTasks, taskBlocks } from "./schema.ts";
import { SchemaFields, summaryItems, useDraftForm, useFieldLabel } from "./FormTask.tsx";
import { DocumentSlot, useSlotTitle } from "./DocumentSlot.tsx";
import { TaskFooter, nextTask } from "./TaskPage.tsx";
import { taskLabelKey } from "./Shell.tsx";
import { Busy, Drawer, ErrorSummary, Failure, StatusDot, initials, useSchemaText } from "./ui.tsx";
import { countryName } from "./widgets.tsx";

const ownershipDeclarations = ["OWNERSHIP_COMPLETE", "DIRECTORSHIP_COMPLETE"];

export function personName(person: Pick<Person, "values">): string {
  const values = flatten(person.values);
  return [values["individual.givenName"], values["individual.familyName"]].filter(Boolean).join(" ");
}
const activeRelations = (app: Application, partyId: string) =>
  app.relations.filter((relation) => relation.relatedPartyId === partyId && relation.status !== "REMOVED" && !relation.effectiveTo);
const ownership = (relations: Relation[]) => {
  const values = relations.filter((relation) => ownershipRole(relation.relationType) && relation.ownershipPercentage).map((relation) => Number(relation.ownershipPercentage));
  return values.length ? Math.max(...values) : null;
};

function missingDocuments(app: Application, schema: FormSchema, person: Person) {
  const roles = activeRelations(app, person.partyId).map((relation) => relation.relationType);
  return personSlots(schema, roles).filter((slot) => slot.required
    && !app.documents.some((doc) => doc.subjectPartyId === person.partyId && doc.requirementCode === slot.requirementCode));
}

/**
 * 人员（N1）：列表行 + 侧边抽屉；列表上方逐条最低要求（√ / ○），阈值文字来自 Schema 的关系人规则
 * <li>一个人可有多个角色，只添加一次；每个角色是一条关系。</li>
 * <li>"已列出全部 UBO / 经理"的确认放在人员页末尾，与声明页同一记录。</li>
 */
export function PeopleTask() {
  const { app, schema, request, reload, touch } = useApplication(), t = useT("onboarding"), label = useSchemaText(), { locale } = useLocale();
  const navigate = useNavigate(), slotTitle = useSlotTitle();
  const [editing, setEditing] = useState<Person | "new" | null>(null), [flash, setFlash] = useState<string | null>(null);
  const [error, setError] = useState<Error | null>(null), [busy, setBusy] = useState(false);
  const rules = schema.relationRules, threshold = rules.uboThresholdPct ?? null;
  const types = relationTypes(schema);
  const roleLabel = (code: string) => label(types.find((type) => type.code === code)?.labelKey, enumLabel("relation_type", code) ?? code);
  const people = app.people.filter((person) => activeRelations(app, person.partyId).length > 0);
  // 规则按关系类型的规则角色（ruleRole）计数，不按国家写死关系代码。
  const roleOf = (code: string) => types.find((type) => type.code === code)?.role ?? code;
  const counts = (role: string) => people.filter((person) => activeRelations(app, person.partyId).some((relation) => roleOf(relation.relationType) === role)).length;
  const total = people.reduce((sum, person) => sum + (ownership(activeRelations(app, person.partyId)) ?? 0), 0);
  const incomplete = people.map((person) => ({ person, missing: missingDocuments(app, schema, person) })).filter((item) => item.missing.length);
  const checks: { ok: boolean; text: string }[] = [];
  if (rules.uboCollectionMode !== "EXEMPT" && rules.uboCollectionMode !== "CONTROL_ONLY") {
    checks.push({ ok: total > 0 && total <= 100, text: t("people.check_total", { total: Math.round(total * 100) / 100 }) });
    if (threshold !== null) checks.push({ ok: counts("UBO") + counts("OWNER") > 0, text: t("people.check_ubo", { threshold, count: counts("UBO") + counts("OWNER") }) });
  }
  if (rules.minSignatories) checks.push({ ok: counts("SIGNATORY") >= rules.minSignatories, text: t("people.check_signatories", { count: rules.minSignatories }) });
  if (rules.requiresManager) checks.push({ ok: counts("MANAGER") + counts("DIRECTOR") > 0, text: t("people.check_manager") });
  if (rules.requiresDirectors) checks.push({ ok: counts("DIRECTOR") > 0, text: t("people.check_directors") });
  checks.push(incomplete.length
    ? { ok: false, text: t("people.check_documents_missing", { name: personName(incomplete[0].person), document: slotTitle(incomplete[0].missing[0]) }) }
    : { ok: people.length > 0, text: t("people.check_documents") });
  const next = nextTask(availableTasks(schema), "people");
  const confirmations = schema.declarations.filter((item) => ownershipDeclarations.includes(item.declarationType));
  async function declare(type: string, value: boolean) {
    setBusy(true); setError(null);
    try {
      await request(api.declarations(app.applicationId), { method: "POST", key: crypto.randomUUID(), body: { declarationType: type, declaredValue: value ? "TRUE" : "FALSE" } });
      touch(); await reload();
    } catch (failure) { setError(failure as Error); } finally { setBusy(false); }
  }
  return <>
    <h1>{t("task.people")}</h1>
    <p className="ob-intro">{threshold !== null ? t("people.intro", { threshold }) : t("people.intro_generic")}</p>
    <ul className="ob-checks">{checks.map((check) => <li key={check.text} className={check.ok ? "ok" : ""}>
      <span aria-hidden="true">{check.ok ? "✓" : "○"}</span>{check.text}<span className="sr-only">{t(check.ok ? "people.met" : "people.not_met")}</span></li>)}</ul>
    <Failure error={error} />
    {people.length > 0 && <table className="ob-people">
      <thead><tr><th>{t("people.col_name")}</th><th>{t("people.col_roles")}</th><th className="num">{t("people.col_ownership")}</th><th>{t("people.col_documents")}</th></tr></thead>
      <tbody>{people.map((person) => {
        const relations = activeRelations(app, person.partyId), values = flatten(person.values), missing = missingDocuments(app, schema, person);
        const share = ownership(relations);
        return <tr key={person.partyId} className={"ob-row" + (flash === person.partyId ? " flash" : "")} tabIndex={0}
          onClick={() => setEditing(person)} onKeyDown={(event) => { if (event.key === "Enter") setEditing(person); }} data-field={"person-" + person.partyId} id={"person-" + person.partyId}>
          <td><span className="ob-person"><span className="avatar">{initials(personName(person))}</span>
            <span><strong>{personName(person) || t("people.unnamed")}</strong>
              <small>{[countryName(values["individual.nationalityCountry"], locale), values["individual.residenceCountry"] && values["individual.residenceCountry"] !== values["individual.nationalityCountry"]
                ? t("people.resident_of", { country: countryName(values["individual.residenceCountry"], locale) }) : ""].filter(Boolean).join(" · ")}</small></span></span></td>
          <td>{new Intl.ListFormat(locale, { style: "long", type: "conjunction" }).format(relations.map((relation) => roleLabel(relation.relationType)))}</td>
          <td className="num mono">{share !== null ? share + "%" : ""}</td>
          <td>{missing.length ? <StatusDot tone="attention">{t("people.missing_count", { count: missing.length })}</StatusDot> : <StatusDot tone="done">{t("people.documents_complete")}</StatusDot>}</td>
        </tr>;
      })}</tbody>
    </table>}
    {!people.length && <p className="ob-muted">{t("people.empty")}</p>}
    {app.editable && <button type="button" className="text-button ob-add" onClick={() => setEditing("new")}>+ {t("people.add")}</button>}
    {confirmations.map((item) => <label className="ob-declaration checkline ob-ownership-confirm" key={item.declarationType} id={"declaration-" + item.declarationType}>
      <input type="checkbox" checked={declared(app, item.declarationType)} disabled={!app.editable || busy}
        onChange={(event) => void declare(item.declarationType, event.target.checked)} />
      <span>{label(item.labelKey, item.declarationType)}</span></label>)}
    {app.editable && <TaskFooter saving={busy} busy={false}
      primary={next ? t("action.continue_to", { task: t(taskLabelKey(next, app.clientType)) }) : t("action.back_to_overview")}
      onPrimary={() => navigate(next ? taskPath(app.applicationId, next) : appPath(app.applicationId))}
      onLater={() => navigate(appPath(app.applicationId))} />}
    {editing && <PersonDrawer person={editing === "new" ? null : editing} close={(saved) => {
      setEditing(null);
      if (saved) { setFlash(saved); window.setTimeout(() => setFlash(null), 700); }
    }} />}
  </>;
}

/**
 * 编辑人员抽屉：角色复选、持股、Schema 中关系人适用的字段、该人员的证件槽
 * <li>新人员先保存（人员 + 首个角色原子创建），再追加其余角色；之后才能上传证件。</li>
 * <li>已有人员：资料改动 PATCH；角色增删分别创建 / 删除关系；持股改动 PATCH 关系。</li>
 */
function PersonDrawer({ person, close }: { person: Person | null; close: (saved?: string) => void }) {
  const { app, schema, request, reload, touch } = useApplication(), t = useT("onboarding"), label = useSchemaText();
  const blocks = taskBlocks(schema, "people", "RELATED_PERSON");
  const types = relationTypes(schema);
  const holdsShares = (code: string) => types.find((type) => type.code === code)?.ownership ?? ownershipRole(code);
  const isUbo = (code: string) => types.find((type) => type.code === code)?.ubo ?? code === "UBO";
  const existing = person ? activeRelations(app, person.partyId) : [];
  const [roles, setRoles] = useState<string[]>(existing.map((relation) => relation.relationType));
  const [share, setShare] = useState(String(ownership(existing) ?? ""));
  const firstUbo = existing.find((relation) => isUbo(relation.relationType));
  const [basis, setBasis] = useState(firstUbo?.uboBasis ?? "");
  const [problems, setProblems] = useState<Record<string, string>>({}), [busy, setBusy] = useState(false), [error, setError] = useState<Error | null>(null);
  const form = useDraftForm(person?.values ?? {}, schema, blocks, async () => {}, false);
  const editable = app.editable && (person?.editable ?? true);
  const owns = roles.some(holdsShares);
  function relationBody(role: string) {
    // client-master 要求关系有生效日期；新关系自今天（本地日期）起生效。
    return { relationType: role, ownershipPercentage: holdsShares(role) && share.trim() ? share.trim() : null, effectiveFrom: today(),
      ...(isUbo(role) && basis ? { uboBasis: basis } : {}) };
  }
  async function save() {
    setError(null);
    const local: Record<string, string> = {};
    if (!roles.length) local.roles = t("people.error_roles");
    if (owns && share.trim() && !(Number(share) > 0 && Number(share) <= 100)) local.share = t("people.error_share");
    const issues = form.validate();
    setProblems(local);
    if (Object.keys(local).length || issues.length) return;
    setBusy(true);
    try {
      const body = form.pending();
      let partyId = person?.partyId;
      if (!partyId) {
        const receipt = await request<{ partyId: string }>(api.people(app.applicationId), { method: "POST", key: crypto.randomUUID(),
          body: { person: body, relation: relationBody(roles[0]) } });
        partyId = receipt.partyId;
        for (const role of roles.slice(1))
          await request(api.relations(app.applicationId), { method: "POST", key: crypto.randomUUID(), body: { relatedPartyId: partyId, ...relationBody(role) } });
      } else {
        if (body.dirtyFields.length) await request(api.person(app.applicationId, partyId), { method: "PATCH", body });
        for (const relation of existing) {
          if (!roles.includes(relation.relationType)) { await request(api.relation(app.applicationId, relation.relationId), { method: "DELETE" }); continue; }
          const wanted = relationBody(relation.relationType), changed: Record<string, string | null> = {};
          if ((relation.ownershipPercentage ?? null) !== wanted.ownershipPercentage && holdsShares(relation.relationType)) changed.ownershipPercentage = wanted.ownershipPercentage;
          if (isUbo(relation.relationType) && (relation.uboBasis ?? "") !== basis) changed.uboBasis = basis || null;
          if (Object.keys(changed).length)
            await request(api.relation(app.applicationId, relation.relationId), { method: "PATCH", body: { dirtyFields: Object.keys(changed), values: changed } });
        }
        for (const role of roles.filter((role) => !existing.some((relation) => relation.relationType === role)))
          await request(api.relations(app.applicationId), { method: "POST", key: crypto.randomUUID(), body: { relatedPartyId: partyId, ...relationBody(role) } });
      }
      touch();
      await reload();
      close(partyId);
    } catch (failure) {
      // 422：关系与人员字段错误挂到抽屉控件；没有可定位字段时显示本地化的通用说明（含请求编号）。
      if (failure instanceof RequestFailure && failure.status === 422) {
        const mapped = drawerErrors(failure.fields, (message) => {
          const code = /^([A-Z][A-Z0-9_]{0,63})(?:\/[A-Z][A-Z0-9_]{0,63})?$/.exec(message);
          return code ? errorText(code[1]) : t("error.generic");
        });
        if (mapped.mapped) {
          setProblems(mapped.relation);
          form.setErrors((previous) => ({ ...previous, ...mapped.person }));
          return;
        }
      }
      setError(failure as Error);
    }
    finally { setBusy(false); }
  }
  const slots = person ? personSlots(schema, roles) : [];
  const labelOf = useFieldLabel(blocks, schema, form.values);
  const items = [
    ...(problems.roles ? [{ id: "person-roles", text: problems.roles }] : []),
    ...(problems.share ? [{ id: "person-share", text: problems.share }] : []),
    ...(problems.basis ? [{ id: "person-basis", text: problems.basis }] : []),
    ...summaryItems("p", blocks, schema, form.errors, labelOf, (name, message) => t("form.summary_item", { label: name, message })),
  ];
  return <Drawer title={person ? t("people.edit_title") : t("people.add_title")} onClose={() => close()} footer={<>
    <button type="button" className="secondary" onClick={() => close()} disabled={busy}>{t("action.cancel")}</button>
    {editable && <button type="button" className="primary" onClick={() => void save()} disabled={busy}>
      <Busy active={busy} label={t("save.saving")}>{t("people.save")}</Busy></button>}
  </>}>
    <ErrorSummary items={items} />
    <Failure error={error} />
    <fieldset className="ob-field" id="person-roles" data-field="person-roles">
      <legend>{t("people.roles")}</legend>
      <p className="ob-help">{t("people.roles_help")}</p>
      {types.map((type) => <label className="checkline" key={type.code}><input type="checkbox" checked={roles.includes(type.code)} disabled={!editable}
        onChange={(event) => setRoles((previous) => event.target.checked ? [...previous, type.code] : previous.filter((code) => code !== type.code))} />
        {label(type.labelKey, enumLabel("relation_type", type.code) ?? type.code)}</label>)}
      {problems.roles && <p className="ob-error">{problems.roles}</p>}
    </fieldset>
    {owns && <div className="ob-field ob-share" data-field="person-share">
      <label htmlFor="person-share">{t("people.ownership")}</label>
      <span className="ob-suffix"><input id="person-share" className="mono" inputMode="decimal" value={share} disabled={!editable}
        aria-invalid={!!problems.share} onChange={(event) => setShare(event.target.value.replace(/[^0-9.]/g, ""))} /><i>%</i></span>
      {problems.share && <p className="ob-error">{problems.share}</p>}
    </div>}
    {roles.some(isUbo) && <fieldset className="ob-field" id="person-basis" data-field="person-basis">
      <legend>{t("people.ubo_basis")}</legend>
      {["OWNERSHIP", "CONTROL", "SENIOR_MANAGING_OFFICIAL"].map((code) => <label className="checkline" key={code}>
        <input type="radio" name="ubo-basis" checked={basis === code} disabled={!editable} onChange={() => setBasis(code)} />{t("people.ubo_basis_" + code.toLowerCase())}</label>)}
      {problems.basis && <p className="ob-error">{problems.basis}</p>}
    </fieldset>}
    <SchemaFields prefix="p" blocks={blocks} schema={schema} form={form} disabled={!editable} countryCode={null} headings={false} />
    {slots.length > 0 && <section className="ob-person-docs"><h3>{t("people.documents")}</h3>
      {slots.map((slot) => <DocumentSlot key={slot.requirementCode} slot={slot} subjectPartyId={person!.partyId} onChanged={reload} compact />)}</section>}
    {!person && personSlots(schema, roles).length > 0 && <p className="ob-muted">{t("people.documents_after_save")}</p>}
  </Drawer>;
}
