# 任务卡 B（account）报告

工作位置：worktree `/Users/kenny/Desktop/Workspace/Code-MoR-chart`（未提交、未建分支、未 install）。

## 开工说明（WRITE_API_EXAMPLE 第 1 节）

- 已读：任务卡 00 / B、requirement、technique 第 2 节、`db/account/ddl.sql`、2026-10-19 决定记录、WRITE_API_EXAMPLE（第 1、2、8、11 节）、DATABASE_DESIGN_GUIDE 中 DataObject 相关条款，以及 account 现有代码与测试。
- 本卡没有新增写接口。账户号在既有开户步骤 `AccountPostingWriter.resolveAccount → openAccount` 中生成，三个入口共用这一步：事实入账、到期转可用、桶移动。生成发生在调用方已开启的 mor_account 本地事务内，没有外部调用。
- 原子写入：先在 `t_owner_account_no_sequence` 上执行 `INSERT … ON DUPLICATE KEY UPDATE last_value = LAST_INSERT_ID(last_value + 1)` 取序号，再插入 `t_owner_account`，两步在同一事务里。并发开同一个账户时，唯一键冲突转为 `ConcurrentRequestException`，整笔回滚，序号也一起回滚。
- 锁顺序：序列键 (entity_type, entity_id, currency, account_code) 是入账流键 (entity_type, entity_id, currency) 的细分，三个调用方都已先锁住流行，所以不会引入新的锁顺序。
- 查询 M5 / M6 只读，不开事务。

## 改动文件

模型与 SQL：
- `paytech/account/docs/database/account.pdma.json`：`t_owner_account` 增加 `account_no`（VARCHAR(48) ascii_bin NOT NULL，放在 id 之后）和 `uk_owner_account_no`；新增 `t_owner_account_no_sequence`（8 个字段，`uk_owner_account_no_seq`），放进「明细账：账户、批次与流水」图组，并更新图组说明。表数 9 → 10。
- `docs/database/sql/account-schema.sql`、`paytech-account-test/src/test/resources/init/ddl.sql`：两份文件同步，测试 DDL 头注释改为 10 张表。
- 新增 `docs/database/sql/changes/2026-10-19-owner-account-no.sql`：与需求 `db/account/ddl.sql` 逐字一致（复制）。
- `scripts/pdmaner/account-model.test.mjs`：表清单加入新表；新增两个用例：Schema 文件的表、列、索引与模型逐表一致；账户号与序列唯一索引的字段正确。

代码（`paytech/account/**`）：
- `OwnerAccountType`：增加 `chartAccountNo` 及 getter，六个编号按 technique 2.1；JavaDoc 写明由 ledger 测试核对一致、运行时不读 ledger。
- `OwnerAccount.accountNo`；`OwnerAccountMapper.xml` 的列、插入和分页筛选（`account_no` 精确匹配）同步。
- 新增 `OwnerAccountNoSequence`（DataObject）、`OwnerAccountNoSequenceMapper`（Java + XML）、`OwnerAccountNoSequenceRepository` 与 Impl（检查影响行数为 1 或 2，同步 `lastValue`）。
- `AccountPostingWriter`：拆出 `openAccount` 与 `allocateAccountNoSequence`；新增公开静态方法 `accountNo(...)`，格式 `{主体段}-{币种}-{科目编号}-S%06d`，FE 主体段为 `FE{entityId}`。已存在的账户直接返回，不做修改。
- API：`OwnerAccountQueryApi.listOwnerAccounts` 增加可选参数 `accountNo`（`[A-Z0-9][A-Z0-9-]{0,47}`，主体与币种仍必填）；`OwnerAccountResponseBody.accountNo`；`AccountPostingResponseBody.Entry.accountNo`。`AccountPosted` 载荷不变，`BalanceQueryApi` 未改。
- 筛选参数一路传到底：Controller → Converter → `ListOwnerAccountsRequest` → Processor → `OwnerAccountQueryService` → Repository → Mapper。

测试：
- 新增 `auto/mq/OwnerAccountNoTest`（4 例），覆盖：首次开户格式（`AE001-USD-220101-S000001`），AccountPosted 顶层与流水行都没有 `accountNo`；同主体同科目第二个 Owner 取到 S000002；同一 Owner 再次入账账户号不变，PENDING / AVAILABLE（到期 Job）/ LOCKED（桶移动 HTTP）依次取 1、2、3；FE 主体为 `FE8001-USD-220101-S000001`。
- `OwnerAccountQueryTest` 新增 `test_filterByAccountNo`：命中、不命中、前缀不匹配、非法格式返回 400；原列表用例补断言 `accountNo`。
- `AccountPostingQueryTest.test_getPostingDetail` 补断言流水行的 `accountNo`。
- 测试基础设施：`AccountTestMapper` 的行数统计包含新表，`insertAccount` 带上 account_no；`AccountDataCleaner` 清理范围加入新表；`OwnerAccountMocker` 按生产格式生成账户号（序号取 12 位测试主键，与序列分配的号不冲突），并新增可指定主体类型的重载。

arch-rules（增量编辑，A 卡随后在其上追加了 ledger 两项）：
- `scripts/generate-table-ownership.mjs`：account 的 stepAddedTables 中加入 `t_owner_account_no_sequence`；`table-ownership.yaml` 由脚本重新生成；`TableOwnershipAutoTest` 的总表数 +1，account 表集合加入新表。

## 对应 AC

- AC-11（account 部分）：新开户生成账户号，格式符合决定第 7 条，表内唯一（唯一索引），创建后不变；按账户号可精确搜索；账户列表和批次详情都返回账户号；事件载荷不含账户号。
- AC-09（account 部分）：原有入账、移动、到期、重建、查询用例全部通过。

## 实际执行的命令与结果

- 本机资源：用 root 删除并重建 `mor_account_chart`，建库 SQL 由 worktree 测试 DDL 改库名得到，并给 `account_app` 授予该库 DML 权限；vhost `/mor-chart` 已存在，未改动。
- `cd paytech && ./mvnw -o -pl account/paytech-account-test -am verify -Dpaytech.local-environment.dir=<主工作区>/local-environment`，环境变量 `ACCOUNT_TEST_DB_URL=…/mor_account_chart`、`MOR_RABBITMQ_VHOST=/mor-chart`：**BUILD SUCCESS**。account-test 63 例，0 失败 0 错误；上游各模块（Foundation、arch-rules 等）的测试也全部通过。
  - 需要显式传 `-Dpaytech.local-environment.dir`：worktree 下没有 `local-environment/.env.local`，不传的话 Foundation messaging 测试会失败。
  - 第一次运行有 33 个失败，原因是 `last_value` 是 MySQL 8 保留字，`upsertNext` SQL 报语法错误。已在 XML 中给该列加反引号（DDL 本来就带反引号），并通知了 A 卡。
- `./mvnw -o -pl tools/arch-rules -am verify -Dtest='com/paytech/arch/**/*Test'`（在 A 卡追加 ledger 两项之后）：arch-rules 60 例，0 失败；`node tools/arch-rules/scripts/generate-table-ownership.mjs --check`：YAML 与脚本一致（10 个 step-added 表）。
- `node --test scripts/pdmaner/account-model.test.mjs`：6 例全部通过。
- 未运行：`npm --prefix requirements-site test`（不在本卡范围）；BFF / Web 编译与测试（不在本卡范围，见下）。account 全量测试是在 A 卡最后一次修改 arch-rules 之前跑的；A 只追加了 ledger 表归属，不影响 account 的 ArchitectureAutoTest，之后没有再跑 account 全量。

## 偏离设计之处

- `AccountPostingResponseBody.Entry` 同时被批次详情（M6）和追平列表 `GET /account-postings/v1` 使用，所以追平列表的流水行也会带 `accountNo`。ledger 用同一个 account-api 类反序列化，不受影响。该类的注释已写明 accountNo 是「与 AccountPosted 载荷一一对应」的唯一例外。
- 增加了 DataObject `OwnerAccountNoSequence`（任务卡没有要求），以符合「每张表都有 DataObject / Mapper / Repository」的约定。
- 账户号格式方法 `AccountPostingWriter.accountNo` 设为 public static，测试 Mocker 也用它，保证测试数据格式与生产一致。

## 未完成项与对其他卡的影响

- **C 卡（BFF）必须改动**：`OwnerAccountQueryApi.listOwnerAccounts` 在 balanceBucket 与 page 之间新增了 `accountNo` 参数，Operations BFF `FinanceSubledgerClientImpl.java:37` 按位置调用，本卡改动后会编译失败。C 卡需要传入 accountNo（或 null），并处理 BFF 严格解析时的新字段 `accountNo`（`OwnerAccountResponseBody` 与 `AccountPostingResponseBody.Entry`）。已通知 main。
- `paytech/account/README.md` 的账户号说明由 D 卡编写，本卡未改。
- 变更 SQL 未在 `mor_account` 或 mor-test 执行（待阶段 5 / 6 授权）。
