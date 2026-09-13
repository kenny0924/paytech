# 决策流数据设计

状态：已确认的数据与并发设计；当前模型、本机数据库及研发验证状态见 [当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md) 和 [开发交付报告](./development-checks.md)。

后续变更待审：用户已确认精简重复逻辑的方向，[精简修订](./simplification.md) 提议调整 3 张旧表及其引用，保留本页四张流程表；该修订尚未改变当前模型或数据库。下列“旧表 0 改动”是原阶段 3 口径。

4 张业务配置表、44 个字段、4 个主键、5 个唯一索引、2 个普通索引；现有表 0 个结构修改、0 字段增改、0 历史回填。Platform 只改注册数据。

## 1. 为什么是这 4 张表

| 表 | 字段数 | 当前需求依据 | 合并或删除的影响 |
|---|---:|---|---|
| t_decision_flow | 10 | 一流一行、不可复用编码、当前入口及整体删除/CAS | 仅用版本表不能直接表达整体生命周期。 |
| t_decision_flow_version | 10 | 每次保存产生完整不可变版本；历史只读 | 与根合并会覆盖历史，无法准确查看任一版。 |
| t_decision_flow_node | 13 | 一个版本有多个节点，规则版本和拒绝原因需直接查询 | 把图放进一个自由 JSON 会隐藏已有字典引用查询和同版引用约束。 |
| t_decision_flow_edge | 11 | 一个节点有多个有序出口；唯一顺序和图连接 | 节点列不能表达多出口；不按节点类型拆子表。 |

没有运行记录、发布、条件目录、字典回执、通用引用账本或重复审计表；规则动作留空沿用现有 action/additionalActions，不增模式字段。新流程没有当前运行消费者，不存 content_hash；命令 Hash 仅复用现有幂等记录，不能把历史保留与防篡改证明混为一谈。

## 2. 字段、空值和完整注释

所有主键由 Risk Snowflake 生成；时间使用 UTC DATETIME(6)。除管理根外，版本和明细不可变，update_time 固定为 create_time。没有物理 FK/CHECK。

### 决策流 · t_decision_flow

| 字段 | 类型 | 可空 / 默认 | 业务用途 |
|---|---|---|---|
| flow_id | BIGINT | 否 / 无默认值 | Risk 服务侧 Snowflake 生成的流程管理身份；创建后不变。 |
| decision_point_id | BIGINT | 否 / 无默认值 | 本库决策点身份；与流程编码共同确定不可改变的业务身份。 |
| flow_code | VARCHAR(64) | 否 / 无默认值 | 决策点内永久唯一的大写流程编码；删除后不回收。取值来源：Risk 流程配置管理；非固定枚举。 |
| current_flow_version_id | BIGINT | 否 / 无默认值 | 本流程当前最高业务版本；与新版本在同一事务切换，删除后保留。 |
| status | VARCHAR(64) | 否 / 无默认值 | 与运行发布无关的整体管理状态。允许值：CONFIGURABLE、DELETED。 |
| version | BIGINT | 否 / 1 | 从 1 开始；每次保存新配置或首次删除加 1；幂等回放不增加。 |
| deleted_by | BIGINT | 是 / 无默认值 | 来源 Platform Account 的永久账号标识，只保存跨系统身份引用；CONFIGURABLE 时为空，DELETED 时必填且不改写。 |
| deleted_time | DATETIME(6) | 是 / 无默认值 | 整体删除的 UTC 微秒时间；CONFIGURABLE 时为空，DELETED 时必填且不改写。 |
| create_time | DATETIME(6) | 否 / 无默认值 | 本行在 Risk 数据库中创建的 UTC 微秒时间。 |
| update_time | DATETIME(6) | 否 / 无默认值 | 当前版本指针或删除状态最后改变的 UTC 微秒时间；成功回放不改变。 |

索引：`uk_decision_flow_code(decision_point_id,flow_code)`；`idx_decision_flow_list(decision_point_id,status,update_time,flow_id)`。

### 决策流配置版本 · t_decision_flow_version

| 字段 | 类型 | 可空 / 默认 | 业务用途 |
|---|---|---|---|
| flow_version_id | BIGINT | 否 / 无默认值 | Risk 服务侧 Snowflake 生成的不可变配置版本身份。 |
| flow_id | BIGINT | 否 / 无默认值 | 所属本库决策流管理身份；创建后不变。 |
| version_no | INT | 否 / 无默认值 | 同一流程内从 1 开始连续递增；只在成功保存新配置时增加。 |
| flow_name | VARCHAR(100) | 否 / 无默认值 | 本次保存的流程名称快照，1 至 100 个 Unicode 码点；以后改名不回写。 |
| input_contract_code | VARCHAR(64) | 否 / 无默认值 | 保存时选定的 Risk 决策点输入合同。取值来源：Risk 输入合同目录；非固定枚举。 |
| input_contract_version | INT | 否 / 无默认值 | 保存时输入合同的准确正整数版本；规则集引用必须与其一致。 |
| created_by | BIGINT | 否 / 无默认值 | 来源 Platform Account 的永久账号标识，只保存可信操作者身份，不复制账号资料。 |
| change_reason | VARCHAR(500) | 否 / 无默认值 | 本次完整保存的人工说明，1 至 500 个 Unicode 码点；不包含个人原值或凭据。 |
| create_time | DATETIME(6) | 否 / 无默认值 | 本行在 Risk 数据库中创建的 UTC 微秒时间。 |
| update_time | DATETIME(6) | 否 / 无默认值 | 不可变配置明细，始终等于 create_time。 |

索引：`uk_flow_version_no(flow_id,version_no)`。

### 决策流配置节点 · t_decision_flow_node

| 字段 | 类型 | 可空 / 默认 | 业务用途 |
|---|---|---|---|
| node_id | BIGINT | 否 / 无默认值 | Risk 服务侧 Snowflake 生成的版本内节点行身份；每次另存生成新行。 |
| flow_version_id | BIGINT | 否 / 无默认值 | 所属不可变流程版本，所有节点及连线必须处于同一版本。 |
| node_key | VARCHAR(64) | 否 / 无默认值 | 版本内大小写敏感的节点键；同一编辑节点跨版本保留，删除再添加使用新键。 |
| node_type | VARCHAR(64) | 否 / 无默认值 | 节点业务类型。允许值：START、RULE_SET、END。 |
| node_name | VARCHAR(100) | 否 / 无默认值 | 保存时的节点名称快照，1 至 100 个 Unicode 码点。 |
| rule_set_version_id | BIGINT | 是 / 无默认值 | 准确引用本库规则集版本；仅 RULE_SET 必填，其他类型为空；不追随最新版本。 |
| decision_code | VARCHAR(64) | 是 / 无默认值 | 仅 END 必填，其他类型为空。允许值：ALLOW、REVIEW、DENY。 |
| reason_code | VARCHAR(64) | 是 / 无默认值 | 仅 DENY 结束节点必填，其他情况为空。取值来源：本库拒绝原因目录；非固定枚举。 |
| reason_message | VARCHAR(500) | 是 / 无默认值 | 仅 DENY 结束节点必填，保存时的内部拒绝说明；目录修改不回写，其他情况为空。 |
| position_x | INT | 否 / 无默认值 | 编辑画布中的整数像素坐标，范围负 100000 至正 100000；HTTP 使用十进制字符串。 |
| position_y | INT | 否 / 无默认值 | 编辑画布中的整数像素坐标，范围负 100000 至正 100000；HTTP 使用十进制字符串。 |
| create_time | DATETIME(6) | 否 / 无默认值 | 本行在 Risk 数据库中创建的 UTC 微秒时间。 |
| update_time | DATETIME(6) | 否 / 无默认值 | 不可变配置明细，始终等于 create_time。 |

索引：`uk_flow_node_key(flow_version_id,node_key)`；`idx_flow_node_reason(reason_code,flow_version_id,node_id)`。

### 决策流配置连线 · t_decision_flow_edge

| 字段 | 类型 | 可空 / 默认 | 业务用途 |
|---|---|---|---|
| edge_id | BIGINT | 否 / 无默认值 | Risk 服务侧 Snowflake 生成的版本内连线行身份。 |
| flow_version_id | BIGINT | 否 / 无默认值 | 所属不可变流程版本；来源和目标节点必须同属此版本。 |
| edge_key | VARCHAR(64) | 否 / 无默认值 | 版本内大小写敏感的稳定连线键；不以创建时间或坐标决定顺序。 |
| source_node_id | BIGINT | 否 / 无默认值 | 本库同一流程版本的来源节点；只允许 START 或 RULE_SET。 |
| target_node_id | BIGINT | 否 / 无默认值 | 本库同一流程版本的目标节点；不能指向 START 或自身。 |
| edge_type | VARCHAR(64) | 否 / 无默认值 | 出口业务含义。允许值：UNCONDITIONAL、CONDITIONAL、ELSE。 |
| priority_no | INT | 否 / 无默认值 | CONDITIONAL 从 1 连续排列且同源唯一；ELSE 与 UNCONDITIONAL 固定为 0，两者不能同源共存。 |
| edge_name | VARCHAR(100) | 否 / 无默认值 | 运营填写的连线名称快照，1 至 100 个 Unicode 码点。 |
| condition_json | VARCHAR(8192) | 是 / 无默认值 | 仅 CONDITIONAL 必填；受控 schemaVersion=1 JSON Object，规范化后最多 8192 UTF-8 字节，最多 32 个条件树节点、深度 8；其他类型为空。仅整树读取，不用 JSON 路径做数据库检索。 |
| create_time | DATETIME(6) | 否 / 无默认值 | 本行在 Risk 数据库中创建的 UTC 微秒时间。 |
| update_time | DATETIME(6) | 否 / 无默认值 | 不可变配置明细，始终等于 create_time。 |

索引：`uk_flow_edge_key(flow_version_id,edge_key)`；`uk_flow_edge_order(flow_version_id,source_node_id,priority_no)`。

## 3. 历史和引用资格

| 场景 | DRAFT / FROZEN，根未删除 | RETIRED，根未删除 | 根已删除 |
|---|---|---|---|
| 新增引用或替换版本 | 允许，但必须同决策点、同输入合同 | 拒绝 | 拒绝 |
| 编辑当前流，保留同一 nodeKey + ruleSetVersionId | 允许 | 允许保留并显示“已退休”；这是准确历史引用，不自动换新版 | 拒绝新保存，提示先替换该节点引用 |
| 复制节点后新 nodeKey 使用旧引用 | 按新引用校验 | 拒绝 | 拒绝 |
| 当前详情/历史只读 | 原样展示 | 原样展示 | 由已保存流程节点上下文读取原规则版本，显示“规则集已删除” |

输入合同由 Risk 在第一次保存取当前 CHECKOUT_PREPARE 的 ORDER_PREPARATION / 4；编辑从当前 base 版本重证其合同仍可编辑。所引用规则版本的 Companion 点及 inputContractCode/version 必须与其一致，不能仅验证 pointCode。执行就绪不是配置资格；含未配置动作规则或附加动作的 CONFIGURATION 仍可被配置引用。

DENY 新选原因必须已登记且 ACTIVE；reasonMessage 明确随请求保存，UI 可先带入默认文案再修改。当前版本原节点的 reasonCode + reasonMessage 均未改变时允许保留已停用原因，并提示状态；新增节点、替换原因或修改文案按新的选择重新校验。历史展示只使用保存的 Code/文案，不因默认文案变更改写。不存在的旧自由输入原因若不是新流 base 上的真实已保存值，不能借“历史兼容”新选。

普通配置引用不阻止规则集整体删除；现有 ACTIVE Release 门禁保持。流程本期没有真实运行引用，不建 inUse 占位数据或虚假检测。未来运行接入须先补精确 flowVersionId 消费关系及使用中删除门禁，本期不能靠冻结/发布名词代替。

## 4. 保存、幂等与事务

保存以一份完整配置为原子边界：锁定流程身份 → 重证 base/CAS、引用和字典 → 新增版本/节点/连线 → CAS 切换根 → 完成既有 Risk 幂等记录 → 一起提交。任一步失败不留下半张图、未引用版本或成功回执。新流先分配所有 ID，根 current_flow_version_id 非空，子行与根在同一事务可见。保存与删除并发只允许一方成功。

协调资源是 `decision:flow / pointCode:flowCode`，由 Foundation `@ResourceLock` 在事务前获取，提交/回滚后释放；删除先在事务外解析身份，再在锁内重证根。不同流程不使用同一个全局锁，不锁 Tenant 或操作者。只声明实际修改的 Flow 资源，不为每个只读引用再增加一组 Redis 锁。

数据库最终保护：现有根按主键锁定并用 expectedVersion + CONFIGURABLE 条件更新，根不存在时由唯一(point,code)防重；涉及的规则根按 ruleSetId 排序加共享行锁，再对准确规则版本行按 ID 读取/锁定，最后按 reasonCode 排序锁定原因行并重证资格。顺序与既有规则根→版本、字典更新路径一致；这些短暂的本库共享锁防止检查与提交间出现退休/删除或停用穿透。仅图校验与版本展示不加写锁。不能在该事务内调用 Platform、BFF、其他 Owner 或重新获取 Redis 锁。

Redis Lease 失效不意味着请求可以无保护写入；CAS、唯一索引与上述本地引用保护继续守住最终事实。实现须从真实 HTTP 入口覆盖同流竞争、不同流并行、保存/删除竞争、规则退休/删除与保存竞争、原因停用竞争和事务回滚。具体责任见 [研发验证计划](./implementation-plan.md)。

复用现有 `t_command_idempotency` 和 Risk 本地 CommandIdempotencyService，新增开放目录操作类型 `SAVE_DECISION_FLOW`、`DELETE_DECISION_FLOW`，不改表或引入 Foundation 幂等迁移。caller_scope 沿用可信调用方/操作者隔离规则；Key 保持大小写。成功回放先于当前 base/字典资格检查，回放当时结果；同键不同业务请求为冲突，不能返回后来版本。

保存请求 Hash 的精确域为 `{domain:"RISK_DECISION_FLOW_SAVE",schemaVersion:1,request:<规范化 SaveFlowRequestBody>}`；删除为 `{domain:"RISK_DECISION_FLOW_DELETE",schemaVersion:1,request:{flowId,expectedVersion}}`，使用 Risk 既有 JCS Adapter + SHA-256。保存包含名称、changeReason、base/CAS、节点引用、全部条件/顺序、文案及整像素布局；nodes 按 nodeKey、edges 按 sourceNodeKey/priority/edgeKey 排序，条件 children/values 顺序保留。身份隔离在 caller_scope，不把 JWT/Session/原始 Key 混入 Hash。

response_ref 使用 `SAVE_DECISION_FLOW:<flowVersionId>` 或 `DELETE_DECISION_FLOW:<flowId>`。保存回执从不可变版本重建；删除回执从只发生一次的删除事实重建。幂等有效窗口沿用现有 Risk 能力，不承诺永久保留每次网络请求。过期后原保存请求不会覆盖当前版本，因为 base/CAS 仍会冲突；不增加保存回执表。

## 5. 查询与完整场景

合成流程 CHECKOUT_STANDARD 首次保存：t_decision_flow 一行，current 指向 #1；版本表一行；示例图的 6 个节点、6 条线各有自己的版本内行。Client 节点引用规则版本 #2，边按 priority=1 判断 matched，priority=2 判断 currency，ELSE 的 priority=0 只表示其他情况，不表示优先匹配。

运营将订单检查引用改为准确的 #5 并保存：新增一行流程版本 #2，完整复制本次图的节点/连线为新行，原 #1 原样保留；根 CAS 从 1→2，current 指向 #2。名称列表只 join 根 current，仍一条流程记录。

原 Client 规则集随后产生新版本使旧 #2 变 RETIRED：历史 #1/#2 仍显示 #2；原节点未替换可再次保存，界面显示已退休提示。若整个规则集删除，已有图及其只读规则详情仍能从持久化引用读取；再次保存该图必须替换删除引用。原因默认文案随后改名也不重写节点 reason_message。

删除流程时根变 DELETED、version 加 1，填 deleted_by/time，版本/节点/线/规则集均保留；普通列表及 N05 不再显示，字典反查的 DELETED/ALL 仍可跳到 N07 历史。规则集现有“使用关系”仅查询真实 Release，不能显示这张配置图正在运行。

查询路径：列表用根(point,status,update_time,flow_id)索引及 current 主键 join；历史用(flow_id,version_no)；图节点/连线由各自 flow_version_id 复合唯一索引前缀批量读取；原因引用由(reason_code,flow_version_id,node_id)读实际 END，再 join 版本/根。节点行存准确 rule_set_version_id，不重存规则内容；引用详情仍执行 Risk 原历史完整性检查。为避免 N+1，摘要查询批量加载当前页/当前图的准确版本，不在列表逐条调用 HTTP。

## 6. 数据分类、模型与 SQL 边界

节点/条件/内部文案属于 Risk 内部配置，非公开数据；只有获准的业务字段与字面量，禁止真实个人原值、完整 Token、凭据和卡数据。只在授权页面与 Risk 业务库保存，普通 HTTP/业务日志只记录请求 ID、操作、资源 ID、结果和计数。审计延续既有入口机制，Flow 版本自身已提供修改人/原因/内容历史，不再重复写节点 BCR。没有 MQ、补偿、执行轨迹或真实动作写入。

本阶段的模型材料是 [model.patch.json](./db/risk-compliance-service/model.patch.json)，它只有对唯一 PDManer `/entities/-` 的 4 个追加操作，不是第二份完整 .pdma.json。补丁基线摘要见 [model-baseline.json](./db/risk-compliance-service/model-baseline.json)。完整类型、中文说明、逻辑关联和索引从既有模型格式派生；[增量 DDL](./db/risk-compliance-service/ddl.sql)逐字段对应补丁。

生成源采用 Risk 原 manifest 体系的 `scripts/pdmaner/risk/manifests/09-decision-flow.mjs`，原 02-decision-engine 不重写；模型策略、Diagram、测试 Oracle 与唯一短名 PDManer/完整 Schema 同步。补丁保留阶段 3 基线，生成器可重建内部 ID 与复用 Domain；检查比较物理字段、类型、索引及解析后的逻辑关系，不把提案内部 ID 当作业务身份。当前指针、准确规则版本、连线来源/目标共四类版本内关系沿用有界查询策略，不为无查询消费者的关系新增索引；审批内容仍为 11 个索引。人工 PDManer GUI 复核属于后续综合验证。

[Risk DML](./db/risk-compliance-service/dml.sql) 为 0 必需初始化行；[Platform 注册材料](./db/platform-support-service/README.md) 为 118 行新增 + 1 行角色版本更新，0 DDL、0 新 Target。本机 Risk DDL 执行范围见 [local-application.md](./db/risk-compliance-service/local-application.md)；其他环境和 Platform 数据库执行仍按明确目标与单独授权处理。
