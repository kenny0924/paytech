import { useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { RequestFailure } from "../auth.ts";
import { flatten } from "../onboarding.ts";
import { formatDate } from "../i18n/index.ts";
import { useT } from "../i18n/react.tsx";
import { appPath, taskPath, useApplication } from "./context.tsx";
import { api, problemErrors, readCheck } from "./model.ts";
import type { RequirementError } from "./model.ts";
import { declared } from "./progress.ts";
import { findField, taskBlocks } from "./schema.ts";
import type { FieldBlock } from "./schema.ts";
import { DocumentSlot } from "./DocumentSlot.tsx";
import { SchemaFields, useDraftForm } from "./FormTask.tsx";
import { useDisplayValue, useErrorLinks } from "./ReviewTask.tsx";
import { useErrorDescription } from "./Results.tsx";
import { taskLabelKey } from "./Shell.tsx";
import { Busy, ErrorSummary, Failure, useSchemaText } from "./ui.tsx";

/**
 * 补件视图（N2 聚焦模式）：只列退回的问题，逐项处理；最后一项的主按钮是"保存并重新提交"
 * <li>处理完的问题打 √：字段值改变、槽位有新文件、声明已勾选，或人员问题已打开处理过。</li>
 * <li>问题之间切换淡出 110ms → 淡入 + 上移 6px 240ms；进入时焦点在 h1，状态变化经 aria-live 播报。</li>
 */
export function Remediation() {
  const { app, schema, request, reload, touch } = useApplication(), t = useT("onboarding"), label = useSchemaText();
  const navigate = useNavigate(), describe = useErrorDescription(), links = useErrorLinks(), display = useDisplayValue();
  const errors = app.review?.errors ?? [];
  const original = useRef({ values: flatten(app.values), documents: new Set(app.documents.map((doc) => doc.documentId)) });
  const [index, setIndex] = useState(0), [visited, setVisited] = useState<Set<number>>(new Set());
  const [busy, setBusy] = useState(false), [error, setError] = useState<Error | null>(null), [blocking, setBlocking] = useState<RequirementError[]>([]);
  const fieldBlocks: FieldBlock[] = taskBlocks(schema, "profile", "PRIMARY").concat(taskBlocks(schema, "address", "PRIMARY"));
  const form = useDraftForm(app.values, schema, fieldBlocks, async (body) => {
    await request(api.draft(app.applicationId), { method: "PATCH", body }); touch();
  });
  const items = errors.map((item) => describe(item));
  function processed(position: number): boolean {
    const item = items[position];
    if (!item) return false;
    const { anchor } = item;
    if (anchor.declarationType) return declared(app, anchor.declarationType);
    if (anchor.requirementCode) return app.documents.some((doc) => doc.requirementCode === anchor.requirementCode && !original.current.documents.has(doc.documentId));
    if (anchor.fieldKey && !anchor.personId) {
      const field = findField(schema, anchor.fieldKey);
      return !!field && display(field, form.values, schema) !== display(field, original.current.values, schema);
    }
    return visited.has(position) || position === index;
  }
  const done = items.filter((_, position) => processed(position)).length;
  const current = items[index], error_ = errors[index];
  async function move(next: number) {
    setError(null);
    try { await form.flush(); } catch (failure) { setError(failure as Error); return; }
    setVisited((previous) => new Set(previous).add(index));
    setIndex(next);
  }
  async function resubmit() {
    setBusy(true); setError(null); setBlocking([]);
    try {
      if (!(await form.flush())) return;
      await reload();
      const check = readCheck(await request(api.submissionCheck(app.applicationId)), t("submit.check_unavailable"));
      if (!check.ready) { setBlocking(check.errors); return; }
      await request(api.submissions(app.applicationId), { method: "POST" });
      await reload();
      navigate(appPath(app.applicationId), { replace: true });
    } catch (failure) {
      const listed = failure instanceof RequestFailure ? problemErrors(failure.problem) : [];
      if (listed.length) setBlocking(listed);
      else if (failure instanceof RequestFailure && failure.code === "SUBMISSION_INCOMPLETE") {
        // 正式提交的 422 可能不带逐项问题：重新核对一次取需求级错误。
        try { setBlocking(readCheck(await request(api.submissionCheck(app.applicationId)), t("submit.check_unavailable")).errors); }
        catch (again) { setError(again as Error); }
      } else setError(failure as Error);
    } finally { setBusy(false); }
  }
  const field = current?.anchor.fieldKey && !current.anchor.personId ? findField(schema, current.anchor.fieldKey) : undefined;
  const blocks = field ? fieldBlocks.map((block) => ({ ...block, fields: block.fields.filter((item) => item === field) })).filter((block) => block.fields.length) : [];
  const slot = current?.anchor.requirementCode ? schema.materials.find((item) => item.requirementCode === current.anchor.requirementCode) : undefined;
  const last = index === items.length - 1;
  return <div className="ob-focus">
    <header className="ob-focus-bar">
      <Link to={appPath(app.applicationId)} className="ob-close" aria-label={t("fix.close")}>×</Link>
      <strong>{t("fix.title")}</strong>
      <span className="ob-muted" aria-live="polite">{[t("fix.progress", { done, total: items.length }),
        app.review?.dueTime ? t("fix.due", { date: formatDate(app.review.dueTime, { dateStyle: "medium" }) }) : ""].filter(Boolean).join(" · ")}</span>
    </header>
    <div className="ob-focus-layout">
      <ol className="ob-fix-list">{items.map((item, position) => <li key={position}>
        <button type="button" className={position === index ? "current" : ""} onClick={() => void move(position)} aria-current={position === index ? "step" : undefined}>
          <span className={"ob-mark" + (processed(position) ? " ok" : "")} aria-hidden="true">{processed(position) ? "✓" : "○"}</span>
          <span><small>{t(taskLabelKey(item.anchor.task, app.clientType))}</small>{item.where.split(" · ").slice(1).join(" · ") || item.where}</span>
        </button></li>)}</ol>
      {current && <main className="ob-fix ob-enter" key={index} id="main">
        <ErrorSummary items={links(blocking)} />
        <p className="ob-eyebrow">{t("fix.counter", { index: index + 1, total: items.length, task: t(taskLabelKey(current.anchor.task, app.clientType)) })}</p>
        <h1 tabIndex={-1}>{field ? t("fix.title_field", { name: label(field.labelKey, field.fieldKey) })
          : slot ? t("fix.title_document") : current.anchor.declarationType ? t("fix.title_declaration") : t("fix.title_person")}</h1>
        <section className="ob-review-note"><h2>{t("fix.reviewer_note")}</h2><p>{current.what}</p></section>
        <Failure error={error} />
        {field && <>
          <SchemaFields prefix="f" blocks={blocks} schema={schema} form={form} disabled={!app.editable} countryCode={app.jurisdictionCode} headings={false} />
          {display(field, original.current.values, schema) && <p className="ob-original">{t("fix.original")} <span className="mono">{display(field, original.current.values, schema)}</span></p>}
        </>}
        {slot && <DocumentSlot slot={slot} subjectPartyId={current.anchor.personId ?? error_?.subjectPartyId ?? app.subjectPartyId} onChanged={reload} />}
        {current.anchor.declarationType && <p>{t("fix.declaration_hint")}</p>}
        {!field && !slot && !current.anchor.declarationType && <p>{t("fix.person_hint")}</p>}
        <div className="ob-fix-actions">
          <Link to={taskPath(app.applicationId, current.anchor.task)} state={{ focus: current.id }}>{t("fix.open_full")}</Link>
          <span>
            {index > 0 && <button type="button" className="secondary" onClick={() => void move(index - 1)}>{t("fix.previous")}</button>}
            {last ? <button type="button" className="primary" disabled={busy || done < items.length} onClick={() => void resubmit()}
              aria-describedby={done < items.length ? "fix-remaining" : undefined}>
              <Busy active={busy} label={t("submit.submitting")}>{t("fix.resubmit")}</Busy></button>
              : <button type="button" className="primary" onClick={() => void move(index + 1)}>{t("fix.next")}</button>}
          </span>
        </div>
        {last && done < items.length && <p className="ob-muted" id="fix-remaining">{t("fix.remaining", { count: items.length - done })}</p>}
      </main>}
    </div>
  </div>;
}
