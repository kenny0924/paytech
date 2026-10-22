# 阶段 6 卡 G：mor-test 发布准备（只读核对 + 发布清单）

- 授权范围：**只读**核对 mor-test（`docker inspect` / `printenv` 查看、只读 SQL `SELECT`、读日志）；在主工作区 `requirements/20261022/payment-3DS全链路/release/` 下写文件。**禁止**：停启 / 替换任何容器、执行任何写 SQL、写 XXL、`docker cp` 进容器、构建或打 tag 镜像（镜像在卡 F 完成后由主会话安排）、连接生产。
- 先读：`AGENTS.md`、`agent-docs/REQUIREMENT_DELIVERY_WORKFLOW.md` 第 3 节阶段 6、两项需求的 `technique.md` 第 3 节与 `delivery.md`“发布前置记录”、本目录 `verification/stage5-report.md`。
- 模板与先例：`requirements/20261020/channel-connector-统一对外渠道出口/release/phase2-release.md`（及同目录 compose / sql）、`requirements/20261022/platform-support-文件多存储并存/release-s2/`（最新 platform / ops-bff 环境）、`requirements/20261021/platform-support-工作流/release/`。mor-test 操作约定见 `test-environment/` 下 README 与 `compose.sh`（只传 release override，不重复传基础文件）。
- 两项需求合并为一次发布，清单写在 `release/release.md`（BIN 需求目录的 delivery 链接到这里）。

## 需要产出

1. **现网快照（只读）：** 受影响服务 card、payment、channel-connector、risk-decision、acquiring、counterparty、platform-support、api-gateway、operations-console-bff、operations-console-web 当前容器的镜像 ID（`docker inspect --format '{{.Image}}'`）、启动时间、`printenv` 键名清单（只记键名，不记值）；以及各服务在历次 `requirements/*/release*/compose-candidate*.yaml` 中出现过的环境变量键（必须全部带入候选与回滚文件）。
2. **数据库只读预检：** `mor_card`、`mor_payment`、`mor_channel_connector`、`mor_risk_decision`、`mor_platform` 中本次新增对象是否已存在（表、列、块 132/133 号段、220003、变量 93014—016、CARD_BIN_CSV、国家区域）；`t_mid_capability.scheme_code` 现有取值清单（带 `schemeCode` 后的精确度冲突风险）；PAYERMAX / PAYERMAX_MOCK 渠道、标准 API PAY / PAY_QUERY 当前版本与字段（含 code-mor-46 新增的 REFUND / CAPTURE `paymentChannelRef`，不得被覆盖）；mor-test Risk 支付发布与 R-11（金额 > 500 要求 3DS）现状；Platform `internal-auth` 模式与 `PAYTECH_INSTANCE_ID`；Payment 是否已是 MQ 消费者（RabbitMQ vhost、队列）；XXL 中 payment / card / channel-connector 执行器与 `compensationEventScan` 登记。
3. **发布清单 `release.md`：** 环境身份；替换服务与顺序（Risk → Payment、acquiring、counterparty → Platform → Card → channel-connector → api-gateway → ops-bff → ops-web，若核对发现需要调整须说明原因）；当前镜像 = 回滚目标；配置差异（新增环境变量，如 Payment `PAYMENT_CHANNEL_CALLBACK_BASE_URL`、MQ 消费配置、card 补偿租约与 Redis 协调、channel-connector RabbitMQ，以及必须保留的 DUAL、`PAYTECH_INSTANCE_ID`、工作流 / 第二段 / data-query 的键）；SQL 执行顺序（引用各服务仓库的增量文件与需求 `db/`，标明目标库与执行身份）；备份对象与位置；激活步骤（块 132、133 激活后重新发布 Gateway Snapshot；CSV 写入路由经存储管理页登记；Nginx 回调入口与 `X-Entry-Channel`——mor-test 无公网入口，写明 AC-07 真实通知为遗留）；`configure_3ds.py` 对 mor-test 的执行步骤；验收步骤与合成数据清理；恢复方案（应用回退与数据恢复分开写，标明不可逆项）。
4. **文件：** `release/compose-candidate.yaml`（镜像位置留占位 `<待构建>`，环境变量完整）、`release/compose-rollback.yaml`（固定为当前镜像 ID）、`release/sql/00-precheck.sql`、`release/sql/10-*.sql`（按库按序，内容来自已评审文件，不新写业务 SQL）、`release/sql/99-postcheck.sql`、`release/acceptance-plan.md`（参照阶段 5 的验收项映射 AC）。
5. 发现会改变发布方案的事实（例如 mor-test 已有冲突号段、MID 能力冲突、PAYERMAX PAY 仍为收银台版）时，单独列在 release.md 开头“需要决定”。

报告即 `release/release.md`；最后回复 ≤15 行摘要。
