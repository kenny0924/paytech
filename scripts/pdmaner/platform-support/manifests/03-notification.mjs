// 2026-09-21 普通 Email 首期；不含回调、抑制或历史模板版本。
import { defineTable, field, relation, uniqueIndex, accessIndex } from "../schema-dsl.mjs";

export const TABLES = Object.freeze([
  defineTable({
    key: "notification_request",
    name: "通知请求",
    comment: "通知请求的当前权威事实；普通 Email 首期，不保存认证秘密或正文快照",
    fields: [
      field("request_id", "BIGINT", "NN", "请求主键", "请求主键", {primaryKey: true}),
      field("tenant_id", "BIGINT", "N", "归属租户", "请求归属的 Platform Access Tenant 稳定标识；平台通知为空，非收件人归属", {}),
      field("source_system", "VARCHAR(64)", "NN", "来源系统", "模块调用方确定的可信来源系统；与 source_event_key 共同标识一次通知意图", {source: "可信模块调用方来源系统目录", characterSet: "utf8mb4", collation: "utf8mb4_bin"}),
      field("source_event_key", "VARCHAR(128)", "NN", "来源业务标识", "来源系统内永久唯一的通知业务标识，不是 HTTP Idempotency-Key", {characterSet: "utf8mb4", collation: "utf8mb4_bin"}),
      field("notification_template_id", "BIGINT", "NN", "模板标识", "模板定义 ID，不是历史版本 ID", {}),
      field("default_locale", "VARCHAR(35)", "NN", "默认语言", "默认语言", {characterSet: "utf8mb4", collation: "utf8mb4_bin"}),
      field("default_parameters", "JSON", "N", "默认参数", "受理后不可变的普通字符串参数 JSON；最多 64 项、16 KiB；禁止认证秘密", {}),
      field("notification_route_id", "BIGINT", "NN", "路由标识", "本次选择的路由", {}),
    ],
    indexes: [
      uniqueIndex("uk_notify_source", "source_system", "source_event_key"),
    ],
    relations: [
      relation("tenant_id", "access_tenant", "tenant_id"),
      relation("notification_template_id", "notification_template", "template_id"),
      relation("notification_route_id", "notification_route", "route_id"),
    ],
  }),
  defineTable({
    key: "notification_delivery",
    name: "通知投递",
    comment: "通知投递的当前权威事实；普通 Email 首期，不保存认证秘密或正文快照",
    fields: [
      field("delivery_id", "BIGINT", "NN", "投递主键", "投递主键", {primaryKey: true}),
      field("request_id", "BIGINT", "NN", "所属 Request", "所属 Request", {}),
      field("address_vault_token", "VARCHAR(255)", "NN", "通知地址引用", "EMAIL_ADDRESS_V1 稳定邮箱引用；Purpose 为 PLATFORM_NOTIFICATION_EMAIL_V1；仅外发前允许 Reveal，禁止日志及补偿 Payload。SMS 尚未实现。", {characterSet: "utf8mb4", collation: "utf8mb4_bin"}),
      field("locale", "VARCHAR(35)", "N", "语言", "收件人语言，缺失时继承 Request.default_locale", {characterSet: "utf8mb4", collation: "utf8mb4_bin"}),
      field("parameters", "JSON", "N", "收件人参数", "收件人普通字符串参数 JSON，按键覆盖 Request 默认值；NULL 或空对象不覆盖；禁止认证秘密", {}),
      field("status", "VARCHAR(32)", "NN", "当前投递状态", "当前投递状态", {values: ["PENDING", "PROCESSING", "RETRY_WAIT", "SENT", "DELIVERED", "FAILED", "UNKNOWN"]}),
      field("attempt_count", "INT", "NN", "发送尝试数", "已创建发送尝试数量，初始为 0；查询不递增", {defaultValue: "0"}),
      field("next_retry_time", "DATETIME(6)", "N", "下次重试时间", "下次允许新建发送的 UTC 微秒时间；无重试时为空，查询不使用", {}),
      field("version", "INT", "NN", "并发版本", "并发更新版本，初始为 1；不是历史修订号", {defaultValue: "1"}),
    ],
    indexes: [
      uniqueIndex("uk_notify_recipient", "request_id", "address_vault_token"),
    ],
    relations: [
      relation("request_id", "notification_request", "request_id"),
    ],
  }),
  defineTable({
    key: "notification_attempt",
    name: "通知发送尝试",
    comment: "通知发送尝试的当前权威事实；普通 Email 首期，不保存认证秘密或正文快照",
    fields: [
      field("attempt_id", "BIGINT", "NN", "发送尝试主键", "发送尝试主键", {primaryKey: true}),
      field("delivery_id", "BIGINT", "NN", "所属 Delivery", "所属 Delivery", {}),
      field("provider_id", "BIGINT", "NN", "本次实际使用的发送账号配置身份", "本次实际使用的发送账号配置身份", {}),
      field("provider_request_no", "VARCHAR(128)", "NN", "供应商请求号", "按供应商协议生成且外发前固定的请求号；与 provider_id 组合唯一", {characterSet: "utf8mb4", collation: "utf8mb4_bin"}),
      field("provider_message_id", "VARCHAR(255)", "N", "供应商消息标识", "Provider 返回的消息标识", {}),
      field("status", "VARCHAR(32)", "NN", "发送尝试状态", "发送提交的确认状态", {values: ["PROCESSING", "ACCEPTED", "FAILED", "UNKNOWN"]}),
      field("response_code", "VARCHAR(64)", "N", "初次发送调用的安全响应码", "初次发送调用的安全响应码", {}),
      field("resolution_source", "VARCHAR(32)", "N", "结果确认来源", "结果确认来源，如 SEND_RESPONSE、PROVIDER_QUERY", {values: ["SEND_RESPONSE", "PROVIDER_QUERY"]}),
      field("resolution_code", "VARCHAR(64)", "N", "后续查询确认结果的安全响应码", "后续查询确认结果的安全响应码", {}),
      field("started_time", "DATETIME(6)", "NN", "本次发送尝试开始时间", "本次发送尝试开始时间", {}),
      field("finished_time", "DATETIME(6)", "N", "初次发送调用结束时间", "初次发送调用结束时间", {}),
      field("resolved_time", "DATETIME(6)", "N", "结果确认时间", "原提交获得明确结果的时间；未确认时为空", {}),
    ],
    indexes: [
      uniqueIndex("uk_notify_provider_request", "provider_request_no", "provider_id"),
      accessIndex("idx_notify_attempt_delivery", "delivery_id", "status"),
    ],
    relations: [
      relation("delivery_id", "notification_delivery", "delivery_id"),
      relation("provider_id", "notification_provider", "provider_id"),
    ],
  }),
  defineTable({
    key: "notification_template",
    name: "通知模板",
    comment: "通知模板的当前权威事实；普通 Email 首期，不保存认证秘密或正文快照",
    fields: [
      field("template_id", "BIGINT", "NN", "模板定义主键", "模板定义主键", {primaryKey: true}),
      field("template_code", "VARCHAR(64)", "NN", "唯一模板编码，表达通知用途", "唯一模板编码，表达通知用途", {characterSet: "utf8mb4", collation: "utf8mb4_bin"}),
      field("medium", "VARCHAR(16)", "NN", "EMAIL 或 SMS", "EMAIL 或 SMS", {values: ["EMAIL", "SMS"]}),
      field("status", "VARCHAR(16)", "NN", "模板启停状态", "模板启停状态", {values: ["ACTIVE", "DISABLED"]}),
      field("version", "INT", "NN", "并发版本", "并发更新版本，初始为 1；不是历史修订号", {defaultValue: "1"}),
    ],
    indexes: [
      uniqueIndex("uk_notify_template_code", "template_code"),
    ],
    relations: [
    ],
  }),
  defineTable({
    key: "notification_template_content",
    name: "通知语言内容",
    comment: "通知语言内容的当前权威事实；普通 Email 首期，不保存认证秘密或正文快照",
    fields: [
      field("content_id", "BIGINT", "NN", "内容主键", "内容主键", {primaryKey: true}),
      field("template_id", "BIGINT", "NN", "所属模板定义", "所属模板定义", {}),
      field("locale", "VARCHAR(35)", "NN", "语言", "内容语言", {characterSet: "utf8mb4", collation: "utf8mb4_bin"}),
      field("subject_template", "TEXT", "N", "邮件标题模板", "邮件标题模板；SMS 不使用", {}),
      field("body_template", "TEXT", "NN", "正文模板", "正文模板", {}),
      field("version", "INT", "NN", "并发版本", "并发更新版本，初始为 1；不是历史修订号", {defaultValue: "1"}),
    ],
    indexes: [
      uniqueIndex("uk_notify_template_locale", "template_id", "locale"),
    ],
    relations: [
      relation("template_id", "notification_template", "template_id"),
    ],
  }),
  defineTable({
    key: "notification_provider",
    name: "通知发送账号",
    comment: "通知发送账号的当前权威事实；普通 Email 首期，不保存认证秘密或正文快照",
    fields: [
      field("provider_id", "BIGINT", "NN", "Provider 主键", "Provider 主键", {primaryKey: true}),
      field("provider_code", "VARCHAR(64)", "NN", "唯一编码，标识具体发送账号", "唯一编码，标识具体发送账号", {characterSet: "utf8mb4", collation: "utf8mb4_bin"}),
      field("medium", "VARCHAR(16)", "NN", "EMAIL 或 SMS", "EMAIL 或 SMS", {values: ["EMAIL", "SMS"]}),
      field("status", "VARCHAR(16)", "NN", "启用、停用", "启用、停用", {values: ["ACTIVE", "DISABLED"]}),
      field("version", "INT", "NN", "并发版本", "并发更新版本，初始为 1；不是历史修订号", {defaultValue: "1"}),
    ],
    indexes: [
      uniqueIndex("uk_notify_provider_code", "provider_code"),
    ],
    relations: [
    ],
  }),
  defineTable({
    key: "notification_route",
    name: "通知主备路由",
    comment: "通知主备路由的当前权威事实；普通 Email 首期，不保存认证秘密或正文快照",
    fields: [
      field("route_id", "BIGINT", "NN", "路由主键", "路由主键", {primaryKey: true}),
      field("notification_template_id", "BIGINT", "NN", "模板标识", "对应模板", {}),
      field("primary_provider_id", "BIGINT", "NN", "主 Provider", "主 Provider", {}),
      field("fallback_provider_id", "BIGINT", "N", "备用 Provider", "备用 Provider", {}),
      field("status", "VARCHAR(16)", "NN", "启用、停用", "启用、停用", {values: ["ACTIVE", "DISABLED"]}),
      field("version", "INT", "NN", "并发版本", "并发更新版本，初始为 1；不是历史修订号", {defaultValue: "1"}),
    ],
    indexes: [
      uniqueIndex("uk_notify_template_route", "notification_template_id"),
    ],
    relations: [
      relation("notification_template_id", "notification_template", "template_id", "1:1"),
      relation("primary_provider_id", "notification_provider", "provider_id"),
      relation("fallback_provider_id", "notification_provider", "provider_id"),
    ],
  }),
]);
