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
  invitationStatusLabels,
  invitationStatuses,
  memberStatusLabels,
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

type Client = ReturnType<typeof createAuthClient>;
const base = "/team/v1";

/** 失败说明：业务错误码用固定文案，其余用服务端说明。 */
function failureText(failure: unknown, fallback: string): string {
  if (failure instanceof RequestFailure) return teamErrorText(failure.code, failure.message || fallback);
  return failure instanceof Error ? failure.message : fallback;
}

function Pager({ page, onPage }: { page: TeamPage<unknown>; onPage: (page: number) => void }) {
  if (page.page <= 1 && !hasNextPage(page)) return null;
  return (
    <div className="actions team-pager">
      <button type="button" className="secondary" disabled={page.page <= 1} onClick={() => onPage(page.page - 1)}>上一页</button>
      <span>第 {page.page} 页</span>
      <button type="button" className="secondary" disabled={!hasNextPage(page)} onClick={() => onPage(page.page + 1)}>下一页</button>
    </div>
  );
}

/** 行操作容器：桌面直接显示；窄屏收进“更多”。 */
function RowActions({ label, children }: { label: string; children: React.ReactNode }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="team-row-actions">
      <button type="button" className="text-button team-more" aria-expanded={open} aria-label={label + " 的更多操作"}
        onClick={() => setOpen((value) => !value)}>更多</button>
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
  const emailInput = useRef<HTMLInputElement>(null);

  async function loadMembers(page = memberPage) {
    setMemberError(null);
    try {
      const value = readMemberPage(await client.request<unknown>(base + "/members?" + teamQuery(page)));
      if (!value) throw new Error("成员列表暂时无法读取");
      setMembers(value);
      setMemberPage(page);
    } catch (failure) {
      setMembers(null);
      setMemberError(failureText(failure, "成员列表暂时无法读取"));
    }
  }
  async function loadInvitations(nextStatus = status, page = invitationPage) {
    if (!canManage) return;
    setInvitationError(null);
    try {
      const value = readInvitationPage(await client.request<unknown>(base + "/invitations?" + teamQuery(page, nextStatus)));
      if (!value) throw new Error("邀请列表暂时无法读取");
      setInvitations(value);
      setStatus(nextStatus);
      setInvitationPage(page);
    } catch (failure) {
      setInvitations(null);
      setInvitationError(failureText(failure, "邀请列表暂时无法读取"));
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
        ? { tone: "error", text: emailFailedText }
        : { tone: "success", text: "邀请已发送" + (value ? "至 " + value.emailMasked : "") + "。" });
      await loadInvitations("PENDING", 1);
    } catch (failure) {
      const fields = failure instanceof RequestFailure ? { ...failure.fields } : {};
      const code = failure instanceof RequestFailure ? failure.code : "";
      if (code === "CLIENT_INVITATION_DUPLICATE" || code === "CLIENT_MEMBER_EXISTS") fields.email = teamErrorText(code, "");
      if (Object.keys(fields).length) setIssues(fields);
      else setInviteError(failureText(failure, "邀请未发出，请稍后重试"));
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
      setNotice({ tone: "success", text: member.emailMasked + " 的角色已改为" + roleLabel(value?.roleCode ?? roleCode) + "。" });
    } catch (failure) {
      setNotice({ tone: "error", text: "角色未更改：" + failureText(failure, "请稍后重试") });
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
        setNotice({ tone: "success", text: pending.member.emailMasked + " 已移出工作区。" });
      } else if (pending.kind === "revoke") {
        await client.request<unknown>(base + "/invitations/" + encodeURIComponent(pending.invitation.invitationId) + "/revocation", { method: "POST" });
        setNotice({ tone: "success", text: "已撤销对 " + pending.invitation.emailMasked + " 的邀请。" });
      } else {
        const value = readInvitation(await client.request<unknown>(
          base + "/invitations/" + encodeURIComponent(pending.invitation.invitationId) + "/resend", { method: "POST" }));
        setNotice(value?.emailDelivery === "FAILED"
          ? { tone: "error", text: "新链接已生成，但邮件未发出，可稍后重发。" }
          : { tone: "success", text: "邀请已重新发送至 " + pending.invitation.emailMasked + "，旧链接已失效。" });
      }
    } catch (failure) {
      setNotice({ tone: "error", text: failureText(failure, "操作未完成，请稍后重试") });
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
          <h1 id="team-title">团队</h1>
          <p>{canManage ? "邀请同事加入工作区，并为成员分配角色。" : "查看工作区成员与其角色。"}</p>
        </div>
        {canManage && <button type="button" className="primary compact" onClick={openInvite}>邀请成员</button>}
      </div>
      {notice && <p className={"notice" + (notice.tone === "error" ? " error" : " success")} role="status">{notice.text}</p>}

      <h2 className="section-title">成员</h2>
      {memberError && (
        <div className="notice error" role="alert">
          {memberError}
          <button type="button" className="text-button" onClick={() => void loadMembers()}>重试</button>
        </div>
      )}
      {!members && !memberError && <p role="status">正在读取成员…</p>}
      {members && members.items.length === 0 && <p className="empty">工作区还没有其他成员。{canManage ? "点击“邀请成员”邀请同事加入。" : ""}</p>}
      {members && members.items.length > 0 && (
        <ul className="team-list" aria-label="成员列表">
          {members.items.map((member) => (
            <li className="team-row" key={member.memberId}>
              <div className="team-main">
                <strong className="break">{member.emailMasked}</strong>
                <small>{roleLabel(member.roleCode)}{member.self ? " · 你" : ""}</small>
              </div>
              <span className={"team-status " + member.status.toLowerCase()}>
                {member.status === "ACTIVE" ? "已加入 " + displayDate(member.joinedAt) : memberStatusLabels[member.status] ?? "状态未知"}
              </span>
              {canOperateMember(member, canManage) && (
                <RowActions label={member.emailMasked}>
                  <label className="team-role">
                    <span>更改角色</span>
                    <select value={member.roleCode} disabled={busy !== null} aria-label={"更改 " + member.emailMasked + " 的角色"}
                      onChange={(event) => void changeRole(member, event.target.value)}>
                      {assignableRoles.map((role) => <option key={role.code} value={role.code}>{role.label}</option>)}
                    </select>
                  </label>
                  {busy === "role:" + member.memberId && <small role="status">正在保存…</small>}
                  <button type="button" className="text-button danger-text" disabled={busy !== null}
                    onClick={() => setPending({ kind: "remove", member })}>移除</button>
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
            <h2 className="section-title">邀请</h2>
            <div className="team-filter" role="group" aria-label="邀请状态">
              {invitationStatuses.map((code) => (
                <button key={code} type="button" className={"chip" + (status === code ? " active" : "")} aria-pressed={status === code}
                  onClick={() => void loadInvitations(code, 1)}>{invitationStatusLabels[code]}</button>
              ))}
            </div>
          </div>
          {invitationError && (
            <div className="notice error" role="alert">
              {invitationError}
              <button type="button" className="text-button" onClick={() => void loadInvitations()}>重试</button>
            </div>
          )}
          {!invitations && !invitationError && <p role="status">正在读取邀请…</p>}
          {invitations && invitations.items.length === 0 && (
            <p className="empty">{status === "PENDING" ? "还没有待接受的邀请。" : "没有" + invitationStatusLabels[status] + "的邀请。"}</p>
          )}
          {invitations && invitations.items.length > 0 && (
            <ul className="team-list" aria-label="邀请列表">
              {invitations.items.map((invitation) => {
                const actions = invitationActions(invitation, canManage);
                return (
                  <li className="team-row" key={invitation.invitationId}>
                    <div className="team-main">
                      <strong className="break">{invitation.emailMasked}</strong>
                      <small>
                        {roleLabel(invitation.roleCode)}
                        {invitation.invitedByEmailMasked ? " · 邀请人 " + invitation.invitedByEmailMasked : ""}
                      </small>
                    </div>
                    <span className={"team-status " + invitation.status.toLowerCase()}>
                      {invitation.status === "PENDING" ? expiryText(invitation.expiresAt) : invitationStatusLabels[invitation.status] ?? "状态未知"}
                    </span>
                    {(actions.resend || actions.revoke) && (
                      <RowActions label={invitation.emailMasked}>
                        {actions.resend && (
                          <button type="button" className="text-button" disabled={busy !== null}
                            onClick={() => setPending({ kind: "resend", invitation })}>重发</button>
                        )}
                        {actions.revoke && (
                          <button type="button" className="text-button danger-text" disabled={busy !== null}
                            onClick={() => setPending({ kind: "revoke", invitation })}>撤销</button>
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
            <h2 id="invite-title">邀请成员</h2>
            <label>邮箱
              <input ref={emailInput} type="email" value={form.email} maxLength={320} autoComplete="off" autoCapitalize="none" spellCheck={false}
                disabled={busy === "invite"} aria-invalid={!!issues.email} aria-describedby={issues.email ? "invite-email-error" : undefined}
                onChange={(event) => { setForm({ ...form, email: event.target.value }); setIssues({ ...issues, email: "" }); setInviteError(null); }} />
            </label>
            {issues.email && <small className="field-error" id="invite-email-error">{issues.email}</small>}
            <fieldset disabled={busy === "invite"}>
              <legend>角色</legend>
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
            <p className="hint">邀请 7 天内有效。对方需使用该邮箱登录或注册后接受。</p>
            <div className="actions">
              <button type="button" className="secondary" disabled={busy === "invite"} onClick={() => setInviting(false)}>取消</button>
              <button type="submit" className="primary compact" disabled={busy === "invite"}>{busy === "invite" ? "处理中…" : "发送邀请"}</button>
            </div>
          </form>
        </div>
      )}
      {pending && (
        <div className="modal-backdrop" role="presentation">
          <div className="modal" role="dialog" aria-modal="true" aria-labelledby="team-confirm-title"
            onKeyDown={(event) => { if (event.key === "Escape" && busy !== "confirm") setPending(null); }}>
            <h2 id="team-confirm-title">{pending.kind === "remove" ? "移除成员" : pending.kind === "revoke" ? "撤销邀请" : "重发邀请"}</h2>
            <p>
              {pending.kind === "remove"
                ? "移除后 " + pending.member.emailMasked + " 将无法再进入本工作区，需要时可重新邀请。"
                : pending.kind === "revoke"
                  ? "撤销后发给 " + pending.invitation.emailMasked + " 的邀请链接立即失效。"
                  : "将向 " + pending.invitation.emailMasked + " 发送新的邀请链接，旧链接立即失效，有效期重新计算 7 天。"}
            </p>
            <div className="actions">
              <button type="button" className="secondary" disabled={busy === "confirm"} onClick={() => setPending(null)}>取消</button>
              <button type="button" className={pending.kind === "resend" ? "primary compact" : "danger"} disabled={busy === "confirm"}
                onClick={() => void confirmPending()}>
                {busy === "confirm" ? "处理中…" : pending.kind === "remove" ? "移除" : pending.kind === "revoke" ? "撤销" : "重发"}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
