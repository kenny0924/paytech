# 运营后台风控评估查询：mor-test 发布清单（阶段 6 评审稿）

- 日期：2026-10-16
- 目标环境：本机 Docker 中的 mor-test（`test-environment/compose.sh`）；入口 `http://localhost:28080`；数据库 `mor_platform`、`mor_risk`
- 替换：Operations BFF、Operations Web。不替换 Gateway、Platform、Risk、Nginx 及其他服务；不改 Compose 文件；不推送

## 1. 现状（2026-10-16 只读核对）

| 项 | 现状 | 处理 |
|---|---|---|
| 容器 | 16 个；BFF `ae568bd0dacd`、Web `6854f89078cc`、Platform `070ccc410ffb` 为通知发送账号维护从提交 `0301719` 构建、2026-10-15 20:03 UTC 部署 | 换 BFF、Web；`0301719` 是本次源码 HEAD 的祖先，替换不会撤掉其功能 |
| Risk | `727d51127e61`（`a55b3b1`，已含评估查询接口） | 不替换 |
| `mor_platform` | 预检引用值：PLATFORM_ADMIN `…510`、风控菜单 `…565`、BFF Target `…001`；ID 段 `900000000000111000～114999` 与编码、`/api/operations/risk-decisions/` 路由全部未占用 | 执行注册与激活 |
| `mor_risk` | 联调基线发布 ACTIVE；4 条部署前写入的支付评估（无路径） | 验收写 3 个合成评估，验收后清理 |

## 2. 制品

| 项 | 值 |
|---|---|
| 源码 | HEAD `41235c6` 导出 + 本需求 34 个文件（BFF 21、Web 13；共享文件只含本需求片段）；差异 SHA-256 前缀 `40426b7fcb45afdf`。BFF 与 risk-api、platform-support-api、client-contract-api、paytech-vault-client 在同一 reactor 离线构建（不读 `~/.m3` 中可能含他人未提交改动的快照） |
| 验证 | 发布源码上 BFF `verify` **1349/1349**；Web `npm test` **1043/1043**、`npm run build` 通过 |
| BFF 镜像 | `mor-test/operations-console-bff:candidate-rev-20261016`，`sha256:376b5b827945…`；jar SHA-256 `c4ffa888…5c0f64`，镜像内一致；健康探针从 `test-environment/java` 现编译 |
| Web 镜像 | `mor-test/operations-console-web:candidate-rev-20261016`，`sha256:d37816c5ba8a…`；入口脚本 `assets/index-DxS9j9NM.js`，镜像内一致 |
| 候选 Compose | [compose-candidate.yaml](./compose-candidate.yaml) |
| 回退镜像 | BFF `ae568bd0dacd`、Web `6854f89078cc`（当前运行，本机仍在） |
| 制品目录 | `test-environment/artifacts/evaluation-query-20261016/`（已忽略，不提交） |

## 3. SQL

| 顺序 | 文件 | 库 | 说明 |
|---|---|---|---|
| 1 | [../db/platform-support-service/registration-dml.sql](../db/platform-support-service/registration-dml.sql) | `mor_platform` | 32 行，新行 DISABLED/REVOKED；先 ROLLBACK 试跑核对再 COMMIT |
| 2 | [test-activate.sql](./test-activate.sql) | `mor_platform` | 3/1/1/1；先试跑再 COMMIT |
| 验收后 | Risk 需求的 [test-acceptance-cleanup.sql](../../risk-评估结果查询与解释/release/test-acceptance-cleanup.sql) | `mor_risk` | 删除 3 个合成支付尝试（`991015000000000001～003`）的评估相关数据；已在 Risk 阶段 7 用过 |
| 回退 | [test-deactivate.sql](./test-deactivate.sql) | `mor_platform` | 3/1/1/1，只改状态 |

注册 DML 已在本机 `mor_platform_e2e` 执行（阶段 5）与一次性容器演练（32 行）。

## 4. 执行顺序（任一步不符即停止并报告）

1. **复核：**重做第 1 节核对；确认没有其他会话正在部署 BFF/Web/Platform（容器启动时间、镜像与第 1 节一致）。
2. **备份**（`~/Code-MoR-backups/20261016-operations-evaluation-query-test/<UTC>/`，0700/0600，SHA256SUMS，不入库）：`mor_platform` 8 张配置表、`mor_risk` 全库、执行前行数、两个容器 inspect 与 16 个容器 image ID。
3. **注册：**执行第 3 节 1。
4. **替换 BFF，再替换 Web：**`compose.sh -f requirements/20261015/operations-console-评估查询与解释/release/compose-candidate.yaml up -d --no-deps --no-build --pull never --wait operations-console-bff`，healthy 后同样替换 `operations-console-web`；确认 Web 返回的 `index.html` 引用 `assets/index-DxS9j9NM.js`。
5. **激活：**执行第 3 节 2；等 Gateway 加载新路由（ACTIVE 路由 +3）。
6. **合成评估：**临时代理（`nginx:1.30.4-alpine`，宿主 `127.0.0.1:28114` → Risk 18104，验收后删除）对联调基线发布评估 3 个合成支付尝试（100 / 600 / 6000）。
7. **浏览器验收：**`E2E_TARGET=test` 运行 [stage5_browser.py](../verification/stage5_browser.py)（test 没有影子评估，影子场景跳过；“路径未记录”用部署前写入的现有评估）。
8. **清理：**第 3 节验收后 SQL（ROLLBACK 试跑再 COMMIT）；删除代理；核对 `mor_risk` 回到第 2 步行数。
9. **记录：**`release/test-acceptance.md`；CURRENT_IMPLEMENTATION 本需求行。

## 5. 验收

| 类别 | 内容 | 通过标准 |
|---|---|---|
| 健康 | BFF、Web healthy；BFF 启动日志无 ERROR；Gateway 新路由生效 | 全部满足 |
| 页面 | 菜单“评估查询”；按支付尝试/评估 ID 查询；结论卡片；路径；规则与条件；变量；输入；3DS、审核；路径未记录；未找到 | 全部通过；除预期 404 外无 ≥400 |
| Web 制品 | 浏览器拿到的入口脚本与候选镜像一致 | 一致 |
| 回归（只读） | 管理员可见页面逐个打开（含通知管理、风控其他页面） | 无失败请求 |
| 日志 | BFF、Gateway、Risk 日志无合成受限原值；test 边缘与 Web Nginx 为 `access_log off` | 0 命中 |

## 6. 恢复

- BFF 或 Web 不健康：用回退镜像以同一方式启动（候选文件中的 image ID 换成回退 ID）。
- 页面问题但服务健康：执行 [test-deactivate.sql](./test-deactivate.sql) 隐藏菜单与路由，再换回旧镜像；注册行保留。
- 不整库还原 `mor_platform`（期间可能有其他写入）；备份只在注册 SQL 结构性出错且无其他写入时使用。

## 7. 待批准范围

按本清单：备份；`mor_platform` 注册与激活；替换 Operations BFF、Web；临时代理与 3 个合成评估；浏览器验收；清理与删除代理；失败按第 6 节恢复。**不包含：**提交（阶段 8）、推送、生产、其他服务。
