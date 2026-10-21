# G：阶段 6 mor-test 发布包报告

> 2026-10-21。本阶段只出发布包、做只读核对、在干净 worktree 里构建镜像；没有改 mor-test（容器、SQL、队列、XXL 都未写），没有提交或推送，没有调用 Superpowers，也没有改生产源码。发布包见 [release/](../release/)。

## 1. 产出

| 文件 | 内容 |
|---|---|
| [release/release.md](../release/release.md) | 范围、制品表（上一版 → 候选）、只读核对结果、SQL 清单、阶段 7 的 11 个步骤、回滚、验收、执行记录表、风险 |
| [release/compose-candidate.yaml](../release/compose-candidate.yaml) / [compose-rollback.yaml](../release/compose-rollback.yaml) | 六个服务都钉死 sha256。候选给 reconciliation 加了 RabbitMQ / Redis / 补偿 / XXL 变量（Worker 19、IP .41 沿用）和对 rabbitmq、redis、vault 的依赖，给 BFF 加了 `OPERATIONS_RECONCILIATION_BASE_URL`。两份都过了 `compose.sh config --quiet` |
| [release/source-overlay.txt](../release/source-overlay.txt) | 基线 `c6eea71a` 加上 448 个本需求文件（每个文件附 blob 哈希） |
| [release/sql/](../release/sql/) | `00-precheck.sql`（只读，已在 mor-test 跑过，输出 `00-precheck-20261021.out`）、`01-reconciliation-drop-old-tables.sql`（先断言为空再删 18 张表，保留库和授权）、`99-postcheck.sql`、`rollback.sql`。其余执行文件直接引用 `db/`，没有复制 |
| [release/scripts/](../release/scripts/) | `backup.sh`（4 个库、镜像 ID、JAR 哈希）、`apply_sql.sh`（按步骤执行 root SQL）、`xxl_reconciliation.py`（plan / apply / trigger / remove；plan 已只读跑过，结果 MISSING） |
| [release/xxl-job-reconciliation.md](../release/xxl-job-reconciliation.md) | 执行器分组，以及 `compensationEventScan`（每 30 秒，分片广播）和 `channelLineWaitExpiry`（每 10 分钟，FIRST）两个任务 |
| [release/acceptance/](../release/acceptance/) | `stage7_acceptance.py`（AC-16 + AC-13 + AC-14 及 AC-02–12 主要项，四类事件都会触发）、`recon7_common.py`、`ReconBillingFactPublisher.java`（只投 `reconciliation.billing.hash`，account 收不到；javac 已通过）、`stage7_cleanup.py`（先核对数据归属再删；支持 `--dry-run`） |
| `db/vault/dml.sql` | 头注释改正：本机和 mor-test 的 Vault 都只从数据库读策略；记录本机 2026-10-20 已执行过 |

## 2. 只读核对

- 运行镜像与 delivery 记录的上一版一致：ops-bff `9708ccdb`、ops-web `abab7475`、platform `95348566`（商务 capability-20261021），billing `998391567701`，ledger `ff20e3358a57`，reconciliation `a3642134`（空壳）。
- `mor_reconciliation`：18 张旧表，全部 0 行。`mor_billing.t_billing_fact_ref`：5 列、0 行。`mor_platform` 块 123：七张表 0 行，PLATFORM_ADMIN 版本 37。`mor_vault`：ID 730 和 `CASH_ACCOUNT_NUMBER_V1` 都不存在，策略版本 17。
- Snowflake Worker 17–31 都已占用且互不相同，19 属于 reconciliation 本身。backend IP .41 属于 reconciliation。健康探针白名单已经包含 19115。mor-test 不加载 Nacos 配置中心，不需要上传配置。
- XXL 还没有 reconciliation 分组，任务排到 19。RabbitMQ 还没有 `reconciliation.*`，用户权限是 `.*`。

## 3. 镜像（tag `recon-slice1-20261021`，部署只用 ID）

| 服务 | 候选 ID |
|---|---|
| reconciliation-service | `sha256:f181a04cad2db3fc41eaadca51d04c7a3ca586264d08c62bd9e7a264f7b4b353` |
| ledger-service | `sha256:b84f8f97a1ec740fbd0b08bdb11463516acfde301f3afbd20dc32dbc3b10a294` |
| billing-service | `sha256:b773d2cdb7a8077ecac181619093d2466cd05172810ecc83060174349a42dc2f` |
| operations-console-bff | `sha256:9520a54538277ce7f4b90bca1f61712bbbce3559601d20b17697e028709e8971` |
| operations-console-web | `sha256:edfb66284e2507727b4fe8707653ae71456c3dae2ec054e543b140985aea4007` |
| platform-support-service | `sha256:79e33d43a2c3d4e64ac8e2a95109f46b86b8a1beeba6ba987e5d2f9b8f7de3b1` |

每个候选 JAR 都和运行中的 JAR 逐类比较过，差异都在本需求文件里。ops-web 用 `c6eea71a` 单独构建一份基线，与运行容器的 143 个文件逐字节一致，所以不会回退商务已发布的内容。

## 4. 决定

1. **基线用 `c6eea71a`，不用 HEAD。** 商务会话还有 301 个文件没提交，但已经发布在 mor-test 上。以 HEAD 为基线会回退其中 290 个。主工作区这 301 个文件里，290 个与 `c6eea71a` 相同，另外 11 个共享文件相对 `c6eea71a` 只多了对账 hunk，所以整文件取用是安全的。
2. **加 ledger 镜像（必须）。** ledger 读 billing 接口用的是严格 JSON（`FAIL_ON_UNKNOWN_PROPERTIES`），新 billing 响应多出两个引用字段，null 也会输出。旧 ledger 读事实会失败，所以 ledger 必须先于 billing 替换，回滚时顺序反过来。
3. **加 platform 镜像（建议）。** 只多了能力清单 JSON，让配置页认识对账组件。
4. **合成事实只投 reconciliation 自己的哈希交换机**，不污染 mor-test 的 account 和 ledger。

## 5. 风险与遗留

- 要换 6 个服务，其中 ops-bff / ops-web / platform 与商务会话 code-mor-98 共用。执行前必须先联系对方，对方如果再发布，本包就要重建。
- 主文件 `platform-support-initial-data.sql` 在三方合并时丢了块 123 末尾的 `COMMIT;`。执行时有隐式提交，结果不受影响；mor-test 用带 COMMIT 的阅读副本。阶段 8 要补回。
- reconciliation 内存仍是 small 档（堆 160m）。mor-test 只跑小数据。
- D-2 与 I-08 照旧：事件没有订阅方时，验收之外产生的事件会留存 FAILED。
- 内部接口（当前银行账户、清算账户解析）从 mor-test 宿主机访问不到，本次不验，阶段 5 已覆盖。
- worktree 已删除，镜像保留。阶段 7 需另行授权，由主会话执行。
