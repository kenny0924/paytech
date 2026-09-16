import {
  accessIndex as ix,
  codeField as code,
  defineTable,
  field,
  hashField as hash,
  primaryKey as pk,
  stringField as str,
  timeField as time,
  uniqueIndex as uk,
} from "../schema-dsl.mjs";

export const TABLES = Object.freeze([
  defineTable({
    key: "compensation_event",
    name: "本地补偿事件",
    comment: "本服务 Handler 的可恢复任务；uk_comp_event_identity(event_key,event_type,sub_event_type)；状态 INIT/PROCESSING/ERROR/FAILED；可选最小 JSON Object 最大 4 KiB UTF-8，禁止敏感值、Vault Token 和已落库上下文；Claim/Lease 使用 lock_version，安全默认值与执行协议服从现行补偿指南；Payload 最小化：payload_json 可空或为最大 4 KiB UTF-8 的不可变 JSON Object；禁止 Vault Token、敏感值及可从本库恢复的上下文。；Lease 状态矩阵：PROCESSING 必须同时具有未过期的 lock_owner 与 lock_until；INIT、ERROR、FAILED 不得保留活动 Lease。；Lease Holder 写回：只有持有当前活动 Lease 的执行者可以完成、删除或回写该次尝试；旧执行者不得覆盖新 Lease Holder 的结果。；登记原子性：权威业务事实、业务 History 与 compensation_event 必须在同一本地事务提交；事务内任一点失败全部回滚。；成功删除：Handler 只有取得确定成功结果后才允许删除 compensation_event；MQ Handler 必须等待 Broker Confirm。；失败保留：达到最大重试次数后必须进入 FAILED、保留记录并告警；人工重开必须记录 Actor、Reason 与 Ticket 后回到 ERROR 等待重新 Claim。；Lease 接管：PROCESSING 的 Lease 超时后允许重新 Claim；Claim 与完成/回写都必须使用 lock_version CAS。",
    fields: [
      field("id", "BIGINT", "NN", "补偿事件标识", "补偿执行记录的内部唯一标识。", {"primaryKey": true}),
      field("event_key", "VARCHAR(128)", "NN", "补偿业务键", "领域事件或本地恢复动作的全局稳定幂等键。", {"characterSet": "utf8mb4", "collation": "utf8mb4_0900_bin"}),
      field("event_type", "VARCHAR(64)", "NN", "补偿类型", "需要发布或本地恢复执行的领域事件类型代码。取值来源：CCT 唯一 Compensation Handler Catalog；非固定枚举。", {"source": "宿主 Handler 受控目录", "characterSet": "utf8mb4", "collation": "utf8mb4_0900_bin"}),
      field("sub_event_type", "VARCHAR(64)", "NN", "补偿业务子类型", "同一 Handler 下的业务子类型；无业务子类型时使用短横线占位。取值来源：所属 Handler 的子类型目录，无业务子类型时固定短横线占位；非固定枚举。", {"source": "宿主 Handler 受控目录", "characterSet": "utf8mb4", "collation": "utf8mb4_0900_bin"}),
      field("payload_json", "VARCHAR(4096)", "N", "最小补充载荷", "可空或最大 4 KiB UTF-8 的不可变 JSON Object；禁止 Vault Token、敏感值及可从本库恢复的上下文。", {"characterSet": "utf8mb4", "collation": "utf8mb4_0900_bin"}),
      field("execute_status", "VARCHAR(16)", "NN", "执行状态", "补偿任务当前的可恢复执行状态。允许值：INIT、PROCESSING、ERROR、FAILED。", {"values": ["INIT", "PROCESSING", "ERROR", "FAILED"], "characterSet": "ascii", "collation": "ascii_bin", "defaultValue": "'INIT'"}),
      field("execute_count", "INT", "NN", "尝试次数", "补偿任务已经尝试执行的累计次数。", {"defaultValue": "0"}),
      field("next_execute_at", "DATETIME(6)", "NN", "下次执行时间", "补偿任务下一次允许被 Claim 的 UTC 微秒精度时刻。", {"defaultValue": "CURRENT_TIMESTAMP(6)"}),
      field("lock_owner", "VARCHAR(64)", "N", "租约持有者", "当前 Claim 补偿任务的执行实例标识。"),
      field("lock_until", "DATETIME(6)", "N", "租约截止时间", "当前补偿任务执行租约到期的 UTC 微秒精度时刻。"),
      field("last_error_code", "VARCHAR(64)", "N", "最后错误代码", "补偿执行最近一次失败的稳定错误分类代码。取值来源：CCT Compensation Error Catalog；非固定枚举。", { source: "Foundation 与宿主错误目录" }),
      field("last_error_summary", "VARCHAR(1000)", "N", "最后错误摘要", "补偿执行最近一次失败的脱敏错误摘要，不保存敏感原文。"),
      field("lock_version", "BIGINT", "NN", "租约并发版本", "补偿任务 Claim 与完成回写使用的独立技术 CAS 版本，不是业务 Revision 或来源版本。", {"defaultValue": "1"}),
    ],
    auditFieldOverrides: {
      create_time: field("create_time", "DATETIME(6)", "NN", "创建时间", "数据库记录创建的 UTC 时间，精确到微秒。", {"defaultValue": "CURRENT_TIMESTAMP(6)"}),
      update_time: field("update_time", "DATETIME(6)", "NN", "更新时间", "数据库记录最后一次更新的 UTC 时间，精确到微秒。", {"defaultValue": "CURRENT_TIMESTAMP(6)"}),
    },
    indexes: [
      { ...uk("uk_comp_event_identity", "event_key", "event_type", "sub_event_type"), comment: "Unique index for compensation_event on event_key, event_type, sub_event_type" },
      { ...ix("idx_comp_event_ready", "execute_status", "next_execute_at", "id"), comment: "Access index for compensation_event on execute_status, next_execute_at, id" },
      { ...ix("idx_comp_event_lease", "execute_status", "lock_until", "id"), comment: "Access index for compensation_event on execute_status, lock_until, id" },
    ],
  }),
  defineTable({
    key: "command_idempotency",
    name: "命令幂等记录",
    comment: "保存认证调用边界内 HTTP 或内部命令的请求摘要与稳定响应引用",
    fields: [
      pk("id", "命令幂等标识", "命令幂等记录的稳定业务标识"),
      hash("caller_scope", "规范调用方范围哈希"),
      code("operation_type", "幂等操作类型"),
      str("idempotency_key", "幂等键", "NN", 128),
      hash("request_hash", "幂等请求哈希"),
      str("response_ref", "幂等响应引用", "N", 255),
      code("status", "幂等执行状态", "NN", ["PROCESSING", "SUCCEEDED", "FAILED"]),
      time("expire_time", "幂等记录到期时间"),
    ],
    indexes: [uk("uk_command_idempotency", "caller_scope", "operation_type", "idempotency_key"), ix("idx_command_idem_expire", "status", "expire_time")],
  }),
]);
