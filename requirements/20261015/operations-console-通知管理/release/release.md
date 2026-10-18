# Operations 通知管理：mor-test 发布记录

阶段 6/7。用户 2026-10-15 回复"按建议来，执行吧"（D1 A、D2 A、D3 A）后执行；实际执行与清单的偏差见第 7 节。当前状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次：test Platform 从 `84b3174` 升级到 `f84a012`，Operations BFF、Operations Web 从 `d02c7dd` 升级到 `f84a012`；test `mor_platform` 加 1 个索引、登记 160 行并启用；Platform 的 Compose 环境变量去掉 SMTP、开启通知；经 `http://localhost:28080` 验收后清理验收数据。**

## 1. 为什么现在看不到菜单

test 一直没有部署本需求：`mor_platform` 没有通知管理的权限、菜单与路由，Platform、BFF、Web 也还是旧镜像。只补数据不换镜像，点进去会缺接口和页面。

## 2. 范围

| 项 | 内容 |
|---|---|
| Platform 代码 | `84b3174..f84a012` 共 3 个 Platform 提交：通知内部 HTTP 接口（`132c982`）、认证邮件接入 Delivery（`7c340ea`，**删除 SMTP**）、通知管理（`f84a012`）。Schema 只多 1 个索引 |
| BFF / Web 代码 | `d02c7dd..f84a012` 两个目录只有本需求一个提交 |
| 配置（Compose） | Platform 删除 `SPRING_MAIL_*` 4 项与 `CLIENT_AUTHENTICATION_MAIL_FROM`；新增 `NOTIFICATION_ENABLED=true`、`NOTIFICATION_SES_ENABLED=false`（`7c340ea` 起，开启 Client 认证必须开启通知，否则启动失败）。BFF、Web 只换镜像 |
| 数据库 | 索引 DDL（[changes 文件](../../../../paytech/platform-support/docs/database/sql/changes/2026-10-15-notification-request-create-time-index.sql)）；注册 DML 160 行（初始化 DML 末尾独立块，默认 DISABLED/REVOKED）；[test-activate.sql](./test-activate.sql)（12/6/2/1） |
| 不动 | Gateway（新路由由 Gateway 从 Platform 快照动态加载）、Risk、CCT、Vault、Nginx、Client 链路；不推送 Git 与镜像，不碰生产 |

## 3. 只读核对结果（2026-10-15）

| 项 | 结果 |
|---|---|
| 运行镜像 | Platform `d5794c9c…`（`84b3174`，07:00 部署）；BFF `68e939f5…`、Web `5fb6911b…`（`d02c7dd`，15:56 部署）；Gateway `1cddfeab…` |
| `mor_platform` | 7 张通知表存在且均为空（无模板、账号、记录）；新索引不存在；本次 ID 段 `86xxx`～`89xxx`、编码、`/api/operations/notification-admin` 路径冲突均为 0；Operations Target ACTIVE；PLATFORM_ADMIN v24；ACTIVE 路由 212 |
| Platform 配置 | `CLIENT_AUTHENTICATION_ENABLED=true`，认证邮件现经 Mailpit SMTP；未配置 `NOTIFICATION_ENABLED` |
| 本机标签 | `mor-test/platform-support-service:development` 指向 `16017501…`，**不是**运行中的 `d5794c9c…`（来源不明，非本会话构建）。本次执行一律按镜像 ID 替换，不依赖 `:development` 标签的现值 |

## 4. 需要你决定

**D1 测试环境的认证邮件（影响最大）。** `7c340ea` 删除了 SMTP，新 Platform 上线后 test 的注册验证与找回密码邮件不再进 Mailpit；test 没有 SES，这两类邮件只会发送失败并计指标（注册、找回密码接口仍返回受理）。在 test 走客户注册→邮箱验证流程会卡住，直到为 test 配置 SES。
- 选项 A（建议）：接受，先上线；在 test 继续验证注册邮件需要另行配置 SES 发件账号（真实凭据，单独授权）。
- 选项 B：暂不升级 Platform（也就看不到菜单），等 test 的邮件方案定了再一起发。

**D2 与"Vault 数据类型值格式约束"需求的 BFF / Web 发布冲突。** 那个需求也在准备向 test 发布 BFF 与 Web，候选基于 `d02c7dd` 加它自己的改动（未提交），不含本需求；本次候选基于 `f84a012`，不含它的改动。后发布的一方会覆盖另一方。
- 选项 A（建议）：本次先发；那边构建时以包含 `f84a012` 的提交为基线。
- 选项 B：等那边先提交，再从同时包含两者的提交构建一次，合并发布。

**D3 是否在 test 执行认证邮件的初始化块**（`SES_PRIMARY` 账号、两个认证模板及内容、路由，上一需求写入初始化 DML、未在任何环境执行）。
- 选项 A（建议）：执行。页面能看到真实的认证模板；未配置 SES 时发送仍失败，结果与不执行相同。
- 选项 B：不执行，test 模板页为空。

## 5. 执行顺序（任一步不符即停止）

1. **复核**：重跑第 3 节查询；三个运行镜像不变；`mor_platform` 冲突仍为 0。
2. **备份**：`mor_platform` 8 张登记表（权限、角色授权、角色、菜单、路由、请求头、查询参数、限流）与 7 张通知表数据，`compose.applications.yaml` 副本，三个容器 inspect；存 `~/Code-MoR-backups/20261015-notification-admin-test/<UTC>/`（0700/0600，SHA256SUMS）。
3. **构建**：干净 worktree `f84a012`。Platform 与依赖 API 同 reactor `package -DskipTests`；BFF 与 `platform-support-api`、`risk-api`、`client-contract-api`、`paytech-vault-client` 同 reactor（不读 `~/.m3` 中可能被覆盖的 SNAPSHOT）；Web `npm run build`；健康探针从 `test-environment/java/*.java` 编译。镜像 tag `mor-test/<服务>:notification-admin-20261015`，记录镜像 ID 与 jar SHA-256。
4. **数据库（旧 Platform 仍在运行，兼容）**：索引 DDL；注册 DML 先试跑回滚核对 160 行，再正式执行；按 D3 执行认证邮件初始化块（账号 1、模板 2、内容 2、路由 2）。
5. **改 Compose 并替换 Platform**（`up -d --no-deps --no-build --pull never --wait`）。替换期间运营端与 Client 认证短暂不可用（约 30 秒）。确认 healthy、日志无启动错误。
6. **替换 BFF，再替换 Web**，各自 healthy；确认 `http://localhost:28080/` 返回的 `index.html` 引用的是新构建的资源。
7. **启用**：[test-activate.sql](./test-activate.sql) 核对 12/6/2/1、PLATFORM_ADMIN v25 后 COMMIT；等待 Gateway 快照加载新路由。
8. **验收**：[local_acceptance.py](../verification/local_acceptance.py) 与 [local_browser.py](../verification/local_browser.py) 以 `E2E_TARGET=test` 运行；合成数据用 [acceptance-fixture-local.sql](../db/platform-support-service/acceptance-fixture-local.sql) 同款数据（2 个 E2E 账号、1 条记录），验收后按 [acceptance-cleanup-local.sql](../db/platform-support-service/acceptance-cleanup-local.sql) 清理；管理员可见页面逐个回归；BFF、Platform、Gateway 日志不含合成参数值与 Token。
9. **记录**：执行记录写入本文第 7 节，更新交付记录与当前台账。

## 6. 恢复

| 情况 | 处理 |
|---|---|
| 第 5 步 Platform 不健康 | 恢复 Compose 副本，用 `d5794c9c…` 回退 Platform（旧镜像需要原 SMTP 配置） |
| 第 6 步 BFF / Web 不健康 | 回退 BFF `68e939f5…`、Web `5fb6911b…`；新 Platform 兼容旧 BFF / Web |
| 启用后发现阻断缺陷 | [test-deactivate.sql](./test-deactivate.sql)（12/6/2/1）停用入口，再按上两行回退镜像 |
| 数据 | 索引与 160 行登记保留（旧版本不读取，默认停用）；认证邮件初始化块的数据保留。只有确认不再需要时，再单独授权按 ID 删除 |

## 7. 执行记录（2026-10-15）

| 步骤 | 结果 |
|---|---|
| 1 复核 | Platform 仍为 `d5794c9c…`；**BFF、Web 已变为 `aa546ee6…`、`7e369f33…`**（Vault 值格式需求 18:07 发布，提交 `994d5b0` 位于 `f84a012` 之后）。从运行容器取出的 BFF jar 含 21 个通知管理类与 `/notification-admin` 日志前缀，Web 静态资源含 3 个通知页面分包——两者已包含本需求代码 |
| 偏差 | **不替换 BFF / Web**（替换会撤掉 Vault 值格式功能，且无必要）；只升级 Platform。D2 的冲突因对方从包含 `f84a012` 的提交构建而消失 |
| 2 备份 | `~/Code-MoR-backups/20261015-notification-admin-test/20261015T181018Z/`（0700/0600，SHA256SUMS）：`mor_platform` 8 张登记表与 7 张通知表数据、`compose.applications.yaml` 副本、三个容器 inspect |
| 3 构建 | 干净 worktree `f84a012`（`f84a012..994d5b0` 无 Platform 提交）；Platform 与 `client-contract-api`、`paytech-payment-api`、`paytech-vault-client` 同 reactor 离线构建；jar SHA-256 前缀 `e2c6d128`；健康探针从 `test-environment/java/*.java` 编译；镜像 `mor-test/platform-support-service:notification-admin-20261015` = `sha256:493026bf…c490918`，制品在 `test-environment/artifacts/notification-admin-20261015/`（已忽略） |
| 4 数据库 | 索引 DDL 完成；注册 DML 试跑 160 → 回滚 0 → 正式 160；认证邮件初始化块（D3）执行后账号 1、模板 2、内容 2、路由 2（执行前冲突核对为 0）；菜单中文名正常 |
| 5 Platform | Compose 删除 `CLIENT_AUTHENTICATION_MAIL_FROM` 与 `SPRING_MAIL_*` 4 项，新增 `NOTIFICATION_ENABLED=true`、`NOTIFICATION_SES_ENABLED=false`；候选镜像打成 `:development` 后 `up -d --no-deps --no-build --pull never --wait`：18:12:01Z 开始，18:12:25Z healthy（中断约 24 秒），启动日志无错误 |
| 6 BFF / Web | 未执行（见偏差） |
| 7 启用 | [test-activate.sql](./test-activate.sql) 试跑 12/6/2/1 回滚后正式提交 12/6/2/1；PLATFORM_ADMIN v25；ACTIVE 路由 212 → 224 |
| 8 验收 | 合成数据同本机 fixture；接口 **22/22**、浏览器 **16/16**（[截图](../assets/stage7/)，只有冲突用例预期的 409）；只读回归 **28 个页面**无 ≥400 请求、无控制台错误；Platform、BFF、Gateway 自验收开始的日志无合成参数值、Token 与供应商流水号 |
| 清理 | 合成账号、记录与验收新建的 `E2E_NOTIFY_*` 模板及其内容、路由已删除；通知请求 / 投递 / 尝试表为 0；保留 D3 的认证邮件初始化数据；审计事件为追加记录，保留 |

**当前 test 状态：** Platform `f84a012`（`493026bf…`），BFF / Web 为 Vault 值格式需求发布的 `994d5b0` 镜像（含通知管理）；运营后台可见「通知管理」菜单。test 的注册验证、找回密码邮件不再进入 Mailpit（未配置 SES，发送失败只计指标）。回退按第 6 节，Platform 回退镜像 `d5794c9c…` 需配合备份中的 Compose 副本。

## 8. 追加发布：预览改为按钮 + 弹窗（2026-10-15）

用户回复"提交并部署test"。只替换 Operations Web，Platform、BFF、数据库不动。

| 步骤 | 结果 |
|---|---|
| 提交 | `88a7807`（`994d5b0..88a7807` 的 Web 改动只有本次）；`design.pen` 未保存，未包含 |
| 构建 | 干净 worktree `88a7807`，lock 文件与主工作区一致，`npm run build`；镜像 `mor-test/operations-console-web:notification-preview-20261015` = `sha256:e5df9ddc…ac04de3d`（arm64） |
| 替换 | 替换前 Web 为 `7e369f33…`；用 [compose-candidate-preview.yaml](./compose-candidate-preview.yaml) 叠加在 Vault 值格式需求的 compose-candidate.yaml 之后，`up -d --no-deps --no-build --pull never --wait operations-console-web`：18:34:18Z 开始，18:34:29Z healthy；入口 `index.html` 引用新构建资源 |
| 验收 | 接口 **22/22**、浏览器 **17/17**（含"编辑区只有预览按钮""点击后弹窗渲染"，截图 03、03b 已更新）；只读回归 **28 页**无 ≥400 请求、无控制台错误；Web、BFF、Platform、Gateway 日志无合成参数值与 Token |
| 过程问题 | 第一次执行时 SQL 包装脚本只读标准输入，fixture 以参数传入而未执行，接口验收在配置路由处失败两次，留下 2 个 `E2E_NOTIFY_ORDER_*` 模板（无路由、无记录）；改为标准输入执行 fixture 后通过，统一由清理脚本删除 |
| 清理 | `acceptance-cleanup-local.sql` 后：E2E 模板 0；模板 2、账号 1、路由 2、内容 2；请求 / 投递 / 尝试 0 |
| 回退 | 用 Vault 值格式需求的 compose-candidate.yaml（不叠加本文件）重新 `up` Web，回到 `7e369f33…` |

