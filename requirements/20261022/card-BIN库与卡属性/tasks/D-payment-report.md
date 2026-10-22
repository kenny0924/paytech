# 卡 BIN-D 报告：payment 卡属性快照、Risk 输入、schemeCode、PaymentSucceeded 字段

- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR-bin-pay`（main 1d39cd94 + 五张卡合并结果，已暂存为基线）。本卡改动全部未暂存，`git diff` 即本卡范围。
- 未提交、未推送、未建分支；没有改主工作区代码（只写了本报告）；没有调用 Superpowers。

## 1. 已读与开工自查

- 已读：`AGENTS.md`、`payment-3DS全链路/tasks/00-common.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`JAVA_PROJECT_STRUCTURE.md`、`examples/WRITE_API_EXAMPLE.md`（含第 11 节）、`UNIT_TEST_GUIDE.md`、`examples/UNIT_TEST_EXAMPLE.md`、`DATABASE_DESIGN_GUIDE.md`（第 5、13—15 节）；本需求 `requirement.md`、`technique.md`（3.4、3.5.2、3.5.4、3.5.5、3.8）、`db/payment/ddl.sql`；任务卡 D（含阶段 4 中途裁决）与卡 A、B 报告。
- 所属 Processor / Context：
  - 申请卡工具：`ApplyCardInstrumentProcessor`（Intent 资源锁），`ApplyCardInstrumentContext` 持有 Intent 与待保存 `PaymentInstrumentToken`。
  - pay：`PayProcessor`（路由），`PayContext`（Intent、Attempt、InstToken）；PAY_RISK / PAY 动作由 `PayRiskCheckActionActor`、`PayActionActor` 执行。
  - 事件：补偿 Handler `PaymentSucceededHandler`。
- 本地原子写入：沿用 `PaymentExecutionService.saveApplyCardInstrumentContext` 的单一事务插入 Token；8 列随同一条 `insertToken` 写入，不新增事务或写入点。
- 事务外步骤：Card 创建工具（读取 `cardAttributes`）在事务前；Risk 评估、MID 候选查询、渠道执行、MQ 发布与改造前一样都在事务外。原提交重放只调 Card 核对工具引用，不改写 Token（快照冻结，B4）。

## 2. 改动清单

**数据库与模型**
- `scripts/pdmaner/generate-payment-model.mjs`：Manifest 在 `card_fingerprint` 后加 8 列（闭合枚举 / 开放来源标注）；开放代码行数 53 → 56，并集字段数 401 → 409。
- `scripts/pdmaner/payment-field-metadata.mjs`：8 个字段元数据（中文名、说明），唯一字段键 233 → 241；`CLOSED_ENUM_VALUES` 加 card_brand、funding_type、card_product；`OPEN_CODE_SOURCES` 加 card_level、issuer_country、issuer_region。生成的注释与 `db/payment/ddl.sql` 逐字一致。
- `scripts/pdmaner/payment-execution-field-overrides.mjs`：6 个字符列 ascii / ascii_bin。
- `scripts/pdmaner/payment-model.test.mjs`：测试自有 Oracle 同步 8 列、字段数 409、模型与结构摘要。
- 重新生成：`paytech/payment/docs/database/payment.pdma.json`（17 表 247 字段）；`payment-schema.sql` 由 `renderSchema` 重生成，只多 8 行；`PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md` 第 2.1 节补一句，并更新 modelSha256。
- `paytech/channel-connector/docs/database/channel-connector.pdma.json`：重新生成，只有 4 个图元的 y 坐标变化（见第 6 节 D3）。
- 测试库 DDL `paytech-payment-test/src/test/resources/init/ddl.sql`：同步 8 列。

**Java（payment）**
- `PaymentInstrumentToken` 加 8 个字段；`PaymentInstrumentTokenMapper.xml` 的 resultMap、列清单与 insert 同步。
- `CardInstrumentResult` 增加嵌套 `CardAttributes`。
- `PaymentCardClientImpl.cardAttributes()` 校验并映射 Card 的 `cardAttributes`：
  - 对象缺失（旧 Card）：8 列全空、按未识别处理，记一条 WARN（只带 instrumentRef，不带卡值）；见 D8。
  - 对象存在时：版本号为空或正数。
  - 未识别时各属性必须为空，只保留版本号。
  - 已识别时：命中位数为 6—8；卡组织必须是 card-api `CardBrand` Code；借贷记、产品类别为空或 card-api 枚举 Code；卡等级为空或匹配 `[A-Z0-9_]{1,32}`；发卡国 `[A-Z]{2}` 必填；区域 `[A-Z0-9_]{1,16}` 必填。
  - 对象存在但违约时抛 `ErrorException`（500），不保存 Token。
- `ApplyCardInstrumentProcessor.bindCardAttributes()`：首次绑定时冻结 8 列。重放分支不经过绑定，所以沿用原 Token。
- Risk：
  - `PaymentRiskCommand` 加 `issuerCountryCode`、`cardBrand`、`fundingType`。
  - `PayRiskCheckActionActor.fillCard()` 对卡工具填入这三项，BIN 和指纹的原有条件不变。
  - `PaymentRiskClientImpl` 改用 20 参数构造器。
- 路由与执行：
  - `RouteContext.schemeCode` 由 `PayProcessor.route()` 取自 `token.card_brand`。
  - `ChannelExecutionCommand.schemeCode` 由 `PayActionActor.command()` 取自同一来源。
  - `PaymentChannelClientImpl.criteria()` 写入 `MidCriteriaBody.schemeCode`。未识别时为 null，请求不带该字段。
- PaymentSucceeded：
  - `PaymentSucceededResult` 加 `cardBrand`、`fundingType`、`cardLevel`。`PaymentSucceededHandler.result()` 只在卡支付时填写。
  - `PaymentSucceededPayload.json()` 在 v2 末尾追加这 3 个字段（可空，`SCHEMA_VERSION` 仍为 2）。
  - `foundation/paytech-messaging/catalog/events.yaml` 条目注释同步。

**risk-decision-api（按阶段 4 裁决）**：删除 `PaymentAttemptRiskInput` 的 17 参数兼容构造器。仓库内已没有调用方；Risk 测试 Builder 用的是 20 参数构造器。

**测试**
- `CardApiMocker`：按生效 BIN 库计算 `cardAttributes`，按 8、7、6 位前缀匹配。默认版本 3、无号段，即“查询过但未识别”。新增 `binRange`、`clearBinRanges`、`activeBinLibrary`、`rawCardAttributes`（违约负例）和 `attributes` 工厂；`clear` / `assertEmpty` 都覆盖了新状态。
- `PaymentTestMapper.instrumentTokens()` 增加读取 8 列。
- `CardStorageTest` 新增 5 例 + 3 组参数：
  - 识别时冻结 8 列（8 位命中）；未识别；无生效版本时全空。
  - 重放沿用原快照，切换版本后新工具用新版本（6 位 → 8 位）。
  - 旧 Card 缺快照对象：工具照常创建、8 列全空，日志有 WARN 且不含卡号（`test_cardAttributesMissingFromOldCard`）。
  - 3 种违约快照返回 500，且不写 Token。
- `PayRiskEvaluationTest` 新增 `test_cardAttributesInRiskInput`：识别时 20 字段精确比对，号段未提供的卡等级不传。原 `test_allowWithoutAuthentication` 在未识别时仍精确断言 17 字段，即三项省略。
- `PayCardChannelTest` 新增 `test_schemeCodeFromCardBrand`：识别时候选查询与渠道执行都带 `VISA`；未识别时两处都为 null。
- `PaymentSucceededPublishTest`：原成功例断言未识别时 3 字段为 JSON null；新增 `test_succeededCarriesCardAttributes`，断言识别值。
- `PaymentSucceededEnvelopeChecker`：字段顺序表加 3 个字段，新增 `cardAttributes()` 断言。

## 3. 对应 AC

- AC-02（Payment 侧）：快照只在首次绑定时冻结；重放后不变，BIN 库切换版本后新工具使用新版本。见 `test_replayKeepsFrozenAttributes`。
- AC-04：未识别时工具创建成功，属性为空，版本号有值。见 `test_cardAttributesUnrecognized`；没有生效版本见 `test_cardAttributesWithoutActiveLibrary`。
- AC-08（Payment 侧）：Token 冻结快照，见 `test_cardAttributesFrozen`；Risk 请求带 `issuerCountryCode`、`cardBrand`、`fundingType`，见 `test_cardAttributesInRiskInput`。规则能否按发卡国命中由 Risk 卡（B）验证。
- AC-09（Payment 侧）：识别时 schemeCode 同时传给选路与执行，未识别时不传，见 `test_schemeCodeFromCardBrand`。精确 `scheme_code` MID 的匹配由 channel-connector 执行，Payment 测试只断言请求字段。
- AC-10：v2 末尾追加可空 3 字段，schemaVersion 仍为 2；未识别时为 null，识别时为冻结值。billing 写入属于计费需求 B。
- AC-11（Payment 侧）：卡值不进日志的原有断言（`test_cardValuesNotLogged`）继续通过；新字段只是标准化属性，不含 BIN 或卡号。

## 4. 本机 SQL 与备份

| 库 | 备份 | 执行 | 核对 |
|---|---|---|---|
| `mor_payment`（127.0.0.1:13306） | `/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/936397ef-ca1e-4532-b001-b27dfa1be0a4/scratchpad/backup/mor_payment-20261022-172348.sql` | `requirements/20261022/card-BIN库与卡属性/db/payment/ddl.sql`（ALTER ADD 8 列） | `information_schema` 显示列序、类型、可空与 ascii 字符集正确；8 列注释与 `payment-schema.sql` 逐字一致 |

没有连接 mor-test 或其他非本机环境。

## 5. 测试命令与结果

- `JAVA_HOME=jdk-25 ./mvnw -o -pl payment/paytech-payment-test -am test -Dtest='com.paytech.payment.**' -Dsurefire.failIfNoSpecifiedTests=false -Dpaytech.local-environment.dir=…/Code-MoR/local-environment`：**96 例全部通过**，覆盖 10 个测试类，含 `ArchitectureAutoTest`。D8 修改后重跑同一命令，仍是 96 例全部通过：新增“缺对象”1 例，违约参数从 4 组减为 3 组。surefire 报告确认新用例已执行。
- `./mvnw -o test-compile`（全 reactor）：BUILD SUCCESS。这一步证明删除兼容构造器后，risk-decision 及其他模块都能编译。
- `node scripts/pdmaner/generate-payment-model.mjs --check`、`generate-channel-connector-model.mjs --check`：都是最新。
- `node --test`：
  - `payment-model`：13/14 通过。失败的 1 例“Payment model is deterministic and preserves nine diagram themes”在基线（含 main 工作区）已经失败（图组只剩 6 个），与本卡无关。
  - `payment-execution-simplification`：3/3 通过。
  - `channel-connector-protocol-model`：9/9 通过。
  - `payment-channel-query`：1/1 通过。
  - `pdmaner-canonical-paths`：3/3 通过。
  - `payment-scenario-doc`：5/7 通过。失败的 2 例（`t_payment_channel.adapter_code`、AUTHENTICATE 场景）是基线遗留，与本卡无关。
- 未运行：risk-decision 的 HTTP 测试（只删了一个已无调用方的构造器，已用编译验证）；`npm --prefix requirements-site test`（本卡没有改 requirements-site）。

## 6. 偏离与裁决

- D1 Risk 字段名：按阶段 4 裁决，线上键用 `issuerCountryCode`（Payment 内部命令字段同名）；Token 列和 Card 字段仍叫 `issuer_country` / `issuerCountry`。
- D2 删除兼容构造器：卡 B 留下的 17 参数构造器已按裁决删除（改动在 risk-decision-api，属于裁决明确交给本卡的范围）。
- D3 channel-connector 模型：Payment 与 channel-connector 两份模型由同一生成器分区产出，channel-connector 的图里引用了 `t_payment_instrument_token`。表变高后，4 个图元的 y 坐标跟着变化。不重新生成的话，`generate-channel-connector-model.mjs --check` 会失败，所以重新生成了。改动只涉及坐标，没有结构变化。
- D4 卡属性在 DataObject 中用 String，没有用 card-api 枚举配 TypeHandler：
  - 原因：这些代码由 Card 拥有，Payment 只冻结并透传给 Risk、channel-connector 和 MQ，三处都按字符串传输。
  - 闭合代码在 Card 客户端边界按 card-api `CardBrand` / `CardFundingType` / `CardProduct` 失败关闭校验；Payment 依赖 card-api，枚举随依赖同步更新。
  - 如果改成枚举 TypeHandler，Card 以后新增品牌会让历史 Token 读取失败。
- D8 缺少快照对象（主会话裁决，选 (a)）：
  - 原因：Payment、acquiring、counterparty 的 Card 客户端都拒收未知字段，所以 Card 必须晚于它们上线。如果 Payment 又要求 `cardAttributes` 恒在，发布就会死锁；同批发布也仍有失败窗口。
  - 现在的处理：响应缺 `cardAttributes` 对象（旧 Card）时，8 列全空、按未识别处理，记一条 WARN（不含卡值）。
  - 对象存在但内容违约时，仍按 D5 返回 500、不写 Token。
  - 部署顺序回到 technique 3.5 的“Payment 先于 Card”。新旧镜像交替期间创建的工具没有卡属性快照，属于预期。
- D5 违约快照的处理：technique 只规定“`recognized=true` 时卡组织、发卡国、区域必须有值”。实现另外拒绝了以下情况：未识别却带属性、未知闭合代码、命中位数不在 6—8、版本号非正数。理由是 Card 合同写明“未识别时各属性为 null”，开发规范也要求未知 Code 失败关闭。
- D6 `PaymentSucceededPublishTest` 的禁止串：原先禁止子串 `card`，新字段 `cardBrand`、`cardLevel` 会误中，所以改为只禁止卡对象键 `"card"`。`cardToken`、`cvv`、卡号等禁止项不变。
- D7 Handler 入口测试：technique 写的是“`PaymentSucceededHandler` 入口测试断言 Payload”。这里沿用 `PaymentSucceededPublishTest`：它从 pay 入口出发，经真实补偿 Dispatcher 执行 Handler 并发布到本机 RabbitMQ，没有另建只直接调用 `handle()` 的测试类。本次 Handler 只改了事实组装（追加 3 个字段）。

## 7. 遗留项

- 部署顺序（technique 3.5）：本卡 DDL 要在新 Payment 镜像之前执行。按 D8，Payment 先于 Card 上线：Card 上线前创建的工具没有卡属性快照，路由只命中 `*`，Risk 不收到这三项。
- billing 写入 `card_network` / `funding_type` / `card_level` 属于计费需求 B；`events.yaml` 的 consumers 暂不加 billing。
- 规则能否使用新字段，要等运营在 Risk 决策点版本 220003 上发布新执行版本（阶段 4 裁决第 3 条）。
- 阶段 6 需要清点 mor-test 的 `t_mid_capability.scheme_code`，看带 schemeCode 后是否出现同精确度冲突（technique 风险项）。

## 8. WRITE_API_EXAMPLE 第 11 节核对

- API 与 Body：本卡没有新增 Payment HTTP 接口；Card 的 `cardAttributes` 合同由卡 A 定义，Payment 只在客户端边界校验。
- Controller / Processor：没有改 Controller。快照绑定放在 `ApplyCardInstrumentProcessor` 的新申请分支；原提交分支先返回，不改写。
- Processor 查询：沿用原来的 Repository 查询，没有直接调用 Mapper，也没有逐表写入。
- Context：沿用 `ApplyCardInstrumentContext` / `PayContext`，schemeCode 直接从 `context.getInstToken()` 读取，没有在 Context 里复制一份。
- Service：没有新增 Service；保存仍是 `saveApplyCardInstrumentContext` 的单事务。
- 事务：Card、Risk、channel-connector 调用和 MQ 发布都在事务外，事务内没有外部调用。
- Repository / Mapper：8 列只随 insert 写入；没有更新语句，因为快照不可变。
- 锁：沿用 Intent 资源锁，没有新增锁。
- 原请求比较：重放仍比较工具引用，不重新检查新建资格，也不改写快照。
- 请求幂等：本卡没有涉及 Gateway Key，没有新增幂等组件。
- View / DataObject 边界：没有新增 View；DataObject 没有越出 payment-core。
- 公共包与多余抽象：复用 Foundation 的 `EnumUtils` 和 card-api 枚举；没有生产 Mock，也没有伪造成功。
- 验证：按 UNIT_TEST_GUIDE 从 HTTP 入口和真实出站客户端访问测试 HTTP 服务，没有用方法 Stub；实际运行 96 例。未覆盖项见第 5 节。
