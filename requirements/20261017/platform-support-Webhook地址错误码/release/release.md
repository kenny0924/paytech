# Webhook 端点地址错误码：mor-test 发布清单

阶段 6 评审稿（2026-10-17）。确认后执行阶段 7，结果写入第 5 节。当前状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次：** mor-test 的 Operations BFF、Platform、Operations Web 换成"当前运行版本的源码 + 本需求差异"构建的镜像，按 BFF → Platform → Web 顺序替换，经运营入口验收后清理合成数据。不涉及数据库、Gateway 路由、Compose 环境变量与 Vault；不推送 Git 与镜像，不碰生产。

## 1. 范围与源码基线

| 服务 | 当前运行（只读核对，2026-10-17） | 本次构建基线 | 本需求差异 |
|---|---|---|---|
| Operations BFF | `6341dd18…`（`webhook-ops-20261017`），源码为 `20677f2` + Operations Webhook 管理的 48 个文件 | 同左：`20677f2` 导出，叠加 `3170984` 中那 48 个文件，再加本需求差异。HEAD 上 BFF 只多了 Risk 一处 JavaDoc 注释，不带上 | `PlatformClientConfiguration`（登记错误码）与 `WebhookManagementBrowserAutoTest`；同 reactor 构建 `platform-support-api`（含新枚举） |
| Platform | `629cccf9…`（`toss-pay-20261017`，Toss Pay 会话以 `fe5a0f3` 构建） | `fe5a0f3` 导出。`fe5a0f3..HEAD` 之间 Platform、Foundation、build 均无改动 | `WebhookEndpointErrorCode`（新）、`WebhookManagementApi`（注释）、`WebhookManagementSupport`、`WebhookEndpointManagementTest` |
| Operations Web | `da6154c7…`（`webhook-ops-20261017`），源码同 BFF 基线 | 同 BFF：`20677f2` + 48 个文件 + 本需求差异。HEAD 上未部署的 Risk 页面与外部 API 编辑改动不带上 | `src/webhook/webhook.ts`、`webhook.test.ts` |

- **顺序理由：** 新 BFF 能识别新、旧两种错误码。若先替换 Platform，旧 BFF 不认识新错误码，地址被拒会变成 502。替换 BFF 和 Platform 之间的间隙里，Platform 仍返回旧错误码，新 BFF 照常处理。
- **不动：** Gateway、Vault、Nginx、XXL-Job、Commerce、Risk、Client Console、Compose 文件（只用候选覆盖文件固定镜像 ID）、数据库。

## 2. 执行顺序（任一步不符即停止）

1. **复核与协调**：
   - 确认三个运行镜像与第 1 节一致。
   - 通知 Toss Pay（ee）、Client API（76）、Payment（1e）、Risk（db）三次替换的窗口，每次约 30 秒。db 之后要发布 Risk / BFF / Web，需请它在本次 BFF / Web 的基础上构建，或带上本需求差异。
2. **备份**：三个容器 inspect、当前 Compose 副本，存放在 `~/Code-MoR-backups/20261017-webhook-errcode-test/<UTC>/`，附 SHA256SUMS。
3. **构建**：
   - 两个干净 worktree（`fe5a0f3`；`20677f2` + 48 个文件），叠加本需求差异，并记录差异的 SHA-256。
   - 临时聚合 POM：Platform 离线打包；BFF 与 `platform-support-api` / `risk-api` / `client-contract-api` / `paytech-vault-client` 同 reactor 离线打包。JDK 25，`-DskipTests -Djacoco.skip=true`。
   - 在发布源码上跑 Platform Webhook 测试类、BFF `verify`、Web `npm test` 与 `npm run build`。
   - 镜像标签为 `mor-test/<服务>:webhook-errcode-20261017`；记录镜像 ID、jar SHA-256、Web 入口资源名，写入 `compose-candidate.yaml`（固定 image ID）。
4. **替换**：`compose.sh -f compose-candidate.yaml up -d --no-deps --no-build --pull never --wait`，依次替换 BFF → Platform → Web。每个服务都要 healthy、日志无启动错误。Platform 还要确认 XXL 执行器重新注册、补偿扫描恢复，Toss Pay 的 `credentialFields` 仍在（镜像源码含 `fe5a0f3`）。
5. **验收**（`E2E_TARGET=test`，经运营入口；合成租户沿用 Operations Webhook 管理的 fixture）：
   - **接口：** 新建端点时，`https://10.0.0.1/hook` → `WEBHOOK_URL_NOT_PUBLIC`；`https://hooks.code-mor.invalid/hook` → `WEBHOOK_URL_UNRESOLVABLE`；`http://example.com/hook`（test 只允许 https）→ `WEBHOOK_URL_NOT_ALLOWED`。修改为内网地址同样返回。其他业务拒绝仍为"业务校验未通过"。
   - **页面：** 对 `https://example.com/…` 端点的修改弹窗，三种地址的原因都显示在字段下，不弹通用通知，并截图。
   - **回归：** 运营 30 页只读回归，每页间隔 7 秒；Platform、BFF 日志无 ERROR。
   - 不发送测试事件、不提交业务事件。
6. **收尾**：按 Operations Webhook 管理的 cleanup SQL 清理合成租户；删除构建 worktree。
7. **记录**：写入第 5 节，更新交付记录与台账，并通知相关会话。

## 3. 恢复

| 情况 | 处理 |
|---|---|
| 某个服务替换后不健康 | 用原镜像 ID 回退：BFF `6341dd18…`、Platform `629cccf9…`（**不是** `efeb1d9e…`，否则 Toss Pay 失效）、Web `da6154c7…` |
| 验收发现阻断缺陷 | 先回退 Platform，再回退 BFF（顺序与替换相反，避免旧 BFF 遇到新错误码），Web 随后回退 |

## 4. 需要你决定

无。基线沿用"当前运行版本 + 本需求差异"，与上次 Operations Webhook 管理发布做法一致。

## 5. 执行记录（2026-10-17 阶段 7，用户"可以"）

| 步骤 | 结果 |
|---|---|
| 1 复核与协调 | 三个运行镜像与第 1 节一致；Toss Pay（ee）、Client API（76）、Risk（db）均未使用 mor-test，同意执行；db 之后发布 BFF / Web 前会先确认基线 |
| 2 备份 | `~/Code-MoR-backups/20261017-webhook-errcode-test/20261017T064613Z/`（Compose 4 个文件、3 个容器 inspect，SHA256SUMS） |
| 3 构建 | 两个干净 worktree：Platform 为 `fe5a0f3` + 4 个文件；BFF / Web 为 `20677f2` + `3170984` 中的 48 个文件 + 本需求差异（差异 SHA-256 前缀 `ab722e02824e0729`）；临时聚合 POM 同 reactor 离线打包。发布源码上：<br>- Platform Webhook 测试类 **33/33**。worktree 中没有 `.env.local`，首次运行因本机 Redis 密码缺失全部启动失败；加载本机 `.env.local` 环境变量后通过。<br>- BFF `verify` **1381/1381**。<br>- Web `npm test` **1051/1051**，build 通过。<br>镜像 `webhook-errcode-20261017`：Platform `sha256:0cdfe0e9…`（jar `56b265510385b30d`）、BFF `sha256:aec81706…`（jar `6cf44cabcd382585`）、Web `sha256:04f9f7d1…`（入口 `assets/index-D3K4sD5J.js`），写入 [compose-candidate.yaml](./compose-candidate.yaml) |
| 4 替换 | BFF 06:52:34Z → healthy 06:52:52Z；Platform 06:52:57Z → healthy 06:53:27Z（76 的 truststore、受信网段 `.31`、执行器 9999、Webhook 开关与调用方均保留，`compensationEventScan` Handler 已注册）；Web → healthy 06:53:46Z，入口资源与构建一致。启动日志均无 ERROR。ee 在新 Platform 上复验 TOSS_PAY（`SBX_PAY_QUERY_NOT_FOUND`）通过 |
| 5 验收 | [test_errcode.py](../verification/test_errcode.py) **13/13**：<br>- 接口：`https://10.0.0.1`、`.invalid` 主机、`http://example.com` 分别返回 3 个错误码与固定说明；修改为内网地址同样返回；其他业务拒绝仍为"业务校验未通过"。<br>- 页面：修改弹窗中三种地址的原因都显示在字段下，不弹通用通知（[截图](../assets/stage7/)）。<br>运营 **30 页**只读回归无 ≥400、无控制台错误；Platform、BFF 自替换起无 ERROR；替换期间补偿扫描 06:52:30—06:58:00 每次调度均成功 |
| 6 收尾 | 合成租户已清理为 0；构建 worktree 已删除 |

**当前 mor-test：** BFF `aec81706…`、Platform `0cdfe0e9…`、Web `04f9f7d1…`（均为 `webhook-errcode-20261017`）。回退按第 3 节，Platform 回退到 `629cccf9…`。
