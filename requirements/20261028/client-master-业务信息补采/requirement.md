# client-master：业务信息补采 · 需求

> 状态：**阶段 1 评审稿（2026-10-28）**。来源：[2026-10-28 客户上线决定](../../../agent-docs/decisions/2026-10-28-client-go-live-orchestration.md)（REQ-12）；上游讨论 [design-discuss/client-activation](../../../design-discuss/client-activation/requirement-plan.md) N2 承保输入与 9.5 节。确认记录见 [delivery.md](./delivery.md)；技术方案从阶段 2 起写入 [technique.md](./technique.md)。

## 1. 背景

- 风控承保（REQ-03）要判断交付周期、拒付暴露和要开通的支付方式，需要 Client 在申请时就填：期望支付方式、退款政策链接、交付方式（即时 / 预售天数 / 订阅）。
- 现状 `t_client_business_profile` 已有：`business_model_code`、`website_url`、`expected_volume`、`expected_ticket`、`currency`、`business_description`，**没有**期望支付方式、退款政策链接、交付方式。
- 入驻表单由采集 Schema（`t_onboarding_schema_field`，BUSINESS 分区）驱动，新增字段主要是 Schema DML + 资料表列 + 展示。

## 2. 目标

1. 入驻申请的业务信息分区新增三项采集：期望支付方式（多选）、退款政策链接、交付方式与预计交付天数。
2. 运营审核工作台与 Client 详情展示这三项；承保页（REQ-03）可读取。
3. 行业网站审查（N1 人工检查项）能直接点开退款政策链接核对。

## 3. 使用者与场景

| 使用者 | 场景 | 成功结果 |
|---|---|---|
| Client 管理员 | 填写入驻申请"业务信息" | 多选期望支付方式（按平台产品目录），填退款政策 URL，选交付方式；按 Schema 要求级别校验 |
| 合规审核员 | 工作台"申请"页签 | 看到三项；退款政策链接可点开；与网站审查检查项并列 |
| 风控 | 承保页（REQ-03） | 自动带出三项作为承保输入 |
| 商务 | 起草合同 | 看到 Client 期望的支付方式，作为开通项参考 |

## 4. 本期范围

| # | 范围 |
|---|---|
| R1 | `t_client_business_profile` 新增：`expected_payment_methods`（多值，编码来自 commercial 产品能力目录或 Platform 参考目录，阶段 2 定来源）、`refund_policy_url`、`fulfillment_type`（INSTANT / PRE_ORDER / SUBSCRIPTION / SERVICE）、`fulfillment_days`（PRE_ORDER 时必填） |
| R2 | 采集 Schema：AE / SA / EG 三辖区 ACTIVE Schema 新版本在 BUSINESS 分区加四个字段；要求级别：期望支付方式 REQUIRED、退款政策链接 EVENTUALLY（批准前必填，无网站的个人可 OPTIONAL，由 Schema 条件控制）、交付方式 REQUIRED、天数 `visible_when fulfillment_type=PRE_ORDER` |
| R3 | 草稿 / 提交 / 修订申请的读写与校验（沿用 Schema 驱动校验；URL 格式、天数范围） |
| R4 | 新控件：多选 SELECT（如 `MULTI_SELECT` 尚不存在则新增控件代码与前端实现） |
| R5 | 运营工作台"申请"页签与 Client 详情展示；BFF 聚合 |
| R6 | 对外读接口（client-master `-api`）增加三项，供 REQ-03 承保页与 commercial 合同页读取 |
| R7 | 文档：client-master 数据库文档、国家包 DML 说明、CURRENT_IMPLEMENTATION |

## 5. 不做项

- 不校验退款政策页面内容（人工检查项负责）。
- 不把期望支付方式自动变成合同开通项（商务决定）。
- 不改在途申请已冻结的 Schema 版本（新申请用新版本；在途申请不补填，承保页显示"未采集"）。
- 不做 Client 自助修改已批准资料（修订申请机制另有切片）。

## 6. 业务规则

| 编号 | 规则 |
|---|---|
| B1 | 期望支付方式编码必须在目录中 ACTIVE；目录来源阶段 2 定（倾向 commercial `t_product_capability` 的支付方式类，经 Platform 参考数据或只读接口暴露给表单） |
| B2 | 退款政策 URL 须为 https，与网站同域不强制 |
| B3 | `fulfillment_days` 只在 PRE_ORDER 时有值且 1–365 |
| B4 | Schema 新版本发布后，新申请冻结新版本；在途申请继续用旧版本（现有规则） |
| B5 | 三项进入 `content_hash` 计算（已批准资料摘要），随资料批准冻结 |

## 7. 逐系统变动点

| 系统 | 变动点 | 类型 |
|---|---|---|
| **client-master** | `t_client_business_profile` 四列；PDManer + DDL | 表 |
| | 三辖区 Schema 新版本 DML（`t_onboarding_schema` / `t_onboarding_schema_field`） | DML |
| | 草稿字段模型、提交校验、资料批准 / hash；`-api` 读响应与 admin 响应增字段 | 代码 / 接口 |
| | auto 测试：Schema 校验（必填 / 条件可见 / URL / 天数）、提交落库、读接口 | 测试 |
| **commercial 或 Platform** | 支付方式目录只读暴露给 client-bff（阶段 2 定：若 commercial 已有产品能力查询则复用） | 接口（可能无改动） |
| **client-console-bff / web** | 业务信息分区渲染新字段；多选控件；目录加载 | 页面 |
| **operations-console-bff / web** | 审核工作台"申请"页签与 Client 详情展示三项；退款政策链接可点开 | 页面 |
| **Platform i18n** | 字段标题 / 帮助文案键（命名空间 onboardingSchema）en / zh-CN | 语言包 |
| **文档** | client-master 数据库文档、国家包说明、CURRENT_IMPLEMENTATION | 文档 |

## 8. 验收标准

功能：

| 编号 | 标准 |
|---|---|
| AC-01 | 三辖区新申请的业务信息分区出现四个字段，按要求级别校验；PRE_ORDER 才显示并要求天数 |
| AC-02 | 期望支付方式只能选目录内 ACTIVE 编码；提交后落库为多值 |
| AC-03 | 退款政策 URL 非 https 被拒并提示 |
| AC-04 | 在途旧版本申请不受影响，可照常提交 / 审核 |
| AC-05 | client-master 读接口返回三项；运营工作台与 Client 详情展示 |
| AC-06 | 资料批准后 `content_hash` 包含三项 |

视觉 / 交互：

| 编号 | 标准 |
|---|---|
| AC-07 | 门户：多选控件符合现有表单风格，已选项可移除，错误提示在字段下；en / zh-CN 完整；1440 / 390 宽度可用 |
| AC-08 | 运营：退款政策链接新开页；三项在"申请"页签业务信息组内，空值显示"未采集" |

## 9. 已确认事实、假设与待确认

已确认：决定 REQ-12 范围；Schema 驱动表单与三辖区 Schema 已有。

待确认：

| 编号 | 问题 | 建议 |
|---|---|---|
| Q1 | 期望支付方式目录来源：commercial 产品能力目录，还是 Platform 参考数据新建"支付方式目录" | commercial 产品能力目录（它已是开通项的编码来源，避免两份） |
| Q2 | 退款政策链接对 INDIVIDUAL 且无网站的申请是否 OPTIONAL | OPTIONAL（Schema 条件 `basic.hasWebsite=false`，阶段 2 核实条件字段） |
| Q3 | 交付方式枚举四值是否够 | 够；行业特殊交付在 `business_description` 说明 |

## 10. 阶段安排

默认八阶段。阶段 2 核实：多选控件是否已有；Schema `visible_when` 是否支持所需条件；commercial 产品能力目录的只读暴露方式。SQL 应用、部署、提交分别授权。
