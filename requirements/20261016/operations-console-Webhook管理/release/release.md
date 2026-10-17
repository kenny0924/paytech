# Operations Webhook 管理：mor-test 发布清单

阶段 6（评审稿，2026-10-17）。确认后执行阶段 7，结果写入第 6 节。当前状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次：mor-test 的 Platform、Operations BFF、Operations Web 换成「`20677f2` + 本需求差异」构建的镜像；`mor_platform` 追加并启用本需求的菜单 / 按钮与 10 条 Gateway 路由；经运营入口完成验收后清理合成数据。Compose 配置、Gateway、Vault 不改。**

## 1. 范围

| 项 | 内容 |
|---|---|
| 源码基线 | 提交 `20677f2` 导出 + 本需求未提交差异（Platform、BFF、Web 三处本需求文件；共享文件只取本需求片段）。理由：Platform 当前运行 `9ae668a`，`9ae668a..HEAD` Platform 及依赖无差异；BFF 运行内容等于 `7fc47d6`，到 `20677f2` 无 BFF 改动；Web 运行 `notification-hint-20261016`（`20677f2`）。HEAD 上之后的 `87fd57f`（Risk 的 Web 页面与 BFF 注释）、`991e932`（risk-api）尚未在 mor-test 发布，不随本次带上 |
| Platform | 新增 `GET /webhook-admin/v1/clients`；测试事件审计修正。配置不变（`WEBHOOK_ENABLED`、调用方登记已在） |
| Operations BFF | 新增 `/webhook-admin/v1/**` 10 个转发；`/webhook-admin` 只记元数据日志 |
| Operations Web | 新增「平台配置 / Webhook 管理」页面；审计页 Webhook 动作中文名 |
| 数据库 `mor_platform` | 初始化 DML 末尾「Operations Webhook management console」块（128 行：菜单 / 按钮 2、路由 10、Header 44、Query 12、限流 60，均 DISABLED）→ [test-activate.sql](./test-activate.sql)（10 / 2 / 1） |
| 不动 | Gateway（路由由快照动态加载）、Vault、CCT、Risk、Nginx、Client 链路、Compose 文件；不推送 Git 与镜像，不碰生产 |

## 2. 只读核对结果（2026-10-17）

| 项 | 结果 |
|---|---|
| 运行镜像 | Platform `86819a6e…`（`client-console-20261016`，`9ae668a`）；BFF `376b5b82…`（`candidate-rev-20261016`）；Web `3dc13acd…`（`notification-hint-20261016`）；Gateway `1cddfeab…`；均 healthy |
| `mor_platform` | Webhook 权限 `9000000000000901xx` 与 PLATFORM_ADMIN 授权 `9000000000000902xx` 均 ACTIVE；`9000000000000903xx—0907xx` 与 `operations-webhook-*`、`/api/operations/webhook-admin` 冲突 0；父菜单「平台配置」、Target `900000000000000001` 存在；PLATFORM_ADMIN 版本 28；合成租户 ID 未占用；已有 1 个端点（Client Console 验收的 example.com，已停用） |
| Platform 配置 | `WEBHOOK_ENABLED=true`，只允许 https 公网地址，调用方 `commerce-service` |

## 3. 需要你决定

**D1 源码基线。**
- 选项 A（建议）：`20677f2` + 本需求差异，三个镜像都不带 Risk 尚未发布的 `87fd57f` / `991e932`。
- 选项 B：当前 HEAD + 本需求差异，会顺带发布 Risk 的结账点评估 Web 改动与新 risk-api（Risk 服务端是否已发布未核实）。

**D2 投递相关验收。** test 只允许 https 公网地址，没有可用接收端。
- 选项 A（建议）：页面与接口验收端点管理（搜索、新建一次性密钥、修改、轮换、停用、只读校验）、投递记录空状态；端点地址用 IANA 保留的 `https://example.com/…`，**不点"发送测试"、不提交事件**，避免向外网推送。测试事件、送达、双签、Replay 已在 Webhook 投递 test 验收（2026-10-16）与本机（阶段 5）验证。
- 选项 B：同 Webhook 投递发布的做法，临时起接收容器并放行 http 与其地址，完整验收测试事件、送达与 Replay；需额外两次 Platform 重启（各约 30 秒）。

## 4. 执行顺序（任一步不符即停止）

1. **复核**：重跑第 2 节查询；三个运行镜像不变；与 Payment / Risk / Client Console 会话确认没有正在进行的 test 部署或验收。
2. **备份**：`mor_platform` 的 `t_console_resource`、`t_gateway_route`、`t_gateway_route_header`、`t_gateway_route_query_param`、`t_gateway_guard_rule`、`t_role`；`compose.applications.yaml` 副本；三个容器 inspect；存 `~/Code-MoR-backups/20261017-webhook-ops-test/<UTC>/`（0700/0600，SHA256SUMS）。
3. **构建**：`20677f2` 建干净 worktree，叠加本需求差异（记录差异 SHA-256）；Platform 与依赖 API 同 reactor、BFF 与 `platform-support-api`/`risk-api`/`client-contract-api`/`paytech-vault-client` 同 reactor 离线打包（JDK 25，`-DskipTests -Djacoco.skip=true`）；BFF 在发布源码上跑 `verify`；Web `npm test` 与 `npm run build`；健康探针从 `test-environment/java/*.java` 编译；镜像 tag `mor-test/<服务>:webhook-ops-20261017`，记录镜像 ID、jar SHA-256、Web 入口资源名，写入 `compose-candidate.yaml`（固定 image ID）。
4. **数据库（旧版本运行中，DISABLED 行不生效）**：注册块先试跑回滚核对 128 行再提交。
5. **替换**：`compose.sh -f compose-candidate.yaml up -d --no-deps --no-build --pull never --wait` 依次 Platform → BFF → Web；各自 healthy、日志无启动错误；Web 入口 `index.html` 引用新资源。各中断约 30 秒。
6. **启用**：[test-activate.sql](./test-activate.sql) 试跑回滚核对 10 / 2 / 1 后提交；等待 Gateway 快照加载新路由。
7. **验收**（`E2E_TARGET=test`，经运营入口；合成租户同本机 [fixture](../db/platform-support-service/acceptance-fixture-local.sql)）：接口与浏览器按 D2 选择的范围；Webhook 管理以外的运营页面逐个只读回归（无 ≥400、无控制台错误）；Platform、BFF、Gateway 自验收开始的日志不含密钥明文与 ERROR。
8. **收尾**：按本机 [cleanup](../db/platform-support-service/acceptance-cleanup-local.sql) 同款 SQL 清理合成租户及其 Webhook 数据（审计与 Vault 合成密文保留）；D2=B 时撤放行、重启 Platform、删接收容器。
9. **记录**：写入第 6 节，更新交付记录与台账。

## 5. 恢复

| 情况 | 处理 |
|---|---|
| 替换后不健康 | 恢复 Compose 副本，用原镜像 ID 回退：Platform `86819a6e…`、BFF `376b5b82…`、Web `3dc13acd…` |
| 启用后发现阻断缺陷 | [test-deactivate.sql](./test-deactivate.sql)（10 / 2 / 1）停用入口，再按上一行回退镜像 |
| 数据 | 128 行注册数据 DISABLED 时对旧版本无影响，保留；删除另行授权 |

## 6. 执行记录（2026-10-17 阶段 7，用户“按建议”：D1=A、D2=A）

| 步骤 | 结果 |
|---|---|
| 1 复核 | 三个运行镜像不变；Payment（code-mor-1e）、Risk（code-mor-db，确认 `87fd57f` 不打算发布、`991e932` 只发了 Risk 镜像）、Client Console（code-mor-76）均未使用 test |
| 2 备份 | `~/Code-MoR-backups/20261017-webhook-ops-test/20261016T205556Z/`（注册相关 6 表数据、Compose 副本、3 个容器 inspect，SHA256SUMS） |
| 3 构建 | 干净 worktree `20677f2` + 本需求 48 个文件（与 HEAD 同名文件在 `20677f2..HEAD` 无改动；差异 SHA-256 前缀 `fd44a47cd8d99597`）；临时聚合 POM 同 reactor 离线打包；发布源码上 BFF **1378/1378**、Web `npm test` **1051/1051** 与 build 通过。镜像 `webhook-ops-20261017`：Platform `sha256:efeb1d9e…`（jar `702003510ce61485`）、BFF `sha256:6341dd18…`（jar `49102159778bb263`）、Web `sha256:da6154c7…`（入口 `assets/index-CwyA7InR.js`），写入 [compose-candidate.yaml](./compose-candidate.yaml) |
| 4 数据库 | 注册块试跑回滚核对 2/10/44/12/60 后提交（20:56:33Z），全部 DISABLED |
| 5 替换 | Platform 20:56:37Z → healthy；BFF 20:57:00Z → healthy；Web 20:57:18Z → healthy（20:57:29Z 完成）；启动日志无 ERROR；Platform Webhook 配置保留 |
| 6 启用 | [test-activate.sql](./test-activate.sql) 试跑后提交：10 / 2 / 1，PLATFORM_ADMIN 版本 28→29 |
| 7 验收 | 接口 [test_acceptance.py](../verification/test_acceptance.py) **22/22**；浏览器 [test_browser.py](../verification/test_browser.py) Webhook 流程 **13/13**（[截图](../assets/stage7/)，密钥已遮盖）；运营 **30 页**只读回归无 ≥400、无控制台错误（首次连续整页加载触发 Gateway 登录上下文限流 429，改为每页间隔 7 秒后通过） |
| 8 收尾 | 按本机 cleanup 同款 SQL 清理两个合成租户及其端点（审计与 Vault 合成密文保留）；构建 worktree 已删除 |

**未覆盖：** 按 D2=A 未发送测试事件、未提交业务事件（送达、双签、Replay 已在 2026-10-16 Webhook 投递 test 验收与本机阶段 5 验证）；只读账号按钮隐藏未在浏览器验证；Client Console 的 `developer_flow.py` 回归由其会话（code-mor-76）在替换后以 `E2E_TARGET=test` 运行，**78/78** 通过（探针使用新 Platform 镜像）。

**当前 mor-test：** Platform `efeb1d9e…`、BFF `6341dd18…`、Web `da6154c7…`（均 `webhook-ops-20261017`）；Webhook 管理菜单与 10 条路由 ACTIVE。回退按第 5 节。
