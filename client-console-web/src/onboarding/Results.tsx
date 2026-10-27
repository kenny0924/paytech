import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { RequestFailure } from "../auth.ts";
import { enterableWorkspace, readWorkspaces } from "../workspace.ts";
import type { Workspaces } from "../workspace.ts";
import { formatDate, formatDateTime } from "../i18n/index.ts";
import { useLocale, useT } from "../i18n/react.tsx";
import { fixPath, taskPath, useApplication } from "./context.tsx";
import { api } from "./model.ts";
import type { RequirementError, StartSummary } from "./model.ts";
import { daysLeft, locate } from "./progress.ts";
import type { Phase } from "./progress.ts";
import { findField } from "./schema.ts";
import { errorText } from "./draft.ts";
import { useSlotTitle } from "./DocumentSlot.tsx";
import { personName } from "./PeopleTask.tsx";
import { anchorId } from "./ReviewTask.tsx";
import { TopBar, taskLabelKey } from "./Shell.tsx";
import { Busy, Failure, InlineConfirm, StatusDot, useSchemaText } from "./ui.tsx";

/** 需求级错误的定位文字（"公司材料 · 银行证明"）与原因（审核方说明优先，否则按错误码）。 */
export function useErrorDescription() {
  const { app, schema } = useApplication(), t = useT("onboarding"), label = useSchemaText(), slotTitle = useSlotTitle();
  return (error: RequirementError) => {
    const anchor = locate(error.requirementKey, error.subjectPartyId, null, app, schema);
    const parts = [t(taskLabelKey(anchor.task, app.clientType))];
    const person = anchor.personId ? app.people.find((item) => item.partyId === anchor.personId) : undefined;
    if (person) parts.push(personName(person));
    const slot = anchor.requirementCode ? schema.materials.find((item) => item.requirementCode === anchor.requirementCode) : undefined;
    if (slot) parts.push(slotTitle(slot));
    else if (anchor.declarationType) parts.push(label(schema.declarations.find((item) => item.declarationType === anchor.declarationType)?.labelKey, anchor.declarationType));
    else if (anchor.fieldKey) { const field = findField(schema, anchor.fieldKey); if (field && !person) parts.push(label(field.labelKey, field.fieldKey)); }
    return { anchor, where: parts.join(" · "), what: error.reason?.trim() || errorText(error.code), id: anchorId(anchor, app.subjectPartyId) };
  };
}

/** 撤回：行内确认（不弹浏览器对话框）；确认后调 CM-8，成功后显示已撤回。 */
export function WithdrawAction() {
  const { app, request, reload } = useApplication(), t = useT("onboarding");
  return <InlineConfirm trigger={t("withdraw.action")} body={t("withdraw.body")} confirm={t("withdraw.confirm")} working={t("withdraw.working")}
    onConfirm={async () => { await request(api.withdrawal(app.applicationId), { method: "POST", key: crypto.randomUUID() }); await reload(); }} />;
}

/**
 * 结果页（C4）：圆点状态行 + 标题 + 一段说明 + 少量事实 + 下一步；不放插图或彩色横幅
 * <li>退回：问题清单 + 开始处理 + 审核记录 + 撤回（N2）。</li>
 */
export function ResultPage({ phase }: { phase: Exclude<Phase, "draft"> }) {
  const { app, client } = useApplication();
  const reviewer = app.responsiblePartnerName?.trim() || null;
  return <div className="ob-page">
    <TopBar reviewer={app.responsiblePartnerName} account={client.current()?.context.emailMasked} />
    {phase === "returned" ? <Returned reviewer={reviewer} /> : <main className="ob-result" id="main"><Outcome phase={phase} reviewer={reviewer} /></main>}
  </div>;
}

function Outcome({ phase, reviewer }: { phase: Exclude<Phase, "draft" | "returned">; reviewer: string | null }) {
  const { app, client, request, fail } = useApplication(), t = useT("onboarding"), navigate = useNavigate(), { locale } = useLocale();
  const describe = useErrorDescription();
  const [workspaces, setWorkspaces] = useState<Workspaces | null>(null), [entering, setEntering] = useState(false), [error, setError] = useState<Error | null>(null);
  useEffect(() => {
    if (phase !== "approved") return;
    let alive = true;
    request<StartSummary>(api.start).then((value) => { if (alive) setWorkspaces(readWorkspaces(value.workspaces)); }).catch(() => { if (alive) setWorkspaces(null); });
    return () => { alive = false; };
  }, [phase]);
  const entry = phase === "approved" ? enterableWorkspace(app.clientId ?? undefined, workspaces) : null;
  async function enter(memberId: string) {
    setEntering(true); setError(null);
    try { await client.selectWorkspace(memberId); navigate("/workspace"); }
    catch (failure) { if (failure instanceof RequestFailure && failure.status === 401) fail(failure); else setError(failure as Error); }
    finally { setEntering(false); }
  }
  const review = app.review;
  const facts: [string, string, boolean?][] = [];
  let tone: "done" | "active" | "attention" | "error" | "todo" = "active", status = "", title = "", body = "";
  let actions = <></>;
  if (phase === "in_review") {
    status = t("result.in_review_status"); title = reviewer ? t("result.in_review_title", { name: reviewer }) : t("result.in_review_title_generic");
    body = app.submittedAt ? t("result.in_review_body_dated", { date: formatDateTime(app.submittedAt) }) : t("result.in_review_body");
    facts.push([t("result.application_no"), app.applicationNo, true]);
    if (reviewer) facts.push([t("result.reviewer"), reviewer]);
    if (client.current()?.context.emailMasked) facts.push([t("result.submitted_by"), client.current()!.context.emailMasked]);
    // 审核中不可撤回（SM-A 只允许 DRAFT / RETURNED）；与 C4 原型不同。
    actions = <Link className="secondary" to={taskPath(app.applicationId, "review")}>{t("result.view_submitted")}</Link>;
  } else if (phase === "approved") {
    tone = "done"; status = t("result.approved_status"); title = t("result.approved_title");
    body = entry ? t("result.approved_body_ready") : t("result.approved_body_pending");
    if (review?.decidedAt) facts.push([t("result.approved_at"), formatDateTime(review.decidedAt)]);
    facts.push([t("result.application_no"), app.applicationNo, true]);
    if (reviewer) facts.push([t("result.reviewer"), reviewer]);
    actions = entry ? <button type="button" className="primary" disabled={entering} onClick={() => void enter(entry.memberId)}>
      <Busy active={entering} label={t("workspace.entering")}>{t("workspace.enter")}</Busy></button> : <></>;
  } else if (phase === "rejected") {
    tone = "error"; status = t("result.rejected_status"); title = t("result.rejected_title");
    body = reviewer ? t("result.rejected_body", { name: reviewer }) : t("result.rejected_body_generic");
    if (review?.publicNote) facts.push([t("result.reason"), review.publicNote]);
    if (review?.decidedAt) facts.push([t("result.decided_at"), formatDateTime(review.decidedAt)]);
    facts.push([t("result.application_no"), app.applicationNo, true]);
  } else if (phase === "expired") {
    tone = "error"; status = t("result.expired_status"); title = t("result.expired_title");
    body = review?.dueTime ? t("result.expired_body_dated", { date: formatDate(review.dueTime, { dateStyle: "medium" }) }) : t("result.expired_body");
    if (review?.dueTime) facts.push([t("result.due_at"), formatDateTime(review.dueTime)]);
    const open = (review?.errors ?? []).map((item) => describe(item).where);
    if (open.length) facts.push([t("result.open_items"), new Intl.ListFormat(locale, { style: "long", type: "conjunction" }).format(open)]);
    actions = <Link className="primary" to="/onboarding/new">{t("result.start_again")}</Link>;
  } else {
    tone = "todo"; status = t("result.withdrawn_status"); title = t("result.withdrawn_title"); body = t("result.withdrawn_body");
    if (app.withdrawnAt) facts.push([t("result.withdrawn_at"), formatDateTime(app.withdrawnAt)]);
    if (client.current()?.context.emailMasked) facts.push([t("result.withdrawn_by"), client.current()!.context.emailMasked]);
    actions = <Link className="primary" to="/onboarding/new">{t("result.start_again")}</Link>;
  }
  return <>
    <StatusDot tone={tone}>{status}</StatusDot>
    <h1>{title}</h1>
    <p className="ob-intro">{body}</p>
    <Failure error={error} />
    <dl className="ob-facts">{facts.map(([name, value, mono]) => <div key={name}><dt>{name}</dt><dd className={mono ? "mono" : undefined}>{value}</dd></div>)}</dl>
    <div className="ob-result-actions">{actions}</div>
  </>;
}

/** 退回首页（N2）：状态写进标题区；问题逐行"去处理"；右栏审核记录与撤回。 */
function Returned({ reviewer }: { reviewer: string | null }) {
  const { app } = useApplication(), t = useT("onboarding"), describe = useErrorDescription(), navigate = useNavigate();
  const errors = app.review?.errors ?? [], due = app.review?.dueTime ?? app.reviewDueTime ?? null, left = daysLeft(due);
  const overdue = left !== null && left < 0;
  return <div className="ob-home">
    <main className="ob-home-main" id="main">
      <StatusDot tone={overdue ? "error" : "attention"}>{due ? (overdue ? t("returned.overdue") : t("returned.status_due", { date: formatDate(due, { dateStyle: "medium" }), days: Math.max(left ?? 0, 0) })) : t("returned.status")}</StatusDot>
      <h1>{reviewer ? t("returned.title", { name: reviewer }) : t("returned.title_generic")}</h1>
      <p className="ob-intro">{errors.length ? t("returned.body", { count: errors.length }) : t("returned.body_no_items")}</p>
      {app.review?.publicNote && <p className="ob-note">{app.review.publicNote}</p>}
      {errors.length > 0 && <ul className="ob-issues">{errors.map((error, index) => {
        const item = describe(error);
        return <li key={index}><div><strong>{item.where}</strong><small>{item.what}</small></div>
          <button type="button" className="text-button" onClick={() => navigate(taskPath(app.applicationId, item.anchor.task), { state: { focus: item.id } })}>{t("returned.go_fix")} ›</button></li>;
      })}</ul>}
      <div className="ob-result-actions">
        {errors.length > 0 && !overdue && <Link className="primary" to={fixPath(app.applicationId)}>{t("returned.start")}</Link>}
        <Link className="secondary" to={taskPath(app.applicationId, "review")}>{t("returned.view_all")}</Link>
      </div>
      <div className="ob-mobile-only"><h2 className="ob-side-title">{t("returned.quit_title")}</h2><WithdrawAction /></div>
    </main>
    <aside className="ob-home-side">
      <h2 className="ob-side-title">{t("returned.history")}</h2>
      <ol className="ob-timeline">
        <li className="current"><strong>{t("returned.history_returned")}</strong><small>{[reviewer ?? "", app.review?.decidedAt ? formatDateTime(app.review.decidedAt) : ""].filter(Boolean).join(" · ")}</small></li>
        {app.submittedAt && <li><strong>{t("returned.history_submitted")}</strong><small>{formatDateTime(app.submittedAt)}</small></li>}
      </ol>
      <h2 className="ob-side-title">{t("returned.quit_title")}</h2>
      <WithdrawAction />
    </aside>
  </div>;
}
