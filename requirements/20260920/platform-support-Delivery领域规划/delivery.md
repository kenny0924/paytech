# Notification 需求确认与交付记录

## 1. 阶段与动作范围

- 2026-09-20：用户要求规划 Delivery 领域模型，确认重点为 Notification、Webhook 保留边界；随后讨论数据库与内部服务接口。
- 数据讨论期间，用户明确要求先不修改文件；该阶段的字段和接口调整仅在会话中进行，早期需求文件没有同步成为最新结论。
- 用户随后要求整理已讨论的表和内部服务需求，明确“Operations 接口先不做，不混合”。助手先提供完整的会话整理稿。
- 用户认可整理稿并询问是否已写入需求文档。本轮据此把已确认结论同步到本需求目录，并做文档静态检查。
- 后续用户明确先只处理邮箱，并要求记录“Provider 先 Mock”。首期限定 Email，真实邮件供应商选型和接入后续再定；SMS 保留后续规划。本次只记录决定，没有实现 Mock。
- 以上为 2026-09-20 文档整理阶段。2026-09-21 用户明确要求开始实现，并依次确认：重复邮箱拒绝；普通邮件先行、认证邮件后续；只开放模块内调用，HTTP 等强服务身份后开放。实现包含模块代码、物理设计和测试，数据库执行单独确认，未授权提交或部署。

## 2. 确认决定

| 主题 | 本轮结论 |
|---|---|
| 通知统一入口 | Email/SMS、普通通知和认证通知统一进入 Notification；认证业务状态仍由认证域负责 |
| 首期媒介与接入 | 只处理 Email，邮件 Provider 先 Mock；SMS 与真实供应商接入后续再定 |
| 接收人 | 单人和批量统一用列表，每条 Delivery 独立发送，每封邮件只有一个收件人 |
| 公共信息与覆盖 | Request 默认语言/参数，Delivery 可覆盖语言和参数 |
| 重试字段 | Delivery 保留 attempt_count 和 next_retry_time |
| Attempt | 删除 attempt_no 和 provider_code，result 改为 status |
| Provider 请求号 | provider_request_key 改为 provider_request_no，唯一键顺序为请求号在前、provider_id 在后 |
| 结果未知 | 查询原 Attempt；不盲目重发，不在 UNKNOWN 时切备用 Provider |
| 回调与抑制 | 从本期方案移除 provider_callback、suppression 两张表及相关能力 |
| 模板 | 定义与语言内容分为两张表，不保留历史版本，重试可以使用最新文案 |
| Provider 配置 | 仅保留账号业务字段，Endpoint、凭据、适配器和能力字段暂不进入表 |
| 路由 | 每模板一条当前主备配置，不做租户差异路由 |
| 收件地址输入 | email 与 phoneNumber 分开，服务端分别映射 dataType |
| 内部提交 | 保留 submitNotification；响应为 requestId、acceptedCount |
| 查询草案 | getNotification 已认可后暂缓继续细化；其他已讨论查询暂不提供 |
| 管理接口 | Operations 单独规划，不混入本轮内部通知需求 |

## 3. 相对早期草案的记录

早期九表草案包含回调、抑制、模板与 Provider 历史版本、正文快照、默认语言回退、非空 scope_key、业务输入 MAC、领域 Lease/Fencing 等提案。用户逐项讨论后，本轮已按上述决定收敛为七表当前方案。

本目录的 requirement.md、technique.md、data-design.md 已按当前结论更新，早期提案仅作为本节和差异表中的讨论历史；不能再从旧提案恢复已删除字段。正式 Platform 模型、SQL、规范中的前序描述未在本轮迁移。

下列讨论建议未被采纳为当前需求：address_masked、提交响应 deliveryIds。2026-09-21 重复邮箱拒绝决定补充了 Delivery 地址唯一约束。取消与人工恢复接口也未获本期确认。

用户同意无模板历史版本，原“重试保持受理时文案”的保证因此取消；Request/Delivery 普通输入保持原值和读取当前模板文案是两个不同约束。

## 4. 交付物与验收映射

| 交付物 | 内容 | 验收映射 |
|---|---|---|
| [requirement.md](./requirement.md) | 范围、确认需求、未决项 | AC-01—AC-08 |
| [technique.md](./technique.md) | 领域关系、事务、状态、查询恢复、前序差异 | AC-01、02、04、07、08 |
| [data-design.md](./data-design.md) | 七表字段、参考类型及已讨论约束 | AC-03、04、07、08 |
| [api.md](./api.md) | 内部提交 Demo、已认可但暂停展开的查询草案 | AC-02、05、06、08 |

关键未决项只在[需求第 6 节](./requirement.md#open-items)维护，不能在实现中自行补齐。本期模块实施参数与恢复合同见 technique.md 第 8 节；跨服务身份、真实供应商和认证临时材料继续单独处理。

## 5. 验证与实际状态

链接、示例 JSON、字段清单和旧方案残留等文档检查结果，统一记录在[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-delivery-domain-plan-20260920)。

2026-09-20 仅完成文档静态检查；2026-09-21 进入实现验证，实际构建、测试和本地 SQL 状态统一记录在当前台账。Mock 证据不代表真实邮件送达。
