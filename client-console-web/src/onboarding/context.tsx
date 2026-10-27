import { createContext, useContext } from "react";
import type { ApiRequest, createAuthClient } from "../auth.ts";
import type { Application, FormSchema } from "./model.ts";
import type { TaskKey } from "./schema.ts";

export type Client = ReturnType<typeof createAuthClient>;
/** 一个申请页面树共享的数据：申请（含需求清单与审核结果）、冻结版本的 Schema 与请求函数。 */
export type ApplicationContext = {
  client: Client;
  request: ApiRequest;
  app: Application;
  schema: FormSchema;
  reload: () => Promise<Application>;
  /** 重新读取 Schema（参考数据选项加载失败后重试）。 */
  reloadSchema: () => Promise<void>;
  /** 写命令成功后更新"已保存"时间。 */
  touch: (savedAt?: string) => void;
  savedAt: string | null;
  fail: (error: unknown) => void;
};
export const Ctx = createContext<ApplicationContext | null>(null);
export function useApplication(): ApplicationContext {
  const value = useContext(Ctx);
  if (!value) throw new Error("ApplicationContext missing");
  return value;
}
export const appPath = (id: string) => "/onboarding/applications/" + id;
export const taskPath = (id: string, task: TaskKey) => appPath(id) + "/tasks/" + task;
export const fixPath = (id: string) => appPath(id) + "/fix";
