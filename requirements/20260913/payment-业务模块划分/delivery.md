# 模块划分评审记录

> 后续推进：2026-09-13 用户进一步要求所有系统中的支付渠道 Provider 命名统一为 Channel，并确认同步评审关联改动。当前先交付[跨系统命名专项](../payment-支付渠道命名统一/delivery.md)；本页保留此前模块范围的确认历史，四模块职责保持。

## 首轮评审范围

2026-09-13 用户要求协助拆分 Payment 模块并给出合适数量。本轮按咨询与方案评审范围交付[需求](./requirement.md)及[模块建议](./technique.md)，没有将该询问登记为对四模块方案或业务研发的确认。

首轮交付时成果状态为 Proposed，尚未获得用户确认。后续确认点遵循[需求交付流程](../../../agent-docs/REQUIREMENT_DELIVERY_WORKFLOW.md)；当前确认见下方命名修订记录。

## 首轮交付范围与当时待确认内容

- 建议四个业务模块：Execution、Routing、Provider、Card；加现有 api/web/test 共七个子模块。
- 给出现有 Schema 全表唯一归属、Card 候选表映射、默认无业务模块互依和 Web 编排/事务边界。
- 推荐按首个已批准业务用例建立实际需要的 JAR，保留 Card 专项字段和恢复细节的原评审状态。
- 待用户确认：是否采用四块职责划分，并以此展开首个 Payment 用例所需模块及公开业务接口的详细设计。

本轮只增加本需求文档及需求检索入口；保留 Payment 初始化工程及其他任务未提交修改。没有修改应用代码、PDManer、Schema、业务目录或全局时序，没有 SQL、部署、提交或推送。当前应用状态继续见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-application-foundation)。

## 验证记录

- 2026-09-13：三份需求文档的 18 个本地文件链接均存在，代码围栏与尾随空白检查通过。
- 从当前 Payment Schema 提取的 30 张表与本建议现有表 Owner 清单逐项比对，无遗漏、额外表或重复 Owner。Card 候选表未混入现有表清单；对应 PMOD-02。
- `git diff --check -- requirements/README.md requirements/20260913/payment-业务模块划分` 通过；新文件另由上述脚本检查空白。
- `npm --prefix requirements-site test` 构建成功，136/136 测试通过、无失败或跳过；日志为 `/tmp/codemor-payment-module-review-tests.log`。这是工作区当时内容的验证，不代表四个业务 JAR 或真实支付能力已实现。
- PMOD-01、03、04、05 通过本轮规范与设计核对；尚无业务代码变更，因此没有运行 Java、数据库、KMS 或 NI 交易测试。未来实施仍须验证架构门禁及业务行为。

## 四模块确认与 Channel 命名修订（2026-09-13）

用户回复：“我认为可以，然后provider改成channel是否合适？”

- 已确认四业务模块的划分，接受继续细化模块业务接口和首个落地用例的方向；本轮优先处理用户提出的名称调整。
- 按用户提出的方向采用 `paytech-payment-channel`，模块清单为 Execution、Routing、Channel、Card。当前技术方案同步模块名、依赖图、表 Owner 和协作叙述。
- Channel 表示模块能力范围；Provider 继续表示外部提供方实体，Provider Account/MID、既有字段与表名不随模块更名。明确区别于 Platform 的访问 Channel。
- 原表归属、事务与 Card 权限边界不变。接口和首个实现用例仍待设计交付，本轮没有新增 Maven 模块或业务实现，也没有将 Card 专项待评审内容标成已批准。

当前状态：四模块划分已确认，Channel 命名修订已写入方案；下一设计范围保持模块业务接口与首个落地用例，不重复请求四模块确认。

命名修订验证：三份文档、18 个本地文件链接及全部现有表唯一归属检查通过；模块清单/依赖使用 Channel，Provider 实体和既有表名保持；围栏、尾随空白及 `git diff --check` 通过。`npm --prefix requirements-site test` 构建成功，136/136 测试通过，日志为 `/tmp/codemor-payment-channel-naming-tests.log`。未改应用代码，本轮不涉及 Java 或渠道交易验证。


## 工程拆分授权与交付（2026-09-13）

用户明确要求“那现在帮我拆一下项目模块吧”。已确认的四模块职责与 Channel 命名继续有效；本轮进入工程实现及其必要本地开发检查，不重跑此前方案评审，不扩展到业务实现、数据库迁移或部署。由于 Payment 现有代码只有初始化入口，[本次范围决定](../../../agent-docs/decisions/2026-09-13-payment-module-scaffolding.md)明确授权先落地四个业务 JAR 骨架，取代本需求原先等待业务用例再建模块的时序。

交付：Parent reactor/版本管理、四个普通业务 POM/根包说明、Web 的装配依赖、业务 JAR Enforcer 隔离、五项模块边界测试、README 和当前方案更新。原 api/web/test 与应用行为保持；既有数据库改名工件和其他任务的未提交修改保留。

验收映射：PMOD-01/03/05 对应七模块与默认依赖图；PMOD-02 对应当前 30 表唯一 Owner 清单的模型核对和源码/SQL 检查；PMOD-04 只固定 Card 包边界，本轮不处理卡数据；PMOD-06 对应完整构建、原真实 HTTP 回归和打包启动检查。后续真实业务接口、跨模块事务和受控用卡验证继续待对应专项。

开发检查证据：

```sh
# 在 payment-orchestration-service 下，使用 JDK 25
./mvnw -B -ntp -nsu clean verify
```

改前先运行新边界测试，因缺四业务模块出现 4 个预期失败；落地后共 19 项测试通过。隔离副本的非法 `Execution → Channel` 依赖明确被 Maven Enforcer 拒绝；该副本没有替换主工作区。首次打包 smoke 脚本把合法 Health `groups` 字段误判为失败，按既有 Health 合同修正为 `UP` 且无组件详情后六路径通过，生产代码未因此改变。

原始证据目录：`/var/folders/t2/xvr6hr7x4yq2c64fx_216r8c0000gn/T/codemor-payment-modules-_pqcdkuu`。其中 `payment-verify.log` 为完整 reactor 结果，`before-split-tests.log` 为改前失败证据，`forbidden-dependency.log` 为依赖拒绝证据，`artifact-smoke-verification.json`、`verify-packaged.py` 与 `packaged-smoke-final.log` 为实际制品装配和启动验证。当前状态汇总仅见[项目台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-business-modules)。
