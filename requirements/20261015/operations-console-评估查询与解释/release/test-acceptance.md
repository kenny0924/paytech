# 运营后台风控评估查询：mor-test 部署与验收记录

- 日期：2026-10-15（UTC 20:15～20:30）
- 授权：用户确认[发布清单](./release.md)（回复“可以”）
- 结果：**已部署 mor-test，验收通过**；只替换 Operations BFF、Web，其余 14 个容器未由本需求改动

## 1. 执行记录

| 步骤 | 结果 |
|---|---|
| 复核 | 与清单一致：BFF `ae568bd0dacd`、Web `6854f89078cc`、Platform `070ccc410ffb`（通知发送账号维护 `0301719`）；PLATFORM_ADMIN v26，ACTIVE 路由 228；本需求 ID 段与编码未占用；`41235c6` 之后 Operations 目录无新提交 |
| 备份 | `~/Code-MoR-backups/20261016-operations-evaluation-query-test/20261015T201606Z/`（0700/0600，SHA256SUMS）：`mor_platform` 8 张配置表、`mor_risk` 全库、执行前行数、16 个容器 image ID、BFF/Web inspect。不进入版本库 |
| 注册 | [registration-dml.sql](../db/platform-support-service/registration-dml.sql) 试跑（预检全 0、插入 32）→ ROLLBACK → 正式 32 行；中文显示名正常 |
| 替换 | BFF 20:16:22 → 20:16:39 healthy（`376b5b827945`），Web 20:16:39 → 20:16:51 healthy（`d37816c5ba8a`）；BFF 启动日志无 ERROR；入口返回的 `index.html` 引用 `assets/index-DxS9j9NM.js`，与候选镜像一致 |
| 激活 | [test-activate.sql](./test-activate.sql) 试跑 3/1/1、v27 → ROLLBACK → 正式；ACTIVE 路由 228 → 231；未登录访问新路由 401（路由已加载） |
| 合成评估 | 临时代理（`nginx:1.30.4-alpine`，`127.0.0.1:28114` → Risk）对联调基线发布评估 3 个合成支付尝试：放行、放行需 3DS、审核；Risk 侧查询检查 34 项通过 |
| 浏览器 | `E2E_TARGET=test` [stage5_browser.py](../verification/stage5_browser.py)：**17/17**（[结果](../verification/stage7-browser-results.txt)、[截图](../verification/screens-test/)）；影子评估与拒绝原因场景因 test 无对应数据跳过（本机已验证） |
| 回归 | [stage7_regression.py](../verification/stage7_regression.py) 管理员打开 29 个菜单页：**29/29** 无失败请求（[结果](../verification/stage7-regression-results.txt)） |
| 日志 | 验收窗口 BFF 443 行、Gateway 665 行、Risk 204 行；12 个合成受限原值命中 0 |
| 清理 | Risk 需求的 [test-acceptance-cleanup.sql](../../risk-评估结果查询与解释/release/test-acceptance-cleanup.sql) 试跑（评估 3、快照 3、累计事件 4 与主体 20、流程轨迹 11、规则轨迹 22、Feature 6）→ COMMIT；临时代理已删除 |

## 2. 过程中的环境事件

- **并行部署：**Client 入驻会话在 20:13～20:22 替换了 client-contract-service、client-console-bff 与 platform-support-service（20:21:50 重启）。重启的十几秒内 BFF 与 Gateway 找不到 Platform 实例，第一轮回归出现 `/auth/v1/context` 503；稳定后核对本需求的注册与激活未被改动（3 条路由、菜单 ACTIVE，v27）。
- **限流：**回归脚本整页重载每页都会读取登录上下文，触发其单操作者限流（429）；改为应用内切换路由后 29/29。属验收脚本问题，不是页面缺陷。
- **行数对比：**清理后 `mor_risk` 支付评估相关表与部署前一致；尽调 7 张表行数增加，来自 Client 入驻会话同时进行的 test 验收，非本需求写入。

## 3. 当前状态与恢复

- test：Operations BFF `376b5b827945`、Web `d37816c5ba8a`（源码 HEAD `41235c6` + 本需求未提交改动 34 个文件）；`mor_platform` 注册 32 行、激活生效（PLATFORM_ADMIN v27）。
- 回退：[test-deactivate.sql](./test-deactivate.sql) 隐藏菜单与路由，BFF/Web 换回 `ae568bd0dacd` / `6854f89078cc`；注册行保留。
- 部署的代码尚未提交；阶段 8 提交后核对提交内容与构建源码一致。
