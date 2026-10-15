# 评估结果查询与解释：mor-test 部署与验收记录

- 日期：2026-10-15（UTC 19:05～19:20）
- 授权：用户确认[发布清单](./release.md)及 D1、D2 按建议（回复“可以”）
- 结果：**已部署 mor-test，验收通过**；只替换 Risk，其余 15 个容器镜像不变

## 1. 执行记录

| 步骤 | 结果 |
|---|---|
| 复核 | 与清单第 1 节一致：16 个容器；Risk image `fcfc9457dab6` healthy；`mor_risk` 84 表；联调基线发布 ACTIVE；4 条支付评估；Nacos 一个健康实例 |
| 备份 | `~/Code-MoR-backups/20261015-risk-evaluation-query-test/20261015T191000Z/`（0700/0600，SHA256SUMS）：`mor_risk` 全库导出（84 表）、执行前行数、16 个容器 image ID、Risk 容器 inspect、Compose 原文件。不进入版本库 |
| 回归基线 | 临时代理（`nginx:1.30.4-alpine`，宿主 `127.0.0.1:28114` → `172.30.73.25:18104`）读取 20 个只读接口：结账 9 个（同上次发布）+ 支付 11 个（创作上下文、流程列表与详情、规则集列表与两个详情、变量、执行发布列表与基线发布详情、名单列表），全部 200 |
| DDL | 19:10:46 执行 `db/ddl.sql`（旧 Risk 运行中）：85 表；与仓库完整 Schema 对比列、索引、表注释差异 0；原 84 表行数不变 |
| 应用 | [compose-candidate.yaml](./compose-candidate.yaml) `up -d --no-deps --no-build --pull never --wait risk-service`：19:10:57 → 19:11:21 healthy（约 24 秒），image `727d51127e61`，启动 5.3 秒，无 ERROR；Nacos 一个健康实例（地址不变）；其他 15 个容器 image ID 不变 |
| 回归 | 20 个接口部署前后响应结构（958 行路径）完全一致；内容除 `valuesAsOf` 外无差异 |
| 验收 | 34 项全部通过，见[证据](./test-acceptance-evidence.md) |
| 日志 | 验收窗口 Risk 日志 146 行，合成请求的 12 个受限原值命中 0；无 ERROR |
| 清理 | [test-acceptance-cleanup.sql](./test-acceptance-cleanup.sql) 先 ROLLBACK 试运行（评估 3、快照 3、累计事件 4 与主体 20、流程轨迹 11、规则轨迹 22、Feature 结果 6）再 COMMIT；原 84 表行数回到 DDL 后状态，新表 0 行；临时代理已删除 |

## 2. 验收

| 类别 | 结果 |
|---|---|
| 现有 4 条评估（部署前写入） | 按 ID 与按支付尝试查询一致、结论正确、`pathAvailable=false`；放行的认证要求取已保存值；解释路径为空，规则、变量、17 个输入照常 |
| 合成评估（对联调基线发布） | 100 → 放行无需认证（`start → block → auth → allow`）；600 → 放行需 3DS（`… → allow_3ds`）；6000 → 审核（`start → block → review`）；结论、输出、发布 ID、路径、规则、变量、输入原值均正确 |
| 错误 | 不存在的评估与业务身份 404 `NOT_FOUND`；ID 格式、未声明 Query、未登记风控点 400 `INVALID_REQUEST` |

## 3. 当前状态与恢复

- test Risk 为本需求代码（未提交，源码 = HEAD `8d26229` + 52 个改动文件），`mor_risk` 85 表；联调基线发布与 4 条评估不变。
- 回退：换回 image `sha256:fcfc9457dab627439e2590ff5a67d91d182e5e02d2e610a19b61370c95a7ea15`；新表可保留（旧代码不读写）。
- 部署的代码尚未提交；阶段 8 提交后需核对提交内容与构建源码一致。
