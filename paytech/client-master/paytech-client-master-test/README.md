# client-master 测试

测试遵循[测试规范](../../../agent-docs/UNIT_TEST_GUIDE.md)：经正式应用、Controller、Processor、Service、Repository 访问本机 MySQL（`mor_client_master`）、Redis 与 RabbitMQ；Platform、Risk、Vault 与 commercial 走测试 HTTP 服务（`MockHttpServer`），不使用方法级行为桩。

| 路径 | 内容 |
|---|---|
| `auto/ext/http/onboarding`、`auto/ext/http/workspace`、`auto/ext/mq`、`auto/ext/eventhandler` | 自原 CCT 整类搬入的 10 类 37 例 |
| `auto/ext/http/onboarding/PersonAgreementSharingTest` | 人员共享标记的 commercial 合同引用与失败关闭 |
| `auto/ext/http/checkout` | 候选（`ResolveCandidatesHttpTest`）、复核与参与方资料（`VerifyCandidatesHttpTest`）、已存卡开关 |
| `auto/architecture/ArchitectureAutoTest` | 共享规则 R1–R12 |
| `test/base` | `ClientMasterMockTestBase`（全局 BeforeEach / AfterEach）与入口基类 |
| `test/mocker/checkout`、`test/dal/checkout` | 候选合成主档夹具（`fixtures/client-master/checkout-candidates.json`）与负例修改 |
| `test/mock/external/commercial`、`test/mocker/commercial` | commercial 合同参与方查询 DTO Mock |
| `test/clear/ClientMasterDataCleaner` | 本库全部表清理，表集合等于 `init/ddl.sql` |

前置：按 `src/test/resources/init/ddl.sql` 人工建库、账号与表（只建一次）；导出 `PAYTECH_REDIS_PASSWORD` 与 `MOR_RABBITMQ_USER/PASSWORD/VHOST`。数据库凭据使用仓库隔离的本地默认值，可经 `MOR_CLIENT_MASTER_DB_PASSWORD` 覆盖。

```bash
cd paytech && ./mvnw -B verify -pl client-master/paytech-client-master-test -am
```
