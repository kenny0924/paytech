# client-master 发布 SQL（cm-sql，2026-10-27）

范围：按主会话决定 P-8（只清在途入驻，不清 ACTIVE Client）与 P-11（遗留行满足新列语义，个人证件号脱敏而不 Token 化）准备 client-master 发布 SQL，并在断网一次性容器上离线试跑。未提交，未推送，也没有在任何真实库执行 SQL。mor-test 与本地库只用 `mysqldump --single-transaction` 做了只读导出。

## 1. 文件与执行顺序

目录：`paytech/client-master/docs/database/sql/changes/`

| 序 | 文件 | 性质 | SHA-256 |
|---|---|---|---|
| ① | `2026-10-27-onboarding-global.sql` | DDL，保留行，隐式提交，不可整段重跑 | `077847603f20d2aa2a7b5e543c0d4b09ae25e6151e975704941bb2c87d900b31` |
| ② | `2026-10-27-onboarding-global-cleanup.sql` | 只删 PENDING Client，一个事务，可重跑 | `5d2093bc536770a1aac1131a93c1d801276d73ab6a1134e265a9f24b0e15d570` |
| ③ | `2026-10-27-onboarding-country-pack.sql` | AE/SA/EG 国家包增量，可重跑 | `a0ed6738c9ce24ebad6c583e303d2ec00312814bbfff56e6ccc9c0fe3107395f` |
| ④ | `2026-10-27-onboarding-global-legacy-backfill.sql` | P-11 回填与脱敏，一个事务，可重跑 | `4e23c4c9e29d65db8cb842ce2770bb317856a778d4c139e565c94bb552f1bbc3` |
| ⑤ | `requirements/…/db/client-master/legacy-redaction-verify.sql`（`release/sql/29-…`） | 只读核验，前 6 项须为 0 | infra-dev 文件，未改 |

**与原定顺序的差异**：原定为 DDL → 清理 → 回填 → 国家包，这里把**国家包放在回填之前**。P-11 要求按 `t_jurisdiction` 中的 ACTIVE 辖区映射遗留申请的 `jurisdiction_code`，个人证件类型也按 `t_rule_setting` 的 `VAULT_REQUIRED` 规则识别。如果先回填，辖区表还是空的，全部遗留申请都会落成 `LEGACY`。回填的预检会在没有任何 ACTIVE 辖区时 SIGNAL 中止，所以顺序错了会直接停下，不会悄悄写错。国家包只依赖 DDL，放在清理之后也没有问题。

所有停止条件都由存储过程 `SIGNAL SQLSTATE '45000'` 中止，与 `2026-10-24-relationship-snapshot-data.sql` 的做法相同。过程在事务外创建和删除，中止发生在写入前；事务内的后置断言失败时，连接断开即回滚。

## 2. 各文件要点

### ② 清理（P-8）
- **删除集合**：`t_client.status='PENDING'` 及其申请、材料、声明、业务画像与分类、CLIENT 角色、Client–Partner 关系、存卡设置、Client BCR 记录与版本、申请下的主体关系，以及只被它们引用的 Party 及全部资料子表（个人 / 机构资料、法律标识与身份键、地址、联系方式、扩展属性）。另外删除这些申请的尽调 Case 对应的 `t_consumed_event` / `t_consumed_aggregate_version`，以及按 key 指向它们的补偿任务：REGISTRATION 按 submission，WITHDRAWAL 按申请，CLASSIFICATION 按 Case，TENANT_PROJECTION 按 BCR 版本。
- **保留判定**：仍被其他 Client、OE、Local Partner、不删除的关系、非本 Client 的角色、不删除的材料，或 OE / Partner 申请资料引用的 Party 一律保留。
- **停止条件**：DDL 未执行；存在 `PROCESSING` 补偿任务；PENDING Client 已激活或有 APPROVED 申请；PENDING Client 有 OE 关联；PENDING Client 是 OE 来源（来源关系或 OE 申请）；PENDING Client 的 Party 被保留数据使用；保留 Party 的资料仍是 PENDING 申请的草稿。
- **后置断言（COMMIT 前）**：没有剩余 PENDING Client；无孤儿申请、画像、角色、材料、关系、资料、标识或身份键；Client、OE、LP、Partner 角色、OE 范围、来源关系、卖方披露、OE 关联的计数与清理前一致。

### ③ 国家包
- 内容与 `client-master-initial-data.sql` 的国家包逐行相同：辖区 3、组织类型 20、标识类型 8、规则 79、关系类型 5、地址组件 27、目录 109、属性 10、Schema 8、字段 275。数据先写入会话临时表，再用 `INSERT … SELECT … ON DUPLICATE KEY UPDATE update_time = update_time` 写入，已存在的行保持不变（包括人工改成 DRAFT / RETIRED 的行），重跑为空操作。
- 停止条件：同一业务唯一键已被国家包 ID 段以外的行占用（否则 ON DUPLICATE 会把它静默当成已存在）。
- 文件头写明：本地与 mor-test 验收直接以 ACTIVE 执行；生产环境在法务 26 项未确认前，在同一会话先 `SET @cm_pack_status = 'DRAFT'`，目录与 Schema 以 DRAFT 写入（Schema 的 `active_slot` 与 `approved_time` 置空）。
- `client-master-initial-data.sql` 未改动，仍供空库与 `JurisdictionPackMocker` 使用。初始化 DML 以后改动时，本文件需要同步。

### ④ 回填（P-11）
- 个人号码取并集判定：INDIVIDUAL Party，或类型的 `VAULT_REQUIRED='1'`，与核验口径一致。处理方式：
  - `identifier_key_value = 'LEGACY-REDACTED-<identity_key_id>'`；
  - `identifier_masked_value = '****' + 原号码末 4 位`，在同一 UPDATE 中先算掩码，再把 `public_identifier_value` 置 NULL；
  - `identifier_vault_token` 保持为空；
  - 机构登记号保持明文；已是 43 位 Token 或已脱敏的行不改。
- 申请字段：
  - `application_type='ONBOARDING'`；
  - `jurisdiction_code` 取 Client 当前资料中的国家（个人：`residence_country`，否则 RESIDENTIAL 地址；机构：`incorporation_country`，否则 REGISTERED 地址），映射到 ACTIVE 辖区，映射不到时取 `LEGACY`；
  - `schema_id` 为 NULL，不为 0；
  - 已提交过的申请（`submitted_time` 非空）`submission_round=1`。
- `t_client.jurisdiction_code` 为空时，取该 Client 最近一次申请的辖区（含 `LEGACY`）。没有申请的 Client 保持 NULL。
- 遗留 Party 资料的 `jurisdiction_code` 与地址的 `address_format_code` 保持 NULL，因为遗留资料不是按国家包采集的。
- 停止条件：DDL 未执行（`schema_id` 仍为 NOT NULL），或没有任何 ACTIVE 辖区。

### ① DDL 的改动（第 3 项）
- `t_client_application.schema_id` 改为 **可空**，注释补充“遗留申请为空”。同步修改了 `client-master-schema.sql`、测试 DDL `paytech-client-master-test/src/test/resources/init/ddl.sql` 和 PDManer `client-master.pdma.json`（直接编辑 `notNull` 与注释，未用生成器）。
- `application_type`：`ADD COLUMN … NOT NULL DEFAULT 'ONBOARDING'`，随后 `ALTER COLUMN … DROP DEFAULT`。存量行直接得到正确值，最终定义与 Schema 文件一致，均无默认值。
- `jurisdiction_code` 保持 NOT NULL：DDL 后暂为空串，由回填写入国家辖区或 `LEGACY`。这个值有确定的遗留取值，改成可空没有必要，核验也会拦截空串。`submission_round` 同理，暂为 0，由回填修正。
- 文件头的执行顺序与“reset 清空”的说明已改为新顺序，并注明 reset 已被 P-8 取代。
- 校验：mor-test 导出执行 DDL 后，全部表的列（类型、可空、默认值、注释）和索引，与按 `client-master-schema.sql` 新建的库**完全一致**；测试 DDL 与 Schema 文件的列定义也一致。

### reset 文件
`2026-10-27-onboarding-global-reset.sql` 已**删除**。它是未跟踪文件，只被 `CLIENT_MASTER_PHYSICAL_DECISIONS.md` 第 3 行引用，该行已改为指向新的四个文件。runbook ⑤ 中出现的文件名是“不执行 …-reset.sql”这句提示，未改。

## 3. 离线试跑

工具与环境：`assets/harness/db_dryrun.sh`，`mysql:8.4.11`（与 mor-test 同一镜像 ID `sha256:b3b90af2…`），容器加 `--network none`。

步骤：计数 → ① → ② → ③ → ④ → ⑤ → 计数 → 重跑 ②③④ → 再跑 ⑤。两库**全部步骤成功**。完整日志：`verification/cm-sql-dryrun-mortest.log`、`verification/cm-sql-dryrun-local.log`。

### 3.1 mor-test `mor_client_master`（只读导出，2026-10-27）

| 表 | 前 | 后 | 表 | 前 | 后 |
|---|---|---|---|---|---|
| Client ACTIVE / PENDING | 37 / 17 | 37 / 0 | t_party_role | 58 | 41 |
| t_client_application | 57 | 37 | t_party_relation | 3 | 0 |
| t_application_document | 54 | 37 | t_business_change_record / version | 56 / 99 | 39 / 82 |
| t_client_business_profile / classification | 54 / 37 | 37 / 37 | t_consumed_event / aggregate_version | 18 / 18 | 15 / 15 |
| t_party / t_party_profile | 59 / 59 | 39 / 39 | t_compensation_event | 0 | 0 |
| t_individual_profile / organization_profile | 54 / 5 | 37 / 2 | t_client_entity_affiliation | 7 | 7 |
| t_party_identity_key / profile_identifier | 50 / 57 | 37 / 37 | OE / LP / partner_role / OE scope / source relation / seller disclosure / legal doc | 1/1/3/1/1/1/3 | 不变 |
| t_party_address / contact | 57 / 100 | 37 / 74 | | | |

保留的 39 个 Party 是 37 个 ACTIVE Client，加上 LP 的 Party 880000000702 和 PLATFORM_HQ 占位 Party 880000000701；后者没有被任何行引用，不属于 PENDING，按规则保留。

- **清理预检**：
  - 申请状态：ACTIVE 37 个均为 APPROVED；PENDING 中 DRAFT 9、UNDER_REVIEW 5、RETURNED 3、REJECTED 3。
  - 删除集合：Client 17、申请 20、Case 11、关系 3、BCR 17 + 17、候选 Party 20、其中被保留数据引用的 0。
  - 停止条件全部通过；后置孤儿检查 9 项全 0。
- **国家包**：唯一键冲突 0；首次写入前已存在 0，写入后各表 present = pack；辖区 ACTIVE 3；Schema 8 行均为 ACTIVE、`active_slot=1`。
- **回填**：待脱敏身份键 37、个人资料标识 37；待回填申请辖区 37、提交轮次 37；国家映射 AE → AE 37。回填后申请为 `ONBOARDING / AE / APPROVED / round 1 / schema NULL`，共 37 条；Client 辖区为 AE ACTIVE 37；标识类型不在目录中的行数 0（PASSPORT 已在目录中）。
- **⑤ 核验**：前 6 项全 0，展示段 `legacy_redacted_keys 37`、`legacy_jurisdiction_apps 0`、`apps_schema_null 37`、`clients_ACTIVE 37`。
- **重跑**：② 删除集合为空，③ 冲突 0 且无写入，④ 待处理全 0，⑤ 仍全 0。

### 3.2 本地 `mor_client_master_v2e2e`

导出时去掉了库名，以 `mor_client_master` 的名字导入，以便直接复用核验文件中的 `USE`。

- 数据现状：ACTIVE Client 2 个（组织，US），**没有申请**，也没有标识或 PENDING Client。
- 结果：清理删除集合为空，各表计数前后不变；国家包写入 3/20/8/79/5/27/109/10/8/275；回填无需脱敏，国家映射 US → LEGACY 2，但因为没有申请，`t_client.jurisdiction_code` 保持 NULL；⑤ 前 6 项全 0；重跑无变化。

### 3.3 停止条件的反向验证（本地导出）

| 场景 | 结果 |
|---|---|
| 未执行 DDL 就跑 ② | `STOP: run 2026-10-27-onboarding-global.sql (DDL) first`，无写入 |
| 存在 PROCESSING 补偿任务时跑 ② | `STOP: compensation events are PROCESSING; stop client-master-service first` |
| 未执行国家包就跑 ④ | `STOP: no ACTIVE jurisdiction; run 2026-10-27-onboarding-country-pack.sql first` |
| 预置一行 ID 不同的 `UBO` 关系类型后跑 ③ | `STOP: a business unique key of the country pack is taken by a row outside the pack ID range` |
| `SET @cm_pack_status='DRAFT'` 后跑 ③ | 辖区 3、目录 109 为 DRAFT；Schema 8 行为 DRAFT，`active_slot` 为 NULL |

## 4. 与 compliance 删除的 17 个案件族的对应关系：**不是一一对应**

在断网容器中把两个只读导出放进同一实例后比对（仅用于核对，发布 SQL 不跨库）。结果：

- compliance 删除的 17 个主案件只对应 **12 个 Client**：
  - 13 个案件属于 **8 个 PENDING Client**：当前一轮的 SUBMITTED 5、REJECTED 3、RETURNED 3，以及 2 个更早轮次的 RETURNED；
  - **4 个案件属于 4 个 ACTIVE Client**：这些 Client 退回后重提获批，删除的是被取代的早期 RETURNED 轮次，它们的 APPROVED 主案件和资格保留。
- **9 个 PENDING Client 只有 DRAFT 申请**，从未提交，compliance 中没有对应案件。
- 因此“17 = 17”只是数量巧合，runbook 3.2 节“删除的 17 个主案件与 client-master 的 17 个 PENDING Client 一致”的说法不成立。两边的清理口径（client-master 按 Client，compliance 按非 APPROVED 主案件族）各自自洽：
  - 删除 4 个 ACTIVE Client 的早期退回轮次，不影响它们当前的申请、案件与资格。client-master 申请只引用当前 APPROVED 的 Case。
  - 其中 2 个早期轮次 Case 在 client-master 中有消费记录，这些记录会保留下来，成为无害的孤儿。
- 已知残留：PENDING Client 237572886959857665 有 1 条更早轮次 Case（237572889170268160）的消费记录。client-master 只记录申请当前一轮的 Case，无法在本库识别这一条，所以保留，文件头已注明。compliance 会删除这个 Case，Case ID 不复用，不影响运行。

## 5. 给其他 agent 的事项

- **cm-dev**：
  - 遗留申请的 `schema_id` 为 NULL。凡是把它拆箱为 `long` 的路径（如 `OnboardingPackService.load(long)`、`OnboardingSchemaRepository.findById(long)`、`ClientDraftMapper` 的 `long schemaId` 参数）都需要处理遗留申请，例如运营详情、遗留 Client 的再次入驻。
  - 遗留资料的 `jurisdiction_code` 为 NULL；申请或 Client 的辖区可能为 `LEGACY`，这个值不在 `t_jurisdiction` 中。
  - 遗留个人标识只有掩码，没有 Token 和明文。
  - 本地测试库若早于这次修改建库，`schema_id` 仍为 NOT NULL；这只会更严格，不影响现有测试。
- **infra-dev / runbook**：
  - ⑤ 的顺序改为 DDL → 清理 → 国家包 → 回填 → `29` 核验。
  - 发布冻结时把四个文件放入 20—39 号段，例如 `20-…global.sql`、`21-…cleanup.sql`、`22-…country-pack.sql`、`23-…legacy-backfill.sql`。
  - 3.1 节表格可更新为本报告 3.1 节的结果。
  - 3.2 节“17 个一致”的结论需要按本报告第 4 节更正。
