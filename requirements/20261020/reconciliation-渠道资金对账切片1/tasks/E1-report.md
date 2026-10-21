# E1 报告：billing 引用表两列落库（Q-02）

## 改动文件

| 文件 | 改动 |
| --- | --- |
| `paytech/billing/docs/database/billing.pdma.json` | `t_billing_fact_ref` 在 `ref_id` 后增加 `external_reference_type` VARCHAR(32) ascii/ascii_bin 可空、`external_transaction_id` VARCHAR(128) ascii/ascii_bin 可空，注释与需求 `db/billing/ddl.sql` 逐字一致；`ref_type` 注释追加 `CHANNEL_TRANSACTION_REF、RECON_RESOLUTION`；`updatedTime` 改为 2026-10-20。JSON 以原格式（2 空格缩进、非 ASCII 原样）回写，`dbConns` 仍为空。 |
| `paytech/billing/docs/database/sql/billing-schema.sql` | 由 `renderSchema` 从模型重新渲染，仅该表三行变化。 |
| `paytech/billing/paytech-billing-test/src/test/resources/init/ddl.sql` | `t_billing_fact_ref` 建表语句替换为 Schema 中同一段，其余不动。 |
| `paytech/billing/docs/database/sql/changes/2026-10-20-billing-fact-ref-channel-transaction.sql` | 新增变更脚本；`ALTER` 正文与需求 `db/billing/ddl.sql` 逐字一致（已 diff 核对），头注释按同目录 `2026-10-20-billing-fact-void.sql` 风格写目标库、前提、影响、发布顺序、回退。 |
| `scripts/pdmaner/billing-model.test.mjs` | `ref_type` 注释结尾正则改为 `…FX_QUOTE、CHANNEL_TRANSACTION_REF、RECON_RESOLUTION。`；新增该表字段数 7 与字段顺序断言、两新列 VARCHAR 可空断言、`external_transaction_id` 长度 128 断言。 |

## 验证

- `node --test scripts/pdmaner/billing-model.test.mjs`：7/7 通过（含 Schema 与模型渲染逐字一致、测试 DDL 与 Schema 逐表一致、标识唯一）。
- `node --test scripts/pdmaner/*.test.mjs`：575 项，530 通过、45 失败；失败均在 Acquiring、Card、Finance、Payment、Platform Support、Notification、Risk 等其他模型及 CURRENT_IMPLEMENTATION 相关断言，无 billing 相关失败，属既有问题，未处理。
- 未执行任何 SQL，未跑 Maven。

## 给 Java 线的说明

- `paytech-billing-core/src/main/resources/mapper/billing/BillingFactRefMapper.xml` 的 `<sql id="columns">` 与 `batchInsert` 都是显式列清单（`id, fact_id, ref_type, ref_id, create_time`），需由 Java 线补上两列，并给 `BillingFactRef` DataObject 加对应字段；不补也不会因新列报错（两列可空），只是读不到、写不进。
- 测试侧 `BillingTestMapper.java` 第 71 行查询与第 97 行插入同为显式列，若测试需要断言外部引用，需同步补列。
- 没有测试把 Mapper 列清单与 Schema 做比对。
- `paytech/billing/README.md` 只列表名，无需因本次改动修改。
