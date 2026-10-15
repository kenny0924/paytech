// 2026-10-15 Client Webhook 投递：订阅并入端点；URL 明文；签名密钥值在 Vault；重试与租约复用公共补偿任务。
import { defineTable, field, relation, uniqueIndex, accessIndex } from "../schema-dsl.mjs";

const BIN = {characterSet: "utf8mb4", collation: "utf8mb4_bin"};

export const TABLES = Object.freeze([
  defineTable({
    key: "webhook_endpoint",
    name: "Webhook 端点",
    comment: "CLIENT 租户的 Webhook 接收地址与订阅事件类型；每个 Client 最多 5 个，由运营代配置",
    fields: [
      field("endpoint_id", "BIGINT", "NN", "端点主键", "Webhook 端点主键", {primaryKey: true}),
      field("tenant_id", "BIGINT", "NN", "归属租户", "端点所属的 CLIENT Access Tenant", {}),
      field("url", "VARCHAR(1000)", "NN", "接收地址", "Client 的 HTTPS 接收地址明文；不含 userinfo 与 fragment，修改后下一次尝试即使用新地址", BIN),
      field("event_types", "JSON", "NN", "订阅事件类型", "订阅的公开事件类型字符串数组，非空且不重复；取消订阅后尚未完成的投递被取消", {}),
      field("status", "VARCHAR(32)", "NN", "端点状态", "端点状态；停用后不再生成新投递，未完成的投递被取出时取消", {values: ["ACTIVE", "DISABLED"]}),
      field("version", "INT", "NN", "并发版本", "并发更新版本，初始为 1", {defaultValue: "1"}),
    ],
    indexes: [
      accessIndex("idx_webhook_endpoint_tenant", "tenant_id"),
    ],
    relations: [
      relation("tenant_id", "access_tenant", "tenant_id"),
    ],
  }),
  defineTable({
    key: "webhook_signing_secret",
    name: "Webhook 签名密钥",
    comment: "端点签名密钥的 Vault 引用与轮换宽限；同一端点同时只有一把 ACTIVE，宽限内的 RETIRED 密钥也参与签名",
    fields: [
      field("signing_secret_id", "BIGINT", "NN", "签名密钥主键", "Webhook 签名密钥主键", {primaryKey: true}),
      field("endpoint_id", "BIGINT", "NN", "所属端点", "所属 Webhook 端点", {}),
      field("rotation_no", "INT", "NN", "轮换序号", "端点内第几把密钥，从 1 开始", {}),
      field("secret_vault_token", "VARCHAR(255)", "NN", "密钥 Vault 引用", "WEBHOOK_SIGNING_SECRET_V1 的 REFERENCE Token；Purpose 为 WEBHOOK_SIGNING；仅发送前 Reveal，禁止日志及补偿 Payload", BIN),
      field("status", "VARCHAR(32)", "NN", "密钥状态", "密钥状态；轮换后旧密钥变为 RETIRED", {values: ["ACTIVE", "RETIRED"]}),
      field("signing_slot", "INT", "N", "当前密钥占位", "ACTIVE 时为 1，否则为空；与端点组成唯一键，保证同时只有一把 ACTIVE", {}),
      field("valid_until", "DATETIME(6)", "N", "签名截止时间", "RETIRED 后仍参与签名的 UTC 截止时间：宽限轮换为轮换时间加 1 小时，立即作废为轮换时间；ACTIVE 时为空", {}),
      field("version", "INT", "NN", "并发版本", "并发更新版本，初始为 1", {defaultValue: "1"}),
    ],
    indexes: [
      uniqueIndex("uk_webhook_secret_rotation", "endpoint_id", "rotation_no"),
      uniqueIndex("uk_webhook_secret_active", "endpoint_id", "signing_slot"),
    ],
    relations: [
      relation("endpoint_id", "webhook_endpoint", "endpoint_id"),
    ],
  }),
  defineTable({
    key: "webhook_event",
    name: "Webhook 事件",
    comment: "来源系统提交的冻结公开事件；受理后不可变，Retry 与 Replay 发送同一 Envelope",
    audit: "immutable",
    fields: [
      field("event_id", "BIGINT", "NN", "事件主键", "Platform 内部事件主键，不作为公开 eventId", {primaryKey: true}),
      field("tenant_id", "BIGINT", "NN", "归属租户", "按来源 Client 解析得到的 CLIENT Access Tenant", {}),
      field("source_system", "VARCHAR(64)", "NN", "来源系统", "通过白名单的调用方服务名", {source: "内部 HTTP 调用方服务名白名单", ...BIN}),
      field("source_event_key", "VARCHAR(128)", "NN", "公开事件标识", "来源冻结的公开 eventId；同一 Client 与来源系统内永久唯一", BIN),
      field("event_type", "VARCHAR(64)", "NN", "事件类型", "公开事件类型", {source: "Webhook 公开事件类型目录", ...BIN}),
      field("payload_schema_version", "INT", "NN", "载荷结构版本", "Envelope 的 schemaVersion", {}),
      field("source_version", "INT", "NN", "来源资源版本", "来源资源版本；只用于重复提交比较", {}),
      field("source_payload_hash", "CHAR(64)", "NN", "对象摘要", "data.object 的 RFC 8785 规范字节 SHA-256 小写十六进制", {}),
      field("payload_hash", "CHAR(64)", "NN", "信封摘要", "完整 Envelope 的 RFC 8785 规范字节 SHA-256 小写十六进制；每次发送前用于校验重新规范化的字节", {}),
      field("payload", "JSON", "NN", "冻结信封", "冻结的公开 Envelope；不含 Token、PII 或内部标识，规范化后不超过 4096 字节", {}),
    ],
    indexes: [
      uniqueIndex("uk_webhook_event_source", "tenant_id", "source_event_key", "source_system"),
    ],
    relations: [
      relation("tenant_id", "access_tenant", "tenant_id"),
    ],
  }),
  defineTable({
    key: "webhook_delivery",
    name: "Webhook 投递",
    comment: "一个事件到一个端点的投递，含原始投递与人工 Replay；重试节奏与执行租约由补偿任务承担",
    fields: [
      field("delivery_id", "BIGINT", "NN", "投递主键", "Webhook 投递主键；也是补偿任务的事件键", {primaryKey: true}),
      field("webhook_event_id", "BIGINT", "NN", "所属事件", "所属 Webhook 事件", {}),
      field("tenant_id", "BIGINT", "NN", "归属租户", "与事件、端点相同的 CLIENT Access Tenant；用于列表查询", {}),
      field("endpoint_id", "BIGINT", "NN", "目标端点", "投递目标端点", {}),
      field("event_type", "VARCHAR(64)", "NN", "事件类型", "冗余事件类型，用于列表筛选与订阅复核", {source: "Webhook 公开事件类型目录", ...BIN}),
      field("status", "VARCHAR(32)", "NN", "投递状态", "投递状态；SUCCEEDED、DEAD、CANCELLED 为终态", {values: ["CREATED", "PROCESSING", "RETRY_WAIT", "SUCCEEDED", "DEAD", "CANCELLED"]}),
      field("attempt_count", "INT", "NN", "尝试次数", "已创建的尝试数量，初始为 0", {defaultValue: "0"}),
      field("last_attempt_time", "DATETIME(6)", "N", "最近尝试时间", "最近一次尝试开始的 UTC 微秒时间；尚未尝试时为空", {}),
      field("replay_of_delivery_id", "BIGINT", "N", "Replay 来源投递", "人工 Replay 时指向原投递；原始投递为空", {}),
      field("requested_by", "BIGINT", "N", "Replay 操作人", "人工 Replay 的 Operations 账号；原始投递为空", {}),
      field("version", "INT", "NN", "并发版本", "并发更新版本，初始为 1", {defaultValue: "1"}),
    ],
    indexes: [
      accessIndex("idx_webhook_delivery_event", "webhook_event_id"),
      accessIndex("idx_webhook_delivery_tenant", "tenant_id", "create_time"),
      accessIndex("idx_webhook_delivery_endpoint", "endpoint_id", "create_time"),
    ],
    relations: [
      relation("webhook_event_id", "webhook_event", "event_id"),
      relation("tenant_id", "access_tenant", "tenant_id"),
      relation("endpoint_id", "webhook_endpoint", "endpoint_id"),
    ],
  }),
  defineTable({
    key: "webhook_attempt",
    name: "Webhook 投递尝试",
    comment: "每次 HTTP 投递尝试的地址、签名密钥与结果；发送前登记为 PROCESSING，结束后写入结果",
    fields: [
      field("attempt_id", "BIGINT", "NN", "尝试主键", "Webhook 投递尝试主键", {primaryKey: true}),
      field("delivery_id", "BIGINT", "NN", "所属投递", "所属 Webhook 投递", {}),
      field("attempt_no", "INT", "NN", "尝试序号", "投递内的尝试序号，从 1 开始", {}),
      field("request_url", "VARCHAR(1000)", "NN", "请求地址", "本次实际请求的端点地址", BIN),
      field("signing_secret_id", "BIGINT", "NN", "主签名密钥", "本次签名使用的 ACTIVE 密钥", {}),
      field("grace_secret_id", "BIGINT", "N", "宽限签名密钥", "宽限期内参与签名的 RETIRED 密钥；无宽限时为空", {}),
      field("status", "VARCHAR(32)", "NN", "尝试状态", "尝试状态；PROCESSING 遗留表示发送中断，恢复时记为 FAILED", {values: ["PROCESSING", "SUCCEEDED", "FAILED"]}),
      field("http_status", "INT", "N", "响应状态码", "收到响应时的 HTTP 状态码", {}),
      field("failure_code", "VARCHAR(64)", "N", "失败代码", "失败原因代码；成功时为空", {values: ["HTTP_STATUS", "TIMEOUT", "CONNECT_FAILED", "TLS_FAILED", "DNS_FAILED", "BLOCKED_ADDRESS", "INTERRUPTED"]}),
      field("duration_ms", "INT", "N", "耗时毫秒", "从发出请求到结束的毫秒数；未完成时为空", {}),
      field("started_time", "DATETIME(6)", "NN", "开始时间", "尝试开始的 UTC 微秒时间", {}),
      field("finished_time", "DATETIME(6)", "N", "结束时间", "尝试结束的 UTC 微秒时间；PROCESSING 时为空", {}),
    ],
    indexes: [
      uniqueIndex("uk_webhook_attempt_no", "delivery_id", "attempt_no"),
    ],
    relations: [
      relation("delivery_id", "webhook_delivery", "delivery_id"),
    ],
  }),
]);
