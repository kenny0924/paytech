import { useEffect, useState } from "react";
import { RequestFailure } from "./auth.ts";
import type { createAuthClient } from "./auth.ts";
import {
  canCloseSecret,
  credentialFormIssues,
  credentialStatusLabels,
  defaultGraceMinutes,
  defaultValidityDays,
  deliveryQuery,
  deliveryStatusLabels,
  displayTime,
  emptyDeliveryFilter,
  endpointFormIssues,
  endpointStatusLabels,
  failureLabels,
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

type Client = ReturnType<typeof createAuthClient>;
const base = "/developer/v1";

/** 错误提示：带请求编号，便于客户反馈。 */
function ErrorNote({ error }: { error: Error | null }) {
  if (!error) return null;
  return (
    <div className="notice error" role="alert">
      {error.message}
      {error instanceof RequestFailure && error.requestId && <small>请求编号：{error.requestId}</small>}
    </div>
  );
}

/**
 * 一次性密钥弹窗
 * <li>密钥只在组件状态中存在，关闭即丢弃，不写入任何浏览器存储。</li>
 * <li>必须勾选“我已安全保存”才能关闭。</li>
 */
export function SecretModal({ title, secret, onClose }: { title: string; secret: string; onClose: () => void }) {
  const [acknowledged, setAcknowledged] = useState(false), [copied, setCopied] = useState(false);
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
        <p className="notice error" role="note">请立即复制并妥善保存。关闭后无法再次查看，遗失只能轮换重新生成。</p>
        <code className="secret-value" aria-label="密钥">{secret}</code>
        <div className="actions">
          <button type="button" className="secondary compact" onClick={() => void copy()}>{copied ? "已复制" : "复制"}</button>
        </div>
        <label className="checkline">
          <input type="checkbox" checked={acknowledged} onChange={(event) => setAcknowledged(event.target.checked)} />
          我已安全保存
        </label>
        <div className="actions">
          <button type="button" className="primary compact" disabled={!canCloseSecret(acknowledged)} onClick={onClose}>关闭</button>
        </div>
      </div>
    </div>
  );
}

/** 二次确认弹窗，写明操作影响。 */
function Confirm({ title, text, confirmText, busy, onConfirm, onCancel, children }: {
  title: string; text: string; confirmText: string; busy: boolean; onConfirm: () => void; onCancel: () => void; children?: React.ReactNode;
}) {
  return (
    <div className="modal-backdrop" role="presentation">
      <div className="modal" role="dialog" aria-modal="true" aria-label={title}>
        <h2>{title}</h2>
        <p>{text}</p>
        {children}
        <div className="actions">
          <button type="button" className="secondary" disabled={busy} onClick={onCancel}>取消</button>
          <button type="button" className="danger" disabled={busy} onClick={onConfirm}>{busy ? "处理中…" : confirmText}</button>
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

  async function load() {
    setLoadError(null);
    try {
      const value = readCredentials(await client.request<unknown>(base + "/credentials"));
      if (!value) throw new Error("凭证列表暂时无法读取");
      setItems(value);
    } catch (failure) {
      setItems(null);
      setLoadError(failure instanceof Error ? failure : new Error("凭证列表暂时无法读取"));
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
      setError(failure instanceof Error ? failure : new Error("权限范围暂时无法读取"));
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
      if (key) setSecret({ title: "新凭证已创建", value: key });
      else setError(new Error("凭证已创建，但响应中没有可显示的密钥，请撤销后重新创建"));
      await load();
    } catch (failure) {
      if (failure instanceof RequestFailure && Object.keys(failure.fields).length) setIssues(failure.fields);
      setError(failure instanceof Error ? failure : new Error("创建未完成"));
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
      if (key) setSecret({ title: "凭证已轮换", value: key });
      else setError(new Error("轮换已完成，但响应中没有可显示的密钥"));
      await load();
    } catch (failure) {
      setError(failure instanceof Error ? failure : new Error("轮换未完成"));
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
      setError(failure instanceof Error ? failure : new Error("撤销未完成"));
      await load();
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-labelledby="api-keys-title">
      <div className="page-heading">
        <div>
          <h1 id="api-keys-title">API 凭证</h1>
          <p>用于服务端调用平台 API。密钥只在创建或轮换时显示一次。</p>
        </div>
        {canManage && <button type="button" className="primary compact" onClick={() => void openCreate()}>新建凭证</button>}
      </div>
      <ErrorNote error={error} />
      {loadError && (
        <div className="notice error" role="alert">
          {loadError.message}
          <button type="button" className="text-button" onClick={() => void load()}>重试</button>
        </div>
      )}
      {!items && !loadError && <p role="status">正在读取凭证…</p>}
      {items && items.length === 0 && <p className="empty">还没有 API 凭证。{canManage ? "点击“新建凭证”创建第一个。" : ""}</p>}
      {items && items.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead><tr><th>名称</th><th>前缀</th><th>权限范围</th><th>状态</th><th>到期</th>{canManage && <th>操作</th>}</tr></thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.credentialId}>
                  <td>{item.name ?? "（未命名）"}</td>
                  <td><code>{item.keyPrefix}</code></td>
                  <td>{item.scopeCodes.join(", ") || "—"}</td>
                  <td>{credentialStatusLabels[item.status] ?? item.status}</td>
                  <td>{displayTime(item.expiresAt)}</td>
                  {canManage && (
                    <td className="row-actions">
                      {item.status === "ACTIVE" && (
                        <>
                          <button type="button" className="text-button" onClick={() => { setGrace(defaultGraceMinutes); setRotating(item); }}>轮换</button>
                          <button type="button" className="text-button" onClick={() => setRevoking(item)}>撤销</button>
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
          <form className="modal" role="dialog" aria-modal="true" aria-label="新建凭证"
            onSubmit={(event) => { event.preventDefault(); void submitCreate(); }}>
            <h2>新建凭证</h2>
            <label>名称
              <input value={form.name} maxLength={64} onChange={(event) => setForm({ ...form, name: event.target.value })} />
            </label>
            {issues.name && <small className="field-error">{issues.name}</small>}
            <fieldset>
              <legend>权限范围</legend>
              {scopes.length === 0 && <p className="empty">暂无可用的权限范围</p>}
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
            <label>有效期
              <select value={form.validityDays} onChange={(event) => setForm({ ...form, validityDays: Number(event.target.value) })}>
                {validityOptions.map((days) => <option key={days} value={days}>{days} 天</option>)}
              </select>
            </label>
            <div className="actions">
              <button type="button" className="secondary" disabled={busy} onClick={() => setCreating(false)}>取消</button>
              <button type="submit" className="primary compact" disabled={busy}>{busy ? "正在创建…" : "创建"}</button>
            </div>
          </form>
        </div>
      )}
      {rotating && (
        <Confirm title="轮换凭证" confirmText="生成新密钥" busy={busy} onConfirm={() => void submitRotate()} onCancel={() => setRotating(null)}
          text={"将为“" + (rotating.name ?? rotating.keyPrefix) + "”生成新密钥，沿用名称和权限范围。旧密钥按下面的选择失效。"}>
          <label>旧密钥失效时间
            <select value={grace} onChange={(event) => setGrace(Number(event.target.value))}>
              {graceOptions.map((option) => <option key={option.minutes} value={option.minutes}>{option.label}</option>)}
            </select>
          </label>
        </Confirm>
      )}
      {revoking && (
        <Confirm title="撤销凭证" confirmText="撤销" busy={busy} onConfirm={() => void submitRevoke()} onCancel={() => setRevoking(null)}
          text={"撤销后“" + (revoking.name ?? revoking.keyPrefix) + "”立即失效，使用它的请求都会被拒绝，且无法恢复。"} />
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

  async function loadEndpoints() {
    setLoadError(null);
    try {
      const value = readEndpoints(await client.request<unknown>(base + "/webhook-endpoints"));
      if (!value) throw new Error("端点列表暂时无法读取");
      setEndpoints(value);
      setSelected((current) => current ? value.find((e) => e.endpointId === current.endpointId) ?? null : null);
    } catch (failure) {
      setEndpoints(null);
      setLoadError(failure instanceof Error ? failure : new Error("端点列表暂时无法读取"));
    }
  }
  async function loadDeliveries(next: DeliveryFilter = filter) {
    setDeliveryError(null);
    try {
      const value = await client.request<{ items?: DeliveryItem[]; total?: number }>(base + "/webhook-deliveries?" + deliveryQuery(next));
      if (!Array.isArray(value?.items)) throw new Error("投递记录暂时无法读取");
      setDeliveries({ items: value.items, total: typeof value.total === "number" ? value.total : null });
    } catch (failure) {
      setDeliveries(null);
      setDeliveryError(failure instanceof Error ? failure : new Error("投递记录暂时无法读取"));
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
        if (key) setSecret({ title: "端点已创建，签名密钥如下", value: key });
        else setError(new Error("端点已创建，但响应中没有可显示的签名密钥，请轮换密钥"));
      } else {
        await client.request<unknown>(base + "/webhook-endpoints/" + encodeURIComponent(editing.endpointId), {
          method: "PUT", body: { url: form.url.trim(), eventTypes: form.eventTypes, expectedVersion: editing.version },
        });
      }
      setEditing(null);
      await loadEndpoints();
    } catch (failure) {
      if (failure instanceof RequestFailure && Object.keys(failure.fields).length) setIssues(failure.fields);
      setError(failure instanceof Error ? failure : new Error("保存未完成"));
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
        if (key) setSecret({ title: "签名密钥已轮换", value: key });
        else setError(new Error("轮换已完成，但响应中没有可显示的密钥"));
      } else {
        const value = await client.request<{ result?: string; httpStatus?: number | null; failureCode?: string | null }>(path + "/test-events", { method: "POST" });
        setNote(value?.result === "SUCCEEDED" ? "测试事件已送达（HTTP " + (value.httpStatus ?? "—") + "）。"
          : "测试事件未送达：" + (value?.failureCode ? failureLabels[value.failureCode] ?? value.failureCode : "请查看投递记录") + "。");
        await loadDeliveries();
      }
      setConfirm(null);
      await loadEndpoints();
    } catch (failure) {
      setError(failure instanceof Error ? failure : new Error("操作未完成"));
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
      setError(failure instanceof Error ? failure : new Error("投递详情暂时无法读取"));
    }
  }
  async function replay(id: string) {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await client.request<unknown>(base + "/webhook-deliveries/" + encodeURIComponent(id) + "/replay", { method: "POST" });
      setNote("已重新发送，结果请在投递记录中查看。");
      setDetail(null);
      await loadDeliveries();
    } catch (failure) {
      setError(failure instanceof RequestFailure && failure.status === 400 ? new Error(replayRejectedText)
        : failure instanceof Error ? failure : new Error("重发未完成"));
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
          <h1 id="webhooks-title">Webhook</h1>
          <p>平台在事件发生时向您的 HTTPS 地址发送签名通知。目前只会收到测试事件，业务事件上线后自动开始发送。</p>
        </div>
        {canManage && <button type="button" className="primary compact" disabled={full} title={full ? "最多 " + maxEndpoints + " 个端点（含已停用）" : undefined} onClick={() => openForm("new")}>新建端点</button>}
      </div>
      <ErrorNote error={error} />
      {note && <p className="notice" role="status">{note}</p>}
      {loadError && (
        <div className="notice error" role="alert">
          {loadError.message}
          <button type="button" className="text-button" onClick={() => void loadEndpoints()}>重试</button>
        </div>
      )}
      {!endpoints && !loadError && <p role="status">正在读取端点…</p>}
      {endpoints && endpoints.length === 0 && <p className="empty">还没有 Webhook 端点。</p>}
      {endpoints && endpoints.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead><tr><th>地址</th><th>订阅事件</th><th>状态</th><th></th></tr></thead>
            <tbody>
              {endpoints.map((endpoint) => (
                <tr key={endpoint.endpointId}>
                  <td className="break">{endpoint.url}</td>
                  <td>{endpoint.eventTypes.length} 种</td>
                  <td>{endpointStatusLabels[endpoint.status] ?? endpoint.status}</td>
                  <td><button type="button" className="text-button" onClick={() => { setNote(null); setSelected(endpoint); }}>详情</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <h2>投递记录</h2>
      <form className="filters" onSubmit={(event) => { event.preventDefault(); applyFilter({ ...filter, page: 1 }); }}>
        <select aria-label="端点" value={filter.endpointId} onChange={(event) => setFilter({ ...filter, endpointId: event.target.value })}>
          <option value="">全部端点</option>
          {endpoints?.map((e) => <option key={e.endpointId} value={e.endpointId}>{e.url}</option>)}
        </select>
        <select aria-label="事件类型" value={filter.eventType} onChange={(event) => setFilter({ ...filter, eventType: event.target.value })}>
          <option value="">全部事件</option>
          <option value="webhook.ping">测试事件</option>
          {webhookEventTypes.map((e) => <option key={e.code} value={e.code}>{e.label}</option>)}
        </select>
        <select aria-label="状态" value={filter.status} onChange={(event) => setFilter({ ...filter, status: event.target.value })}>
          <option value="">全部状态</option>
          {Object.entries(deliveryStatusLabels).map(([code, label]) => <option key={code} value={code}>{label}</option>)}
        </select>
        <input aria-label="开始时间" type="datetime-local" value={filter.from} onChange={(event) => setFilter({ ...filter, from: event.target.value })} />
        <input aria-label="结束时间" type="datetime-local" value={filter.to} onChange={(event) => setFilter({ ...filter, to: event.target.value })} />
        <button type="submit" className="secondary">查询</button>
      </form>
      {deliveryError && <div className="notice error" role="alert">{deliveryError.message}</div>}
      {!deliveries && !deliveryError && <p role="status">正在读取投递记录…</p>}
      {deliveries && deliveries.items.length === 0 && <p className="empty">没有符合条件的投递记录。</p>}
      {deliveries && deliveries.items.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead><tr><th>事件</th><th>状态</th><th>尝试次数</th><th>创建时间</th><th></th></tr></thead>
            <tbody>
              {deliveries.items.map((item) => (
                <tr key={item.deliveryId}>
                  <td>{webhookEventTypes.find((e) => e.code === item.eventType)?.label ?? (item.eventType === "webhook.ping" ? "测试事件" : item.eventType)}</td>
                  <td>{deliveryStatusLabels[item.status] ?? item.status}</td>
                  <td>{item.attemptCount}</td>
                  <td>{displayTime(item.createTime)}</td>
                  <td><button type="button" className="text-button" onClick={() => void openDetail(item.deliveryId, item.status)}>详情</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <div className="actions">
        <button type="button" className="secondary" disabled={filter.page <= 1} onClick={() => applyFilter({ ...filter, page: filter.page - 1 })}>上一页</button>
        <span>第 {filter.page} 页</span>
        <button type="button" className="secondary" disabled={!deliveries || deliveries.items.length < filter.size}
          onClick={() => applyFilter({ ...filter, page: filter.page + 1 })}>下一页</button>
      </div>

      {editing && (
        <div className="modal-backdrop" role="presentation">
          <form className="modal" role="dialog" aria-modal="true" aria-label={editing === "new" ? "新建端点" : "修改端点"}
            onSubmit={(event) => { event.preventDefault(); void submitForm(); }}>
            <h2>{editing === "new" ? "新建端点" : "修改端点"}</h2>
            <label>接收地址（HTTPS）
              <input value={form.url} inputMode="url" onChange={(event) => setForm({ ...form, url: event.target.value })} />
            </label>
            {issues.url && <small className="field-error">{issues.url}</small>}
            <fieldset>
              <legend>订阅事件</legend>
              <p className="hint">业务事件暂未开始发送，目前只能收到测试事件。</p>
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
              <button type="button" className="secondary" disabled={busy} onClick={() => setEditing(null)}>取消</button>
              <button type="submit" className="primary compact" disabled={busy}>{busy ? "正在保存…" : "保存"}</button>
            </div>
          </form>
        </div>
      )}

      {selected && (
        <aside className="drawer" role="dialog" aria-label="端点详情">
          <div className="drawer-head">
            <h2>端点详情</h2>
            <button type="button" className="text-button" onClick={() => setSelected(null)}>关闭</button>
          </div>
          <dl className="account">
            <dt>地址</dt><dd className="break">{selected.url}</dd>
            <dt>状态</dt><dd>{endpointStatusLabels[selected.status] ?? selected.status}</dd>
            <dt>订阅事件</dt><dd>{selected.eventTypes.map((code) => webhookEventTypes.find((e) => e.code === code)?.label ?? code).join("、") || "—"}</dd>
            <dt>密钥轮换次数</dt><dd>{selected.rotationNo ?? "—"}</dd>
            {selected.previousValidUntil && <><dt>旧密钥有效至</dt><dd>{displayTime(selected.previousValidUntil)}</dd></>}
          </dl>
          {canManage && (
            <div className="actions wrap">
              <button type="button" className="secondary" disabled={busy} onClick={() => openForm(selected)}>修改</button>
              <button type="button" className="secondary" disabled={busy} onClick={() => setConfirm("status")}>{selected.status === "ACTIVE" ? "停用" : "启用"}</button>
              <button type="button" className="secondary" disabled={busy} onClick={() => { setImmediate(false); setConfirm("rotate"); }}>轮换密钥</button>
              <button type="button" className="primary compact" disabled={busy || selected.status !== "ACTIVE"} onClick={() => void endpointAction("test")}>{busy ? "处理中…" : "发送测试事件"}</button>
            </div>
          )}
        </aside>
      )}
      {selected && confirm === "status" && (
        <Confirm title={selected.status === "ACTIVE" ? "停用端点" : "启用端点"} confirmText={selected.status === "ACTIVE" ? "停用" : "启用"} busy={busy}
          onConfirm={() => void endpointAction("status")} onCancel={() => setConfirm(null)}
          text={selected.status === "ACTIVE" ? "停用后不再向该地址发送事件，尚未发送的投递将被取消；重新启用不会补发。" : "启用后新产生的事件会发送到该地址。"} />
      )}
      {selected && confirm === "rotate" && (
        <Confirm title="轮换签名密钥" confirmText="生成新密钥" busy={busy} onConfirm={() => void endpointAction("rotate")} onCancel={() => setConfirm(null)}
          text="生成新签名密钥。默认 1 小时内同时用新旧密钥签名，方便您切换验签。">
          <label className="checkline">
            <input type="checkbox" checked={immediate} onChange={(event) => setImmediate(event.target.checked)} />
            旧密钥立即作废（之后的通知只用新密钥签名）
          </label>
        </Confirm>
      )}
      {detail && (
        <aside className="drawer" role="dialog" aria-label="投递详情">
          <div className="drawer-head">
            <h2>投递详情</h2>
            <button type="button" className="text-button" onClick={() => setDetail(null)}>关闭</button>
          </div>
          <dl className="account">
            <dt>事件 ID</dt><dd className="break">{detail.value.event?.eventId ?? "—"}</dd>
            <dt>事件类型</dt><dd>{detail.value.event?.eventType ?? "—"}</dd>
          </dl>
          <h3>发送尝试</h3>
          {(detail.value.attempts ?? []).length === 0 && <p className="empty">尚未发送。</p>}
          <ol className="attempts">
            {(detail.value.attempts ?? []).map((attempt) => (
              <li key={attempt.attemptNo}>
                第 {attempt.attemptNo} 次 · {displayTime(attempt.startedTime)} · {attempt.httpStatus ? "HTTP " + attempt.httpStatus : "无响应"}
                {attempt.failureCode && " · " + (failureLabels[attempt.failureCode] ?? attempt.failureCode)}
                {typeof attempt.durationMs === "number" && " · " + attempt.durationMs + " ms"}
              </li>
            ))}
          </ol>
          {canManage && canReplay(detail.status) && <div className="actions"><button type="button" className="primary compact" disabled={busy} onClick={() => void replay(detail.id)}>{busy ? "处理中…" : "重新发送"}</button></div>}
        </aside>
      )}
      {secret && <SecretModal title={secret.title} secret={secret.value} onClose={() => setSecret(null)} />}
    </section>
  );
}
