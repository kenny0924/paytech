# 阶段确认与交付记录

## 当前阶段

阶段 3：用户已确认 Payment 统一接收卡与协议确认的一次支付请求，已同步[接口字段稿](./api-contract.md)和[数据、事务及实施稿](./data-design.md)。公共目标为 Commerce 3 操作、Payment 2 操作；删除前稿独立 /card 与内部 registrations 提案。拟建三张业务表仍保留，Submission 改为 Payment 本地命令来源，卡表删除独立 capture_command_id/索引。新增的恢复字段、内部确认查询与完整表/技术增量仍在设计评审阶段，未进入代码、模型或环境操作。

## 确认记录

| 日期 | 用户要求 | 已确认范围 | 本轮处理 |
|---|---|---|---|
| 2026-09-12 | 先做自己存卡吧，后面应该会有 PCI 资质 | 自建卡数据托管方向；未来计划取得适用 PCI 证明 | 登记新需求，标记前序 NI Hosted Session 方案受影响，提交隔离卡库、Card Token、CVV 与真实卡上线边界 |
| 2026-09-12 | 把卡相关的放到 payment 系统吧，单独弄一个模块 | 卡相关能力归 Payment，在服务内单独设业务模块 | 收敛为一个 Card Maven JAR，建议命名 `paytech-payment-card`；明确专用采集入口、卡库/Token/受控用卡和普通支付编排的边界，不默认增加服务、仓库或数据库 |
| 2026-09-12 | 可以（回复模块方案） | 接受前轮 Payment Card 模块边界，进入现状与影响分析 | 核对 Payment/Commerce/Checkout Web 工程及模型、Foundation 日志能力和 NI Direct 官方协议，交付阶段 2；不解释为全部后续研发或环境操作授权 |
| 2026-09-12 | 可以（回复调用顺序确认） | 一次Pay点击先Commerce确认并登记，再Payment直接采集执行；进入接口和表结构设计 | 交付阶段3字段、状态/幂等、三表/Action增量、技术表影响及测试/实施清单；修订原Customer Card字段并同步设计路由 |
| 2026-09-12 | 卡与协议信息应一起提交；可以的（回复统一入口方案） | 一次公开 payments 请求由 Payment 接收卡与当前合同确认；Payment 调用 Commerce 保存接受后继续执行，Card 模块保留、独立 /card 删除 | 修订 API Owner/请求、内部确认/查询与无回环顺序、Pay 单命令幂等及恢复、数据引用、Customer 合同和全局入口；前一行两请求确认在这一范围被取代 |

Payment 模块、最新单请求入口、Commerce 接受 Owner、CVV 不持久化与事务公理已确认；具体字段/表、恢复细节和 Payment 技术表迁移仍待整体评审。历史确认与验证按当时上下文保留，不把“可以的”扩大为 SQL、实现、提交、部署或真实卡接入授权。未收取卡数据或 NI 凭据。

## 验证与交付

前轮核对 PCI SSC 官方材料；本轮核对模块规范及 Payment 仓库并更新需求文件和受影响索引。核对时 Payment 仓库只有数据库设计材料，没有 Java 工程，本轮未创建空 Maven 模块。未改应用代码、数据库模型或 SQL，未部署采集页面、提交、推送或发起支付。实际实现/部署状态继续只见 [CURRENT_IMPLEMENTATION](../../../agent-docs/CURRENT_IMPLEMENTATION.md)。

前轮静态验证：本需求与受影响 Customer 目录共 11 份 Markdown、71 个本地文件链接通过检查，9 个历史/当前 JSON 示例可解析，新需求无 SQL 文件；`npm --prefix requirements-site run test:content` 71/71 通过，相关已跟踪文件 `git diff --check` 通过。Commerce 和 Payment 独立仓库保持干净。没有执行业务、加密实现、数据库或真实卡测试；文档门禁不代表卡库已实现或通过 PCI 验证。

本轮模块归属修订验证：11 份 Markdown、72 个本地文件链接、9 个 JSON 示例及尾随空白检查通过，新需求无 SQL 文件；相关已跟踪文件 `git diff --check` 通过，Commerce 和 Payment 仓库保持干净。需求站内容测试为 70/71：唯一失败为 `the current implementation ledger owns core model snapshots while entry documents route to it`，测试期望 Risk 为 `78 / 942 / 115 / 82 / 122 / 9 / 8`，当前台账为 `78 / 944 / 115 / 80 / 120 / 9 / 8`。该断言及 Risk 统计均未在本次修改，保留其他任务的工作区变更，不将本轮记录为全量通过。

阶段 2 本轮仅更新需求及分析/交付材料，未修改应用工程、Risk、模型或 SQL。NI 页面仅作公开文档核对，没有访问账户、执行 SDK 或发起交易；Foundation 日志能力为源码证据，未在本轮重跑其 Java 测试。

阶段 2 静态验证：本需求及 Customer 目录 11 份 Markdown、77 个本地文件链接、9 个 JSON 示例与尾随空白检查通过；本需求无 SQL。Commerce、Payment、Foundation 仓库均干净；Checkout Web 原有未跟踪 `docs/` 保留未改。本轮没有运行 Java/数据库/NI 测试，也未重跑需求站全量测试；前轮 70/71 的 Risk 统计断言差异仍作为历史验证结果保留。

阶段3本轮更新设计稿与相关文档入口，不修改业务工程、PDManer或SQL；未执行数据库、支付、部署、提交或推送。NI与AWS/PCI材料仅公开文档查询，不能据此宣称Card能力已实现或合规。

阶段3验证：13份Markdown、93个本地文件链接、9个JSON示例、围栏/尾随空白及拟建列/索引32字符限制通过；相关已跟踪文档`git diff --check`通过。需求站内容测试70/71，唯一失败仍是本需求未修改的Risk模型统计与断言差异，未处理该其他任务文件。Payment、Commerce、Foundation独立仓库保持干净；没有运行Java、数据库、KMS或NI交易测试。

本轮同时将已确认自建方向的窄例外同步到AGENTS、项目上下文、数据库设计规范和应用架构及需求/时序目录；原非卡Vault边界保留，阶段3候选字段没有被标成已上线。Customer公共稿只保留订单/Consumer/legal权威，Card字段统一链接本需求；前序实施/NI资料保留历史取代说明。

前轮下一步按[项目阶段流程](../../../agent-docs/REQUIREMENT_DELIVERY_WORKFLOW.md)评审阶段3接口、三张业务表与技术表增量，再明确首片研发范围；不重复确认Payment Owner或已确认调用链，也不将设计确认扩大为环境或真实卡上线授权。

## 单请求修订交付（2026-09-12）

已将当前需求、API/数据稿、原 Customer 权威入口、应用架构、需求/全局时序目录同步为 Payment 单请求；旧 NI/两请求资料只保留明确历史语境。前端无需独立提交卡接口；正常请求直接执行，AWAITING_CARD 仅用于已受理但瞬时输入丢失的异常恢复。Commerce 确认回执查询不补造同意，Payment 恢复任务不保存 Token/网络原值或 CVV。

本轮仅维护设计材料；真实 Java、PDManer、SQL、路由和部署状态没有变化。验证结果在本节追加，不覆盖上面的历史测试记录。

本轮验证：13份Markdown、100个本地链接、9个JSON示例、公开操作5项及旧 /card/registrations 退出检查通过；拟建业务表37字段与列/索引命名检查通过，相关 git diff --check 通过。两个目录内容源的 ESLint 通过。需求站完整 npm test 构建成功、135/136测试通过；最终文档更新后 test:content 为70/71。唯一失败仍是原 Risk 模型统计断言（期望78/942/115/82/122/9/8，与当前台账不同），没有修改 Risk 或该测试来绕过。完整日志为 /tmp/codemor-single-pay-full-tests.log，最终内容日志为 /tmp/codemor-single-pay-final-content.log。

Commerce、Payment、Foundation 独立仓库保持干净，Checkout Web 原有未跟踪 docs/ 保留；未运行 Java、数据库、KMS 或 NI 交易测试。下阶段按本稿明确的模型/技术表增量及分片计划进入研发，设计确认不代替环境操作授权。
