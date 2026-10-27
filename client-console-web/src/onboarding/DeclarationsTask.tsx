import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { flatten } from "../onboarding.ts";
import { useT } from "../i18n/react.tsx";
import { appPath, taskPath, useApplication } from "./context.tsx";
import { api } from "./model.ts";
import { declared } from "./progress.ts";
import { availableTasks } from "./schema.ts";
import { TaskFooter, nextTask } from "./TaskPage.tsx";
import { taskLabelKey } from "./Shell.tsx";
import { Failure, useSchemaText } from "./ui.tsx";

/**
 * 声明（C3）：每条陈述一个复选框，勾选即记录（账号、时间、IP 与浏览器由服务端记录）
 * <li>取消勾选追加一条 FALSE 记录，不删除历史。</li>
 */
export function DeclarationsTask() {
  const { app, schema, request, reload, touch, client } = useApplication(), t = useT("onboarding"), label = useSchemaText();
  const navigate = useNavigate();
  const [busy, setBusy] = useState<string | null>(null), [error, setError] = useState<Error | null>(null);
  const next = nextTask(availableTasks(schema), "declarations");
  async function toggle(type: string, value: boolean) {
    setBusy(type); setError(null);
    try {
      await request(api.declarations(app.applicationId), { method: "POST", key: crypto.randomUUID(), body: { declarationType: type, declaredValue: value ? "TRUE" : "FALSE" } });
      touch();
      await reload();
    } catch (failure) { setError(failure as Error); }
    finally { setBusy(null); }
  }
  const values = flatten(app.values);
  const declarant = [app.clientType === "INDIVIDUAL" ? [values["individual.givenName"], values["individual.familyName"]].filter(Boolean).join(" ") : "",
    client.current()?.context.emailMasked].filter(Boolean).join(" · ");
  return <>
    <h1>{t("task.declarations")}</h1>
    <p className="ob-intro">{t("task_intro.declarations")}</p>
    <Failure error={error} />
    <div className="ob-declarations">{schema.declarations.map((item) => {
      const checked = declared(app, item.declarationType);
      return <label className="ob-declaration checkline" key={item.declarationType} id={"declaration-" + item.declarationType}>
        <input type="checkbox" checked={checked} disabled={!app.editable || busy !== null}
          onChange={(event) => void toggle(item.declarationType, event.target.checked)} />
        <span>{label(item.labelKey, item.declarationType)}</span>
      </label>;
    })}</div>
    {declarant && <p className="ob-muted ob-declarant">{t("declarations.declarant", { name: declarant })}</p>}
    {app.editable && <TaskFooter saving={busy !== null} busy={false}
      primary={next ? t(next === "review" ? "action.review_and_submit" : "action.continue_to", { task: t(taskLabelKey(next, app.clientType)) }) : t("action.back_to_overview")}
      onPrimary={() => navigate(next ? taskPath(app.applicationId, next) : appPath(app.applicationId))}
      onLater={() => navigate(appPath(app.applicationId))} />}
  </>;
}
