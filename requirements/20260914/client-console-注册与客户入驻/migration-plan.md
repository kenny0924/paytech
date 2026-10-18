# Client 入驻：数据库切换与共享读取兼容

阶段 3 迁移设计。目标字段和索引以[数据设计](./data-design.md)、[字段投影](./db/target-field-projection.json)为准。本文件明确阶段 4 要交付的模型/SQL/消费者及阶段 6 的环境门禁。没有执行迁移，也不把局部 CREATE 当成全库建库文件。

## 1. 两类数据基线分别处理

| 基线 | 可实施的路径 | 必须停止的条件 |
|---|---|---|
| 新数据库 | 使用阶段 4 同步后的完整 Owner Schema；初始化配置仍由原服务唯一初始化入口维护 | 只拿本目录 10/4/7 张局部表建库，或服务启动自动迁移 |
| 已有库，受影响资料及引用闭包确实为空 | 停止相关写入后，基于准确 SHOW CREATE TABLE 差异生成逐 Owner 增量 DDL；保留其他业务数据和 Platform 账号/IAM | 任一闭包有行、实际字段不同、存在待处理回调/补偿或旧实例仍可能写入 |
| 已有资料、历史引用或处理中申请 | 先保持旧表及读链；以只读盘点形成该环境逐行映射和历史兼容方案，再独立评审切换 | 用 MAX(version_no)、最近时间、删除历史或伪造批准时间来满足唯一索引 |

非空迁移不借“先开发”默认为允许清库。当前开发可以针对目标模型和合成数据推进；某个非空环境缺少迁移证据时，该环境不启用新入驻。业务方案已明确，环境行级处置不是编码阶段必须猜出的事实。

空数据也不等于 DDL 可无损回退。MySQL DDL 会隐式提交；执行前备份结构与受影响数据闭包，逐 Owner 列出备份位置、校验和恢复演练证据。失败后停止入口，按兼容矩阵回退应用或恢复备份；新结构已有真实写入时，不能只改回旧列或恢复旧应用。

## 2. CCT 迁移责任

| 对象 | 目标与迁移要求 |
|---|---|
| Party Profile 与类型、地址、联系人、标识子表 | 保留稳定 profile_id；同 Party 多行时不自动归并。approved_time 必须有真实批准证据；无证据不能补当前时间。批准 content_hash 原值与既有读取合同保留 |
| Client Business Profile / Classification | 每 Client 一份；保留 business_profile_id 及准确分类引用。草稿经营金额/类型可空，提交门禁留在应用。已批准分类不能按当前目录重算历史 |
| Party Identity Key / Profile Identifier | 旧原始号码移到对应 Profile Identifier 前，逐引用核对一对多及完整规范化组合；旧永久身份键归属不改。不能在未复制并验证前删除原始列 |
| Party Relation | relation_version_id 重命名 relation_id 时保留数值；多个历史关系不能压成最新一条。新 Client 关系明确 application_id，旧 Partner 等来源可空且保持已有授权语义 |
| Client Application | requested_by_account_id 不得缺失；四种活动状态 slot=1，终态 NULL。账号跨个人/组织活动冲突必须人工处置，不批量清 slot。旧 Base/Target 指针必须完成读取/追溯映射后才删除 |
| 共享引用 | t_partner_application.target_party_profile_id、t_operating_entity_application.party_profile_id、t_agreement_party.party_profile_id 继续指向原稳定资料 ID；不得随 Client 简化删除这些表或篡改其业务含义 |

[CCT 预检](./db/preflight.sql)检测重复、状态及账号；[闭包盘点](./db/cct-migration-preflight.sql)只报告本库行数和引用缺失数量，不查询敏感姓名/证件值。零行结果是空数据路径的必要条件，不能证明外部历史引用不存在。跨系统引用由各 Owner 分别查询稳定 ID 汇总后受控比对，不做跨库 JOIN。

## 3. 必须同批适配的消费者

现有 [CheckoutQueryMapper.xml](../../../paytech/commercial/paytech-commercial-core/src/main/resources/mapper)和 [CheckoutRecords.java](../../../paytech/client-master/paytech-client-master-core/src/main/java/com/paytech/client/master/core/dal/dataobject/CheckoutRecords.java)读取待删状态/生效列；[CheckoutCandidateRules.java](../../../paytech/client-master/paytech-client-master-core/src/main/java/com/paytech/client/master/domainservice)使用这些事实过滤资料；[CheckoutQueryConfiguration.java](../../../paytech/commercial/paytech-commercial-app/src/main/java/com/paytech/commercial/configuration)注册旧枚举映射。阶段 4 必须一起修改查询、记录、资格规则、枚举的真实消费者和测试 Fixture。

目标门禁为单份资料已批准、Client 当前业务/资格有效、批准资料 Hash 和精确分类齐备，再继续既有 OE/Scope/Source/Agreement 检查。不能简单去掉旧 WHERE 或把缺失状态默认为 ACTIVE；无批准事实、零/多主分类、数据矛盾和依赖不可用均拒绝支付准备。旧商业条款 Version/Scope Wire、BCR current_version 及其他生命周期并发机制有独立消费者，本期不删除。

新批准资料的摘要继续使用既有已批准内容规范，批准后内容与 Hash 不变；已冻结到历史订单的 Profile ID/Hash 不重算。若某个历史 Reader 必须通过已删除的 Profile 版本定位，非空环境切换前必须提供保留原语义的读取实现及样例证明，不能伪造 version=1。

## 4. Risk 与 Platform

Risk 新 Client 以 source_submission_id 识别每次真实提交；source_submission_version/source_submission_hash 和旧 relation/profile version 列只服务未迁移来源，新 Client 不填假版本。存量 APPLICATION 来源拟使用 `legacy:<applicationId>`，但仅在原来源身份唯一时成立；出现重复或多次不同提交时必须停止并制定准确映射。定时复审保留独立键。旧任务 Snapshot、检查、材料和决定的追溯保持完整。

[Risk 预检](./db/risk-migration-preflight.sql)覆盖来源唯一性、任务主体和决定孤立引用。新增分类表不回填虚构运营确认；处理中旧 Client Case 不自动当新 Case 批准。新来源写入、旧来源适配、唯一索引、迟到事件校验必须同时部署，避免 NULL 绕过唯一性或老事件覆盖新任务。

Platform 7 张补充表已在模型中；[Platform 元数据预检](./db/platform-migration-preflight.sql)核对实际表/字段/索引。表不存在才加入增量 CREATE；已存在时逐字段比较，不能依靠 IF NOT EXISTS 跳过不兼容结构。账号、Tenant、Member、Role 和现有会话保持原语义；不能为入驻邮件创建假 Tenant。邮件、对象存储和扫描的真实配置在阶段 6 按环境绑定。

## 5. 实施交付与发布门禁

阶段 4 首先同步 CCT/Risk PDManer、模型生成器、完整 Schema、对应枚举/Mapper 和数据库测试；Platform 将已建模补充表纳入现行 Schema。生成空闭包路径的增量 SQL，附源模型/结构指纹与逐步验证；不在本轮评审时直接改现行模型。数据库执行仍须准确环境授权。

发布按“关闭新路由 → 停止受影响写入并排空旧任务 → 备份与复核预检 → 人工 SQL → 部署匹配的 Owner/BFF/Web → 回读不变量与联调 → 注册并启用入口”执行。中间不能让旧 Mapper 查询已删除列。新流程未验收时，原 Operations 管理入口保持可用。

最低验证包括空草稿保存、跨类型并发只有一份、RETURNED 重提的旧消息隔离、批准后所有写入口锁定；以及草稿不能 Checkout、已批准稳定 Profile/Hash 可读取、Partner/OE/Agreement 引用无损、重复与孤立引用使迁移停止。完整业务验证见[实施计划](./implementation-plan.md)。

迁移方案的边界已收口；实际数据盘点、环境 SQL、备份和执行记录由阶段 6/7 产生。当前状态统一见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。
