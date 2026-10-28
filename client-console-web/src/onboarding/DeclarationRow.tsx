import { useState } from "react";
import { useT } from "../i18n/react.tsx";
import { useApplication } from "./context.tsx";
import { declarationBody, documentReadable, isDocumentOutdated, legalTextKey, paragraphs } from "./declarations.ts";
import { api } from "./model.ts";
import type { DeclarationDef } from "./model.ts";
import { declarationState } from "./progress.ts";
import { Busy, Drawer, StatusDot, useSchemaText } from "./ui.tsx";

/**
 * 声明写入：每次勾选 / 取消追加一条记录；带文档的声明附所读版本
 * <li>条款版本已更新（422 DECLARATION_DOCUMENT_OUTDATED）时重新读取 Schema，让页面显示新版本后再确认。</li>
 */
export function useDeclare() {
  const { app, request, reload, reloadSchema, touch } = useApplication();
  const [busy, setBusy] = useState<string | null>(null), [error, setError] = useState<Error | null>(null);
  async function declare(item: DeclarationDef, value: boolean) {
    setBusy(item.declarationType); setError(null);
    try {
      await request(api.declarations(app.applicationId), { method: "POST", key: crypto.randomUUID(), body: declarationBody(item, value) });
      touch();
      await reload();
    } catch (failure) {
      setError(failure as Error);
      if (isDocumentOutdated(failure)) await reloadSchema().catch(() => undefined);
    } finally { setBusy(null); }
  }
  return { busy, error, declare };
}

/**
 * 一条声明：复选框 + 陈述；带文档的声明右侧"阅读"打开侧边面板
 * <li>已失效的声明复选框回到未选，下方提示"确认后修改了资料"，重新勾选即重新确认。</li>
 * <li>所附文档该版本的正文未随语言包发布时不能勾选，下方提示暂不可读；已确认的仍可取消。</li>
 */
export function DeclarationRow({ item, busy, onDeclare, className, compactStale }: {
  item: DeclarationDef; busy: boolean; onDeclare: (item: DeclarationDef, value: boolean) => void; className?: string;
  /** 页面顶部已有失效汇总时，行内只标"需重新确认"。 */
  compactStale?: boolean;
}) {
  const { app } = useApplication(), t = useT("onboarding"), label = useSchemaText();
  const [reading, setReading] = useState(false);
  const state = declarationState(app, item.declarationType), statement = label(item.labelKey, item.declarationType);
  const readable = documentReadable(item, t), noteId = "declaration-" + item.declarationType + "-note";
  return <div className={"ob-declaration" + (className ? " " + className : "") + (state === "stale" ? " stale" : "")} id={"declaration-" + item.declarationType}>
    <label className="checkline">
      <input type="checkbox" checked={state === "confirmed"} disabled={!app.editable || busy || (!readable && state !== "confirmed")}
        aria-describedby={state === "stale" || !readable ? noteId : undefined}
        onChange={(event) => onDeclare(item, event.target.checked)} />
      <span>{statement}</span>
    </label>
    {item.documentVersion && <button type="button" className="text-button ob-declaration-read" onClick={() => setReading(true)}
      aria-haspopup="dialog">{t("declarations.read")}</button>}
    {!readable ? <p className="ob-declaration-stale ob-muted" id={noteId}>{t("declarations.text_unavailable")}</p>
      : state === "stale" && <p className="ob-declaration-stale" id={noteId}>
        <StatusDot tone="attention">{t(compactStale ? "declarations.confirm_again" : "declarations.stale_row")}</StatusDot></p>}
    {reading && item.documentVersion && <LegalDocumentDrawer item={item} version={item.documentVersion} confirmed={state === "confirmed"}
      busy={busy} onClose={() => setReading(false)} onConfirm={() => { setReading(false); onDeclare(item, true); }} />}
  </div>;
}

/** 条款 / 告知阅读面板：标题、"草案"标识与版本、正文；未确认时可在面板内直接确认。 */
function LegalDocumentDrawer({ item, version, confirmed, busy, onClose, onConfirm }: {
  item: DeclarationDef; version: string; confirmed: boolean; busy: boolean; onClose: () => void; onConfirm: () => void;
}) {
  const { app } = useApplication(), t = useT("onboarding"), label = useSchemaText();
  const titleKey = legalTextKey(item.declarationType, version, "title"), bodyKey = legalTextKey(item.declarationType, version, "body");
  const title = t(titleKey), body = t(bodyKey);
  // 该版本的正文未随语言包发布时不显示键名，提示暂不可读，也不提供确认。
  const available = documentReadable(item, t);
  return <Drawer title={available ? title : label(item.labelKey, item.declarationType)} onClose={onClose} footer={<>
    <button type="button" className="secondary" onClick={onClose}>{t("drawer.close")}</button>
    {app.editable && !confirmed && available && <button type="button" className="primary" onClick={onConfirm} disabled={busy}>
      <Busy active={busy} label={t("save.saving")}>{t("declarations.agree")}</Busy></button>}
  </>}>
    <p className="ob-legal-meta"><span className="ob-draft-mark">{t("declarations.draft")}</span>
      <span className="mono">{t("declarations.version", { version })}</span></p>
    {available
      ? <div className="ob-legal-text">{paragraphs(body).map((text, index) => <p key={index}>{text}</p>)}</div>
      : <p className="ob-muted">{t("declarations.text_unavailable")}</p>}
  </Drawer>;
}
