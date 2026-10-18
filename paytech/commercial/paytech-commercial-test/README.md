# commercial 测试

测试遵循[测试规范](../../../agent-docs/UNIT_TEST_GUIDE.md)：经正式应用访问本机 MySQL（`mor_commercial`）；client-master 走测试 HTTP 服务（`MockClientMasterCheckoutApi` + `ClientMasterCandidateMocker`，DTO 样本 `fixtures/commercial/client-master-candidates.json`）。Risk 资格仍使用正式不可用端口，测试断言 503，成功解析路径无覆盖（既有缺口）。

| 路径 | 内容 |
|---|---|
| `auto/ext/http/checkout/ResolveCheckoutHttpTest` | 原 13 例改写为 commercial 入口测试，另增 client-master 不可用 503 且本库只读 |
| `auto/ext/http/checkout/ResolveCheckoutValidationTest` | 原 3 例 HTTP 边界 |
| `auto/ext/integration/CheckoutVerificationTest` | 复核阶段（HTTP 在 Risk 处结束，经公开端口与领域合同触发）：主档版本不一致、本库合同根变化、合同终止、verify 不可用 |
| `auto/ext/http/agreement/AgreementPartyQueryHttpTest` | client-master 人员共享标记使用的合同参与方引用查询 |
| `auto/architecture/ArchitectureAutoTest` | 共享规则 R1–R12 |
| `test/mocker/local/checkout` | 商务合成夹具（`fixtures/commercial/checkout-query.json`）、只读断言 |
| `test/clear/CommercialDataCleaner` | 本库全部表清理，表集合等于 `init/ddl.sql` |

前置：按 `src/test/resources/init/ddl.sql` 人工建库、账号与表；凭据使用仓库隔离的本地默认值，可经 `MOR_COMMERCIAL_DB_PASSWORD` 覆盖。

```bash
cd paytech && ./mvnw -B verify -pl commercial/paytech-commercial-test -am
```
