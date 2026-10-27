import { useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { Link, Navigate, useLocation, useNavigate, useParams } from "react-router-dom";
import { useT } from "../i18n/react.tsx";
import { appPath, taskPath, useApplication } from "./context.tsx";
import { api } from "./model.ts";
import { fieldRequirementIssues, taskSummaries } from "./progress.ts";
import { SCHEMA_NAMESPACE, availableTasks, taskBlocks } from "./schema.ts";
import type { TaskKey } from "./schema.ts";
import { errorText, fieldPaths, serverIssues } from "./draft.ts";
import { RequestFailure } from "../auth.ts";
import { SchemaFields, summaryItems, useDraftForm, useFieldLabel } from "./FormTask.tsx";
import { PeopleTask } from "./PeopleTask.tsx";
import { MaterialsTask } from "./MaterialsTask.tsx";
import { DeclarationsTask } from "./DeclarationsTask.tsx";
import { ReviewTask } from "./ReviewTask.tsx";
import { TaskLayout, TaskRail, TopBar, taskLabelKey } from "./Shell.tsx";
import { Busy, ErrorSummary, Failure, SavedIndicator, focusAnchor} from "./ui.tsx";

const taskKeys = new Set<string>(["profile", "address", "people", "materials", "declarations", "review"]);

/** 任务页外壳：左侧任务栏、移动端步骤行、页面切换时焦点落在 h1。 */
export function TaskPage() {
  const { task } = useParams(), { app, schema, client } = useApplication(), t = useT("onboarding");
  const tasks = availableTasks(schema);
  if (!task || !taskKeys.has(task) || !tasks.includes(task as TaskKey)) return <Navigate to={appPath(app.applicationId)} replace />;
  const key = task as TaskKey;
  const summaries = taskSummaries(app, schema, app.status === "RETURNED" ? app.review?.errors ?? [] : []);
  if (key === "review" && app.editable && summaries.find((item) => item.key === "review")?.state === "locked" && app.status === "DRAFT")
    return <Navigate to={appPath(app.applicationId)} replace />;
  const index = tasks.indexOf(key);
  const step = <><span>{t("shell.step", { index: index + 1, total: tasks.length, name: t(taskLabelKey(key, app.clientType)) })}</span>
    <Link to={appPath(app.applicationId)}>{t("shell.overview")}</Link></>;
  const page: Record<TaskKey, ReactNode> = {
    profile: <FormTask task="profile" key="profile" />, address: <FormTask task="address" key="address" />,
    people: <PeopleTask />, materials: <MaterialsTask />, declarations: <DeclarationsTask />, review: <ReviewTask />,
  };
  return <TaskLayout top={<TopBar reviewer={app.responsiblePartnerName} account={client.current()?.context.emailMasked} />}
    rail={<TaskRail appId={app.applicationId} tasks={summaries} current={key} clientType={app.clientType} href={(item) => taskPath(app.applicationId, item)} />}
    step={step}><FocusTitle key={key}>{page[key]}</FocusTitle></TaskLayout>;
}

/** 切换任务后把焦点移到新页面的 h1；从错误汇总跳来时改为聚焦并高亮目标字段。 */
function FocusTitle({ children }: { children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null), location = useLocation();
  const focus = (location.state as { focus?: string } | null)?.focus;
  useEffect(() => {
    window.scrollTo({ top: 0 });
    if (focus) { const timer = window.setTimeout(() => focusAnchor(focus), 60); return () => clearTimeout(timer); }
    const heading = ref.current?.querySelector<HTMLElement>("h1");
    if (heading) { heading.tabIndex = -1; heading.focus({ preventScroll: true }); }
  }, [focus]);
  return <div className="ob-enter" ref={ref}>{children}</div>;
}

/** 下一项任务（跳过已完成的任务不做，按顺序）；最后一项之后是核对并提交。 */
export function nextTask(tasks: TaskKey[], current: TaskKey): TaskKey | null {
  const index = tasks.indexOf(current);
  return index >= 0 && index < tasks.length - 1 ? tasks[index + 1] : null;
}

/** 页脚操作：左侧保存状态，右侧"保存并稍后继续" + 写动作结果的主按钮。 */
export function TaskFooter({ saving, primary, onPrimary, onLater, busy, disabled, failed, onRetry, laterDisabled }: {
  saving: boolean; primary: string; onPrimary: () => void; onLater?: () => void; busy: boolean; disabled?: boolean; failed?: boolean;
  onRetry?: () => void; laterDisabled?: boolean;
}) {
  const { savedAt } = useApplication(), t = useT("onboarding"), tc = useT("common");
  return <footer className="ob-actions">
    {failed && !saving ? <span className="ob-saved failed" aria-live="polite">{t("save.failed")}
      {onRetry && <> · <button type="button" className="text-button" onClick={onRetry}>{t("save.retry")}</button></>}</span>
      : <SavedIndicator savedAt={savedAt} saving={saving} />}
    <div className="ob-actions-buttons">
      {onLater && <button type="button" className="secondary" disabled={busy || laterDisabled} onClick={onLater}>{t("action.save_later")}</button>}
      <button type="button" className="primary" disabled={busy || disabled} onClick={onPrimary}>
        <Busy active={busy} label={tc("status.processing")}>{primary}</Busy></button>
    </div>
  </footer>;
}

/** 公司 / 个人信息、地址与联系方式：Schema 字段表单。 */
function FormTask({ task }: { task: "profile" | "address" }) {
  const { app, schema, request, touch, reload, fail } = useApplication(), t = useT("onboarding");
  const navigate = useNavigate();
  const blocks = taskBlocks(schema, task, "PRIMARY");
  const tasks = availableTasks(schema), next = nextTask(tasks, task);
  const [busy, setBusy] = useState(false), [error, setError] = useState<Error | null>(null), [summary, setSummary] = useState(false);
  const form = useDraftForm(app.values, schema, blocks, async (body) => {
    await request(api.draft(app.applicationId), { method: "PATCH", body });
    touch();
  });
  const disabled = !app.editable;
  const labelOf = useFieldLabel(blocks, schema, form.values);
  // 需求清单里本页字段的非"缺少"类问题（如已停用的编码）：打开即挂到字段并显示页顶汇总，不让任务只标"需要处理"却找不到原因。
  // 文案在国家包语言包加载后（tSchema 变化）重新取；只替换本处挂上且用户尚未改动的错误。
  const tSchema = useT(SCHEMA_NAMESPACE), seeded = useRef<Record<string, string>>({});
  useEffect(() => {
    const paths = new Set(blocks.flatMap((block) => block.fields.flatMap((field) => fieldPaths(field, schema, form.values).map((item) => item.path))));
    const issues = fieldRequirementIssues(app, paths);
    if (!Object.keys(issues).length) return;
    const texts = Object.fromEntries(Object.entries(issues).map(([path, code]) => [path, errorText(code)]));
    const before = { ...seeded.current };
    form.setErrors((previous) => {
      const next = { ...previous };
      for (const [path, text] of Object.entries(texts))
        if (path in before ? next[path] === before[path] : next[path] === undefined) next[path] = text;
      return next;
    });
    seeded.current = { ...before, ...texts };
    setSummary(true);
  }, [app.requirements, tSchema]);
  async function leave(target: string, validate: boolean) {
    setError(null);
    if (validate && form.validate().length) { setSummary(true); return; }
    setBusy(true);
    try {
      const ok = await form.flush();
      if (!ok && validate) { setSummary(true); return; }
      const fresh = await reload();
      form.acceptServer(fresh.values);
      navigate(target);
    } catch (failure) {
      // 保存失败不离开本页：字段级错误进页顶汇总，其余显示本地化说明；会话失效回登录。
      if (failure instanceof RequestFailure && failure.status === 401) { fail(failure); return; }
      if (Object.keys(serverIssues(failure, schema)).length) setSummary(true);
      setError(failure as Error);
    }
    finally { setBusy(false); }
  }
  const items = summary ? summaryItems("f", blocks, schema, form.errors, labelOf, (name, message) => t("form.summary_item", { label: name, message })) : [];
  const title = t(taskLabelKey(task, app.clientType));
  return <>
    <ErrorSummary items={items} />
    <h1>{title}</h1>
    <p className="ob-intro">{t("task_intro." + (task === "profile" ? (app.clientType === "INDIVIDUAL" ? "profile_individual" : "profile_organization") : "address"))}</p>
    <Failure error={error} />
    <form className="ob-form" noValidate onSubmit={(event) => { event.preventDefault(); void leave(next ? taskPath(app.applicationId, next) : appPath(app.applicationId), true); }}>
      <SchemaFields prefix="f" blocks={blocks} schema={schema} form={form} disabled={disabled} countryCode={app.jurisdictionCode} />
      {!disabled && <TaskFooter saving={form.saving} failed={form.failed} busy={busy}
        onRetry={() => void form.flush().then(() => setError(null)).catch((failure) => {
          if (Object.keys(serverIssues(failure, schema)).length) setSummary(true);
          setError(failure as Error);
        })}
        primary={next ? t("action.continue_to", { task: t(taskLabelKey(next, app.clientType)) }) : t("action.back_to_overview")}
        onPrimary={() => void leave(next ? taskPath(app.applicationId, next) : appPath(app.applicationId), true)}
        onLater={() => void leave(appPath(app.applicationId), false)} />}
    </form>
  </>;
}
