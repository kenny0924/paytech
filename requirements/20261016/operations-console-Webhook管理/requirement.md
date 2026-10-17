# Operations Webhook 管理需求

> 状态：阶段 1 已确认（2026-10-16，Q-01—Q-05 按建议），阶段 2 已确认，阶段 3 评审稿待确认。确认记录见 [delivery.md](./delivery.md)。

## 1. 背景与来源

[Client Webhook 投递](../../20261015/platform-support-Webhook投递/requirement.md)已在 Platform 实现，2026-10-16 已部署 mor-test。Platform 提供 Operations 代 Client 配置用的管理接口 `/webhook-admin/v1`（9 个），已登记权限 `WEBHOOK_READ` / `WEBHOOK_MANAGE`，test 中 PLATFORM_ADMIN 已获授权。该需求明确"Operations 页面、BFF 转发、Gateway 路由本期不做"，所以运营后台现在看不到 Webhook 菜单。本需求把这些接口接到运营后台。

当前事实（2026-10-16）：

| 项 | 现状 |
|---|---|
| Platform 管理接口 | `WebhookManagementApi`：端点列表、新建、修改、启停、密钥轮换、测试事件、投递列表、投递详情、Replay；每次调用重检 Operations 会话，读需 `WEBHOOK_READ`、写需 `WEBHOOK_MANAGE`；写请求带 `reason` 并记审计 |
| 端点规则 | 每个 Client 最多 5 个端点；只允许 https 公网地址（保存与每次发送都做 DNS 校验）；订阅事件为 V1 八种（order.paid / cancelled / expired / funds_available，refund.created / succeeded / failed / cancelled） |
| 密钥 | 存 Vault；明文只在新建与轮换的响应中出现一次（`Cache-Control: no-store`）；轮换默认旧密钥再参与签名 1 小时，可选立即作废 |
| 投递查询 | 按 Client 必填 + 时间范围（≤31 天）分页，可按端点、事件类型、状态、eventId 筛选；详情含冻结 Envelope 与每次尝试 |
| Client 选择 | Platform Webhook 接口只接收 Client ID，不提供 Client 搜索；现有"租户访问"页面能按名称查租户，但需要租户读取权限 |
| Gateway / BFF / Web | 无 `/webhook-admin` 路由、无 BFF 转发、无菜单与页面、无 Console Resource |

## 2. 使用者与目标

| 使用者 | 目标 |
|---|---|
| 运营 / 技术支持 | 代 Client 登记、修改、停用接收地址和订阅事件；新建或轮换时把密钥安全地交给 Client；发测试事件确认对方能收到 |
| 运营排查人员 | 按 Client、时间、状态、eventId 查投递，看到每次尝试的结果码，对已结束的投递人工 Replay |
| 平台管理员 | 通过角色控制谁能看、谁能改；每次修改有审计 |

## 3. 本期范围（建议）

1. **Client 选择**：页面先选 Client（按 Client ID 或名称搜索，见 Q-01），再显示该 Client 的端点和投递。
2. **端点**：列表（地址、订阅事件、状态、当前密钥序号、旧密钥宽限截止时间、更新时间）；新建（地址、事件多选、原因）；修改；启用 / 停用（二次确认，说明停用后未完成的投递会被取消，重新启用不补发）。
3. **密钥**：新建成功和轮换成功后弹窗**只显示一次**密钥明文，提供复制按钮，并提示"关闭后无法再次查看"；轮换可选"立即作废旧密钥"（默认宽限 1 小时，二次确认说明影响）。
4. **测试事件**：对端点发送 `webhook.ping`，页面显示对方返回的 HTTP 状态或失败原因。
5. **投递记录**：列表（eventId、事件类型、端点、状态、尝试次数、下次重试时间、创建时间）；筛选与分页；详情显示冻结的事件 Envelope 和每次尝试（时间、HTTP 状态、失败代码、耗时）；对已结束的投递 Replay（填写原因）。
6. **权限与审计**：新增"Webhook 管理"菜单及只读 / 维护两级按钮权限，复用已有的 `WEBHOOK_READ` / `WEBHOOK_MANAGE`，注册 Console Resource 与 Gateway 路由；写操作由 Platform 记审计（已实现），审计页面显示中文动作名。
7. **分层**：Gateway `/api/operations/webhook-admin/v1/**` → Operations BFF 简单转发（会话 Header 由 BFF 填入）→ Platform；页面放 Operations Web，交互与通知管理、Vault 等现有配置页一致。

## 4. 明确不做

| 项 | 说明 |
|---|---|
| Client Console 自助配置 | 首期只做 Operations 代配置（Webhook 投递决定 D-02） |
| 再次查看已有密钥 | 密钥只在生成时出现一次；忘记就轮换 |
| 删除端点 | Platform 没有删除接口，只停用 |
| 修改投递、取消进行中的投递、批量 Replay | Platform 无对应能力 |
| Commerce 事件生产端 | 属于 Commerce 需求；没有 Commerce 接入时，投递记录只有测试或人工 Replay 数据 |
| mor-test 补偿扫描调度（XXL-Job） | 影响失败重试，不影响页面；单独处理 |
| 执行 SQL、部署、提交 | 按阶段单独授权 |

## 5. 验收标准

**功能验收**

| 编号 | 验收标准 |
|---|---|
| AC-01 | 有 `WEBHOOK_READ` 的运营账号能看到"Webhook 管理"菜单并能查询；没有的看不到菜单，直接调接口返回 403；只读账号看不到新建、修改、启停、轮换、测试、Replay 按钮 |
| AC-02 | 选择 Client 后能看到该 Client 的端点；未开通平台访问或不存在的 Client 给出明确提示 |
| AC-03 | 新建端点成功后弹窗显示一次密钥并可复制；关闭后页面、列表、后续接口响应都不再出现明文；浏览器不缓存该响应 |
| AC-04 | 地址非 https、指向内网 / 保留地址、超过 5 个端点、事件类型为空时，保存被拒且错误就地显示 |
| AC-05 | 修改与启停带版本号，两人同时修改时后者收到冲突提示并可重新加载，不覆盖他人修改 |
| AC-06 | 轮换后弹窗显示一次新密钥，列表显示新序号与旧密钥宽限截止时间；选择立即作废时宽限为空 |
| AC-07 | 测试事件显示对方 HTTP 状态或失败原因（地址被拦截、超时、连接失败等中文说明） |
| AC-08 | 投递记录按时间范围（≤31 天）、端点、事件类型、状态、eventId 筛选和分页；详情显示 Envelope 与尝试；已结束的投递可 Replay，进行中的不可 |
| AC-09 | 密钥明文不出现在 BFF / Gateway 访问日志、错误响应和审计详情中 |
| AC-10 | 注册 DML（Console Resource、按钮、Gateway 路由、Header、Query 参数、限流）写入 Platform 初始化 SQL；SQL 不执行，激活另行授权 |
| AC-11 | BFF 与 Web 自动化测试覆盖转发、错误透传、权限按钮控制、密钥弹窗只显示一次；Platform 若有改动，现有 Webhook 测试全部通过 |

**视觉 / 交互验收**

| 编号 | 验收标准 |
|---|---|
| AC-12 | 菜单、列表、表单抽屉、确认弹窗与现有运营配置页面风格一致；有加载、空状态（未选 Client、无端点、无投递）和错误反馈 |
| AC-13 | 密钥弹窗突出"只显示一次"，复制后有反馈；关闭需确认已保存 |
| AC-14 | 投递状态用统一的状态标签；时间按本地时区显示；尝试按先后排列 |

## 6. 问题与结论（2026-10-16 已确认：全部按建议）

| 编号 | 问题 | 建议 |
|---|---|---|
| Q-01 | 怎么选 Client？Platform Webhook 接口只接收 Client ID | **建议 B**。A：只输入 Client ID（最简单，但运营要先去别处查 ID）。B：Platform 在 `/webhook-admin/v1` 下新增一个按 ID 或名称搜索 CLIENT 租户的只读接口（`WEBHOOK_READ`），返回 Client ID、名称和端点数。C：BFF 调现有租户列表接口，但这样只有 Webhook 权限的账号也需要租户读取权限 |
| Q-02 | 投递记录做成独立菜单，还是放在 Client 页面里的标签页？ | **一个菜单、两个标签页**："端点"和"投递记录"，共用页面顶部的 Client 选择 |
| Q-03 | 投递列表默认时间范围 | **最近 24 小时**，可选到 31 天 |
| Q-04 | 本需求要改 Operations BFF、Operations Web，以及 Platform 的注册 SQL（Q-01 选 B 时还有 Platform 接口）。本会话可以编辑这些目录吗？**注意**：[Client Console 接入自助](../client-console-接入自助/requirement.md)会话正在做 Client 自助的 Webhook，工作区里有它未提交的 Platform 改动（`platform-support-initial-data.sql`、Schema、PDManer、access-control 等） | 允许本会话在 BFF / Web **新增** Webhook 相关文件，共享文件（菜单注册、路由、BFF 配置、审计动作名）只做增量修改；Platform 初始化 SQL 只追加独立的注册块，不碰对方的块；发现他人正在改同一文件时先停下说明 |
| Q-05 | 适用阶段 | 1—5 照常（阶段 3 含 Pencil 页面设计）；你要在 test 看到菜单，所以 **6—7 test 部署适用**；8 提交单独授权 |

## 7. 依据

- [Client Webhook 投递需求](../../20261015/platform-support-Webhook投递/requirement.md)、[技术方案](../../20261015/platform-support-Webhook投递/technique.md)、[决定](../../../agent-docs/decisions/2026-10-15-platform-webhook-delivery.md)
- 接口合同：`platform-support-service/platform-support-api/.../api/webhookmanagement/WebhookManagementApi.java`
- 页面与注册先例：[Operations 通知管理](../../20261015/operations-console-通知管理/requirement.md)
- [应用架构](../../../agent-docs/APPLICATION_ARCHITECTURE.md)：BFF 负责页面聚合，核心服务负责事实与授权
