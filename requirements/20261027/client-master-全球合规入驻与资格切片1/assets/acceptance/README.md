# 切片 1 验收脚本（AC-01—AC-15）

`onboarding_acceptance.py` 只经真实外部入口驱动：Client 通道 `/api/client/...`，Operations 通道 `/api/operations/...`。入口地址、管理员凭据和只读 SQL 账号都复用 V2 全链路公共件 `requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/e2e_http.py`：`--env local` 对应 `E2E_TARGET=local`，`--env mor-test` 对应 `E2E_TARGET=test`。邮箱验证先查 Mailpit，查不到时用同目录 `acceptance_flow.verification_link` 与 `e2e_http.mint_signup_link` 的既有机制。上传沿用文件地址原则：`POST /onboarding-files/v1/uploads {filename, mimeType, sizeBytes}`，然后对象存储 PUT、`completion`，业务数据只存 `/files/v1/{fileId}`。

```bash
python3 -I onboarding_acceptance.py --env local [--create-reviewer] [--db-suffix _v2e2e] [--log-dir <日志目录>]
python3 -I onboarding_acceptance.py --env mor-test --create-reviewer        # 仅在主会话授权后
# 第二个运营账号也可由 ONB_REVIEWER_EMAIL / ONB_REVIEWER_PASSWORD 提供；--only S0,S1,SA,... 只跑部分场景
```

运行时的约束：

- 结果写 `verification/acceptance-<env>-<UTC>.json`，每步记 PASS、FAIL、SKIP 或 INFO 与证据 ID。退出码 0 表示没有 FAIL。
- 证件原值、口令、令牌和入驻码明文都不打印，证据里的证件号只保留末 4 位。
- 不删除任何数据。账号邮箱、公司名、证件号都按运行时间戳生成，每次运行唯一。
- 合成复核人（`--create-reviewer`）会在 IAM 新增一个角色和一个账号，权限为 CLIENT_ONBOARDING 资源下的全部权限。凭据写到 `~/Code-MoR-backups/onboarding-slice1-acceptance/reviewer-<run>.json`（0600）。
- 场景 SD 会撤销本次新建 Client A 的资格，使其变为 SUSPENDED。被撤销的只是本次的测试数据。

## AC → 场景步骤 → 证据

| AC | 场景 / 步骤 | 证据 |
|---|---|---|
| AC-01 | S1：Client 辖区列表；AE / SA / EG × 机构 LLC / 个人的 form-schema 分区、字段、标识、地址、关系规则、材料、声明；SA NID 为 LUHN、EG NID 为 EG_NID；未知辖区返回 422 `JURISDICTION_NOT_ACCEPTED`。S0：运营辖区包与 Schema | schemaId、分区与组件清单、Problem code |
| AC-02 | SA：空草稿需求清单的错误码集合；提交核对与需求清单同源（CURRENTLY_DUE ⊆ errors）；不完整提交返回 422 `SUBMISSION_INCOMPLETE`；填满后 ready。SC：SA NID 错误 Luhn 得到 `IDENTIFIER_CHECKSUM_FAILED`，草稿回显 identifierCheckResult | 错误码集合、ready |
| AC-03 | SA、SC：Client 读回、运营案件详情、运营 Client 详情中只有 identifierMasked，没有 identifierValue。SE：client-master 与 compliance 两库全部文本列不含本次证件原值；本次个人证件行只有 Vault Token 与 `****` 末 4 位；mor-test 的 5 个容器日志不含原值（本机需 `--log-dir`） | 命中表 / 容器、行数统计 |
| AC-04 | S0：签发返回一次性明文（格式 XXXX-XXXX、掩码 `****-` 加末 4 位）；列表只返回掩码；作废后为 REVOKED；尝试签发过期码。SA：有效码使申请冻结责任方，案件也冻结责任方。SC：不存在、作废、用尽（一次性码已被 B 使用）、过期的码都返回 422 `ONBOARDING_CODE_INVALID`；无码申请的 responsiblePartnerId 为空 | codeId、applicationId、caseId |
| AC-05 | SA：案件出现在工作台；主案件加 2 个关系人子案件（dependencyTypes）；冻结辖区、档次、程序版本 | caseId、子案件 caseId、programVersionId |
| AC-06 | SA：接受材料、豁免一项可豁免的非必需材料、全部人工检查 PASS、分类设置并确认。未评 CRR 时批准返回 422（RISK_RATING_MISSING）；评 CRR 加因子；运营 A 推荐 APPROVE 后由 A 自己批准，返回 422（FOUR_EYES_SAME_PERSON）。SB：有 OPEN 命中且有必需材料未接受时，批准返回 422；命中处置为 FALSE_POSITIVE；拒绝材料记原因码 | Problem detail 中的门禁码、evidenceId、hitId |
| AC-07 | SB：退回带需求级 errors[] 与 dueDays；Client 能看到 errors[].dueTime 与 reviewDueTime，需求清单中带 dueTime；删除被拒材料并重新上传；重提生成 roundNo=2 的新案件，其 previousCaseId 指向上一案件；已接受的要求与 PASS 的检查带 reusedFromCaseId；被拒材料重新进入待核验；审核中撤回返回 409；再次退回后撤回 RETURNED 申请，申请变为 WITHDRAWN，案件也变为 WITHDRAWN。SC：撤回 DRAFT 得到 WITHDRAWN，重复撤回返回原结果 | caseB1、caseB2、roundNo、状态 |
| AC-08 | SA：复核人 B 代 PayFac 批准；Client 状态为 APPROVED，运营 Client 详情为 ACTIVE 且带辖区与责任方；资格投影 APPROVED。只读 SQL：决定的 tier、CRR、辖区、责任方、推荐人 ≠ 决定人；资格的 next_review_time；client-master 已消费 ClientReviewDecided 与 QualificationChanged | decisionId、clientId、SQL 行 |
| AC-09 | SD：运营手工撤销资格后，Client 变为 SUSPENDED | qualificationId、clientId |
| AC-10 | SA：按 PayFac 过滤时可见，按平台自营（0）过滤时不可见；时间线与决定行记 operated_by=PLATFORM 及 on_behalf_of_partner_id | caseId、SQL 行 |
| AC-11 | 只由自动测试覆盖（见下表） | — |
| AC-12 | SA：form-schema(applicationId) 等于申请冻结的 schemaId。发布新版本不影响在途申请与案件，这一点只由自动测试覆盖 | schemaId |
| AC-13 | SA：筛查 run 的 providerCode 为 S_SCREENING_MOCK（channel-connector 渠道码）、状态 SUCCEEDED，非 TESTHIT 姓名无命中；程序含 MANUAL_SCREENING 且未录入时参与门禁。SB：TESTHIT 关系人产生 PEP 命中 | runId、hitId |
| AC-14 | S1：AE 地址为 AE_POBOX 且含 poBox；SA 国家地址的 4 个组件必填；EG 省必填且有目录；AE 个人 SOLE_ESTABLISHMENT Schema。SC：AE 个人 SOLE_ESTABLISHMENT 申请填满后 ready（Q6）。SE：client-console 的 en 与 zh-CN 语言包（Platform 命名空间 `onboarding-schema`，后端 labelKey `onboardingSchema.X` 映射为 `onboarding-schema.X`） | schemaId、manifest releaseNo |
| AC-15 | S0：运营入驻配置的辖区、各辖区 Schema 与尽调程序、EG 省目录项、Client 列表。SE：operations-console 的 en 与 zh-CN `onboarding-schema` 语言包 | HTTP 状态、数量 |
| 迁移 | SE：用只读 SQL 选一个 ACTIVE 且申请 schema_id 为空的遗留 Client（mor-test 迁移时按国家映射辖区，不是 LEGACY），断言运营详情 200、只有掩码证件；环境里没有这类 Client 时记 SKIP | clientId |

AC-14 与 AC-15 的视觉和交互部分（渲染、错误定位、空状态）不在本脚本内，需要浏览器验收。

## 只由自动测试覆盖的 AC

| 范围 | 测试 |
|---|---|
| AC-01 `ACCEPTANCE_POLICY=CLOSED`（三国均为 OPEN，不改数据） | `OnboardingFormSchemaTest.test_notAccepted` |
| AC-02 全部错误码矩阵：格式、EG NID、到期、UBO 超 100、二选一、签字人 | `SubmitClientApplicationTest.test_individualRuleMatrix`、`test_organizationRuleMatrix`；`DraftIdentifierVaultTest.test_formatAndChecksumRecorded` |
| AC-03 提交时的 `IDENTITY_CONFLICT`，以及 I01 只传 Token、MQ 不含原值 | `SubmitClientApplicationTest.test_identityConflict`；`DraftIdentifierVaultTest.test_vaultRequiredIdentifierStoresTokenOnly`；`ClientReviewRegistrationApiAutoTest.test_normalFlow_organization`。SC 在草稿阶段检查冲突，结果记为 INFO |
| AC-05 `programs/resolve` 无程序时失败关闭 | `ProgramResolutionApiAutoTest.test_noDataFound`；`ClientReviewRegistrationApiAutoTest.test_noDataFound_program` |
| AC-07 尾部：退回超期变为 EXPIRED、申请变为 WITHDRAWN。dueDays 最小为 1 天，无法安全地做时间旅行 | `ClientReviewReturnApiAutoTest.test_normalFlow_expiry`；`ClientReviewLifecycleV2Test.test_returnedThenExpired` |
| AC-09 compliance 侧的事件发布 | `QualificationChangedEventAutoTest.test_normalFlow_manualAndRevoke`；client-master 侧为 `ClientReviewLifecycleV2Test.test_qualificationRevokedSuspendsClient` |
| AC-11 ZZ 国家包全链与字面量禁令 | `NewJurisdictionByDataTest.test_zzFullChain`；`ZzCountryPackAutoTest.test_normalFlow_zzFullChain`；`JurisdictionLiteralAutoTest.test_noJurisdictionLiterals`；`ComplianceHardcodingGuardTest.test_businessRuleViolation_noCatalogLiteralsInProduction` |
| AC-12 版本发布不影响在途 | `ClientReviewCaseDetailApiAutoTest.test_edgeCase_versionFrozen`；`OnboardingFormSchemaTest.test_byApplication` |
| AC-13 Mock 只在 local 与 mor-test | 环境配置：渠道 `S_SCREENING_MOCK` 只由 `assets/harness/screening_channel_setup.py` 在这两个环境登记 |
