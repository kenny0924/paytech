import { Link } from "react-router-dom";
import { flatten } from "../onboarding.ts";
import { useLocale, useT } from "../i18n/react.tsx";
import { taskPath, useApplication } from "./context.tsx";
import type { Application, FormSchema } from "./model.ts";
import { taskSummaries } from "./progress.ts";
import type { TaskSummary } from "./progress.ts";
import { isPersonRole, taskBlocks } from "./schema.ts";
import { TopBar, taskLabelKey } from "./Shell.tsx";
import { SavedIndicator, StatusDot, useSchemaText } from "./ui.tsx";
import { countryName } from "./widgets.tsx";
import { WithdrawAction } from "./Results.tsx";

const tone = { done: "done", attention: "attention", active: "active", todo: "todo", locked: "todo" } as const;

/** 申请标题副行：法定名称 / 姓名 · 国家 · 组织形式。 */
export function subjectLine(app: Application, schema: FormSchema, locale: string, label: (key: string, code: string) => string): string {
  const values = flatten(app.values);
  const name = app.clientType === "ORGANIZATION" ? values["organization.legalName"]
    : [values["individual.givenName"], values["individual.familyName"]].filter(Boolean).join(" ");
  const type = schema.organizationTypes.find((item) => item.code === app.organizationTypeCode);
  return [name, countryName(app.jurisdictionCode, locale), type ? label(type.labelKey, type.code) : ""].filter(Boolean).join(" · ");
}

/**
 * 入驻首页（任务清单，N1）：每行一个任务，右侧圆点状态 + ›；暂不可开始的任务整行置灰
 * <li>进度只写"已完成 n / m 项"，不用进度环或百分比。</li>
 * <li>右栏"需要准备"列出本 Schema 的材料槽，按国家与组织形式不同。</li>
 */
export function Home() {
  const { app, schema, savedAt, client } = useApplication(), t = useT("onboarding"), label = useSchemaText(), { locale } = useLocale();
  const tasks = taskSummaries(app, schema);
  const counted = tasks.filter((task) => task.key !== "review");
  const done = counted.filter((task) => task.state === "done").length;
  const reviewer = app.responsiblePartnerName ?? null;
  const materials = schema.materials.filter((slot) => slot.documentTypes.length);
  return <div className="ob-page">
    <TopBar reviewer={reviewer} account={client.current()?.context.emailMasked} />
    <div className="ob-home">
      <main className="ob-home-main" id="main">
        <h1>{t("home.title")}</h1>
        <p className="ob-subtitle">{subjectLine(app, schema, locale, label)}</p>
        <div className="ob-progress-row">
          <strong>{t("home.progress", { done, total: counted.length })}</strong>
          <SavedIndicator savedAt={savedAt} saving={false} />
        </div>
        <ol className="ob-tasks">{tasks.map((task) => <TaskRow key={task.key} task={task} />)}</ol>
        <p className="ob-footnote">{reviewer ? t("home.footnote_partner", { name: reviewer }) : t("home.footnote_generic")}</p>
        {app.editable && <div className="ob-mobile-only"><h2 className="ob-side-title">{t("returned.quit_title")}</h2><WithdrawAction /></div>}
      </main>
      <aside className="ob-home-side" aria-label={t("home.prepare_title")}>
        {materials.length > 0 && <><h2 className="ob-side-title">{t("home.prepare_title")}</h2>
          <ul className="ob-plain-list">{materials.map((slot) => <li key={slot.requirementCode + slot.subjectRole}>
            {label("onboarding.material." + slot.documentTypes[0].toLowerCase(), slot.documentTypes[0])}
            {isPersonRole(slot.subjectRole) && <span className="ob-muted"> · {t("home.per_person")}</span>}</li>)}</ul></>}
        {app.editable && <><h2 className="ob-side-title">{t("returned.quit_title")}</h2><WithdrawAction /></>}
      </aside>
    </div>
  </div>;

  function TaskRow({ task }: { task: TaskSummary }) {
    const title = t(taskLabelKey(task.key, app.clientType));
    const body = <>
      <span className="ob-task-text"><strong>{title}</strong><small>{describe(task)}</small></span>
      <StatusDot tone={tone[task.state]}>{t("task_state." + task.state)}</StatusDot>
      <span className="ob-chevron" aria-hidden="true">›</span>
    </>;
    return <li>{task.state === "locked"
      ? <span className="ob-task locked" aria-disabled="true">{body}</span>
      : <Link className="ob-task" to={taskPath(app.applicationId, task.key)}>{body}</Link>}</li>;
  }

  function describe(task: TaskSummary): string {
    if (task.key === "people") return task.total ? t("home.people_count", { count: task.total }) : t("task_desc.people");
    if (task.key === "materials") return t("home.materials_count", { total: task.total ?? 0, done: task.done ?? 0 });
    if (task.key === "declarations") return t("home.declarations_count", { total: task.total ?? 0, done: task.done ?? 0 });
    if (task.key === "review") return task.state === "locked" ? t("task_desc.review_locked") : t("task_desc.review");
    const groups = taskBlocks(schema, task.key, "PRIMARY").map((block) => label(block.group.labelKey, block.group.group));
    const unique = [...new Set(groups)].slice(0, 4);
    return unique.length ? new Intl.ListFormat(locale, { style: "long", type: "conjunction" }).format(unique) : "";
  }
}

