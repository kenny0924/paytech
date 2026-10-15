# Operations 通知发送账号维护：交付记录

## 阶段

| 阶段 | 状态 |
|---|---|
| 1. 需求评审与范围确认 | 已确认（[requirement.md](./requirement.md)） |
| 2. 现状差异与影响分析 | 已确认（[analysis.md](./analysis.md)） |
| 3. 方案、接口与数据设计 | 已确认（[technique.md](./technique.md)、[db](./db/platform-support-service/)、Pencil 07 改造 + 07b～07e） |
| 4. 研发实现 | 已确认（见下方阶段 4 记录） |
| 5. 综合验证与本地验收 | 已确认 |
| 6–7. test 发布准备与部署 | 阶段 6 [发布清单](./release/release.md) 已确认，阶段 7 执行中 |
| 8. 代码提交与交付归档 | 未开始，提交单独授权 |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-15 | 立项 | 看到发送账号页后问"账号都有维护了，是不是密钥应该跟账号绑定呢"；对方案回复"1. 不用支持不用密钥的方式，其余的没问题" | 按新需求进入阶段 1；不支持运行环境角色 / 默认凭据链 |
| 2026-10-15 | 1→2 | "继续"：确认需求范围、AC-01～AC-14 与 Q-01～Q-10 全部按建议 | 阶段 2 现状差异与影响分析（只读核对，产出 analysis.md） |
| 2026-10-15 | 2→3 | "继续"：确认缺口判断、影响范围与 R-01（适配器按服务商类型注册，测试用独立测试类型） | 阶段 3 方案、接口、数据与页面设计；不写业务代码、不执行 SQL |
| 2026-10-15 | 3→4 | "继续"：确认方案、接口、数据与页面设计 | 阶段 4 研发：Platform、BFF、Web 代码，SQL/PDManer 文件同步，Vault 决定文档；不执行 SQL、不改环境 Vault 规则 |
| 2026-10-15 | 4 | "可以，执行并跑测试"：授权对本机测试库 `mor_platform` 执行 ddl.sql 以运行 Platform 自动化测试 | 已执行（执行时表为空） |

| 2026-10-15 | 4→5 | "继续"：确认阶段 4；授权本机 `mor_platform_e2e` 执行 DDL、SES_PRIMARY 补数据、注册与本机启用 SQL，本机 Vault 加 Protect 规则，重新打包重启本机 Platform / BFF，插入合成数据验收并清理 | 阶段 5 本机综合验证 |
| 2026-10-15 | 5→6 | 回复"1"：确认阶段 5，选择部署到 mor-test | 阶段 6 发布准备：只读核对与发布清单，不执行 test 变更 |
| 2026-10-15 | 6→7 | "可以"：确认发布清单；D1 A（先本地提交本需求，再从该提交构建；不推送）、D2 A（合成数据验收不调用 SES） | 本地提交本需求；执行阶段 7 的 mor-test 备份、构建、SQL、Vault 规则、Compose 修改与镜像替换、验收与清理；不碰生产 |
## 阶段 4 研发记录（2026-10-15）

**改动范围**：Platform（API 4 个新接口 + 列表字段、Delivery 适配器按类型注册与 SES 按账号发送、4 个 Web Processor 与 2 个 Delivery 公开 Processor、审计字段、配置收敛）、BFF（4 个转发 + 模型）、Web（发送账号页改造、3 个弹窗）、SQL（schema / PDManer / 测试 DDL / changes / 初始化 DML 的 `SES_PRIMARY` 与 56 行注册块）、文档（[Vault 决定](../../../agent-docs/decisions/2026-10-15-platform-notification-credential-protect.md)、Vault 架构例外、Platform README、SES 需求取代说明）。实现偏差见 [technique.md 第 7 节](./technique.md#7-实现偏差阶段-4)。

**验证（实际运行）**

| 项 | 结果 |
|---|---|
| Platform `verify`（只含本需求改动的隔离工作区，本机 `mor_platform` 已加列） | **619/619** 通过；全服务 90% 聚合覆盖率门禁仍未达标（0.55 / 0.38，既有为 0.54 / 0.37） |
| Delivery 模块覆盖率 | 指令 95.96%、分支 89.74%（上一需求后为 96.78% / 90.83%；未覆盖的主要是 Vault 客户端缺失、写入行数异常等防御分支） |
| 新增测试 | `NotificationProviderMaintenanceTest` 8 例（新建 / 拒绝、编辑并即时作用于测试发送、录入密钥与日志 / 审计零泄露、Vault 拒绝 503、测试发送受理 / 拒绝、启用要求配置完整）；`SesNotificationDeliveryTest` 新增 2 例（未录密钥受理即拒、受理后失去密钥失败关闭）；`SesEmailConfigurationTest` 改写（按区域复用客户端、无 VaultClient 拒绝启动） |
| BFF | 通知管理与应用装配测试 **47/47**（16 个端点转发、授权拒绝、上游问题与字段投影） |
| Web | `npm test` **1025/1025**，`npm run build` 通过 |
| 未做 | 浏览器联调与页面核对、本机全链路、Vault 规则、mor-test（阶段 5 及以后） |

## 阶段 5 综合验证（2026-10-15）

**本机执行的动作（已授权）**

| 动作 | 结果 |
|---|---|
| `mor_platform_e2e` 执行 [ddl.sql](./db/platform-support-service/ddl.sql) | 6 列已加 |
| [dml.sql](./db/platform-support-service/dml.sql) | 本机没有 `SES_PRIMARY`（认证邮件初始化块从未在本机执行），影响 0 行，未执行 |
| 初始化 DML 末尾注册块 | 56 行（权限 1、授权 1、按钮 2、路由 4、Header 24、限流 24），按钮中文名正常 |
| [activation-local.sql](./db/platform-support-service/activation-local.sql) | 4 / 2 / 1 / 1 |
| 本机 Vault 规则 `platform-support-service / PROTECT / NOTIFICATION_PROVIDER_CREDENTIAL_V1 / NOTIFICATION_PROVIDER_SETUP` | 经运营后台 Vault 管理接口新增，策略版本 16 |
| 本机 Platform（只含本需求改动的隔离工作区打包）与 Operations BFF 重新打包、重启 | 启动成功；本机 `notification.ses.enabled` 未开启 |

**验收结果**

- 接口验收 [local_acceptance.py](./verification/local_acceptance.py)：经运营入口（Nginx → Gateway → BFF → Platform → Vault）**15/15**：新建 / 重复 / 非法区域 422 / 不完整不能启用、编辑与 409、录入密钥经真实 Vault Protect（Vault 访问记录 `SUCCESS`，策略 `db-16`；账号表 Token 为 43 字符引用）、响应与列表不含密钥和 Token、启用、测试发送在本机未启用 SES 时返回 503、四类审计事件；Platform / BFF / Gateway / Nginx 日志不含合成密钥。
- 浏览器验收 [local_browser.py](./verification/local_browser.py)：**11/11**，截图在 [assets/stage5](./assets/stage5/)，与 Pencil 画板 07、07b、07c、07d 一致；无控制台错误、无意外失败请求。
- 发现并修正：本环境未启用服务商时「测试发送」改为不可用（technique.md 第 7 节第 9 条）。
- 测试复核：Web `npm test` 1043/1043（含其他会话新增测试）、`npm run build` 通过；Platform 与 BFF 结果见阶段 4。
- `npm --prefix requirements-site test`：127/137，10 项失败均为既有或其他会话修改共享文档所致（Gateway 台账行、写接口规范等），与本需求无关。
- 清理：验收新建的 4 个 `E2E_PROV_` 账号已删除，账号表回到空表；审计事件与 Vault 中的合成密文保留（追加记录 / Vault V1 无 Retire）。
- 其他：上一需求的本机验收 Fixture（`operations-console-通知管理/db/.../acceptance-fixture-local.sql`）改为显式列并写入未注册类型，以适配新列。
- 未做：真实 SES 发信（本机无 SES）、mor-test。

