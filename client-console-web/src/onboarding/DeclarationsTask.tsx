import { useNavigate } from "react-router-dom";
import { flatten } from "../onboarding.ts";
import { useT } from "../i18n/react.tsx";
import { appPath, taskPath, useApplication } from "./context.tsx";
import { DeclarationRow, useDeclare } from "./DeclarationRow.tsx";
import { declarationState } from "./progress.ts";
import { availableTasks } from "./schema.ts";
import { TaskFooter, nextTask } from "./TaskPage.tsx";
import { taskLabelKey } from "./Shell.tsx";
import { Failure } from "./ui.tsx";

/**
 * 声明（C3）：每条陈述一行复选框，勾选即记录（账号、时间、IP 与浏览器由服务端记录）
 * <li>取消勾选追加一条 FALSE 记录，不删除历史。</li>
 * <li>条款 / 隐私告知可在侧边面板阅读，确认时带上所读版本；确认后改了资料的声明显示失效，需重新确认。</li>
 */
export function DeclarationsTask() {
  const { app, schema, client } = useApplication(), t = useT("onboarding");
  const navigate = useNavigate();
  const { busy, error, declare } = useDeclare();
  const next = nextTask(availableTasks(schema), "declarations");
  const stale = schema.declarations.filter((item) => declarationState(app, item.declarationType) === "stale").length;
  const values = flatten(app.values);
  const declarant = [app.clientType === "INDIVIDUAL" ? [values["individual.givenName"], values["individual.familyName"]].filter(Boolean).join(" ") : "",
    client.current()?.context.emailMasked].filter(Boolean).join(" · ");
  return <>
    <h1>{t("task.declarations")}</h1>
    <p className="ob-intro">{t("task_intro.declarations")}</p>
    <Failure error={error} />
    {stale > 0 && <p className="notice ob-declarations-stale" role="status">{t("declarations.stale", { count: stale })}</p>}
    <div className="ob-declarations">{schema.declarations.map((item) =>
      <DeclarationRow key={item.declarationType} item={item} busy={busy !== null} compactStale
        onDeclare={(def, value) => void declare(def, value)} />)}</div>
    {declarant && <p className="ob-muted ob-declarant">{t("declarations.declarant", { name: declarant })}</p>}
    {app.editable && <TaskFooter saving={busy !== null} busy={false}
      primary={next ? t(next === "review" ? "action.review_and_submit" : "action.continue_to", { task: t(taskLabelKey(next, app.clientType)) }) : t("action.back_to_overview")}
      onPrimary={() => navigate(next ? taskPath(app.applicationId, next) : appPath(app.applicationId))}
      onLater={() => navigate(appPath(app.applicationId))} />}
  </>;
}
