# commercial 测试

测试遵循[测试规范](../../../agent-docs/UNIT_TEST_GUIDE.md)：经正式应用访问本机 MySQL（`mor_commercial`）；client-master 走测试 HTTP 服务（`MockClientMasterCheckoutApi` + `ClientMasterCandidateMocker`，DTO 样本 `fixtures/commercial/client-master-candidates.json`）。Risk 资格仍使用正式不可用端口，测试断言 503，成功解析路径无覆盖（既有缺口）。

| 路径 | 内容 |
|---|---|
| `auto/ext/http/checkout/ResolveCheckoutHttpTest` | 原 13 例改写为 commercial 入口测试，另增 client-master 不可用 503 且本库只读 |
| `auto/ext/http/checkout/ResolveCheckoutValidationTest` | 原 3 例 HTTP 边界 |
| `auto/ext/integration/CheckoutVerificationTest` | 复核阶段（HTTP 在 Risk 处结束，经公开端口与领域合同触发）：主档版本不一致、本库合同根变化、合同终止、verify 不可用 |
| `auto/ext/http/agreement/AgreementPartyQueryHttpTest` | client-master 人员共享标记使用的合同参与方引用查询 |
| `auto/repository/ProductCatalogRepositoryTest` | 产品目录三表：种子目录读回、唯一键、条件状态迁移 |
| `auto/repository/AgreementSubscriptionRepositoryTest` | 合同订阅、开通项与约束：唯一键、按父读取、删除、以状态 / update_time 为条件的更新 |
| `auto/repository/AgreementWriteRepositoryTest`、`PricingProfileWriteRepositoryTest`、`CommercialTermWriteRepositoryTest` | 既有 11 张表的完整行写入映射：插入、唯一键、按父读取、条件更新与整体替换 |
| `auto/repository/CheckoutFactsMappingTest` | Checkout 只读快照的第三轮新列映射；闭合枚举之外的数据库 Code 失败关闭 |
| `auto/database/CommercialDatabaseFilesTest` | 纯文件检查：`init/ddl.sql` 逐表等于生成 Schema、清理器表集合、初始化 DML 等于目录固件 |
| `auto/architecture/ArchitectureAutoTest` | 共享规则 R1–R12 |
| `test/mocker/local/checkout` | 商务合成夹具（`fixtures/commercial/checkout-query.json`）、只读断言 |
| `test/mocker/local/catalog` | 种子产品目录（`fixtures/commercial/product-catalog.json`，与初始化 DML 一致）及单行插入 |
| `test/mocker/local/agreement` | 合同订阅、开通项与约束的前置数据 |
| `test/builder` | 17 个完整行 DataObject 的 Builder（`template()` 合法默认值） |
| `test/clear/CommercialDataCleaner` | 本库全部表清理，表集合等于 `init/ddl.sql` |

前置：按 `src/test/resources/init/ddl.sql` 人工建库、账号与表；凭据使用仓库隔离的本地默认值，可经 `MOR_COMMERCIAL_DB_PASSWORD` 覆盖。

```bash
cd paytech && ./mvnw -B verify -pl commercial/paytech-commercial-test -am
```
