import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ApiKeysPanel, WebhooksPanel } from "./DeveloperPages.tsx";
import { TeamPanel } from "./TeamPage.tsx";
import { teamAccess } from "./team.ts";
import { developerMenu } from "./developer.ts";
import { RequestFailure } from "./auth.ts";
import type { createAuthClient } from "./auth.ts";
import {
  homeNoticeText,
  readHome,
  workspaceName,
  workspaceSubtitle,
} from "./workspace.ts";
import type { WorkspaceHome } from "./workspace.ts";
import { enumLabel } from "./enum-label.ts";
import { formatDate, translator } from "./i18n/index.ts";
import { useT } from "./i18n/react.tsx";
import { LocaleSwitcher } from "./LocaleSwitcher.tsx";

const tw = translator("workspace");

type Client = ReturnType<typeof createAuthClient>;


/**
 * 工作区外壳与首页（租户上下文）
 * <li>账号上下文访问或服务端要求租户上下文时回到开始页；会话失效回登录。</li>
 * <li>切换工作区回到开始页列表重新选择；返回账号新建账号上下文后回到入驻申请。</li>
 */
export function WorkspacePage({ client, section = "home" }: { client: Client; section?: "home" | "api-keys" | "webhooks" | "team" }) {
  const navigate = useNavigate();
  const grant = useSyncExternalStore(client.subscribe, client.current);
  const [home, setHome] = useState<WorkspaceHome | null>(null),
    [error, setError] = useState<Error | null>(null),
    [busy, setBusy] = useState<"account" | "logout" | null>(null),
    [menuOpen, setMenuOpen] = useState(false);
  const alert = useRef<HTMLDivElement>(null), t = useT("workspace"), tc = useT("common"), ta = useT("auth");
  const contextType = grant?.context.contextType;

  useEffect(() => {
    // 刷新降级或返回账号后，租户页面不再可用。
    if (contextType === "ACCOUNT" && busy === null) navigate("/onboarding", { replace: true });
  }, [contextType, busy, navigate]);

  useEffect(() => {
    let alive = true;
    client.request<unknown>("/workspace/v1/home").then((value) => {
      if (!alive) return;
      const parsed = readHome(value);
      if (parsed) setHome(parsed);
      else setError(new Error(tw("error.home_read_retry")));
    }).catch((failure) => {
      if (!alive) return;
      if (failure instanceof RequestFailure && failure.status === 401) {
        client.clear();
        navigate("/login", { replace: true });
      } else if (failure instanceof RequestFailure && failure.code === "CLIENT_WORKSPACE_CONTEXT_REQUIRED") {
        navigate("/onboarding", { replace: true });
      } else setError(failure instanceof Error ? failure : new Error(tw("error.home_read")));
    });
    return () => {
      alive = false;
    };
  }, []);

  useEffect(() => {
    if (error) alert.current?.focus();
  }, [error]);

  async function backToAccount() {
    if (busy) return;
    setBusy("account");
    setError(null);
    try {
      await client.backToAccount();
      navigate("/onboarding", { replace: true });
    } catch (failure) {
      if (failure instanceof RequestFailure && failure.status === 401) {
        client.clear();
        navigate("/login", { replace: true });
      } else setError(failure instanceof Error ? failure : new Error(tw("error.back_failed")));
    } finally {
      setBusy(null);
    }
  }
  async function logout() {
    if (busy) return;
    setBusy("logout");
    setError(null);
    try {
      await client.logout();
      navigate("/login", { replace: true });
    } catch (failure) {
      setError(failure instanceof Error ? failure : new Error(tw("error.logout_failed")));
    } finally {
      setBusy(null);
    }
  }

  const name = home ? workspaceName(home.workspace) : t("default_name");
  const capabilities = home?.capabilities ?? [];
  const menu = developerMenu(capabilities);
  const team = teamAccess(capabilities);
  const allowed = section === "home" || (section === "team" ? team.visible : menu.some((item) => item.key === section));
  const subtitle = home ? workspaceSubtitle(home.workspace) : "";
  return (
    <div className="workspace-shell">
      <aside className={"workspace-side" + (menuOpen ? " open" : "")}>
        <div className="workspace-identity">
          <span className="console-brand">PAYTECH</span>
          <strong title={name}>{name}</strong>
          {home && subtitle && <small>{subtitle}</small>}
          <button className="secondary workspace-menu" aria-expanded={menuOpen} onClick={() => setMenuOpen((open) => !open)}>
            {t("menu")}
          </button>
        </div>
        <nav className="workspace-nav" aria-label={t("nav.label")}>
          <Link to="/workspace" aria-current={section === "home" ? "page" : undefined} onClick={() => setMenuOpen(false)}>{t("nav.home")}</Link>
          {team.visible && (
            <Link to="/workspace/team" aria-current={section === "team" ? "page" : undefined} onClick={() => setMenuOpen(false)}>{t("nav.team")}</Link>
          )}
          {menu.length > 0 && <span className="nav-group">{t("nav.developer")}</span>}
          {menu.map((item) => (
            <Link key={item.key} to={item.path} aria-current={section === item.key ? "page" : undefined} onClick={() => setMenuOpen(false)}>
              {t("nav." + item.key.replace("-", "_"))}
            </Link>
          ))}
        </nav>
        <div className="workspace-actions">
          <Link to="/onboarding" onClick={() => setMenuOpen(false)}>{t("action.switch")}</Link>
          <button className="text-button" disabled={busy !== null} onClick={() => void backToAccount()}>
            {busy === "account" ? t("action.returning") : t("action.back_to_account")}
          </button>
          <button className="text-button" disabled={busy !== null} onClick={() => void logout()}>
            {busy === "logout" ? ta("action.signing_out") : t("action.sign_out")}
          </button>
          <LocaleSwitcher />
        </div>
      </aside>
      <main className="workspace-main">
        {error && (
          <div className="notice error" role="alert" tabIndex={-1} ref={alert}>
            {error.message}
            {error instanceof RequestFailure && error.requestId && <small>{tc("request_id", { requestId: error.requestId })}</small>}
          </div>
        )}
        {!home && !error && <p role="status">{t("loading")}</p>}
        {home && section !== "home" && !allowed && <p className="notice error" role="alert">{t("no_access")}</p>}
        {home && section === "api-keys" && allowed && <ApiKeysPanel client={client} canManage={capabilities.includes("API_CREDENTIAL_MANAGE")} />}
        {home && section === "webhooks" && allowed && <WebhooksPanel client={client} canManage={capabilities.includes("WEBHOOK_MANAGE")} />}
        {home && section === "team" && allowed && <TeamPanel client={client} canManage={team.canManage} />}
        {home && section === "home" && (
          <>
            <div className="page-heading">
              <div>
                <h1>{name}</h1>
                {subtitle && <p>{subtitle}</p>}
              </div>
              {home.workspace.clientStatus && (
                <span className="status">{enumLabel("client_status", home.workspace.clientStatus) ?? home.workspace.clientStatus}</span>
              )}
            </div>
            <p className="notice" role="note">
              {homeNoticeText(home.notice)}
            </p>
            <dl className="account workspace-profile">
              <dt>{home.workspace.clientType === "ORGANIZATION" ? t("field.incorporation_country") : t("field.residence_country")}</dt>
              <dd>{home.profile.country ?? "—"}</dd>
              <dt>{t("field.business_model")}</dt>
              <dd>{home.profile.businessModelCode ? enumLabel("business_model", home.profile.businessModelCode) ?? home.profile.businessModelCode : "—"}</dd>
              <dt>{t("field.approved_at")}</dt>
              <dd>{formatDate(home.profile.approvedAt)}</dd>
            </dl>
          </>
        )}
      </main>
    </div>
  );
}
