import { useSyncExternalStore } from "react";
import { useNavigate } from "react-router-dom";
import { uploads } from "./upload.ts";
import { useT } from "../i18n/react.tsx";
import { appPath, taskPath, useApplication } from "./context.tsx";
import { isPersonRole, availableTasks } from "./schema.ts";
import { DocumentSlot } from "./DocumentSlot.tsx";
import { TaskFooter, nextTask } from "./TaskPage.tsx";
import { taskLabelKey } from "./Shell.tsx";

/**
 * 公司 / 个人材料（C3）：主体材料槽逐个列出；关系人证件在人员抽屉里上传
 * <li>槽位、可选性、时效全部来自 Schema 合并的程序材料（CP-1）。</li>
 */
export function MaterialsTask() {
  const { app, schema, reload } = useApplication(), t = useT("onboarding"), navigate = useNavigate();
  const slots = schema.materials.filter((slot) => !isPersonRole(slot.subjectRole));
  const next = nextTask(availableTasks(schema), "materials");
  const transferring = useSyncExternalStore(uploads.subscribe, uploads.activeCount) > 0;
  return <>
    <h1>{t(taskLabelKey("materials", app.clientType))}</h1>
    <p className="ob-intro">{t("task_intro.materials")}</p>
    <div className="ob-slots">{slots.map((slot) =>
      <DocumentSlot key={slot.requirementCode} slot={slot} subjectPartyId={app.subjectPartyId} onChanged={reload} />)}</div>
    {!slots.length && <p className="ob-muted">{t("materials.none")}</p>}
    {transferring && <p className="ob-muted ob-wait" role="status">{t("upload.wait")}</p>}
    {app.editable && <TaskFooter saving={false} busy={false} disabled={transferring} laterDisabled={transferring}
      primary={next ? t("action.continue_to", { task: t(taskLabelKey(next, app.clientType)) }) : t("action.back_to_overview")}
      onPrimary={() => navigate(next ? taskPath(app.applicationId, next) : appPath(app.applicationId))}
      onLater={() => navigate(appPath(app.applicationId))} />}
  </>;
}
