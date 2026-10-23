import { useEffect, useState } from "react";
import { RequestFailure } from "./auth.ts";
import type { createAuthClient } from "./auth.ts";
import {
  canCloseSecret,
  credentialFormIssues,
  defaultGraceMinutes,
  defaultValidityDays,
  deliveryQuery,
  displayTime,
  emptyDeliveryFilter,
  endpointFormIssues,
  eventTypeLabel,
  testEventType,
  graceOptions,
  maxEndpoints,
  readCredentials,
  readEndpoints,
  readSecret,
  validityOptions,
  webhookEventTypes,
} from "./developer.ts";
import { canReplay, replayRejectedText } from "./developer.ts";
import type { Credential, DeliveryFilter, Endpoint, Scope } from "./developer.ts";
import { enumCodes, enumLabel } from "./enum-label.ts";
import { translator } from "./i18n/index.ts";
import { useT } from "./i18n/react.tsx";

const td = translator("developer");

type Client = ReturnType<typeof createAuthClient>;
const base = "/developer/v1";

/** 错误提示：带请求编号，便于客户反馈。 */
function ErrorNote({ error }: { error: Error | null }) {
  const tc = useT("common");
  if (!error) return null;
  return (
    <div className="notice error" role="alert">
      {error.message}
      {error instanceof RequestFailure && error.requestId && <small>{tc("request_id", { requestId: error.requestId })}</small>}
    </div>
  );
}

/**
 * 一次性密钥弹窗
 * <li>密钥只在组件状态中存在，关闭即丢弃，不写入任何浏览器存储。</li>
 * <li>必须勾选“我已安全保存”才能关闭。</li>
 */
export function SecretModal({ title, secret, onClose }: { title: string; secret: string; onClose: () => void }) {
  const [acknowledged, setAcknowledged] = useState(false), [copied, setCopied] = useState(false), t = useT("developer");
  async function copy() {
    try {
      await navigator.clipboard.writeText(secret);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }
  return (
    <div className="modal-backdrop" role="presentation">
      <div className="modal" role="dialog" aria-modal="true" aria-labelledby="secret-title"
        onKeyDown={(event) => { if (event.key === "Escape" && canCloseSecret(acknowledged)) onClose(); }}>
        <h2 id="secret-title">{title}</h2>
        <p className="notice error" role="note">{t("secret.warning")}</p>
        <code className="secret-value" aria-label={t("secret.label")}>{secret}</code>
        <div className="actions">
          <button type="button" className="secondary compact" onClick={() => void copy()}>{copied ? t("secret.copied") : t("secret.copy")}</button>
        </div>
        <label className="checkline">
          <input type="checkbox" checked={acknowledged} onChange={(event) => setAcknowledged(event.target.checked)} />
          {t("secret.acknowledge")}
        </label>
        <div className="actions">
          <button type="button" className="primary compact" disabled={!canCloseSecret(acknowledged)} onClick={onClose}>{t("action.close")}</button>
        </div>
      </div>
    </div>
  );
}

/** 二次确认弹窗，写明操作影响。 */
function Confirm({ title, text, confirmText, busy, onConfirm, onCancel, children }: {
  title: string; text: string; confirmText: string; busy: boolean; onConfirm: () => void; onCancel: () => void; children?: React.ReactNode;
}) {
  const t = useT("developer"), tc = useT("common");
  return (
    <div className="modal-backdrop" role="presentation">
      <div className="modal" role="dialog" aria-modal="true" aria-label={title}>
        <h2>{title}</h2>
        <p>{text}</p>
        {children}
        <div className="actions">
          <button type="button" className="secondary" disabled={busy} onClick={onCancel}>{t("action.cancel")}</button>
          <button type="button" className="danger" disabled={busy} onClick={onConfirm}>{busy ? tc("status.processing") : confirmText}</button>
        </div>
      </div>
    </div>
  );
}

/** API 凭证页：列表、创建、轮换、撤销；无管理权限时只读。 */
export function ApiKeysPanel({ client, canManage }: { client: Client; canManage: boolean }) {
  const [items, setItems] = useState<Credential[] | null>(null), [loadError, setLoadError] = useState<Error | null>(null);
  const [scopes, setScopes] = useState<Scope[]>([]), [error, setError] = useState<Error | null>(null);
  const [creating, setCreating] = useState(false), [busy, setBusy] = useState(false);
  const [form, setForm] = useState({ name: "", scopeCodes: [] as string[], validityDays: defaultValidityDays });
  const [issues, setIssues] = useState<Record<string, string>>({});
  const [rotating, setRotating] = useState<Credential | null>(null), [grace, setGrace] = useState(defaultGraceMinutes);
  const [revoking, setRevoking] = useState<Credential | null>(null);
  const [secret, setSecret] = useState<{ title: string; value: string } | null>(null);
  const t = useT("developer");

  async function load() {
    setLoadError(null);
    try {
      const value = readCredentials(await client.request<unknown>(base + "/credentials"));
      if (!value) throw new Error(td("credentials.load_failed"));
      setItems(value);
    } catch (failure) {
      setItems(null);
      setLoadError(failure instanceof Error ? failure : new Error(td("credentials.load_failed")));
    }
  }
  useEffect(() => { void load(); }, []);

  async function openCreate() {
    setError(null);
    setIssues({});
    setForm({ name: "", scopeCodes: [], validityDays: defaultValidityDays });
    try {
      const value = await client.request<{ items?: Scope[] }>(base + "/scopes");
      const list = Array.isArray(value?.items) ? value.items.filter((s) => typeof s?.scopeCode === "string") : [];
      setScopes(list);
      setForm((current) => ({ ...current, scopeCodes: list.map((s) => s.scopeCode) }));
      setCreating(true);
    } catch (failure) {
      setError(failure instanceof Error ? failure : new Error(td("credentials.scopes_failed")));
    }
  }
  async function submitCreate() {
    const found = credentialFormIssues(form, scopes.map((s) => s.scopeCode));
    setIssues(found);
    if (Object.keys(found).length || busy) return;
    setBusy(true);
    setError(null);
    try {
      const value = await client.request<unknown>(base + "/credentials", {
        method: "POST", body: { name: form.name.trim(), scopeCodes: form.scopeCodes, validityDays: form.validityDays },
      });
      const key = readSecret(value, "secret");
      setCreating(false);
      if (key) setSecret({ title: td("credentials.created_title"), value: key });
      else setError(new Error(td("credentials.created_without_secret")));
      await load();
    } catch (failure) {
      if (failure instanceof RequestFailure && Object.keys(failure.fields).length) setIssues(failure.fields);
      setError(failure instanceof Error ? failure : new Error(td("credentials.create_failed")));
    } finally {
      setBusy(false);
    }
  }
  async function submitRotate() {
    if (!rotating || busy) return;
    setBusy(true);
    setError(null);
    try {
      const value = await client.request<unknown>(base + "/credentials/" + encodeURIComponent(rotating.credentialId) + "/rotation", {
        method: "POST", body: { expectedVersion: rotating.version, graceMinutes: grace },
      });
      const key = readSecret(value, "secret");
      setRotating(null);
      if (key) setSecret({ title: td("credentials.rotated_title"), value: key });
      else setError(new Error(td("rotation.done_without_secret")));
      await load();
    } catch (failure) {
      setError(failure instanceof Error ? failure : new Error(td("credentials.rotate_failed")));
      await load();
    } finally {
      setBusy(false);
    }
  }
  async function submitRevoke() {
    if (!revoking || busy) return;
    setBusy(true);
    setError(null);
    try {
      await client.request<unknown>(base + "/credentials/" + encodeURIComponent(revoking.credentialId) + "/revocation", {
        method: "POST", body: { expectedVersion: revoking.version },
      });
      setRevoking(null);
      await load();
    } catch (failure) {
      setError(failure instanceof Error ? failure : new Error(td("credentials.revoke_failed")));
      await load();
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-labelledby="api-keys-title">
      <div className="page-heading">
        <div>
          <h1 id="api-keys-title">{t("credentials.title")}</h1>
          <p>{t("credentials.intro")}</p>
        </div>
        {canManage && <button type="button" className="primary compact" onClick={() => void openCreate()}>{t("credentials.new")}</button>}
      </div>
      <ErrorNote error={error} />
      {loadError && (
        <div className="notice error" role="alert">
          {loadError.message}
          <button type="button" className="text-button" onClick={() => void load()}>{t("action.retry")}</button>
        </div>
      )}
      {!items && !loadError && <p role="status">{t("credentials.loading")}</p>}
      {items && items.length === 0 && <p className="empty">{canManage ? t("credentials.empty_manage") : t("credentials.empty")}</p>}
      {items && items.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead><tr><th>{t("credentials.col.name")}</th><th>{t("credentials.col.prefix")}</th><th>{t("credentials.col.scopes")}</th><th>{t("credentials.col.status")}</th><th>{t("credentials.col.expires")}</th>{canManage && <th>{t("credentials.col.actions")}</th>}</tr></thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.credentialId}>
                  <td>{item.name ?? t("credentials.unnamed")}</td>
                  <td><code>{item.keyPrefix}</code></td>
                  <td>{item.scopeCodes.join(", ") || "—"}</td>
                  <td>{enumLabel("credential_status", item.status) ?? item.status}</td>
                  <td>{displayTime(item.expiresAt)}</td>
                  {canManage && (
                    <td className="row-actions">
                      {item.status === "ACTIVE" && (
                        <>
                          <button type="button" className="text-button" onClick={() => { setGrace(defaultGraceMinutes); setRotating(item); }}>{t("credentials.rotate")}</button>
                          <button type="button" className="text-button" onClick={() => setRevoking(item)}>{t("credentials.revoke")}</button>
                        </>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {creating && (
        <div className="modal-backdrop" role="presentation">
          <form className="modal" role="dialog" aria-modal="true" aria-label={t("credentials.new")}
            onSubmit={(event) => { event.preventDefault(); void submitCreate(); }}>
            <h2>{t("credentials.new")}</h2>
            <label>{t("credentials.form.name")}
              <input value={form.name} maxLength={64} onChange={(event) => setForm({ ...form, name: event.target.value })} />
            </label>
            {issues.name && <small className="field-error">{issues.name}</small>}
            <fieldset>
              <legend>{t("credentials.form.scopes")}</legend>
              {scopes.length === 0 && <p className="empty">{t("credentials.form.no_scopes")}</p>}
              {scopes.map((scope) => (
                <label key={scope.scopeCode} className="checkline">
                  <input type="checkbox" checked={form.scopeCodes.includes(scope.scopeCode)}
                    onChange={(event) => setForm({
                      ...form,
                      scopeCodes: event.target.checked ? [...form.scopeCodes, scope.scopeCode] : form.scopeCodes.filter((c) => c !== scope.scopeCode),
                    })} />
                  {scope.displayName ?? scope.scopeCode}
                </label>
              ))}
            </fieldset>
            {issues.scopeCodes && <small className="field-error">{issues.scopeCodes}</small>}
            <label>{t("credentials.form.validity")}
              <select value={form.validityDays} onChange={(event) => setForm({ ...form, validityDays: Number(event.target.value) })}>
                {validityOptions.map((days) => <option key={days} value={days}>{t("credentials.form.days", { days })}</option>)}
              </select>
            </label>
            <div className="actions">
              <button type="button" className="secondary" disabled={busy} onClick={() => setCreating(false)}>{t("action.cancel")}</button>
              <button type="submit" className="primary compact" disabled={busy}>{busy ? t("credentials.form.creating") : t("credentials.form.create")}</button>
            </div>
          </form>
        </div>
      )}
      {rotating && (
        <Confirm title={t("credentials.rotate_title")} confirmText={t("credentials.generate_key")} busy={busy} onConfirm={() => void submitRotate()} onCancel={() => setRotating(null)}
          text={t("credentials.rotate_body", { name: rotating.name ?? rotating.keyPrefix })}>
          <label>{t("credentials.grace_label")}
            <select value={grace} onChange={(event) => setGrace(Number(event.target.value))}>
              {graceOptions.map((option) => <option key={option.minutes} value={option.minutes}>{option.label}</option>)}
            </select>
          </label>
        </Confirm>
      )}
      {revoking && (
        <Confirm title={t("credentials.revoke_title")} confirmText={t("credentials.revoke")} busy={busy} onConfirm={() => void submitRevoke()} onCancel={() => setRevoking(null)}
          text={t("credentials.revoke_body", { name: revoking.name ?? revoking.keyPrefix })} />
      )}
      {secret && <SecretModal title={secret.title} secret={secret.value} onClose={() => setSecret(null)} />}
    </section>
  );
}

type DeliveryItem = {
  deliveryId: string; eventId: string; eventType: string; endpointId: string; status: string;
  attemptCount: number; lastAttemptTime: string | null; replayOfDeliveryId: string | null; createTime: string | null;
};
type DeliveryDetail = {
  event?: { eventId?: string; eventType?: string };
  attempts?: { attemptNo: number; requestUrl?: string; status?: string; httpStatus?: number | null; failureCode?: string | null;
    durationMs?: number | null; startedTime?: string | null }[];
};

/** Webhook 页：端点管理、密钥轮换、测试事件、投递查询与重发。 */
export function WebhooksPanel({ client, canManage }: { client: Client; canManage: boolean }) {
  const [endpoints, setEndpoints] = useState<Endpoint[] | null>(null), [loadError, setLoadError] = useState<Error | null>(null);
  const [error, setError] = useState<Error | null>(null), [busy, setBusy] = useState(false), [note, setNote] = useState<string | null>(null);
  const [editing, setEditing] = useState<Endpoint | "new" | null>(null);
  const [form, setForm] = useState({ url: "", eventTypes: [] as string[] }), [issues, setIssues] = useState<Record<string, string>>({});
  const [selected, setSelected] = useState<Endpoint | null>(null);
  const [confirm, setConfirm] = useState<"status" | "rotate" | null>(null), [immediate, setImmediate] = useState(false);
  const [secret, setSecret] = useState<{ title: string; value: string } | null>(null);
  const [filter, setFilter] = useState<DeliveryFilter>(emptyDeliveryFilter);
  const [deliveries, setDeliveries] = useState<{ items: DeliveryItem[]; total: number | null } | null>(null);
  const [deliveryError, setDeliveryError] = useState<Error | null>(null);
  const [detail, setDetail] = useState<{ id: string; status: string | null; value: DeliveryDetail } | null>(null);
  const t = useT("developer"), tc = useT("common");

  async function loadEndpoints() {
    setLoadError(null);
    try {
      const value = readEndpoints(await client.request<unknown>(base + "/webhook-endpoints"));
      if (!value) throw new Error(td("webhooks.endpoints_failed"));
      setEndpoints(value);
      setSelected((current) => current ? value.find((e) => e.endpointId === current.endpointId) ?? null : null);
    } catch (failure) {
      setEndpoints(null);
      setLoadError(failure instanceof Error ? failure : new Error(td("webhooks.endpoints_failed")));
    }
  }
  async function loadDeliveries(next: DeliveryFilter = filter) {
    setDeliveryError(null);
    try {
      const value = await client.request<{ items?: DeliveryItem[]; total?: number }>(base + "/webhook-deliveries?" + deliveryQuery(next));
      if (!Array.isArray(value?.items)) throw new Error(td("webhooks.deliveries_failed"));
      setDeliveries({ items: value.items, total: typeof value.total === "number" ? value.total : null });
    } catch (failure) {
      setDeliveries(null);
      setDeliveryError(failure instanceof Error ? failure : new Error(td("webhooks.deliveries_failed")));
    }
  }
  useEffect(() => { void loadEndpoints(); void loadDeliveries(); }, []);

  function openForm(target: Endpoint | "new") {
    setIssues({});
    setError(null);
    setForm(target === "new" ? { url: "", eventTypes: webhookEventTypes.map((e) => e.code) } : { url: target.url, eventTypes: target.eventTypes });
    setEditing(target);
  }
  async function submitForm() {
    const found = endpointFormIssues(form);
    setIssues(found);
    if (Object.keys(found).length || busy || !editing) return;
    setBusy(true);
    setError(null);
    try {
      if (editing === "new") {
        const value = await client.request<unknown>(base + "/webhook-endpoints", { method: "POST", body: { url: form.url.trim(), eventTypes: form.eventTypes } });
        const key = readSecret(value, "signingSecret");
        if (key) setSecret({ title: td("webhooks.created_title"), value: key });
        else setError(new Error(td("webhooks.created_without_secret")));
      } else {
        await client.request<unknown>(base + "/webhook-endpoints/" + encodeURIComponent(editing.endpointId), {
          method: "PUT", body: { url: form.url.trim(), eventTypes: form.eventTypes, expectedVersion: editing.version },
        });
      }
      setEditing(null);
      await loadEndpoints();
    } catch (failure) {
      if (failure instanceof RequestFailure && Object.keys(failure.fields).length) setIssues(failure.fields);
      setError(failure instanceof Error ? failure : new Error(td("webhooks.save_failed")));
    } finally {
      setBusy(false);
    }
  }
  async function endpointAction(action: "status" | "rotate" | "test") {
    if (!selected || busy) return;
    setBusy(true);
    setError(null);
    setNote(null);
    const path = base + "/webhook-endpoints/" + encodeURIComponent(selected.endpointId);
    try {
      if (action === "status") {
        await client.request<unknown>(path + "/status", {
          method: "POST", body: { status: selected.status === "ACTIVE" ? "DISABLED" : "ACTIVE", expectedVersion: selected.version },
        });
      } else if (action === "rotate") {
        const value = await client.request<unknown>(path + "/secret-rotation", { method: "POST", body: { immediate } });
        const key = readSecret(value, "signingSecret");
        if (key) setSecret({ title: td("webhooks.secret_rotated_title"), value: key });
        else setError(new Error(td("rotation.done_without_secret")));
      } else {
        const value = await client.request<{ result?: string; httpStatus?: number | null; failureCode?: string | null }>(path + "/test-events", { method: "POST" });
        setNote(value?.result === "SUCCEEDED" ? td("webhooks.test_delivered", { httpStatus: value.httpStatus ?? "—" })
          : value?.failureCode ? td("webhooks.test_failed", { reason: enumLabel("webhook_failure", value.failureCode) ?? value.failureCode })
            : td("webhooks.test_failed_unknown"));
        await loadDeliveries();
      }
      setConfirm(null);
      await loadEndpoints();
    } catch (failure) {
      setError(failure instanceof Error ? failure : new Error(td("webhooks.action_failed")));
      await loadEndpoints();
    } finally {
      setBusy(false);
    }
  }
  async function openDetail(id: string, status: string | null) {
    setError(null);
    try {
      setDetail({ id, status, value: await client.request<DeliveryDetail>(base + "/webhook-deliveries/" + encodeURIComponent(id)) });
    } catch (failure) {
      setError(failure instanceof Error ? failure : new Error(td("webhooks.detail_failed")));
    }
  }
  async function replay(id: string) {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await client.request<unknown>(base + "/webhook-deliveries/" + encodeURIComponent(id) + "/replay", { method: "POST" });
      setNote(td("webhooks.replayed"));
      setDetail(null);
      await loadDeliveries();
    } catch (failure) {
      setError(failure instanceof RequestFailure && failure.status === 400 ? new Error(replayRejectedText())
        : failure instanceof Error ? failure : new Error(td("webhooks.replay_failed")));
    } finally {
      setBusy(false);
    }
  }
  function applyFilter(next: DeliveryFilter) {
    setFilter(next);
    void loadDeliveries(next);
  }

  const full = (endpoints?.length ?? 0) >= maxEndpoints;
  return (
    <section aria-labelledby="webhooks-title">
      <div className="page-heading">
        <div>
          <h1 id="webhooks-title">{t("webhooks.title")}</h1>
          <p>{t("webhooks.intro")}</p>
        </div>
        {canManage && <button type="button" className="primary compact" disabled={full} title={full ? t("webhooks.limit", { max: maxEndpoints }) : undefined} onClick={() => openForm("new")}>{t("webhooks.new_endpoint")}</button>}
      </div>
      <ErrorNote error={error} />
      {note && <p className="notice" role="status">{note}</p>}
      {loadError && (
        <div className="notice error" role="alert">
          {loadError.message}
          <button type="button" className="text-button" onClick={() => void loadEndpoints()}>{t("action.retry")}</button>
        </div>
      )}
      {!endpoints && !loadError && <p role="status">{t("webhooks.endpoints_loading")}</p>}
      {endpoints && endpoints.length === 0 && <p className="empty">{t("webhooks.endpoints_empty")}</p>}
      {endpoints && endpoints.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead><tr><th>{t("webhooks.col.url")}</th><th>{t("webhooks.col.events")}</th><th>{t("webhooks.col.status")}</th><th></th></tr></thead>
            <tbody>
              {endpoints.map((endpoint) => (
                <tr key={endpoint.endpointId}>
                  <td className="break">{endpoint.url}</td>
                  <td>{t("webhooks.event_count", { count: endpoint.eventTypes.length })}</td>
                  <td>{enumLabel("endpoint_status", endpoint.status) ?? endpoint.status}</td>
                  <td><button type="button" className="text-button" onClick={() => { setNote(null); setSelected(endpoint); }}>{t("action.details")}</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <h2>{t("deliveries.title")}</h2>
      <form className="filters" onSubmit={(event) => { event.preventDefault(); applyFilter({ ...filter, page: 1 }); }}>
        <select aria-label={t("deliveries.filter.endpoint")} value={filter.endpointId} onChange={(event) => setFilter({ ...filter, endpointId: event.target.value })}>
          <option value="">{t("deliveries.filter.all_endpoints")}</option>
          {endpoints?.map((e) => <option key={e.endpointId} value={e.endpointId}>{e.url}</option>)}
        </select>
        <select aria-label={t("deliveries.filter.event_type")} value={filter.eventType} onChange={(event) => setFilter({ ...filter, eventType: event.target.value })}>
          <option value="">{t("deliveries.filter.all_events")}</option>
          <option value={testEventType}>{t("webhooks.test_event")}</option>
          {webhookEventTypes.map((e) => <option key={e.code} value={e.code}>{e.label}</option>)}
        </select>
        <select aria-label={t("deliveries.filter.status")} value={filter.status} onChange={(event) => setFilter({ ...filter, status: event.target.value })}>
          <option value="">{t("deliveries.filter.all_statuses")}</option>
          {enumCodes.delivery_status.map((code) => <option key={code} value={code}>{enumLabel("delivery_status", code)}</option>)}
        </select>
        <input aria-label={t("deliveries.filter.from")} type="datetime-local" value={filter.from} onChange={(event) => setFilter({ ...filter, from: event.target.value })} />
        <input aria-label={t("deliveries.filter.to")} type="datetime-local" value={filter.to} onChange={(event) => setFilter({ ...filter, to: event.target.value })} />
        <button type="submit" className="secondary">{t("deliveries.filter.search")}</button>
      </form>
      {deliveryError && <div className="notice error" role="alert">{deliveryError.message}</div>}
      {!deliveries && !deliveryError && <p role="status">{t("deliveries.loading")}</p>}
      {deliveries && deliveries.items.length === 0 && <p className="empty">{t("deliveries.empty")}</p>}
      {deliveries && deliveries.items.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead><tr><th>{t("deliveries.col.event")}</th><th>{t("deliveries.col.status")}</th><th>{t("deliveries.col.attempts")}</th><th>{t("deliveries.col.created")}</th><th></th></tr></thead>
            <tbody>
              {deliveries.items.map((item) => (
                <tr key={item.deliveryId}>
                  <td>{eventTypeLabel(item.eventType)}</td>
                  <td>{enumLabel("delivery_status", item.status) ?? item.status}</td>
                  <td>{item.attemptCount}</td>
                  <td>{displayTime(item.createTime)}</td>
                  <td><button type="button" className="text-button" onClick={() => void openDetail(item.deliveryId, item.status)}>{t("action.details")}</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <div className="actions">
        <button type="button" className="secondary" disabled={filter.page <= 1} onClick={() => applyFilter({ ...filter, page: filter.page - 1 })}>{t("pager.previous")}</button>
        <span>{t("pager.page", { page: filter.page })}</span>
        <button type="button" className="secondary" disabled={!deliveries || deliveries.items.length < filter.size}
          onClick={() => applyFilter({ ...filter, page: filter.page + 1 })}>{t("pager.next")}</button>
      </div>

      {editing && (
        <div className="modal-backdrop" role="presentation">
          <form className="modal" role="dialog" aria-modal="true" aria-label={editing === "new" ? t("endpoint.new_title") : t("endpoint.edit_title")}
            onSubmit={(event) => { event.preventDefault(); void submitForm(); }}>
            <h2>{editing === "new" ? t("endpoint.new_title") : t("endpoint.edit_title")}</h2>
            <label>{t("endpoint.form.url")}
              <input value={form.url} inputMode="url" onChange={(event) => setForm({ ...form, url: event.target.value })} />
            </label>
            {issues.url && <small className="field-error">{issues.url}</small>}
            <fieldset>
              <legend>{t("endpoint.form.events")}</legend>
              <p className="hint">{t("endpoint.form.events_hint")}</p>
              {webhookEventTypes.map((event) => (
                <label key={event.code} className="checkline">
                  <input type="checkbox" checked={form.eventTypes.includes(event.code)}
                    onChange={(change) => setForm({
                      ...form,
                      eventTypes: change.target.checked ? [...form.eventTypes, event.code] : form.eventTypes.filter((c) => c !== event.code),
                    })} />
                  {event.label} <code>{event.code}</code>
                </label>
              ))}
            </fieldset>
            {issues.eventTypes && <small className="field-error">{issues.eventTypes}</small>}
            <div className="actions">
              <button type="button" className="secondary" disabled={busy} onClick={() => setEditing(null)}>{t("action.cancel")}</button>
              <button type="submit" className="primary compact" disabled={busy}>{busy ? t("action.saving") : t("action.save")}</button>
            </div>
          </form>
        </div>
      )}

      {selected && (
        <aside className="drawer" role="dialog" aria-label={t("endpoint.detail_title")}>
          <div className="drawer-head">
            <h2>{t("endpoint.detail_title")}</h2>
            <button type="button" className="text-button" onClick={() => setSelected(null)}>{t("action.close")}</button>
          </div>
          <dl className="account">
            <dt>{t("endpoint.field.url")}</dt><dd className="break">{selected.url}</dd>
            <dt>{t("endpoint.field.status")}</dt><dd>{enumLabel("endpoint_status", selected.status) ?? selected.status}</dd>
            <dt>{t("endpoint.field.events")}</dt><dd>{selected.eventTypes.map(eventTypeLabel).join(t("endpoint.event_separator")) || "—"}</dd>
            <dt>{t("endpoint.field.rotations")}</dt><dd>{selected.rotationNo ?? "—"}</dd>
            {selected.previousValidUntil && <><dt>{t("endpoint.field.previous_valid_until")}</dt><dd>{displayTime(selected.previousValidUntil)}</dd></>}
          </dl>
          {canManage && (
            <div className="actions wrap">
              <button type="button" className="secondary" disabled={busy} onClick={() => openForm(selected)}>{t("endpoint.edit")}</button>
              <button type="button" className="secondary" disabled={busy} onClick={() => setConfirm("status")}>{selected.status === "ACTIVE" ? t("endpoint.disable") : t("endpoint.enable")}</button>
              <button type="button" className="secondary" disabled={busy} onClick={() => { setImmediate(false); setConfirm("rotate"); }}>{t("endpoint.rotate_secret")}</button>
              <button type="button" className="primary compact" disabled={busy || selected.status !== "ACTIVE"} onClick={() => void endpointAction("test")}>{busy ? tc("status.processing") : t("endpoint.send_test")}</button>
            </div>
          )}
        </aside>
      )}
      {selected && confirm === "status" && (
        <Confirm title={selected.status === "ACTIVE" ? t("endpoint.disable_title") : t("endpoint.enable_title")} confirmText={selected.status === "ACTIVE" ? t("endpoint.disable") : t("endpoint.enable")} busy={busy}
          onConfirm={() => void endpointAction("status")} onCancel={() => setConfirm(null)}
          text={selected.status === "ACTIVE" ? t("endpoint.disable_body") : t("endpoint.enable_body")} />
      )}
      {selected && confirm === "rotate" && (
        <Confirm title={t("endpoint.rotate_title")} confirmText={t("credentials.generate_key")} busy={busy} onConfirm={() => void endpointAction("rotate")} onCancel={() => setConfirm(null)}
          text={t("endpoint.rotate_body")}>
          <label className="checkline">
            <input type="checkbox" checked={immediate} onChange={(event) => setImmediate(event.target.checked)} />
            {t("endpoint.rotate_immediate")}
          </label>
        </Confirm>
      )}
      {detail && (
        <aside className="drawer" role="dialog" aria-label={t("delivery.detail_title")}>
          <div className="drawer-head">
            <h2>{t("delivery.detail_title")}</h2>
            <button type="button" className="text-button" onClick={() => setDetail(null)}>{t("action.close")}</button>
          </div>
          <dl className="account">
            <dt>{t("delivery.event_id")}</dt><dd className="break">{detail.value.event?.eventId ?? "—"}</dd>
            <dt>{t("delivery.event_type")}</dt><dd>{detail.value.event?.eventType ?? "—"}</dd>
          </dl>
          <h3>{t("delivery.attempts_title")}</h3>
          {(detail.value.attempts ?? []).length === 0 && <p className="empty">{t("delivery.not_sent")}</p>}
          <ol className="attempts">
            {(detail.value.attempts ?? []).map((attempt) => (
              <li key={attempt.attemptNo}>
                {t("delivery.attempt_no", { attemptNo: attempt.attemptNo })} · {displayTime(attempt.startedTime)} · {attempt.httpStatus ? "HTTP " + attempt.httpStatus : t("delivery.no_response")}
                {attempt.failureCode && " · " + (enumLabel("webhook_failure", attempt.failureCode) ?? attempt.failureCode)}
                {typeof attempt.durationMs === "number" && " · " + attempt.durationMs + " ms"}
              </li>
            ))}
          </ol>
          {canManage && canReplay(detail.status) && <div className="actions"><button type="button" className="primary compact" disabled={busy} onClick={() => void replay(detail.id)}>{busy ? tc("status.processing") : t("delivery.resend")}</button></div>}
        </aside>
      )}
      {secret && <SecretModal title={secret.title} secret={secret.value} onClose={() => setSecret(null)} />}
    </section>
  );
}
