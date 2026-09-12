# RCO-16 数据与兼容设计

> **Gateway 补改数据边界：** 原 Risk 数据方案保持；Platform 另准备 [t_gateway_target 表注释 DDL](./db/platform-support-service/ddl.sql)，0 新表/业务列/索引/注册 DML。执行与模型同步边界见[修订方案第 3 节](./gateway-dynamic-routing-design.md#3-接口与数据库)，当前未应用。

阶段 3 已确认数据合同；数据库执行与验证状态见当前台账。用户明确移除字典变更回执和标签适用主体关系，优先由人工确认字典含义及异常操作结果；本稿取代首稿的四表方案。接口字段见 [api.md](./api.md)，现行模型仍由 Risk PDManer 和 [数据库 Owner](../../../agent-docs/database/RISK_COMPLIANCE_DATABASE.md) 拥有，阶段 4 同步。

## 1. 两张字典主表

| 新表 | 列数 | 保存的事实 |
|---|---:|---|
| `t_rule_reject_reason` | 11 | 原因编码、名称、内部说明、默认内部消息、状态、并发版本、创建/最后更新人及时间 |
| `t_rule_subject_tag` | 10 | 标签编码、名称、含义说明、状态、并发版本、创建/最后更新人及时间 |

共 2 表、21 列。标签不设置适用主体关系、单一主体字段、主体类型数组或隐藏 JSON 约束；运营根据标签名称/说明判断使用场景。实际规则动作选择哪个目标绑定，仍由既有工程目录动态提供；不恢复硬编码 Client/Consumer，也不新增实际打标签或 Consumer 输入能力。

字典只维护当前值和创建/最后修改信息，不保存逐次变更原因、响应快照或业务历史版本。移除首稿回执表及相关 Hash/幂等扩展，不迁入通用历史表来变相恢复这套记录。原不可变规则集版本、保存原因、内容 Hash 和成功命令回放完全保持。

主键由 Risk 服务侧 Snowflake 生成；代码创建后固定且永久唯一，包括停用记录。根的 version 支持实际并发编辑，创建为 1、成功更新加 1。无自增、物理 FK/CHECK；create_time/update_time 为应用生成的 UTC 微秒时间。字段、索引、默认值及完整注释见 [Risk DDL](./db/risk-compliance-service/ddl.sql)。

既有表仅有以下变更，无旧记录更新：

- `t_risk_rule`：新增 `(reason_code,rule_set_version_id)` 反查索引，调整 reason_code/reason_message 注释；类型/可空性不变，原因字典只治理 DENY。
- `t_rule_action_parameter`：新增 `(string_value,parameter_definition_id,rule_action_id)` 索引；VARCHAR(512) 的 utf8mb4 完整索引在 MySQL 3072 字节上限内。反查仍验证 ADD_RISK_TAG、TAG 参数和 LITERAL 来源，不把普通同值字符串算作标签。

历史版本及发布引用图直接读现有版本、绑定、发布表。既有 `t_command_idempotency` 形状和消费范围不变，新字典写入不使用它、不新增操作类型或响应引用前缀。

## 2. 字典写入与人工确认

写操作仍经既有认证/权限校验，操作者来自可信上下文。创建检查永久唯一 code；编辑/启停携带 expectedVersion，冲突为 409。保留格式、非空、长度、状态校验；停用不受已有引用数量阻止。

字典修改在 Risk 单个本地事务中完成。按项目既有资源锁规范，锁实际字典 `<dictionaryType>:<code>`，外层 Redis 协调、内层事务；编辑/启停对字典根 FOR UPDATE，验证 expectedVersion 并以 CAS 更新一行。创建最终由唯一索引防止同码双记录。无规则集反向锁、子表写入、幂等 claim、响应归档或异步恢复任务。

写请求不带 Idempotency-Key。响应为本次事务形成的结果摘要，在事务内构造后返回，不在提交后重新查询并冒用他人随后更新的结果：

1. 两个相同 code 的创建仅一条记录能成功；重复创建返回 CODE_EXISTS，不猜测它是否等同于上一次成功。
2. 同一个 expectedVersion 的两次更新/启停只有一次成功；另一次 CHANGED。人工重新读取并确认后，才可用新版本再次提交。
3. 超时、断网或 5xx 可能意味着已提交但响应丢失。页面保留草稿，显示“结果未确认，请刷新查询后确认”；不自动重试、不轮询恢复、不保证返回首次响应。
4. 人工用代码查询当前值、version、更新人/时间，确认后决定继续修改、取消或重建尚不存在的目录。当前值与输入相同不证明某次历史请求一定成功，不生成伪造成功提示。

原规则保存/冻结/删除/Candidate 的幂等流程及未决命令保护不受这个简化影响。

## 3. 原规则首次保存的目录校验

仅在既有规则保存的 FIRST 分支判断字典新选资格，规范化、历史回读和成功命令回放均不依赖当前字典状态。沿用原规则集资源锁、决策点/管理根事务和 CAS 边界。

- 当前持久化版本是唯一兼容基线；新规则集没有基线。客户端 legacy 标志或其他规则集的内容不能授予例外。
- DENY 新选择的原因及 ADD_RISK_TAG 新选择的标签必须已登记、ACTIVE。标签不按主体类型再校验；名称/说明表达的使用场景交由人工判断。
- 对存在的相关字典根按 `(dictionaryType,code)` 升序 FOR SHARE 当前读，读状态并持有到本次规则事务提交，不使用锁前缓存。字典启停/更新锁同一行，保护读取与保存之间的竞争。
- 不存在的新代码直接失败；未登记但严格沿用的旧项允许保留，不创建占位字典。Risk 保存请求已确定的 reasonMessage，不用目录默认值覆盖。

保存先拿到共享锁时，按当时有效状态提交，后续停用不撤销它；停用先提交时，新选择看到停用状态。字典命令不获取规则集/决策点锁，避免反向加锁。数据库锁超时/死锁回滚后，规则保存按其原幂等协议处理，字典页面按本稿人工查询确认。

## 4. 保留旧配置的判据

| 情况 | 处理 |
|---|---|
| 原因未登记/停用 | 同一基线 ruleCode 的 `(terminalActionCode,actionVersionNo,reasonCode,reasonMessage)` 完全一致可沿用；改条件/名称/优先级不改变此事实。换原因、消息、终态/动作版本或新 ruleCode 则校验当前目录 |
| 标签未登记/停用 | 按 `(ruleCode,actionCode,actionVersionNo,targetBindingCode,tagCode)` 计数，请求不超过基线的部分可保留，动作重排不算替换；复制增加次数、换规则/目标/代码属于新选择，要求登记且启用，但不要求目录配置主体类型 |
| 移除旧项 | 允许，不能将移除的额度借给其他新项 |
| 非 DENY 的历史原因 | 保持原合同，不由拒绝原因字典治理或反查 |
| 历史 DENY 空消息 | 回读保持原值；保存新版本仍遵循原必填门禁，不自动补默认或放宽 |
| 工程动作/绑定退役 | 按已有工程资格和旧内容格式合同处理；标签不设适用类型不等于放开未知工程输入 |

比较基于原合同规范化后的持久化事实。字典改名、改默认消息、停用都不重写旧规则内容/Hash，不改变原成功命令回放。默认消息只在 UI 明确选择原因时带入，初始加载或选项刷新不覆盖用户草稿。

## 5. 查询与删除边界

历史分页、字典引用 count/rows 使用同一只读事务；主列表仍一集一行。发布引用使用独立投影，原 ACTIVE 占槽/闭包检查保持，不把候选或历史发布混入原删除保护 Mapper。

字典引用区分当前配置、历史版本、已删除根，ACTIVE 环境按精确版本表示。已删除根在普通详情和历史仍为 404；引用列表保留条目但不提供普通详情入口。关系损坏/依赖失败为错误，不显示成空图。

## 6. SQL 清单与执行边界

| 文件 | 目标库 | 内容 |
|---|---|---|
| [Risk ddl.sql](./db/risk-compliance-service/ddl.sql) | mor_risk | 2 表/21 列；2 张旧表新增 2 个反查索引、调整 2 个字段注释，旧表新增字段为 0 |
| [Risk dml.sql](./db/risk-compliance-service/dml.sql) | mor_risk | 业务种子/回填为 0；由运营页面录入真实目录 |
| [Platform dml.sql](./db/platform-support-service/dml.sql) | mor_platform | 14 Route、6 Permission、6 个 REVOKED 管理员绑定、10 Console Resource、40 Header、22 Query、84 Guard，共 182 行插入及一次 Role.version CAS 更新；对应 Route 开放时同事务激活绑定，见[注册时序修正](./pending-registration-permissions.md) |

相比首稿，去掉主体类型 GET、标签 subjectType Query 和六个字典写接口的 Idempotency-Key CORS/转发 Header，共减少 22 条注册插入。保留其余 ID，撤销项 ID 不重新分配；详细清单见 [registration.json](./db/platform-support-service/registration.json)。首稿从未应用，不生成 DROP 业务表或删除数据库记录的迁移。

新 Route/Resource 默认 DISABLED，新写权限只授予既有 PLATFORM_ADMIN，读取沿用 READ；不发布 Snapshot 或自动启用。合成 UI/API 样例不是业务种子，不扫描旧规则推断默认文案或标签含义。

SQL 执行仍须另行核对环境、停写、备份和文件授权。MySQL DDL 隐式提交，不以事务回滚承诺恢复；部分执行后停止，按检查点及备份处理，不能用 --force/IF NOT EXISTS 掩盖差异。研发阶段同步 Risk PDManer/完整 Schema 与 Platform 单一初始化 DML，新库初始化与本增量互斥。

阶段 3 已确认并进入阶段 4；该确认不授权 SQL 应用、test 或提交。实际实现和环境状态见 [台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md)。
