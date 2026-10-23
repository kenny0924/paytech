import { useEffect, useRef, useState } from "react";
import { RequestFailure } from "./auth.ts";
import type { createAuthClient } from "./auth.ts";
import {
  assignableRoles,
  canOperateMember,
  defaultInviteRole,
  displayDate,
  emailFailedText,
  expiryText,
  hasNextPage,
  invitationActions,
  invitationStatusLabel,
  invitationStatuses,
  memberStatusLabel,
  inviteFormIssues,
  readInvitation,
  readInvitationPage,
  readMember,
  readMemberPage,
  roleLabel,
  teamErrorText,
  teamQuery,
} from "./team.ts";
import type { InvitationStatus, TeamInvitation, TeamMember, TeamPage } from "./team.ts";
import { translator } from "./i18n/index.ts";
import { useT } from "./i18n/react.tsx";

const tt = translator("team");

type Client = ReturnType<typeof createAuthClient>;
const base = "/team/v1";

/** 失败说明：业务错误码用固定文案，其余用服务端说明。 */
function failureText(failure: unknown, fallback: string): string {
  if (failure instanceof RequestFailure) return teamErrorText(failure.code, failure.message || fallback);
  return failure instanceof Error ? failure.message : fallback;
}

function Pager({ page, onPage }: { page: TeamPage<unknown>; onPage: (page: number) => void }) {
  const t = useT("team");
  if (page.page <= 1 && !hasNextPage(page)) return null;
  return (
    <div className="actions team-pager">
      <button type="button" className="secondary" disabled={page.page <= 1} onClick={() => onPage(page.page - 1)}>{t("pager.previous")}</button>
      <span>{t("pager.page", { page: page.page })}</span>
      <button type="button" className="secondary" disabled={!hasNextPage(page)} onClick={() => onPage(page.page + 1)}>{t("pager.next")}</button>
    </div>
  );
}

/** 行操作容器：桌面直接显示；窄屏收进“更多”。 */
function RowActions({ label, children }: { label: string; children: React.ReactNode }) {
  const [open, setOpen] = useState(false), t = useT("team");
  return (
    <div className="team-row-actions">
      <button type="button" className="text-button team-more" aria-expanded={open} aria-label={t("row.more_label", { name: label })}
        onClick={() => setOpen((value) => !value)}>{t("row.more")}</button>
      <div className={"team-actions" + (open ? " open" : "")}>{children}</div>
    </div>
  );
}

type Pending =
  | { kind: "remove"; member: TeamMember }
  | { kind: "revoke"; invitation: TeamInvitation }
  | { kind: "resend"; invitation: TeamInvitation };

/**
 * 团队页（租户上下文）
 * <li>TEAM_READ：只读成员列表；TEAM_MANAGE：邀请区、发起邀请与全部行操作。</li>
 * <li>OWNER 与自己所在行不显示操作；权限最终以服务端拦截为准。</li>
 */
export function TeamPanel({ client, canManage }: { client: Client; canManage: boolean }) {
  const [members, setMembers] = useState<TeamPage<TeamMember> | null>(null), [memberError, setMemberError] = useState<string | null>(null);
  const [memberPage, setMemberPage] = useState(1);
  const [status, setStatus] = useState<InvitationStatus>("PENDING"), [invitationPage, setInvitationPage] = useState(1);
  const [invitations, setInvitations] = useState<TeamPage<TeamInvitation> | null>(null);
  const [invitationError, setInvitationError] = useState<string | null>(null);
  const [notice, setNotice] = useState<{ tone: "success" | "error"; text: string } | null>(null);
  const [inviting, setInviting] = useState(false), [form, setForm] = useState({ email: "", roleCode: defaultInviteRole as string });
  const [issues, setIssues] = useState<Record<string, string>>({}), [inviteError, setInviteError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null), [pending, setPending] = useState<Pending | null>(null);
  const emailInput = useRef<HTMLInputElement>(null), t = useT("team"), tc = useT("common");

  async function loadMembers(page = memberPage) {
    setMemberError(null);
    try {
      const value = readMemberPage(await client.request<unknown>(base + "/members?" + teamQuery(page)));
      if (!value) throw new Error(tt("members.load_failed"));
      setMembers(value);
      setMemberPage(page);
    } catch (failure) {
      setMembers(null);
      setMemberError(failureText(failure, tt("members.load_failed")));
    }
  }
  async function loadInvitations(nextStatus = status, page = invitationPage) {
    if (!canManage) return;
    setInvitationError(null);
    try {
      const value = readInvitationPage(await client.request<unknown>(base + "/invitations?" + teamQuery(page, nextStatus)));
      if (!value) throw new Error(tt("invitations.load_failed"));
      setInvitations(value);
      setStatus(nextStatus);
      setInvitationPage(page);
    } catch (failure) {
      setInvitations(null);
      setInvitationError(failureText(failure, tt("invitations.load_failed")));
    }
  }
  useEffect(() => {
    void loadMembers(1);
    void loadInvitations("PENDING", 1);
  }, [canManage]);
  useEffect(() => {
    if (inviting) emailInput.current?.focus();
  }, [inviting]);

  function openInvite() {
    setForm({ email: "", roleCode: defaultInviteRole });
    setIssues({});
    setInviteError(null);
    setInviting(true);
  }
  async function submitInvite() {
    if (busy) return;
    const found = inviteFormIssues(form);
    setIssues(found);
    setInviteError(null);
    if (Object.keys(found).length) {
      if (found.email) emailInput.current?.focus();
      return;
    }
    setBusy("invite");
    try {
      const value = readInvitation(await client.request<unknown>(base + "/invitations", {
        method: "POST", body: { email: form.email.trim(), roleCode: form.roleCode },
      }));
      setInviting(false);
      setNotice(value?.emailDelivery === "FAILED"
        ? { tone: "error", text: emailFailedText() }
        : { tone: "success", text: value ? tt("invite.sent_to", { email: value.emailMasked }) : tt("invite.sent") });
      await loadInvitations("PENDING", 1);
    } catch (failure) {
      const fields = failure instanceof RequestFailure ? { ...failure.fields } : {};
      const code = failure instanceof RequestFailure ? failure.code : "";
      if (code === "CLIENT_INVITATION_DUPLICATE" || code === "CLIENT_MEMBER_EXISTS") fields.email = teamErrorText(code, "");
      if (Object.keys(fields).length) setIssues(fields);
      else setInviteError(failureText(failure, tt("invite.failed")));
    } finally {
      setBusy(null);
    }
  }

  async function changeRole(member: TeamMember, roleCode: string) {
    if (busy || roleCode === member.roleCode) return;
    setBusy("role:" + member.memberId);
    setNotice(null);
    try {
      const value = readMember(await client.request<unknown>(base + "/members/" + encodeURIComponent(member.memberId) + "/role", {
        method: "POST", body: { roleCode },
      }));
      setNotice({ tone: "success", text: tt("role.changed", { email: member.emailMasked, role: roleLabel(value?.roleCode ?? roleCode) }) });
    } catch (failure) {
      setNotice({ tone: "error", text: tt("role.change_failed", { reason: failureText(failure, tt("retry_later")) }) });
    } finally {
      setBusy(null);
      await loadMembers();
    }
  }

  async function confirmPending() {
    if (!pending || busy) return;
    setBusy("confirm");
    setNotice(null);
    try {
      if (pending.kind === "remove") {
        await client.request<unknown>(base + "/members/" + encodeURIComponent(pending.member.memberId) + "/removal", { method: "POST" });
        setNotice({ tone: "success", text: tt("member.removed", { email: pending.member.emailMasked }) });
      } else if (pending.kind === "revoke") {
        await client.request<unknown>(base + "/invitations/" + encodeURIComponent(pending.invitation.invitationId) + "/revocation", { method: "POST" });
        setNotice({ tone: "success", text: tt("invitation.revoked", { email: pending.invitation.emailMasked }) });
      } else {
        const value = readInvitation(await client.request<unknown>(
          base + "/invitations/" + encodeURIComponent(pending.invitation.invitationId) + "/resend", { method: "POST" }));
        setNotice(value?.emailDelivery === "FAILED"
          ? { tone: "error", text: tt("invitation.resend_email_failed") }
          : { tone: "success", text: tt("invitation.resent", { email: pending.invitation.emailMasked }) });
      }
    } catch (failure) {
      setNotice({ tone: "error", text: failureText(failure, tt("action_failed")) });
    } finally {
      const kind = pending.kind;
      setPending(null);
      setBusy(null);
      if (kind === "remove") await loadMembers();
      else await loadInvitations();
    }
  }

  return (
    <section aria-labelledby="team-title" className="team">
      <div className="page-heading">
        <div>
          <h1 id="team-title">{t("title")}</h1>
          <p>{canManage ? t("intro.manage") : t("intro.read")}</p>
        </div>
        {canManage && <button type="button" className="primary compact" onClick={openInvite}>{t("invite.open")}</button>}
      </div>
      {notice && <p className={"notice" + (notice.tone === "error" ? " error" : " success")} role="status">{notice.text}</p>}

      <h2 className="section-title">{t("members.title")}</h2>
      {memberError && (
        <div className="notice error" role="alert">
          {memberError}
          <button type="button" className="text-button" onClick={() => void loadMembers()}>{t("retry")}</button>
        </div>
      )}
      {!members && !memberError && <p role="status">{t("members.loading")}</p>}
      {members && members.items.length === 0 && <p className="empty">{canManage ? t("members.empty_manage") : t("members.empty")}</p>}
      {members && members.items.length > 0 && (
        <ul className="team-list" aria-label={t("members.list_label")}>
          {members.items.map((member) => (
            <li className="team-row" key={member.memberId}>
              <div className="team-main">
                <strong className="break">{member.emailMasked}</strong>
                <small>{member.self ? t("members.role_self", { role: roleLabel(member.roleCode) }) : roleLabel(member.roleCode)}</small>
              </div>
              <span className={"team-status " + member.status.toLowerCase()}>
                {member.status === "ACTIVE" ? t("members.joined_on", { date: displayDate(member.joinedAt) }) : memberStatusLabel(member.status) ?? t("status_unknown")}
              </span>
              {canOperateMember(member, canManage) && (
                <RowActions label={member.emailMasked}>
                  <label className="team-role">
                    <span>{t("role.change")}</span>
                    <select value={member.roleCode} disabled={busy !== null} aria-label={t("role.change_label", { email: member.emailMasked })}
                      onChange={(event) => void changeRole(member, event.target.value)}>
                      {assignableRoles.map((role) => <option key={role.code} value={role.code}>{role.label}</option>)}
                    </select>
                  </label>
                  {busy === "role:" + member.memberId && <small role="status">{t("saving")}</small>}
                  <button type="button" className="text-button danger-text" disabled={busy !== null}
                    onClick={() => setPending({ kind: "remove", member })}>{t("action.remove")}</button>
                </RowActions>
              )}
            </li>
          ))}
        </ul>
      )}
      {members && <Pager page={members} onPage={(page) => void loadMembers(page)} />}

      {canManage && (
        <>
          <div className="team-section-head">
            <h2 className="section-title">{t("invitations.title")}</h2>
            <div className="team-filter" role="group" aria-label={t("invitations.status_filter")}>
              {invitationStatuses.map((code) => (
                <button key={code} type="button" className={"chip" + (status === code ? " active" : "")} aria-pressed={status === code}
                  onClick={() => void loadInvitations(code, 1)}>{invitationStatusLabel(code)}</button>
              ))}
            </div>
          </div>
          {invitationError && (
            <div className="notice error" role="alert">
              {invitationError}
              <button type="button" className="text-button" onClick={() => void loadInvitations()}>{t("retry")}</button>
            </div>
          )}
          {!invitations && !invitationError && <p role="status">{t("invitations.loading")}</p>}
          {invitations && invitations.items.length === 0 && (
            <p className="empty">{t("invitations.empty", { status })}</p>
          )}
          {invitations && invitations.items.length > 0 && (
            <ul className="team-list" aria-label={t("invitations.list_label")}>
              {invitations.items.map((invitation) => {
                const actions = invitationActions(invitation, canManage);
                return (
                  <li className="team-row" key={invitation.invitationId}>
                    <div className="team-main">
                      <strong className="break">{invitation.emailMasked}</strong>
                      <small>
                        {invitation.invitedByEmailMasked
                          ? t("invitations.role_inviter", { role: roleLabel(invitation.roleCode), inviter: invitation.invitedByEmailMasked })
                          : roleLabel(invitation.roleCode)}
                      </small>
                    </div>
                    <span className={"team-status " + invitation.status.toLowerCase()}>
                      {invitation.status === "PENDING" ? expiryText(invitation.expiresAt) : invitationStatusLabel(invitation.status) ?? t("status_unknown")}
                    </span>
                    {(actions.resend || actions.revoke) && (
                      <RowActions label={invitation.emailMasked}>
                        {actions.resend && (
                          <button type="button" className="text-button" disabled={busy !== null}
                            onClick={() => setPending({ kind: "resend", invitation })}>{t("action.resend")}</button>
                        )}
                        {actions.revoke && (
                          <button type="button" className="text-button danger-text" disabled={busy !== null}
                            onClick={() => setPending({ kind: "revoke", invitation })}>{t("action.revoke")}</button>
                        )}
                      </RowActions>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
          {invitations && <Pager page={invitations} onPage={(page) => void loadInvitations(status, page)} />}
        </>
      )}

      {inviting && (
        <div className="modal-backdrop" role="presentation">
          <form className="modal" role="dialog" aria-modal="true" aria-labelledby="invite-title" noValidate aria-busy={busy === "invite"}
            onKeyDown={(event) => { if (event.key === "Escape" && busy !== "invite") setInviting(false); }}
            onSubmit={(event) => { event.preventDefault(); void submitInvite(); }}>
            <h2 id="invite-title">{t("invite.title")}</h2>
            <label>{t("invite.email")}
              <input ref={emailInput} type="email" value={form.email} maxLength={320} autoComplete="off" autoCapitalize="none" spellCheck={false}
                disabled={busy === "invite"} aria-invalid={!!issues.email} aria-describedby={issues.email ? "invite-email-error" : undefined}
                onChange={(event) => { setForm({ ...form, email: event.target.value }); setIssues({ ...issues, email: "" }); setInviteError(null); }} />
            </label>
            {issues.email && <small className="field-error" id="invite-email-error">{issues.email}</small>}
            <fieldset disabled={busy === "invite"}>
              <legend>{t("invite.role")}</legend>
              {assignableRoles.map((role) => (
                <label key={role.code} className="role-option">
                  <input type="radio" name="roleCode" value={role.code} checked={form.roleCode === role.code}
                    onChange={() => { setForm({ ...form, roleCode: role.code }); setIssues({ ...issues, roleCode: "" }); }} />
                  <span><strong>{role.label}</strong><small>{role.description}</small></span>
                </label>
              ))}
            </fieldset>
            {issues.roleCode && <small className="field-error">{issues.roleCode}</small>}
            {inviteError && <p className="notice error" role="alert">{inviteError}</p>}
            <p className="hint">{t("invite.hint")}</p>
            <div className="actions">
              <button type="button" className="secondary" disabled={busy === "invite"} onClick={() => setInviting(false)}>{t("action.cancel")}</button>
              <button type="submit" className="primary compact" disabled={busy === "invite"}>{busy === "invite" ? tc("status.processing") : t("invite.submit")}</button>
            </div>
          </form>
        </div>
      )}
      {pending && (
        <div className="modal-backdrop" role="presentation">
          <div className="modal" role="dialog" aria-modal="true" aria-labelledby="team-confirm-title"
            onKeyDown={(event) => { if (event.key === "Escape" && busy !== "confirm") setPending(null); }}>
            <h2 id="team-confirm-title">{t("confirm." + pending.kind + "_title")}</h2>
            <p>
              {t("confirm." + pending.kind + "_body", { email: pending.kind === "remove" ? pending.member.emailMasked : pending.invitation.emailMasked })}
            </p>
            <div className="actions">
              <button type="button" className="secondary" disabled={busy === "confirm"} onClick={() => setPending(null)}>{t("action.cancel")}</button>
              <button type="button" className={pending.kind === "resend" ? "primary compact" : "danger"} disabled={busy === "confirm"}
                onClick={() => void confirmPending()}>
                {busy === "confirm" ? tc("status.processing") : t("action." + pending.kind)}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
