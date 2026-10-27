import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { RequestFailure } from "../auth.ts";
import { enumLabel } from "../enum-label.ts";
import { fallbackText, readWorkspaces, workspaceName, workspaceSubtitle } from "../workspace.ts";
import type { Workspaces } from "../workspace.ts";
import { useLocale, useT } from "../i18n/react.tsx";
import type { Client } from "./context.tsx";
import { appPath } from "./context.tsx";
import { api, readJurisdictions, readSchema } from "./model.ts";
import type { ClientType, FormSchema, Jurisdiction, StartSummary } from "./model.ts";
import { formatOnboardingCode, isPersonRole, onboardingCodeComplete } from "./schema.ts";
import { useSlotTitle } from "./DocumentSlot.tsx";
import { TopBar } from "./Shell.tsx";
import { Busy, Failure, StatusDot, useSchemaText } from "./ui.tsx";
import { countryName } from "./widgets.tsx";

function useAuthFailure(client: Client) {
  const navigate = useNavigate();
  return (failure: unknown, set: (error: Error) => void) => {
    if (failure instanceof RequestFailure && failure.status === 401) { client.clear(); navigate("/login", { replace: true }); }
    else set(failure as Error);
  };
}

/**
 * 我的工作区与申请（`/onboarding`）：可进入的工作区、已有申请；没有任何记录时直接进入新申请
 * <li>工作区区域读取失败只影响该区域。</li>
 */
export function StartList({ client }: { client: Client }) {
  const t = useT("onboarding"), navigate = useNavigate(), handle = useAuthFailure(client);
  const grant = useSyncExternalStore(client.subscribe, client.current);
  const [start, setStart] = useState<StartSummary | null>(null), [workspaces, setWorkspaces] = useState<Workspaces | null>(null);
  const [error, setError] = useState<Error | null>(null), [entering, setEntering] = useState<string | null>(null);
  const [notice] = useState(() => fallbackText(client.takeFallback()));
  useEffect(() => {
    let alive = true;
    client.request<StartSummary>(api.start).then((value) => {
      if (!alive) return;
      setStart(value); setWorkspaces(readWorkspaces(value.workspaces));
    }).catch((failure) => { if (alive) handle(failure, setError); });
    return () => { alive = false; };
  }, []);
  async function enter(memberId: string) {
    setEntering(memberId); setError(null);
    try { await client.selectWorkspace(memberId); navigate("/workspace"); }
    catch (failure) { handle(failure, setError); } finally { setEntering(null); }
  }
  if (start && !start.applications.length && workspaces?.items.length === 0 && !notice) return <Navigate to="/onboarding/new" replace />;
  return <div className="ob-page"><TopBar account={grant?.context.emailMasked} />
    <main className="ob-narrow wide" id="main">
      <h1>{t("start.title")}</h1>
      {notice && <p className="notice" role="status">{notice}</p>}
      <Failure error={error} />
      {!start && !error && <p className="ob-loading" role="status">{t("page.loading")}</p>}
      {workspaces && workspaces.items.length > 0 && <section className="ob-list-section">
        <h2>{t("workspaces.title")}</h2>
        {workspaces.status === "UNAVAILABLE" && <p className="notice">{t("workspaces.unavailable")}</p>}
        <ul className="ob-rows">{workspaces.items.map((item) => <li key={item.memberId}>
          <span><strong>{workspaceName(item)}</strong><small>{[workspaceSubtitle(item), item.current ? t("workspaces.current") : ""].filter(Boolean).join(" · ")}</small></span>
          {item.state === "ACCESSIBLE" ? <button type="button" className="secondary" disabled={entering !== null} onClick={() => void enter(item.memberId)}>
            {entering === item.memberId ? t("workspace.entering") : t("workspace.enter")}</button>
            : <span className="ob-muted">{item.reasonText ?? t("workspaces.restricted")}</span>}
        </li>)}</ul>
      </section>}
      {start && start.applications.length > 0 && <section className="ob-list-section">
        <h2>{t("start.applications_title")}</h2>
        <ul className="ob-rows">{start.applications.map((row) => <li key={row.applicationId}>
          <Link to={appPath(row.applicationId)} className="ob-row-link">
            <span><strong>{t(row.clientType === "ORGANIZATION" ? "start.row_organization" : "start.row_individual")}</strong>
              <small className="mono">{row.applicationNo}</small></span>
            <StatusDot tone={row.status === "APPROVED" ? "done" : row.status === "RETURNED" ? "attention" : ["REJECTED", "WITHDRAWN", "EXPIRED"].includes(row.status) ? "todo" : "active"}>
              {enumLabel("application_status", row.status) ?? row.status}</StatusDot>
          </Link></li>)}</ul>
        {start.hasMore && <p className="ob-muted">{t("start.has_more")}</p>}
      </section>}
      {start && !start.activeApplicationId && <Link className="primary" to="/onboarding/new">{t("start.new_application")}</Link>}
    </main>
  </div>;
}

type Step = "code" | "entity" | "checklist";

/**
 * 开始申请（C2）三步：入驻码 → 注册国家与主体类型 → 准备清单；"开始填写"时才创建申请
 * <li>入驻码只在本地做格式整理；有效性在创建时由服务端判断，无效只提示"入驻码无效"，不透露原因。</li>
 * <li>国家列表只含可受理的辖区；组织形式与准备清单来自对应 Schema。</li>
 */
export function StartFlow({ client }: { client: Client }) {
  const t = useT("onboarding"), label = useSchemaText(), { locale } = useLocale(), navigate = useNavigate(), handle = useAuthFailure(client);
  const grant = useSyncExternalStore(client.subscribe, client.current);
  const [step, setStep] = useState<Step>("code");
  const [code, setCode] = useState(""), [codeError, setCodeError] = useState<string | null>(null);
  const [jurisdictions, setJurisdictions] = useState<Jurisdiction[] | null>(null);
  const [jurisdiction, setJurisdiction] = useState(""), [clientType, setClientType] = useState<ClientType>("ORGANIZATION");
  const [organizationType, setOrganizationType] = useState(""), [schema, setSchema] = useState<FormSchema | null>(null);
  const [checklist, setChecklist] = useState<FormSchema | null>(null);
  const [error, setError] = useState<Error | null>(null), [entityError, setEntityError] = useState<string | null>(null), [busy, setBusy] = useState(false);
  const heading = useRef<HTMLHeadingElement>(null), key = useRef<string>(crypto.randomUUID());
  const slotTitle = useSlotTitle();
  useEffect(() => { heading.current?.focus({ preventScroll: true }); }, [step]);
  useEffect(() => {
    let alive = true;
    client.request(api.jurisdictions).then((value) => { if (alive) setJurisdictions(readJurisdictions(value)); })
      .catch((failure) => { if (alive) handle(failure, setError); });
    return () => { alive = false; };
  }, []);
  // 选定国家与主体类型后读取默认 Schema，取组织形式列表。
  useEffect(() => {
    if (!jurisdiction) return;
    let alive = true;
    setSchema(null); setOrganizationType("");
    client.request(api.schema(jurisdiction, clientType, null)).then((value) => { if (alive) setSchema(readSchema(value, t("schema.unavailable"))); })
      .catch((failure) => { if (alive) handle(failure, setError); });
    return () => { alive = false; };
  }, [jurisdiction, clientType]);
  async function toChecklist() {
    setEntityError(null); setError(null);
    if (!jurisdiction) { setEntityError(t("start.error_country")); return; }
    if (schema?.organizationTypes.length && !organizationType) { setEntityError(t("start.error_org_type")); return; }
    setBusy(true);
    try {
      setChecklist(organizationType ? readSchema(await client.request(api.schema(jurisdiction, clientType, organizationType)), t("schema.unavailable")) : schema);
      setStep("checklist");
    } catch (failure) { handle(failure, setError); } finally { setBusy(false); }
  }
  async function create() {
    setBusy(true); setError(null);
    try {
      const receipt = await client.request<{ applicationId: string }>(api.applications, { method: "POST", key: key.current, body: {
        clientType, jurisdictionCode: jurisdiction, organizationTypeCode: organizationType || null, onboardingCode: code || null } });
      navigate(appPath(receipt.applicationId), { replace: true });
    } catch (failure) {
      key.current = crypto.randomUUID();
      if (failure instanceof RequestFailure && failure.code === "ONBOARDING_CODE_INVALID") { setCodeError(t("start.code_invalid")); setStep("code"); }
      else if (failure instanceof RequestFailure && failure.code === "JURISDICTION_NOT_ACCEPTED") { setEntityError(t("start.country_closed")); setStep("entity"); }
      else handle(failure, setError);
    } finally { setBusy(false); }
  }
  const orgType = schema?.organizationTypes.find((item) => item.code === organizationType);
  const people = checklist?.materials.filter((slot) => isPersonRole(slot.subjectRole)) ?? [];
  return <div className="ob-page"><TopBar account={grant?.context.emailMasked} />
    <main className="ob-narrow" id="main">
      <Failure error={error} />
      {step === "code" && <form className="ob-enter" noValidate onSubmit={(event) => {
        event.preventDefault();
        if (code && !onboardingCodeComplete(code)) { setCodeError(t("start.code_format")); return; }
        setStep("entity");
      }}>
        <h1 ref={heading} tabIndex={-1}>{t("start.code_title")}</h1>
        <p className="ob-intro">{t("start.code_intro")}</p>
        <div className="ob-field">
          <label htmlFor="onboarding-code">{t("start.code_label")}</label>
          <input id="onboarding-code" className="mono ob-code" value={code} autoComplete="off" spellCheck={false} autoCapitalize="characters"
            inputMode="text" aria-invalid={!!codeError} aria-describedby={codeError ? "onboarding-code-error" : undefined}
            onChange={(event) => { setCode(formatOnboardingCode(event.target.value)); setCodeError(null); }} />
          {codeError && <p className="ob-error" id="onboarding-code-error" role="alert">{codeError}</p>}
        </div>
        <button type="submit" className="primary full" disabled={!onboardingCodeComplete(code)}>{t("start.continue")}</button>
        <button type="button" className="text-button ob-below" onClick={() => { setCode(""); setCodeError(null); setStep("entity"); }}>{t("start.no_code")}</button>
      </form>}
      {step === "entity" && <form className="ob-enter ob-entity" noValidate onSubmit={(event) => { event.preventDefault(); void toChecklist(); }}>
        <h1 ref={heading} tabIndex={-1}>{t("start.entity_title")}</h1>
        <p className="ob-intro">{t("start.entity_intro")}</p>
        {entityError && <div className="ob-summary" role="alert"><strong>{entityError}</strong></div>}
        <fieldset className="ob-field">
          <legend>{t("start.country")}</legend>
          {!jurisdictions && !error && <p className="ob-loading">{t("page.loading")}</p>}
          <div className="ob-options">{jurisdictions?.map((item) => <label key={item.jurisdictionCode} className={"ob-option" + (jurisdiction === item.jurisdictionCode ? " selected" : "")}>
            <input type="radio" name="jurisdiction" checked={jurisdiction === item.jurisdictionCode} onChange={() => setJurisdiction(item.jurisdictionCode)} />
            <span>{label(item.labelKey, countryName(item.countryCode, locale) || item.jurisdictionCode)}</span></label>)}</div>
        </fieldset>
        <fieldset className="ob-field">
          <legend>{t("start.entity_type")}</legend>
          <div className="ob-options two">{(["ORGANIZATION", "INDIVIDUAL"] as const).map((type) => <label key={type} className={"ob-option" + (clientType === type ? " selected" : "")}>
            <input type="radio" name="client-type" checked={clientType === type} onChange={() => setClientType(type)} />
            <span><strong>{t(type === "ORGANIZATION" ? "start.organization_title" : "start.individual_title")}</strong>
              <small>{t(type === "ORGANIZATION" ? "start.organization_desc" : "start.individual_desc")}</small></span></label>)}</div>
        </fieldset>
        {schema && schema.organizationTypes.length > 0 && <div className="ob-field ob-reveal">
          <label htmlFor="organization-type">{t("start.organization_type")}</label>
          <select id="organization-type" value={organizationType} onChange={(event) => setOrganizationType(event.target.value)}>
            <option value="">{t("form.select")}</option>
            {schema.organizationTypes.map((item) => <option key={item.code} value={item.code}>{label(item.labelKey, item.code)}</option>)}
          </select>
        </div>}
        <div className="ob-row-actions">
          <button type="button" className="text-button" onClick={() => setStep("code")}>{t("start.back")}</button>
          <button type="submit" className="primary" disabled={busy || !jurisdiction || (jurisdiction !== "" && !schema)}>
            <Busy active={busy} label={t("page.loading")}>{t("start.see_checklist")}</Busy></button>
        </div>
      </form>}
      {step === "checklist" && checklist && <div className="ob-enter">
        <h1 ref={heading} tabIndex={-1}>{t("start.checklist_title")}</h1>
        <p className="ob-intro">{t("start.checklist_intro", { subject: [countryName(jurisdictions?.find((item) => item.jurisdictionCode === jurisdiction)?.countryCode, locale),
          orgType ? label(orgType.labelKey, orgType.code) : t(clientType === "ORGANIZATION" ? "start.organization_title" : "start.individual_title")].filter(Boolean).join(" · ") })}</p>
        <ul className="ob-checklist">
          {checklist.materials.filter((slot) => !isPersonRole(slot.subjectRole)).map((slot) => <li key={slot.requirementCode}>
            <strong>{slotTitle(slot)}</strong>
            <small>{[slot.required ? "" : t("materials.if_applicable"), slot.maxAgeDays ? t("materials.max_age", { months: Math.max(1, Math.round(slot.maxAgeDays / 30)) }) : ""].filter(Boolean).join(" · ")}</small></li>)}
          {people.map((slot) => <li key={"person-" + slot.requirementCode}>
            <strong>{slotTitle(slot)}</strong>
            <small>{checklist.relationRules.uboThresholdPct ? t("start.checklist_people", { threshold: checklist.relationRules.uboThresholdPct }) : t("home.per_person")}</small></li>)}
        </ul>
        <p className="ob-muted">{t("materials.requirements_line")}</p>
        <div className="ob-row-actions">
          <button type="button" className="text-button" onClick={() => setStep("entity")}>{t("start.change_entity")}</button>
          <button type="button" className="primary" disabled={busy} onClick={() => void create()}><Busy active={busy} label={t("start.creating")}>{t("start.begin")}</Busy></button>
        </div>
      </div>}
    </main>
  </div>;
}

