import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { LocaleSwitcher } from "../LocaleSwitcher.tsx";
import { useT } from "../i18n/react.tsx";
import type { TaskSummary } from "./progress.ts";
import type { TaskKey } from "./schema.ts";
import { initials } from "./ui.tsx";

/** 顶栏：品牌 | 入驻申请 …… 审核方 · 语言 · 头像；没有其他导航。 */
export function TopBar({ context, reviewer, account }: { context?: string; reviewer?: string | null; account?: string | null }) {
  const t = useT("onboarding");
  return <header className="console-bar">
    <Link className="console-brand" to="/onboarding">Paytech<span>{context ?? t("shell.context")}</span></Link>
    <div className="console-actions">
      {reviewer && <span className="ob-reviewer">{t("shell.reviewed_by", { name: reviewer })}</span>}
      <LocaleSwitcher />
      {account && <span className="avatar" aria-hidden="true">{initials(account)}</span>}
    </div>
  </header>;
}

/** 任务标题键：公司 / 个人信息随主体类型变化。 */
export function taskLabelKey(task: TaskKey, clientType: string): string {
  return task === "profile" ? (clientType === "INDIVIDUAL" ? "task.profile_individual" : "task.profile_organization")
    : task === "materials" ? (clientType === "INDIVIDUAL" ? "task.materials_individual" : "task.materials_organization") : "task." + task;
}

const dotTone: Record<string, string> = { done: "done", attention: "attention", active: "active", todo: "todo", locked: "todo" };

/**
 * 左侧任务栏（桌面）：当前任务浅蓝底；状态只用圆点；不可开始的任务置灰不可点
 * <li>移动端隐藏，改为步骤行"第 n 步，共 m 步 · 名称"+"概览"。</li>
 */
export function TaskRail({ appId, tasks, current, clientType, href }: {
  appId: string; tasks: TaskSummary[]; current: TaskKey | null; clientType: string; href: (task: TaskKey) => string;
}) {
  const t = useT("onboarding");
  return <nav className="ob-rail" aria-label={t("shell.tasks_label")}>
    <Link className="ob-back" to={"/onboarding/applications/" + appId}>{t("shell.back_to_overview")}</Link>
    <ol>{tasks.map((task) => <li key={task.key}>
      {task.state === "locked" && task.key !== current ? <span className="ob-rail-item locked"><i className={"ob-dot " + dotTone[task.state]} />{t(taskLabelKey(task.key, clientType))}</span>
        : <Link className={"ob-rail-item" + (task.key === current ? " current" : "")} to={href(task.key)}
          aria-current={task.key === current ? "step" : undefined}>
          <i className={"ob-dot " + dotTone[task.state]} aria-hidden="true" />{t(taskLabelKey(task.key, clientType))}
          <span className="sr-only">{t("task_state." + task.state)}</span></Link>}
    </li>)}</ol>
  </nav>;
}

/** 任务页布局：顶栏 + 左侧任务栏 + 内容；移动端内容上方是步骤行。 */
export function TaskLayout({ top, rail, step, children }: { top: ReactNode; rail: ReactNode; step: ReactNode; children: ReactNode }) {
  return <div className="ob-page">{top}
    <div className="ob-step-line">{step}</div>
    <div className="ob-task-layout">{rail}<main className="ob-main" id="main">{children}</main></div>
  </div>;
}
