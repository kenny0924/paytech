import { useCallback, useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { Navigate, Route, Routes, useNavigate, useParams } from "react-router-dom";
import { RequestFailure } from "../auth.ts";
import type { ApiOptions } from "../auth.ts";
import { useT } from "../i18n/react.tsx";
import type { Client } from "./context.tsx";
import { Ctx } from "./context.tsx";
import { api, readApplication, readSchema } from "./model.ts";
import type { Application, FormSchema } from "./model.ts";
import { phaseOf } from "./progress.ts";
import { readCheckpoint, runUpload } from "../onboarding.ts";
import { uploadKeyPrefix, uploads } from "./upload.ts";
import { Home } from "./Home.tsx";
import { TaskPage } from "./TaskPage.tsx";
import { Remediation } from "./Remediation.tsx";
import { ResultPage } from "./Results.tsx";
import { TopBar } from "./Shell.tsx";
import { Failure } from "./ui.tsx";

const validId = (value: string | undefined): value is string =>
  !!value && /^[1-9][0-9]{0,18}$/.test(value) && BigInt(value) <= 9223372036854775807n;

/**
 * 一个申请的页面树：读申请（BFF 聚合：草稿 + 需求清单 + 审核结果 + 文件状态），再按申请冻结的辖区 / 主体 / 组织形式读 Schema
 * <li>DRAFT 进任务清单首页；RETURNED 进退回首页与补件视图；其余状态进对应结果页。</li>
 * <li>401 回登录；其他读取失败留在本页并可重试。</li>
 */
export function ApplicationRoot({ client }: { client: Client }) {
  const { applicationId } = useParams(), navigate = useNavigate(), t = useT("onboarding");
  const grant = useSyncExternalStore(client.subscribe, client.current);
  const [app, setApp] = useState<Application | null>(null), [schema, setSchema] = useState<FormSchema | null>(null);
  const [error, setError] = useState<Error | null>(null), [savedAt, setSavedAt] = useState<string | null>(null);
  const request = useCallback(<T,>(path: string, options?: ApiOptions) => client.request<T>(path, options), [client]);
  const fail = useCallback((failure: unknown) => {
    if (failure instanceof RequestFailure && failure.status === 401) { client.clear(); navigate("/login", { replace: true }); return; }
    setError(failure instanceof Error ? failure : new Error(t("page.action_failed")));
  }, [client, navigate]);
  const reload = useCallback(async () => {
    if (!validId(applicationId)) throw new Error(t("page.not_found"));
    const value = readApplication(await request(api.application(applicationId)));
    setApp(value);
    setSavedAt((previous) => value.savedAt ?? previous);
    return value;
  }, [applicationId, request]);
  const loadSchema = useCallback(async (value: Application) => {
    if (!value.jurisdictionCode) throw new Error(t("schema.unavailable"));
    return readSchema(await request(api.schema(value.jurisdictionCode, value.clientType, value.organizationTypeCode, value.applicationId)), t("schema.unavailable"));
  }, [request]);
  useEffect(() => {
    let alive = true;
    setApp(null); setSchema(null); setError(null);
    (async () => {
      const form = await loadSchema(await reload());
      if (alive) setSchema(form);
    })().catch((failure) => { if (alive) fail(failure); });
    return () => { alive = false; };
  }, [applicationId]);
  // 只替换 Schema（含参考数据选项）；失败交给页面级错误。
  const reloadSchema = useCallback(async () => {
    if (!app) return;
    try { setSchema(await loadSchema(app)); } catch (failure) { fail(failure); }
  }, [app, loadSchema, fail]);
  // 离开材料页后仍在检查中的上传：申请页后台按恢复坐标续传并关联，完成后刷新申请。
  useEffect(() => {
    if (!app) return;
    const running = new Set<string>();
    const timer = window.setInterval(() => {
      const prefix = uploadKeyPrefix(client.current()?.context.accountId, app.applicationId);
      let keys: string[] = [];
      try { keys = Object.keys(sessionStorage).filter((key) => key.startsWith(prefix)); } catch { return; }
      for (const key of keys) {
        if (running.has(key) || uploads.isClaimed(key)) continue;
        const state = readCheckpoint(sessionStorage.getItem(key), app.applicationId);
        if (!state) continue;
        running.add(key);
        void (async () => {
          for (let attempt = 0; attempt < 60 && !uploads.isClaimed(key); attempt++) {
            await runUpload(state, request);
            if (state.phase === "DONE") break;
            await new Promise((resolve) => setTimeout(resolve, 2000));
          }
          if (state.phase === "DONE") { try { sessionStorage.removeItem(key); } catch { /* 存储不可用时忽略 */ } await reload(); }
          else if (!uploads.isClaimed(key)) sessionStorage.setItem(key, JSON.stringify(state));
        })().catch(() => {}).finally(() => running.delete(key));
      }
    }, 3000);
    return () => clearInterval(timer);
  }, [app?.applicationId]);
  const context = useMemo(() => app && schema ? {
    client, request, app, schema, reload, reloadSchema, savedAt, fail,
    touch: (value?: string) => setSavedAt(value ?? new Date().toISOString()),
  } : null, [app, schema, savedAt, client, request, reload, reloadSchema, fail]);
  if (!context) return <div className="ob-page"><TopBar account={grant?.context.emailMasked} />
    <main className="ob-narrow">{error ? <><Failure error={error} />
      <button className="secondary" onClick={() => window.location.reload()}>{t("page.reload")}</button></>
      : <p className="ob-loading" role="status">{t("page.loading")}</p>}</main></div>;
  const phase = phaseOf(context.app);
  return <Ctx.Provider value={context}>
    <Routes>
      <Route index element={phase === "draft" ? <Home /> : <ResultPage phase={phase} />} />
      <Route path="tasks/:task" element={<TaskPage />} />
      <Route path="fix" element={phase === "returned" ? <Remediation /> : <Navigate to=".." replace />} />
      <Route path="*" element={<Navigate to="." replace />} />
    </Routes>
  </Ctx.Provider>;
}
