import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { RequestFailure } from "./auth.ts";
import type { createAuthClient, EmailLink } from "./auth.ts";
import {
  clearStoredInvitation,
  defaultStartPath,
  invitationOpen,
  invitationProblem,
  invitationProblemText,
  invitationWorkspaceName,
  readAcceptance,
  readDetails,
  readPreview,
  readStoredInvitation,
} from "./invitation.ts";
import type { InvitationDetails, InvitationPreview } from "./invitation.ts";
import { displayDate, roleLabel } from "./team.ts";
import { translator } from "./i18n/index.ts";
import { useT } from "./i18n/react.tsx";
import { LocaleSwitcher } from "./LocaleSwitcher.tsx";

const ti = translator("invitation");

type Client = ReturnType<typeof createAuthClient>;
type Store = Pick<Storage, "getItem" | "setItem" | "removeItem"> | null;
type View =
  | { kind: "loading" }
  | { kind: "anonymous"; preview: InvitationPreview }
  | { kind: "ready"; details: InvitationDetails; accountEmail: string }
  | { kind: "mismatch"; emailMasked: string | null }
  | { kind: "problem"; text: string }
  | { kind: "error"; error: Error };

/**
 * 接受邀请页（公开，无工作区外壳）
 * <li>未登录：公开预览受邀邮箱（脱敏）、角色与到期时间，提供登录 / 注册；登录后经 return-to 回到本页。</li>
 * <li>已登录：租户上下文先切回账号上下文，再读取详情；邮箱一致才可接受，接受后回到开始页“我的工作区”。</li>
 * <li>链接只保存在本标签页的 sessionStorage，接受、失效或无效时清除。</li>
 */
export function InvitationPage({ client, store, memoryLink }: { client: Client; store: Store; memoryLink: EmailLink | null }) {
  const navigate = useNavigate();
  const [view, setView] = useState<View>({ kind: "loading" }), [busy, setBusy] = useState<"accept" | "logout" | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const link = useRef<EmailLink | null>(readStoredInvitation(store) ?? memoryLink);
  const heading = useRef<HTMLHeadingElement>(null), t = useT("invitation"), tc = useT("common"), ta = useT("auth");

  function close(text: string) {
    clearStoredInvitation(store);
    setView({ kind: "problem", text });
  }
  /** 业务错误码对应的页面状态；返回 false 表示需按通用失败处理。 */
  function handleProblem(failure: unknown, emailMasked: string | null): boolean {
    if (!(failure instanceof RequestFailure)) return false;
    const problem = invitationProblem(failure.code);
    if (problem === "invalid" || problem === "closed" || problem === "unavailable") {
      close(invitationProblemText(problem));
      return true;
    }
    if (problem === "mismatch") {
      setView({ kind: "mismatch", emailMasked });
      return true;
    }
    return false;
  }

  async function loadPreview(current: EmailLink) {
    try {
      const preview = readPreview(await client.publicRequest<unknown>("/invitations/v1/preview", current));
      if (!preview) throw new Error(ti("error.read_retry"));
      if (!invitationOpen(preview.status)) close(invitationProblemText("closed"));
      else setView({ kind: "anonymous", preview });
    } catch (failure) {
      if (!handleProblem(failure, null)) setView({ kind: "error", error: failure instanceof Error ? failure : new Error(ti("error.read")) });
    }
  }

  async function readDetailsInAccountContext(current: EmailLink, retried = false): Promise<InvitationDetails | null> {
    if (client.current()?.context.contextType === "TENANT") await client.backToAccount();
    try {
      return readDetails(await client.request<unknown>("/invitations/v1/details", { method: "POST", body: current }));
    } catch (failure) {
      if (!retried && failure instanceof RequestFailure && invitationProblem(failure.code) === "account-context") {
        await client.backToAccount();
        return readDetailsInAccountContext(current, true);
      }
      throw failure;
    }
  }

  async function load() {
    const current = link.current;
    setActionError(null);
    if (!current) {
      setView({ kind: "problem", text: invitationProblemText("missing") });
      return;
    }
    setView({ kind: "loading" });
    if (!client.current()) {
      try {
        await client.restore();
      } catch (failure) {
        if (failure instanceof RequestFailure && failure.status === 401) return loadPreview(current);
        setView({ kind: "error", error: failure instanceof Error ? failure : new Error(ti("error.session")) });
        return;
      }
      if (!client.current()) return loadPreview(current);
    }
    try {
      const details = await readDetailsInAccountContext(current);
      if (!details) throw new Error(ti("error.read_retry"));
      if (!details.emailMatches) setView({ kind: "mismatch", emailMasked: details.emailMasked });
      else if (!invitationOpen(details.status)) close(invitationProblemText("closed"));
      else setView({ kind: "ready", details, accountEmail: client.current()?.context.emailMasked ?? "—" });
    } catch (failure) {
      if (failure instanceof RequestFailure && failure.status === 401) {
        client.clear();
        return loadPreview(current);
      }
      if (!handleProblem(failure, null)) setView({ kind: "error", error: failure instanceof Error ? failure : new Error(ti("error.read")) });
    }
  }
  useEffect(() => { void load(); }, []);
  useEffect(() => { if (view.kind !== "loading") heading.current?.focus(); }, [view.kind]);

  async function accept() {
    const current = link.current;
    if (!current || busy || view.kind !== "ready") return;
    setBusy("accept");
    setActionError(null);
    try {
      if (client.current()?.context.contextType === "TENANT") await client.backToAccount();
      const result = readAcceptance(await client.request<unknown>("/invitations/v1/acceptance", { method: "POST", body: current }));
      if (!result) throw new Error(ti("error.accept_unconfirmed"));
      clearStoredInvitation(store);
      link.current = null;
      navigate(defaultStartPath, { replace: true });
    } catch (failure) {
      if (failure instanceof RequestFailure && failure.status === 401) {
        client.clear();
        navigate("/login", { replace: true });
      } else if (!handleProblem(failure, view.details.emailMasked)) {
        setActionError(failure instanceof Error ? failure.message : ti("error.accept_failed"));
      }
    } finally {
      setBusy(null);
    }
  }

  async function switchAccount() {
    if (busy) return;
    setBusy("logout");
    setActionError(null);
    try {
      await client.logout();
      navigate("/login", { replace: true });
    } catch (failure) {
      setActionError(failure instanceof Error ? failure.message : ti("error.logout_failed"));
    } finally {
      setBusy(null);
    }
  }

  const title = view.kind === "ready" ? t("title.accept") : view.kind === "mismatch" ? t("title.mismatch")
    : view.kind === "problem" ? t("title.problem") : t("title.join");
  return (
    <main className="auth-shell">
      <section className="auth invitation" aria-labelledby="invitation-title" aria-busy={view.kind === "loading"}>
        <span className="brand">PAYTECH</span>
        <header><h1 id="invitation-title" tabIndex={-1} ref={heading}>{title}</h1></header>
        {view.kind === "loading" && <p role="status">{t("loading")}</p>}
        {view.kind === "anonymous" && (
          <>
            <p>{t("anonymous.intro")}</p>
            <dl className="account">
              <dt>{t("field.role")}</dt><dd>{roleLabel(view.preview.roleCode)}</dd>
              <dt>{t("field.invited_email")}</dt><dd className="break">{view.preview.emailMasked}</dd>
              <dt>{t("field.expires")}</dt><dd>{displayDate(view.preview.expiresAt)}</dd>
            </dl>
            <Link className="primary link-button" to="/login">{t("action.login")}</Link>
            <Link className="secondary link-button" to="/register">{t("action.register")}</Link>
            <p className="hint">{t("anonymous.hint")}</p>
          </>
        )}
        {view.kind === "ready" && (
          <>
            <dl className="account">
              <dt>{t("field.workspace")}</dt><dd className="break">{invitationWorkspaceName(view.details)}</dd>
              <dt>{t("field.role")}</dt><dd>{roleLabel(view.details.roleCode)}</dd>
              <dt>{t("field.current_account")}</dt><dd className="break">{view.accountEmail}</dd>
              {view.details.status === "PENDING" && <><dt>{t("field.expires")}</dt><dd>{displayDate(view.details.expiresAt)}</dd></>}
            </dl>
            {view.details.status === "ACCEPTED" && <p className="notice">{t("ready.already_accepted")}</p>}
            {actionError && <p className="notice error" role="alert">{actionError}</p>}
            <button type="button" className="primary" disabled={busy !== null} onClick={() => void accept()}>
              {busy === "accept" ? tc("status.processing") : t("action.accept")}
            </button>
            <Link to={defaultStartPath}>{t("action.not_now")}</Link>
          </>
        )}
        {view.kind === "mismatch" && (
          <>
            <p className="notice error" role="alert">
              {view.emailMasked ? t("mismatch.with_email", { email: view.emailMasked }) : t("mismatch.without_email")}
            </p>
            {actionError && <p className="notice error" role="alert">{actionError}</p>}
            <button type="button" className="primary" disabled={busy !== null} onClick={() => void switchAccount()}>
              {busy === "logout" ? ta("action.signing_out") : t("action.switch_account")}
            </button>
          </>
        )}
        {view.kind === "problem" && (
          <>
            <p className="notice" role="alert">{view.text}</p>
            <Link className="primary link-button" to={client.current() ? defaultStartPath : "/login"}>
              {client.current() ? t("action.back_to_workspaces") : ta("action.go_to_login")}
            </Link>
          </>
        )}
        {view.kind === "error" && (
          <>
            <div className="notice error" role="alert">
              {view.error.message}
              {view.error instanceof RequestFailure && view.error.requestId && <small>{tc("request_id", { requestId: view.error.requestId })}</small>}
            </div>
            <button type="button" className="primary" onClick={() => void load()}>{t("action.retry")}</button>
          </>
        )}
        <footer><span>PAYTECH Client Console</span><LocaleSwitcher /></footer>
      </section>
    </main>
  );
}
