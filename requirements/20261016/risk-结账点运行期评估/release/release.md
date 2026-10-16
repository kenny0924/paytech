# 结账点运行期评估：mor-test 发布清单（阶段 6 评审稿）

- 日期：2026-10-17
- 目标环境：本机 Docker 中的 mor-test（Compose 项目 `mor-test`，`test-environment/compose.sh`）；数据库容器 `mor-test-mysql-1`，库 `mor_risk`
- 只换 Risk；mor-test 没有 Commerce 与 Payment；不涉及生产、推送、Operations BFF/Web、Gateway/Nginx

## 1. test 现状（2026-10-17 只读核对）

| 项 | 现状 | 本次处理 |
|---|---|---|
| 容器 | 16 个；Risk 为 image `727d51127e61`（评估结果查询与解释），healthy 25 小时；其他会话近 1 小时内换过 Client Console、CCT、Platform、Operations Web | 只换 Risk |
| `mor_risk` 结构 | 85 表；`t_checkout_input_snapshot` 旧结构 0 行 | 执行 `db/ddl.sql` |
| `mor_risk` 数据 | 结账输入变量 91001～91010 为 v4 系统变量（10 行）；locale 在规则、动作参数、流程连线中引用均为 0；91018～91020、220001、`(210001, 100)` 均未占用；结账变量共 16 个 | 执行 `db/dml.sql`（预检全部符合期望） |
| 评估与发布 | 只有支付评估 5 条；执行发布只有支付的 1 个 ACTIVE（Operations 联调基线）；结账点没有执行发布，旧候选发布保留只读 | 不改动 |
| 结账配置 | v4 规则集 28 个、流程 2 个（旧格式，部分含附加动作） | 部署后只读可查；不能直接发布（需另存 v5，见设计第 3 节） |
| 调用方 | 无（test 没有 Commerce） | 新接口无调用方，结账点无生效发布不影响任何现有调用 |

## 2. 制品

| 项 | 值 |
|---|---|
| 源码 | HEAD `23708f6` 的 `risk-compliance-service` + 本需求未提交改动（与阶段 5 本机验收使用的 jar 相同；阶段 8 提交后与提交内容核对） |
| jar | `risk-web-1.0.0-SNAPSHOT-exec.jar`，SHA-256 `baa1a685a95c6affcf575370abc4b9d506ef74d417313a4144a5218c95d0292d`（Risk 全量 auto 2064/2064；本机端到端与浏览器 9/9） |
| 镜像 | `mor-test/risk-service:checkout-runtime-20261017`，image ID `sha256:8e26b91b519c8c69b00c7e8c264b23b5e80a37c53fdff94a8f596e742e6ddfe6`（`test-environment/java/Dockerfile`，构建目录 `test-environment/artifacts/checkout-runtime-20261017/`，已忽略）；镜像内 jar 摘要已核对一致 |
| 候选 Compose | [compose-candidate.yaml](./compose-candidate.yaml)（只固定 image ID） |
| 回退 | [compose-rollback.yaml](./compose-rollback.yaml)：`727d51127e61`；[dml-rollback.sql](./dml-rollback.sql) |

## 3. SQL

| 顺序 | 文件 | 说明 |
|---|---|---|
| 1 | [../db/ddl.sql](../db/ddl.sql) | 重建 `t_checkout_input_snapshot`（0 行）。旧代码不读写该表，可在旧 Risk 运行时执行 |
| 2 | [../db/dml.sql](../db/dml.sql) | 变量迁到 v5、locale 停用、新增 3 个变量与 220001。**旧镜像读不到 v5 结账变量（结账变量与结账创作页面会报错），所以 DML 后立即换镜像**，窗口约 1 分钟；支付不受影响 |
| 验收后 | `test-acceptance-cleanup.sql`（阶段 7 按实际验收数据编写，参照本机 [stage5-cleanup-risk.sql](../verification/stage5-cleanup-risk.sql)） | 删除合成结账评估族数据 |

- **执行方式：**`docker exec -i mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 mor_risk < 文件`，口令经 `MYSQL_PWD` 从 `.env.test` 传入、不回显。DML 先原样执行（文件末尾无 COMMIT，会话结束回滚）核对命中行数：UPDATE 1+7+1+1、INSERT 3+1，再追加 COMMIT 执行。
- **本机已执行过同一对文件**（阶段 4，备份后执行，核对通过），test 预检结果与本机执行前一致。
- **迁移后核对：**85 表；新快照表 17 列 5 个索引；结账 v5 变量 12 个；91002 v4 DISABLED；220001 FROZEN、v5、version_no=100、content_hash `7f730e19…4a45`。

## 4. 需要你决定

- **D1 是否在 test 激活结账默认放行基线：**
  - **A（建议）：**激活。用 Risk 接口创建流程 `CHECKOUT_BASELINE_ALLOW`（开始 → 放行）并经执行发布激活，与本机一致；以后 test 部署 Commerce 时不会因“无生效发布”全部 503。代价是 test 结账点多一个生效发布（Operations 页面可见、可替换）。
  - B：不激活。结账点保持无生效发布，验收只用临时发布并在验收后无法“撤销激活”（执行发布没有停用操作，只能被替代），所以 B 下验收只做“无发布 → 503”与配置类只读检查，不做评估写入。
- **D2 验收写入（D1=A 时）：**在基线生效后，用 3 个合成订单 ID（`991017000000000001`～`003`）直接调用评估接口：放行、同内容重放、内容不一致 409；外加格式错误 422、v4 合同 400、按订单 ID 查询与解释。验收后用清理 SQL 删除这些评估。不在 test 创建拒绝规则（避免给 Operations 的 test 留下业务规则）。
- **D3 验收入口：**与以往相同，验收窗口内用本地镜像 `nginx:1.30.4-alpine` 起临时代理（宿主 `127.0.0.1:28114` → 部署后 Risk 容器后端地址:18104），验收后删除；不改 test Nginx/Gateway。
- **D4 Operations Web：**本次不部署（test 的 Web 刚由其他会话更新）。结账点的执行发布页、评估查询“订单 ID”在 test 页面上暂不可用，经 Risk 接口验收；需要时另行发布 Web。

## 5. 执行顺序（任一步与预期不符即停止并报告）

1. **通知与复核：**通知 code-mor-76（并行使用 test 的会话）部署窗口；重跑第 1 节只读查询与 dml 预检；记录 16 个容器 image ID、Risk `docker inspect`、Nacos 实例、`mor_risk` 各表行数。
2. **备份**（`~/Code-MoR-backups/20261017-risk-checkout-runtime-test/<UTC>/`，目录 0700、文件 0600，附 SHA256SUMS，不进版本库）：`mor_risk` 全库 `mysqldump --single-transaction`、执行前行数、Risk 容器 inspect、image ID 清单。
3. **起代理、记录基线：**按 D3；读取回归接口（第 6 节）作为部署前基线。
4. **DDL：**执行 `db/ddl.sql`，核对。旧 Risk 继续运行。
5. **DML + 换镜像（同一窗口）：**dml 空跑核对 → COMMIT → 立即 `compose.sh -f requirements/20261016/risk-结账点运行期评估/release/compose-candidate.yaml up -d --no-deps --no-build --pull never --wait risk-service`；确认候选 image ID、healthy、Nacos 恰一个实例、启动日志无 ERROR；代理指向新容器地址。
6. **基线（D1=A）：**经接口创建基线流程与执行发布，校验、激活。
7. **检查与验收：**第 6 节。
8. **清理：**验收评估按清理 SQL 先 ROLLBACK 再 COMMIT；删除代理。
9. **记录：**`release/test-acceptance.md`；通知 code-mor-76 窗口结束。

## 6. 部署检查与验收

| 类别 | 内容 | 期望 |
|---|---|---|
| 健康 | readiness、启动日志、Nacos | UP；一个实例；无 ERROR |
| 支付回归（只读） | 支付流程/规则集/变量列表、执行发布列表与基线详情、名单、5 条支付评估按 ID 查询与解释 | 部署前后响应结构一致，内容只差读取时刻 |
| 结账旧数据（只读） | 结账规则集/流程列表与详情（v4）、旧候选发布列表、结账变量列表与创作上下文 | 200；v4 规则集与流程可读；创作上下文只列 v5，无 locale、无冷却/打标签动作 |
| 结账基线（D1=A） | 创建 → 校验 → 激活 | ACTIVE；引用 220001 |
| 结账评估（D2） | 放行、重放、409、422、v4 400；按订单 ID 查询与解释 | 与本机一致；输入 12 个字段 |
| 门禁 | v4 流程版本创建执行发布 | 400（只读请求无副作用：以现有 v4 流程版本尝试创建，期望拒绝且不写发布） |
| 日志 | 验收窗口 Risk 日志 | 无请求体与输入原值 |
| 写入范围 | 只写基线配置（D1=A）与合成评估 | 清理后评估行数回到部署后状态 |

## 7. 恢复

- **新镜像启动失败：**停止新容器 → 执行 [dml-rollback.sql](./dml-rollback.sql)（此时尚无结账发布与 v5 配置，预检应全为 0）→ 以 [compose-rollback.yaml](./compose-rollback.yaml) 启动旧镜像。快照表保留（旧代码不读写）。
- **运行后需要回退：**先按清理 SQL 删除结账评估；若已创建基线发布（D1=A）或 v5 规则集/流程，DML 回滚预检不为 0，不能换回旧镜像——只能保持新镜像修复（结账发布无法删除，旧镜像也不认识 v5）。这是本次发布的主要不可逆点。
- **整库还原**只在 SQL 执行后出现结构异常、且新镜像未处理请求时使用第 2 步备份。

## 8. 待批准范围

**按本清单：**D1～D4 按你的选择；通知 code-mor-76；备份；起临时代理；在 test `mor_risk` 执行 `db/ddl.sql`、`db/dml.sql`；以候选镜像替换 Risk；（D1=A）创建并激活结账基线；第 6 节检查与验收（D2 写入合成评估）；执行清理 SQL；删除代理；失败按第 7 节恢复。**不包含：**代码提交（阶段 8）、推送、生产、Operations Web/BFF 与其他服务变更。
