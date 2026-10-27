import { useEffect, useRef, useState } from "react";
import { RequestFailure } from "../auth.ts";
import { downloadUrl, newUpload, readCheckpoint, runUpload } from "../onboarding.ts";
import type { Upload } from "../onboarding.ts";
import { useLocale, useT } from "../i18n/react.tsx";
import { useApplication } from "./context.tsx";
import { api } from "./model.ts";
import type { DocumentRef, MaterialSlot } from "./model.ts";
import { fileKind, fileProblem, fileSize, progressTransport, uploadKeyPrefix, uploads } from "./upload.ts";
import { FieldShell, InlineConfirm, StatusDot, useSchemaText } from "./ui.tsx";
import { dateRules, documentDateIssues, today } from "./documentDates.ts";
import type { DateIssues } from "./documentDates.ts";
import { issueText } from "./draft.ts";
import { formatDate } from "../i18n/index.ts";

type Flight = { phase: "uploading" | "checking" | "failed"; progress: number; name: string; size: number; error: string | null };

/** 材料槽标题：先找按要求编码登记的文案，再退到第一种文件类型的文案。 */
export function useSlotTitle() {
  const label = useSchemaText();
  return (slot: MaterialSlot) => {
    const byType = label("onboarding.material." + (slot.documentTypes[0] ?? slot.requirementCode).toLowerCase(), slot.documentTypes[0] ?? slot.requirementCode);
    return label("onboarding.requirement." + slot.requirementCode.toLowerCase(), byType);
  };
}

/**
 * 一个材料槽的上传状态机（C3）：未上传 → 上传中（进度 + 取消）→ 检查中（Platform 扫描）→ 已完成；失败可重试或删除
 * <li>文件直传文件服务，扫描通过后才关联到申请；关联只提交文件服务地址 `/files/v1/{fileId}`。</li>
 * <li>只在本标签页 sessionStorage 保存无敏感内容的恢复坐标，刷新后从"检查中"继续。</li>
 * <li>替换：新文件关联成功后再删除旧引用，避免中途失败留下空槽。</li>
 * <li>选定文件后先填签发日期 / 到期日（按槽位时效与到期要求校验），再开始上传；日期随关联提交。</li>
 */
export function DocumentSlot({ slot, subjectPartyId, onChanged, compact }: {
  slot: MaterialSlot; subjectPartyId: string; onChanged: () => Promise<unknown>; compact?: boolean;
}) {
  const { app, request, client, touch } = useApplication(), t = useT("onboarding"), { locale } = useLocale();
  const title = useSlotTitle(), label = useSchemaText();
  const storageKey = uploadKeyPrefix(client.current()?.context.accountId, app.applicationId) + subjectPartyId + ":" + slot.requirementCode;
  const documents = app.documents.filter((doc) => doc.subjectPartyId === subjectPartyId && doc.requirementCode === slot.requirementCode);
  const [documentType, setDocumentType] = useState(slot.documentTypes[0] ?? slot.requirementCode);
  const [flight, setFlight] = useState<Flight | null>(null), [dragging, setDragging] = useState(false);
  const state = useRef<Upload | null>(null), file = useRef<File | null>(null), abort = useRef<AbortController | null>(null);
  const replacing = useRef<DocumentRef[]>([]), input = useRef<HTMLInputElement>(null), alive = useRef(true);
  const [pending, setPending] = useState<{ file: File; replace: DocumentRef[] } | null>(null);
  const [dates, setDates] = useState({ issueDate: "", expiryDate: "" }), [dateErrors, setDateErrors] = useState<DateIssues>({});
  const rules = dateRules(slot);
  // 正在传输或检查时登记，材料页据此提示等待；卸载后未完成的检查由申请页后台续传。
  useEffect(() => { uploads.setActive(storageKey, flight?.phase === "uploading" || flight?.phase === "checking"); }, [flight?.phase]);
  useEffect(() => {
    alive.current = true;
    uploads.claim(storageKey);
    const resume = readCheckpoint(safeGet(storageKey), app.applicationId);
    if (resume && resume.subjectPartyId === subjectPartyId && resume.requirementCode === slot.requirementCode) {
      state.current = resume;
      setFlight({ phase: "checking", progress: 1, name: "", size: 0, error: null });
      void advance();
    }
    return () => { alive.current = false; abort.current?.abort(); uploads.release(storageKey); };
  }, []);

  /** 选定文件：先校验类型与大小，通过后展示日期字段，等待确认上传。 */
  function choose(chosen: File, replace: DocumentRef[] = []) {
    const problem = fileProblem(chosen);
    if (problem) { setFlight({ phase: "failed", progress: 0, name: chosen.name, size: chosen.size, error: t("upload.problem_" + problem) }); return; }
    setFlight(null);
    setPending({ file: chosen, replace });
    setDates({ issueDate: "", expiryDate: "" });
    setDateErrors({});
  }
  /** 日期通过本地检查后开始上传。 */
  function confirmPending() {
    if (!pending) return;
    const issues = documentDateIssues(slot, dates.issueDate, dates.expiryDate);
    setDateErrors(issues);
    if (issues.issueDate || issues.expiryDate) {
      document.getElementById(dateId(issues.issueDate ? "issue" : "expiry"))?.focus();
      return;
    }
    const { file: chosen, replace } = pending;
    setPending(null);
    begin(chosen, replace, dates);
  }
  const dateId = (kind: "issue" | "expiry") => "date-" + kind + "-" + subjectPartyId + "-" + slot.requirementCode;

  function begin(chosen: File, replace: DocumentRef[], chosenDates: { issueDate: string; expiryDate: string }) {
    file.current = chosen;
    replacing.current = replace;
    state.current = { ...newUpload(app.applicationId, subjectPartyId, slot.requirementCode, documentType),
      ...(chosenDates.issueDate ? { issueDate: chosenDates.issueDate } : {}), ...(chosenDates.expiryDate ? { expiryDate: chosenDates.expiryDate } : {}) };
    setFlight({ phase: "uploading", progress: 0, name: chosen.name, size: chosen.size, error: null });
    void advance();
  }
  async function advance() {
    const upload = state.current;
    if (!upload) return;
    abort.current = new AbortController();
    const transport = progressTransport((ratio) => {
      if (alive.current) setFlight((previous) => previous && { ...previous, progress: ratio });
    }, abort.current.signal);
    try {
      while (alive.current) {
        await runUpload(upload, request, file.current ?? undefined, transport, (value) => {
          safeSet(storageKey, value.phase === "DONE" ? null : JSON.stringify(value));
          if (value.phase !== "PUT" && alive.current) setFlight((previous) => previous && { ...previous, phase: "checking", progress: 1 });
        });
        if (upload.phase === "DONE") break;
        await new Promise((resolve) => setTimeout(resolve, 2000));
      }
      if (!alive.current) return;
      for (const old of replacing.current) await request(api.document(app.applicationId, old.documentId), { method: "DELETE" });
      touch();
      await onChanged();
      if (alive.current) setFlight(null);
      state.current = null; file.current = null; replacing.current = [];
    } catch (failure) {
      if (!alive.current) return;
      const cancelled = failure instanceof RequestFailure && failure.status === 0 && abort.current?.signal.aborted;
      if (cancelled) { cancel(); return; }
      setFlight((previous) => ({ phase: "failed", progress: previous?.progress ?? 0, name: previous?.name ?? "", size: previous?.size ?? 0,
        error: failure instanceof Error ? failure.message : t("upload.failed") }));
    }
  }
  function cancel() {
    abort.current?.abort();
    safeSet(storageKey, null);
    state.current = null; file.current = null;
    setFlight(null);
  }
  async function remove(doc: DocumentRef) {
    await request(api.document(app.applicationId, doc.documentId), { method: "DELETE" });
    touch();
    await onChanged();
  }
  async function view(doc: DocumentRef) {
    const grant = await request<{ url: string; expiresAt: string }>("/onboarding-files/v1/files/" + doc.fileId + "/download", { method: "POST" });
    window.open(downloadUrl(grant), "_blank", "noopener,noreferrer");
  }
  const editable = app.editable;
  const status = flight?.phase === "uploading" ? <StatusDot tone="active">{t("upload.uploading", { percent: Math.round(flight.progress * 100) })}</StatusDot>
    : flight?.phase === "checking" ? <StatusDot tone="active">{t("upload.checking")}</StatusDot>
    : flight?.phase === "failed" ? <StatusDot tone="error">{t("upload.failed_short")}</StatusDot>
    : documents.length ? <StatusDot tone="done">{t("upload.done")}</StatusDot>
    : <StatusDot tone="todo">{slot.required ? t("upload.missing") : t("upload.not_uploaded")}</StatusDot>;
  const documentLabel = label("onboarding.material." + documentType.toLowerCase(), documentType);
  const pick = (replace: DocumentRef[] = []) => { if (input.current) { input.current.dataset.replace = replace.map((doc) => doc.documentId).join(","); input.current.click(); } };
  return <div className={"ob-slot" + (compact ? " compact" : "")} data-field={"slot-" + subjectPartyId + "-" + slot.requirementCode} id={"slot-" + subjectPartyId + "-" + slot.requirementCode}>
    <div className="ob-slot-head">
      <div><strong>{title(slot)}</strong>
        <small>{[!slot.required ? t("materials.if_applicable") : "", slot.maxAgeDays ? t("materials.max_age", { months: Math.max(1, Math.round(slot.maxAgeDays / 30)) }) : "",
          t("materials.formats")].filter(Boolean).join(" · ")}</small></div>
      {status}
    </div>
    {documents.map((doc) => <div className="ob-file" key={doc.documentId}>
      <span className="ob-kind">{fileKind(doc.file?.displayName ?? "", doc.file?.mimeType ?? "")}</span>
      <span className="ob-file-name">{doc.file?.displayName ?? t("materials.file_fallback")}{doc.file && <span className="ob-muted"> · {fileSize(doc.file.sizeBytes, locale)}</span>}</span>
      <span className="ob-file-actions">
        {doc.file?.allowedActions?.includes("DOWNLOAD") && <button type="button" className="text-button" onClick={() => void view(doc).catch(() => {})}>{t("materials.view")}</button>}
        {editable && !flight && !pending && <button type="button" className="text-button" onClick={() => pick([doc])}>{t("materials.replace")}</button>}
      </span>
      {(doc.issueDate || doc.expiryDate) && <span className="ob-file-dates">{[doc.issueDate ? t("materials.issued_on", { date: formatDate(doc.issueDate + "T00:00:00", { dateStyle: "medium" }) }) : "",
        doc.expiryDate ? t("materials.expires_on", { date: formatDate(doc.expiryDate + "T00:00:00", { dateStyle: "medium" }) }) : ""].filter(Boolean).join(" \u00b7 ")}</span>}
      {editable && !flight && !pending && <span className="ob-file-remove"><InlineConfirm trigger={t("action.remove")} body={t("materials.remove_confirm")}
        confirm={t("action.remove")} working={t("materials.removing")} onConfirm={() => remove(doc)} /></span>}
    </div>)}
    {flight && <div className={"ob-file" + (flight.phase === "failed" ? " failed" : "")}>
      <span className="ob-kind">{fileKind(flight.name, "")}</span>
      <span className="ob-file-name">{flight.name || t("materials.file_fallback")}{flight.size > 0 && <span className="ob-muted"> · {fileSize(flight.size, locale)}</span>}
        {flight.phase === "uploading" && <span className="ob-bar" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(flight.progress * 100)}>
          <i style={{ transform: "scaleX(" + flight.progress + ")" }} /></span>}
        {flight.error && <span className="ob-error-inline" role="alert">{flight.error}</span>}</span>
      <span className="ob-file-actions">
        {flight.phase === "uploading" && <button type="button" className="text-button" onClick={cancel}>{t("upload.cancel")}</button>}
        {flight.phase === "failed" && (state.current || file.current) && <button type="button" className="text-button" onClick={() => {
          setFlight((previous) => previous && { ...previous, phase: state.current?.phase === "PUT" ? "uploading" : "checking", error: null, progress: 0 });
          void advance();
        }}>{t("upload.retry")}</button>}
        {flight.phase === "failed" && <button type="button" className="text-button danger-text" onClick={cancel}>{t("action.remove")}</button>}
      </span>
    </div>}
    {pending && <div className="ob-pending ob-enter">
      <div className="ob-file">
        <span className="ob-kind">{fileKind(pending.file.name, pending.file.type)}</span>
        <span className="ob-file-name">{pending.file.name}<span className="ob-muted"> · {fileSize(pending.file.size, locale)}</span></span>
        <span />
      </div>
      <div className="ob-dates">
        {(["issue", "expiry"] as const).map((kind) => {
          const key = kind === "issue" ? "issueDate" : "expiryDate", issue = dateErrors[key], id = dateId(kind);
          const required = kind === "issue" ? rules.issueRequired : rules.expiryRequired;
          return <FieldShell key={kind} id={id} optional={!required} error={issue ? issueText(issue) : null}
            label={t(kind === "issue" ? "materials.issue_date_of" : "materials.expiry_date_of", { document: documentLabel })}>
            <input id={id} type="date" value={dates[key]} max={kind === "issue" ? today() : undefined} aria-invalid={!!issue}
              aria-describedby={issue ? id + "-error" : undefined}
              onChange={(event) => { setDates((previous) => ({ ...previous, [key]: event.target.value })); setDateErrors((previous) => ({ ...previous, [key]: undefined })); }} />
          </FieldShell>;
        })}
      </div>
      <div className="ob-pending-actions">
        <button type="button" className="secondary" onClick={() => setPending(null)}>{t("action.cancel")}</button>
        <button type="button" className="primary" onClick={confirmPending}>{t("upload.start")}</button>
      </div>
    </div>}
    {editable && !documents.length && !flight && !pending && <>
      {slot.documentTypes.length > 1 && <div className="ob-type-select">
        <label htmlFor={"type-" + subjectPartyId + "-" + slot.requirementCode}>{t("materials.document_type")}</label>
        <select id={"type-" + subjectPartyId + "-" + slot.requirementCode} value={documentType} onChange={(event) => setDocumentType(event.target.value)}>
          {slot.documentTypes.map((type) => <option key={type} value={type}>{label("onboarding.material." + type.toLowerCase(), type)}</option>)}
        </select></div>}
      <div className={"ob-drop" + (dragging ? " over" : "")} onDragOver={(event) => { event.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)} onDrop={(event) => { event.preventDefault(); setDragging(false); const chosen = event.dataTransfer.files[0]; if (chosen) choose(chosen); }}>
        {t("upload.drop")} <button type="button" className="text-button" onClick={() => pick()}>{t("upload.choose")}</button>
      </div>
    </>}
    <input ref={input} type="file" hidden accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png" onChange={(event) => {
      const chosen = event.target.files?.[0];
      const replace = (event.target.dataset.replace ?? "").split(",").filter(Boolean);
      event.target.value = "";
      if (chosen) choose(chosen, documents.filter((doc) => replace.includes(doc.documentId)));
    }} />
  </div>;
}

function safeGet(key: string): string | null { try { return sessionStorage.getItem(key); } catch { return null; } }
function safeSet(key: string, value: string | null) {
  try { if (value === null) sessionStorage.removeItem(key); else sessionStorage.setItem(key, value); } catch { /* 存储不可用时只在本页内恢复。 */ }
}
