const metadata = (defName, description) => Object.freeze({ defName, description });

/**
 * PDManer 表中文显示名。物理表名继续使用英文 snake_case defKey；
 * 显示名独立维护，避免把较长的“表用途”文案误作表名。
 */
export const TABLE_DISPLAY_NAMES = Object.freeze({
  party: "法律主体主档",
  party_profile: "法律主体资料版本",
  individual_profile: "个人资料版本",
  organization_profile: "机构资料版本",
  party_identity_key: "法律标识注册主档",
  party_profile_identifier: "主体资料法律标识关联",
  party_address: "主体地址版本",
  party_contact: "主体联系方式版本",
  party_relation: "主体关系版本",
  client: "客户角色",
  local_partner: "本地合作伙伴角色",
  operating_entity: "经营主体角色",
  client_business_profile: "客户业务资料版本",
  client_business_classification: "客户经营分类",
  client_application: "客户申请",
  partner_application: "本地合作伙伴入驻申请",
  operating_entity_application: "经营主体入驻申请",
  application_document: "申请材料",
  client_entity_affiliation: "客户经营主体适用关系",
  client_entity_assignment: "客户经营主体分配",
  entity_source_relation: "经营主体来源关系",
  entity_assignment_rule: "经营主体自动分配规则",
  agreement: "合同",
  agreement_signatory: "合同签约方",
  agreement_document_ref: "合同文件引用",
  agreement_acceptance: "合同签署记录",
  agreement_operating_entity: "合同覆盖经营主体",
  consumer_legal_document: "消费者法律文档",
  seller_disclosure: "法律卖方披露",
  commercial_term_plan: "商业条款方案",
  commercial_term_version: "商业条款版本",
  commercial_term_scope: "商业条款适用范围",
  commercial_term_component: "商业条款计算组件",
  business_change_record: "业务行变更历史主档",
  business_change_version: "业务行变更版本明细",
  compensation_event: "本地补偿事件",
  command_idempotency: "命令幂等记录",
});

/**
 * Client & Contract 物理字段的中文名称与业务说明。
 *
 * 这里故意不提供按英文单词拆分的兜底逻辑：新增字段必须先明确业务语义，
 * 否则模型生成直接失败，避免再次出现 “Field Name for Table” 式空泛描述。
 */
export const FIELD_METADATA = Object.freeze({
  acceptance_id: metadata("接受记录标识", "合同签署或接受事实的内部唯一标识"),
  acceptance_method: metadata("接受方式", "签约方完成合同接受时采用的受控方式代码"),
  accepted_time: metadata("接受时间", "接受事实正式成立的 UTC 微秒精度时刻"),
  activation_time: metadata("激活时间", "业务角色首次进入可用状态的 UTC 微秒精度时刻"),
  active_slot: metadata("稀疏唯一槽位", "以 1 或 NULL 实现条件稀疏唯一约束的技术槽位，具体占槽含义由表级规则定义"),
  active_source_slot: metadata("有效来源唯一槽位", "用于保证同一 Operating Entity 同时只有一个有效来源关系"),
  address_id: metadata("地址标识", "Party Profile 地址记录的内部唯一标识"),
  address_line1: metadata("地址第一行", "主体地址的主要街道、门牌或建筑信息"),
  address_line2: metadata("地址第二行", "主体地址的可选补充单元、楼层或区域信息"),
  address_type: metadata("地址类型", "地址用途或法律属性的受控分类代码"),
  affiliation_id: metadata("适用关系标识", "Client 获准在准确交易范围内使用 Operating Entity 的关系标识"),
  aggregate_root_id: metadata("聚合根业务标识", "该变更记录所属业务聚合根的稳定业务标识"),
  aggregate_root_type: metadata("聚合根类型", "该变更记录所属聚合根的稳定领域类型代码"),
  agreement_id: metadata("合同标识", "商业合同稳定身份的内部唯一标识"),
  agreement_no: metadata("合同编号", "可在业务操作中识别合同的唯一编号"),
  agreement_type: metadata("合同类型", "区分 Client 合同与 Local Partner 合同的类型代码"),
  application_id: metadata("申请标识", "业务申请记录的内部唯一标识"),
  application_no: metadata("申请编号", "供业务查询与人工操作使用的唯一申请编号"),
  application_type: metadata("申请类型", "区分首次入驻与资料变更申请的闭合业务类型"),
  partner_application_id: metadata("Local Partner 申请标识", "材料所属 Local Partner Application 的本库标识；三个申请父标识恰好一个非空"),
  requested_by_account_id: metadata("申请发起 Account 标识", "发起申请的 Platform CUSTOMER Account 外部稳定标识，不证明法律主体或签约资格"),
  approved_source_relation_id: metadata("批准来源关系标识", "申请批准后创建或激活的 Operating Entity 来源关系标识"),
  assignment_id: metadata("主体分配标识", "已经验证并生效的 Client Operating Entity 自动分配事实标识"),
  base_business_profile_id: metadata("基准业务资料标识", "资料变更提交时冻结的 Client 当前有效业务资料版本标识"),
  base_party_profile_id: metadata("基准主体资料标识", "资料变更提交时冻结的 Client 当前有效 Party Profile 标识"),
  birth_date: metadata("出生日期", "个人法定出生日期"),
  business_model_code: metadata("商业模式编码", "Client 声明商业模式的受控业务代码"),
  business_profile_id: metadata("业务资料标识", "Client 业务资料版本的内部唯一标识"),
  calculation_base: metadata("计算基数", "费用、分润或平台服务费组件执行计算时采用的受控金额基数代码"),
  caller_scope: metadata("调用方作用域", "隔离幂等键命名空间的稳定调用方作用域"),
  change_version_id: metadata("变更快照标识", "不可变业务行审计快照的内部唯一标识"),
  classification_id: metadata("经营分类标识", "经营分类行的内部唯一标识"),
  client_application_id: metadata("Client 申请标识", "材料所属 Client Application 的本库标识；三个申请父标识恰好一个非空"),
  client_id: metadata("Client 标识", "平台 Client 角色的内部唯一标识"),
  client_no: metadata("Client 编号", "供外部接口与业务查询使用的唯一 Client 编号"),
  client_source_basis: metadata("Client 来源依据", "Organization Client 提供 Operating Entity 时采用的法律关系依据"),
  component_code: metadata("组件编码", "同一定价、分润或平台服务费 Scope 内唯一的业务组件代码"),
  component_type: metadata("组件类型", "区分百分比组件与固定金额组件的类型代码"),
  term_component_id: metadata("商业条款组件标识", "商业条款计算组件的内部唯一标识"),
  term_plan_id: metadata("商业条款方案标识", "Client Pricing、Partner Share 或 Platform Service Charge 方案根的内部唯一标识"),
  term_scope_id: metadata("商业条款范围标识", "商业条款适用范围记录的内部唯一标识"),
  term_type: metadata("商业条款类型", "区分客户定价、本地合作伙伴分润与平台服务费的闭合业务类型"),
  term_version_id: metadata("商业条款版本标识", "商业条款不可变业务版本的内部唯一标识"),
  city: metadata("城市", "主体地址所在城市或同级行政区域名称"),
  contact_id: metadata("联系方式标识", "Party Profile 联系方式记录的内部唯一标识"),
  contact_masked_value: metadata("联系方式脱敏值", "Vault 按联系方式类型生成的最小受控展示值"),
  contact_type: metadata("联系方式类型", "区分邮箱与电话号码的闭合类型"),
  contact_vault_token: metadata("联系方式 Vault Token", "指向 Vault 联系方式标量原值的不可枚举 Token；EMAIL 对应 EMAIL_ADDRESS_V1，PHONE 对应 PHONE_E164_V1"),
  content_file_id: metadata("正文文件标识", "Platform 中保存法律正文内容的文件标识"),
  content_hash: metadata("封存内容哈希", "业务版本封存边界内全部规范化内容的完整性哈希"),
  country_code: metadata("国家代码", "ISO 3166-1 alpha-2 国家代码；Scope 场景可使用批准的通配语义"),
  create_by: metadata("创建人标识", "创建不可变审计快照的正式 Human 或 System Actor 标识"),
  create_time: metadata("创建时间", "数据库记录创建的 UTC 时间，精确到微秒"),
  currency: metadata("币种代码", "ISO 4217 三位币种代码；Scope 场景可使用批准的通配语义"),
  current_version: metadata("当前变更版本", "该业务行最近一次审计快照采用的聚合根版本坐标，允许跳号"),
  disclosure_id: metadata("卖方披露标识", "Operating Entity 公开卖方披露聚合的内部唯一标识"),
  document_id: metadata("文档引用标识", "业务资料包或合同文档引用记录的内部唯一标识"),
  document_code: metadata("法律文档编号", "同一消费者法律文档类型下唯一且稳定的业务编号"),
  document_type: metadata("文档类型", "文档用途或法律类别的受控业务代码"),
  due_diligence_case_id: metadata("尽调案件标识", "Risk & Compliance 拥有的尽职调查案件外部标识"),
  effective_from: metadata("生效开始时间", "业务记录生效区间的 UTC 微秒精度起点，包含该时刻"),
  effective_to: metadata("生效结束时间", "业务记录生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束"),
  entity_no: metadata("Operating Entity 编号", "供业务查询使用的唯一 Operating Entity 编号"),
  entity_source_relation_id: metadata("主体来源关系标识", "Assignment 冻结引用的 Operating Entity 来源关系标识"),
  event_key: metadata("事件幂等键", "领域事件或本地恢复动作的全局稳定幂等键"),
  event_type: metadata("事件类型", "需要发布或本地恢复执行的领域事件类型代码"),
  execute_count: metadata("执行次数", "补偿任务已经尝试执行的累计次数"),
  execute_status: metadata("执行状态", "补偿任务当前的可恢复执行状态"),
  execution_type: metadata("执行方式", "区分 MQ 发布与本地 Handler 执行的方式代码"),
  expected_ticket: metadata("预期客单价", "Client 声明的预期单笔交易金额"),
  expected_volume: metadata("预期交易量", "Client 声明的预期周期交易金额"),
  expire_time: metadata("过期时间", "幂等记录停止复用并可进入清理流程的 UTC 微秒精度时刻"),
  family_name: metadata("个人姓氏", "个人姓氏，即法定姓名中的姓氏部分"),
  file_id: metadata("文件标识", "Platform 文件对象的稳定外部标识"),
  fixed_amount: metadata("固定金额", "固定费用、固定分润或固定平台服务费组件使用的金额；不适用时为空"),
  given_name: metadata("个人名字", "个人法定姓名中的名字"),
  hash_schema_version: metadata("哈希结构版本", "解释 Canonical Hash 输入字段、顺序与编码规则的版本号"),
  history_id: metadata("历史记录标识", "业务变更主记录或不可变资格评估历史的内部标识"),
  id: metadata("补偿任务标识", "补偿执行记录的内部唯一标识"),
  idempotency_id: metadata("幂等记录标识", "命令幂等执行记录的内部唯一标识"),
  idempotency_key: metadata("幂等键", "调用方在指定作用域和操作类型下提供的稳定请求键"),
  identifier_value: metadata("法律标识原值", "身份证件号、护照号或机构注册号等法律标识的结构化原值"),
  identifier_type: metadata("法律标识类型", "注册号、证件号等法律标识的受控类型代码"),
  identity_key_id: metadata("法律标识根标识", "跨 Profile 稳定的 Party 法律标识身份键"),
  incorporation_country: metadata("注册成立国家", "机构依法注册成立的 ISO 3166-1 alpha-2 国家代码"),
  incorporation_date: metadata("机构成立日期", "机构依法成立或注册的日期"),
  industry_code: metadata("行业编码", "平台定义的经营行业代码"),
  issuer_scope_key: metadata("签发作用域键", "法律标识签发国家或 GLOBAL Sentinel 形成的稳定作用域键"),
  issuing_country: metadata("签发国家", "签发该法律标识的 ISO 3166-1 alpha-2 国家代码；全球标识可为空"),
  last_error_code: metadata("最后错误代码", "补偿执行最近一次失败的稳定错误分类代码"),
  last_error_summary: metadata("最后错误摘要", "补偿执行最近一次失败的脱敏错误摘要，不保存敏感原文"),
  legal_name: metadata("机构法定名称", "机构在注册司法辖区登记的完整法定名称"),
  legal_document_id: metadata("法律文档标识", "一份独立、可发布且可审批的消费者法律文档内部唯一标识"),
  local_partner_id: metadata("Local Partner 标识", "Local Partner 角色的内部唯一标识"),
  locale: metadata("语言区域代码", "内容解析和展示使用的受控 IETF BCP 47 Locale 代码"),
  lock_version: metadata("技术锁版本", "高并发技术记录条件更新使用的独立乐观锁版本号"),
  lock_owner: metadata("租约持有者", "当前 Claim 补偿任务的执行实例标识"),
  lock_until: metadata("租约截止时间", "当前补偿任务执行租约到期的 UTC 微秒精度时刻"),
  max_amount: metadata("金额上限", "百分比组件计算结果允许收取或分配的最高金额"),
  mcc_code: metadata("MCC 编码", "支付收单语义下的商户类别代码"),
  min_amount: metadata("金额下限", "百分比组件计算结果至少收取或分配的最低金额"),
  nationality_country: metadata("国籍国家", "个人国籍的 ISO 3166-1 alpha-2 国家代码"),
  next_execute_at: metadata("下次执行时间", "补偿任务下一次允许被 Claim 的 UTC 微秒精度时刻"),
  operating_country: metadata("经营国家", "Operating Entity 开展业务的 ISO 3166-1 alpha-2 国家代码"),
  operating_entity_id: metadata("Operating Entity 标识", "合同卖方及 Provider MID 法律持有人的内部唯一标识"),
  operating_entity_application_id: metadata("经营主体申请标识", "材料所属 Operating Entity Application 的本库标识；三个申请父标识恰好一个非空"),
  operation_type: metadata("操作类型", "命令幂等隔离或业务变更快照使用的稳定操作代码"),
  organization_party_id: metadata("机构 Party 标识", "Party Relation 中被持有、控制或任职的机构 Party 标识"),
  organization_type: metadata("机构类型", "按司法辖区和法律形态分类的受控机构类型代码"),
  ownership_percentage: metadata("持股比例", "具备持股语义的 Party 关系所记录的持股百分比，取值范围为 0 至 100"),
  parent_history_id: metadata("父变更记录标识", "同一聚合内部直接父业务行对应的变更主记录标识"),
  partner_no: metadata("Local Partner 编号", "供业务查询使用的唯一 Local Partner 编号"),
  party_id: metadata("Party 标识", "自然人或机构法律身份根的内部唯一标识"),
  party_no: metadata("Party 编号", "供业务查询与关联使用的唯一法律主体编号"),
  party_profile_id: metadata("Party Profile 标识", "申请或合同冻结引用的 Party 资料版本标识"),
  party_type: metadata("法律主体类型", "区分自然人与机构法律身份的类型代码"),
  payload_hash: metadata("Payload 哈希", "规范化事件或任务 Payload 的 SHA-256 内容哈希"),
  payload_json: metadata("Payload JSON 内容", "领域事件或本地恢复动作的受控 JSON Payload，大小不得超过 64 KiB"),
  payment_method: metadata("支付方式代码", "Pricing Scope 匹配的支付方式能力代码"),
  plan_code: metadata("计划编号", "定价、分润或平台服务费计划的唯一稳定业务编号"),
  primary_slot: metadata("主要分类唯一槽位", "用于保证同一 Business Profile 最多存在一组主要经营分类"),
  priority_no: metadata("优先级", "同一准确交易范围内 Operating Entity 候选的确定性顺序，数值越小越优先"),
  profile_id: metadata("Party Profile 标识", "Party 资料版本头及其类型明细的内部唯一标识"),
  profile_status: metadata("Party Profile 状态", "Party 资料版本当前所处的生命周期状态"),
  middle_name: metadata("个人中间名", "个人法定姓名中的可选中间名"),
  normalized_identifier_value: metadata("规范化法律标识值", "按法律标识类型和签发作用域批准规则确定性规范化后的值，用于本库查重和唯一约束"),
  postal_code: metadata("邮政编码", "主体地址的可选邮政编码"),
  profile_identifier_id: metadata("资料标识关联标识", "Party Profile 与稳定法律标识关联行的内部唯一标识"),
  public_address_text: metadata("公开地址文本", "按司法辖区规则批准向 Consumer 披露的卖方地址文本"),
  public_legal_name: metadata("公开法定名称", "批准向 Consumer 展示的 Operating Entity 法定名称"),
  public_registration_number: metadata("公开注册号", "批准向 Consumer 展示的 Operating Entity 注册编号"),
  public_tax_registration_number: metadata("公开税务登记号", "按国家披露规则可选展示的卖方税务登记编号"),
  qualification_decision_id: metadata("资格决定标识", "Risk & Compliance 返回的逻辑决定引用，不建立跨库物理外键"),
  qualification_decision_version: metadata("资格决定版本", "Risk & Compliance 决定的外部版本坐标，与决定标识成组出现"),
  qualification_type: metadata("资格审核类型", "提交时按申请角色和 Party 类型确定并冻结的 Risk 资格类型"),
  rate: metadata("比例费率", "百分比费用或分润组件使用的小数费率，取值范围为 0 至 1"),
  record_id: metadata("业务记录标识", "被审计业务行在其 record_type 下的稳定业务标识"),
  record_type: metadata("业务记录类型", "解释 record_id 及其 Snapshot Schema 的稳定领域类型代码"),
  related_party_id: metadata("关联 Party 标识", "Party Relation 中所有者、控制人、董事、签字人或 UBO 的 Party 标识"),
  relation_type: metadata("Party Relation 类型", "所有权、控制、任职或 UBO 等法律关系类型代码"),
  relation_version_id: metadata("Party Relation 版本标识", "不可变法律关系版本的内部唯一标识"),
  requirement_code: metadata("材料要求编码", "Risk 冻结材料要求目录中的稳定开放编码"),
  request_hash: metadata("请求哈希", "幂等命令规范化请求内容的哈希，用于识别同键不同请求"),
  residence_country: metadata("居住国家", "个人主要居住地的 ISO 3166-1 alpha-2 国家代码"),
  response_ref: metadata("响应引用", "首次成功幂等执行结果的受控响应引用；未完成时为空"),
  rounding_mode: metadata("舍入模式", "金额计算采用的确定性十进制舍入规则"),
  rule_id: metadata("分配规则标识", "生成该 Operating Entity 分配结果的自动分配规则标识"),
  schema_version: metadata("Payload 结构版本", "解释事件或任务 Payload JSON 结构的版本号"),
  scope_country: metadata("适用国家", "业务关系或分配使用的准确 ISO 3166-1 alpha-2 国家代码"),
  scope_currency: metadata("适用币种", "业务关系或分配使用的准确 ISO 4217 三位币种代码"),
  snapshot_json: metadata("业务行审计快照", "按 Record Type 白名单生成的不可变 JSON 审计视图，含 Schema、Trigger、Reason 与完整性哈希"),
  source_client_id: metadata("来源 Client 标识", "提交并经平台审核关联 Operating Entity 的 Organization Client 标识"),
  source_id: metadata("来源业务标识", "在 source_type 命名空间内定位补偿来源业务记录的标识"),
  source_partner_id: metadata("来源 Local Partner 标识", "提供 Operating Entity 的 Local Partner 标识"),
  source_relation_id: metadata("来源关系标识", "Operating Entity 来源关系的稳定内部标识"),
  source_type: metadata("来源类型", "解释来源主体或补偿来源业务记录类别的受控代码"),
  source_version: metadata("来源版本", "来源系统聚合或业务事实的单调版本号，用于拒绝乱序回退"),
  status: metadata("生命周期状态", "当前业务记录在其所属聚合生命周期中的状态代码"),
  subject_party_id: metadata("材料主体标识", "材料所证明的申请主 Party 或 UBO、Director、Control 图节点的本库 Party 标识"),
  submitted_time: metadata("提交时间", "业务申请正式提交并进入审核流程的 UTC 微秒精度时刻"),
  submission_content_hash: metadata("提交内容哈希", "申请类型、角色、Profile、来源和排序材料组成的版本化提交包完整性哈希"),
  submission_hash_schema_version: metadata("提交哈希结构版本", "解释申请提交包 Canonical Hash 输入对象、排序与编码规则的版本号"),
  suspension_time: metadata("暂停时间", "业务角色进入暂停状态的 UTC 微秒精度时刻"),
  termination_time: metadata("终止时间", "业务角色终止并停止用于新业务的 UTC 微秒精度时刻"),
  target_business_profile_id: metadata("目标业务资料标识", "申请拟冻结或激活的 Client Business Profile 标识"),
  target_party_profile_id: metadata("目标主体资料标识", "申请拟冻结或激活的 Party Profile 标识"),
  state_or_province: metadata("州省地区", "主体地址所在的可选州、省或同级行政区域"),
  trade_name: metadata("机构商号", "机构对外经营使用的可选商号或交易名称"),
  trigger_code: metadata("计费触发代码", "决定费用或分润组件何时计算的稳定业务事件代码"),
  update_time: metadata("更新时间", "数据库记录最后一次更新的 UTC 时间，精确到微秒"),
  valid_from: metadata("有效开始时间", "法律标识值有效区间的 UTC 微秒精度起点，包含该时刻"),
  valid_to: metadata("有效结束时间", "法律标识值有效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束"),
  vault_metadata_hash: metadata("Vault 元数据哈希", "Vault 返回的记录元数据完整性哈希，不包含可恢复原值"),
  vault_record_version: metadata("Vault 记录版本", "Vault 敏感记录的当前元数据版本号"),
  version_no: metadata("业务版本号", "同一业务聚合内部不可变版本的序号"),
  website_url: metadata("业务网站地址", "Client 对外经营网站的受控 URL；无网站时可为空"),
  acceptance_reference: metadata("接受参考编号", "平台生成的全局唯一、稳定合同接受事件编号"),
  accepted_by_actor_id: metadata("接受执行主体标识", "代表签约方完成合同接受的外部 Actor 稳定标识"),
  activated_time: metadata("激活时间", "合同正式进入 ACTIVE 状态的 UTC 微秒精度时刻"),
  agreement_document_id: metadata("合同文件标识", "合同文件引用记录的内部唯一标识"),
  agreement_name: metadata("合同名称", "合同对外展示和业务识别使用的名称"),
  agreement_operating_entity_id: metadata("合同经营主体覆盖标识", "合同覆盖 Operating Entity 关系的内部唯一标识"),
  agreement_signatory_id: metadata("合同签约方标识", "合同内签约 Party 记录的内部唯一标识"),
  approved_time: metadata("批准时间", "法律事实通过批准的 UTC 微秒精度时刻"),
  business_calendar_code: metadata("业务日历代码", "计算 Client 合同 T+N 工作日结算到期日使用的受控业务日历代码"),
  cutoff_second_of_day: metadata("结算截止日内秒数", "解释 Client 合同结算基准日的本地日内截止秒数，范围为 0 至 86399"),
  evidence_document_id: metadata("接受证据文件标识", "合同接受方式矩阵要求的必填文件引用证据标识"),
  public_support_email: metadata("公开支持邮箱", "向消费者公开展示的卖方支持邮箱"),
  public_support_phone: metadata("公开支持电话", "向消费者公开展示的卖方支持电话"),
  published_time: metadata("发布时间", "法律事实正式向消费者发布的 UTC 微秒精度时刻"),
  signatory_role: metadata("签约方角色", "Party 在该合同中的受控签约角色"),
  signed_time: metadata("签署时间", "合同完成必要签署的 UTC 微秒精度时刻"),
  settlement_business_days: metadata("结算工作日天数", "Client 合同约定的 T+N 工作日结算到期天数"),
  settlement_timezone: metadata("结算时区", "解释 Client 合同工作日和截止秒数使用的 IANA 时区代码"),
});

export const FIELD_DESCRIPTION_OVERRIDES = Object.freeze({
  "agreement_operating_entity.settlement_business_days":
    "CLIENT Agreement 覆盖关系的 T+N 工作日结算到期天数；CLIENT 必填且不得小于 0，PARTNER 必须为空",
  "agreement_operating_entity.business_calendar_code":
    "计算 CLIENT Agreement 结算到期日使用的受控业务日历代码；CLIENT 必填，PARTNER 必须为空",
  "agreement_operating_entity.settlement_timezone":
    "解释 CLIENT Agreement 工作日和截止秒数的 IANA 时区代码；CLIENT 必填，PARTNER 必须为空；取值来源：IANA Time Zone Database；非固定枚举",
  "agreement_operating_entity.cutoff_second_of_day":
    "CLIENT Agreement 结算基准日的本地日内截止秒数，范围为 0 至 86399；CLIENT 必填，PARTNER 必须为空",
  "entity_assignment_rule.client_id":
    "该自动分配规则唯一适用的 Client 标识；当前不支持 GLOBAL 规则",
  "entity_assignment_rule.country_code":
    "规则匹配的准确 ISO 3166-1 alpha-2 国家代码；当前不支持通配值",
  "entity_assignment_rule.currency":
    "规则匹配的准确 ISO 4217 三位币种代码；当前不支持通配值",
  "entity_assignment_rule.industry_code":
    "规则匹配的准确平台行业目录代码；当前不支持通配值",
  "entity_assignment_rule.mcc_code":
    "规则匹配的准确四位 MCC 代码；当前不支持通配值",
  "entity_assignment_rule.operating_entity_id":
    "该规则指定的候选 Operating Entity；生成 Assignment 前必须校验主体、Affiliation 和 Source Relation 均有效",
  "client_entity_assignment.affiliation_id":
    "证明 Client 可在同行准确 Scope 下使用所选 Operating Entity 的有效适用关系标识",
  "client_entity_assignment.entity_source_relation_id":
    "证明所选 Operating Entity 当前来源的唯一有效来源关系标识",
  "client_entity_assignment.rule_id":
    "本次自动分配命中的规则标识；当前自动分配模式下必须非空",
  "client_entity_assignment.operating_entity_id":
    "规则、Affiliation 与 Source Relation 共同验证后冻结的 Operating Entity 标识",
  "commercial_term_scope.payment_method":
    "Client Pricing 可取支付方式目录代码或批准的 *；Partner Share 当前固定为 *",
  "operating_entity_application.operating_entity_id":
    "申请创建时即冻结的稳定 Operating Entity 角色标识；审核拒绝或撤回后重提继续复用同一 PENDING 角色",
  "operating_entity_application.party_profile_id":
    "本次申请冻结审核的机构 Party Profile 版本标识，必须属于 operating_entity_id 对应的 Party",
  "operating_entity_application.source_type":
    "本次 Operating Entity 入驻申请的法律来源类型；PLATFORM 表示平台自有主体，提交 Actor 或入口渠道不由该字段表达",
  "operating_entity_application.source_client_id":
    "source_type=CLIENT 时提交自身或经平台审核关联经营主体的 Organization Client 标识，其他来源为空",
  "operating_entity_application.source_partner_id":
    "source_type=PARTNER 时提交经营主体的 Local Partner 标识，其他来源为空",
  "operating_entity_application.client_source_basis":
    "source_type=CLIENT 时说明目标 Party 与 Client 是同一法律主体或经材料和资格审核确认的关联主体；其他来源为空",
  "operating_entity_application.approved_source_relation_id":
    "仅在申请 APPROVED 时回填的来源关系标识，必须与申请来源和批准经营主体一致",
  "entity_source_relation.source_type":
    "该 Operating Entity 来源关系对应的来源主体类型",
  "application_document.client_application_id":
    "Client Application 父标识；与 partner_application_id、operating_entity_application_id 三个父标识恰好一个非空，使用本库 RESTRICT 外键",
  "application_document.partner_application_id":
    "Local Partner Application 父标识；与 client_application_id、operating_entity_application_id 三个父标识恰好一个非空，使用本库 RESTRICT 外键",
  "application_document.operating_entity_application_id":
    "Operating Entity Application 父标识；与 client_application_id、partner_application_id 三个父标识恰好一个非空，使用本库 RESTRICT 外键",
  "application_document.file_id":
    "Platform File 的逻辑引用，与 content_hash 成组冻结，不建立跨库物理外键",
  "application_document.subject_party_id":
    "材料所证明的申请主 Party 或 UBO、Director、Control 图节点，使用本库 Party 外键",
  "party_address.country_code":
    "地址所在国家的精确 ISO 3166-1 alpha-2 代码",
  "client_business_profile.currency":
    "预期交易量与客单价采用的精确 ISO 4217 三位币种代码",
  "consumer_legal_document.country_code":
    "消费者法律文档适用的精确 ISO 3166-1 alpha-2 国家代码",
  "seller_disclosure.country_code":
    "Seller Disclosure 适用的精确 ISO 3166-1 alpha-2 国家代码",
  "commercial_term_component.currency":
    "固定金额或金额上下限存在时必填的精确 ISO 4217 币种，必须等于所属 Scope 币种；纯百分比且无上下限时为空",
  "compensation_event.source_version":
    "产生该事件或本地恢复动作的 CCT 来源业务记录版本坐标，并参与来源四元组唯一约束",
  "commercial_term_scope.operating_entity_id":
    "可选的 Operating Entity Scope；为空表示该 Agreement 覆盖的全部经营主体，封存时校验完整 Scope Tuple 不重复",
  "entity_source_relation.client_source_basis":
    "Organization Client 提供 Operating Entity 时的来源认定依据；source_type=CLIENT 时为 SAME_PARTY 或 VERIFIED_ASSOCIATION，其他来源为空",
  "party_identity_key.issuing_country":
    "国家签发标识使用的 ISO 3166-1 alpha-2 国家代码；issuer_scope_key=GLOBAL 时为空",
  "party_relation.ownership_percentage":
    "法律关系类型具备持股语义时填写的百分比，取值范围为 0 至 100；其他关系类型为空",
  "business_change_version.create_by":
    "创建不可变审计快照的外部 Actor 稳定标识，是逻辑引用且不建立跨库物理外键",
  "business_change_version.version_no":
    "本次变化提交后的聚合根版本坐标，不是连续的行版本号，同一业务行允许跳号",
  "business_change_record.history_id":
    "一条受审计业务行稳定历史身份的内部唯一标识",
  "business_change_version.history_id":
    "该不可变快照所属 business_change_record 的内部标识",
  "business_change_version.operation_type":
    "本次不可变审计快照对应的业务行操作类型",
  "command_idempotency.operation_type":
    "参与调用方幂等隔离的 CCT 命令操作代码",
  "compensation_event.source_type":
    "产生该待执行事件或本地恢复动作的 CCT 业务记录类型代码",
  "commercial_term_component.calculation_base":
    "Client Pricing 使用受控计算基数目录；Partner Share 固定为 ACTUAL_CLIENT_FEE",
  "client_application.base_party_profile_id":
    "PROFILE_CHANGE 的基准 Party Profile；ONBOARDING 必须为空，提交时必须等于 Client 当时的 ACTIVE Profile",
  "client_application.requested_by_account_id":
    "发起申请的 Platform CUSTOMER Account 外部稳定标识；一般可空，但 ONBOARDING 必填，不建立跨库物理外键",
  "client_application.active_slot":
    "同一 Client 的进行中申请稀疏唯一槽位；DRAFT、SUBMITTED、UNDER_REVIEW 占用，终态释放，不表示 ACTIVE 业务状态",
  "client_application.target_party_profile_id":
    "申请目标 Party Profile；两类申请均必填，与 Base 不同时提交事务必须将其封存为 FROZEN",
  "client_application.base_business_profile_id":
    "PROFILE_CHANGE 的基准 Client Business Profile；ONBOARDING 必须为空，提交时必须等于 Client 当时的 ACTIVE Profile",
  "client_application.target_business_profile_id":
    "申请目标 Client Business Profile；两类申请均必填，与 Base 不同时提交事务必须将其封存为 FROZEN",
  "client_application.due_diligence_case_id":
    "Risk & Compliance 尽调 Case 的逻辑引用，不建立跨库物理外键",
  "partner_application.local_partner_id":
    "申请创建时同一 CCT 事务创建或复用的 PENDING Local Partner 角色标识；REJECTED/WITHDRAWN 重提继续复用，APPROVED 后才激活",
  "partner_application.target_party_profile_id":
    "申请创建时同一 CCT 事务创建或复用的 Organization Target Party Profile 标识",
  "partner_application.requested_by_account_id":
    "发起申请的 Platform CUSTOMER Account 外部稳定标识；Partner Application 必填，不建立跨库物理外键",
  "partner_application.create_by":
    "记录该可变生命周期申请行的 CCT 录入或命令 Actor 标识；不同于 requested_by_account_id，后者仅是外部 Platform CUSTOMER Account 申请人且不证明法律主体或签约资格",
  "partner_application.active_slot":
    "同一 Local Partner 的进行中申请稀疏唯一槽位；DRAFT、SUBMITTED、UNDER_REVIEW 占用，终态释放，不表示 ACTIVE 业务状态",
  "partner_application.due_diligence_case_id":
    "Risk & Compliance 尽调 Case 的逻辑引用；APPROVED 必填，不建立跨库物理外键",
  "partner_application.qualification_decision_id":
    "Risk & Compliance 资格决定的逻辑引用，与 qualification_decision_version 成组为空或成组非空；APPROVED 必填，不建立跨库物理外键",
  "partner_application.qualification_type":
    "系统固定为 PARTNER_DUE_DILIGENCE，不接受调用方选择，并在申请提交时冻结",
  "partner_application.submission_content_hash":
    "输入遵循 CODE-MOR:CCT:PARTNER_APPLICATION_SUBMISSION:1，使用 UTF-8 RFC 8785/JCS 后的 SHA-256 小写 64 位 Hex",
  "operating_entity_application.due_diligence_case_id":
    "Risk & Compliance 尽调 Case 的逻辑引用，不建立跨库物理外键",
  "operating_entity_application.active_slot":
    "同一 Operating Entity 的进行中申请稀疏唯一槽位；DRAFT、SUBMITTED、UNDER_REVIEW 占用，终态释放，不表示 ACTIVE 业务状态",
  "client_application.qualification_decision_id":
    "Risk & Compliance 资格决定的逻辑引用，与 qualification_decision_version 成组为空或成组非空，不建立跨库物理外键",
  "operating_entity_application.qualification_decision_id":
    "Risk & Compliance 资格决定的逻辑引用，与 qualification_decision_version 成组为空或成组非空，不建立跨库物理外键",
  "operating_entity_application.qualification_type":
    "系统固定为 ENTITY_UNDERWRITING，不按 Party Type 推导，不接受调用方选择，并在申请提交时冻结",
  "agreement_document_ref.file_id":
    "Platform File 中合同正文的逻辑引用，与 content_hash 成组冻结，不建立跨库物理外键",
  "party_contact.contact_vault_token":
    "Vault 联系方式标量原值的逻辑引用，不建立跨库物理外键；EMAIL 对应 EMAIL_ADDRESS_V1，PHONE 对应 PHONE_E164_V1",
  "individual_profile.birth_date":
    "个人法定出生 LocalDate 日期事实，不包含时区或时刻",
  "organization_profile.incorporation_date":
    "机构依法成立或注册的 LocalDate 日期事实，不包含时区或时刻",
  "compensation_event.lock_version":
    "补偿任务 Claim 与完成回写使用的独立技术 CAS 版本，不是业务 Revision 或来源版本",
  "business_change_record.record_type":
    "仅标识已批准接入 BCR 的审计对象类型；不在白名单内的业务表和技术表不得登记",
  "business_change_record.aggregate_root_type":
    "该审计对象所属且负责并发串行化的业务聚合根类型；只能使用已批准的 Root 类型",
  "business_change_record.current_version":
    "Root 记录中是唯一 expected_change_version CAS 令牌；受审计 Child 中只指向最近一次快照采用的 Root 版本，不执行独立 Child CAS",
  "party_profile.content_hash":
    "输入为封存 Party Profile 头、类型明细、Identifier、Address 与 Contact 的规范化业务字段",
  "party_contact.vault_metadata_hash":
    "输入为 Vault 返回的记录元数据，不包含可恢复原值或随机 Vault Token",
  "party_relation.content_hash":
    "输入为关系两端、关系类型、持股比例与业务有效期",
  "client_business_profile.content_hash":
    "输入为封存 Business Profile 与按 industry_code、mcc_code 稳定排序的完整经营分类集合和 primary_slot",
  "client_application.submission_content_hash":
    "输入遵循 CODE-MOR:CCT:CLIENT_APPLICATION_SUBMISSION:1，使用 UTF-8 RFC 8785/JCS 后的 SHA-256 小写 64 位 Hex",
  "application_document.content_hash":
    "Platform 文件内容哈希；输入为 Platform File 返回的文件正文 SHA-256，与 file_id 成组冻结",
  "operating_entity_application.submission_content_hash":
    "输入遵循 CODE-MOR:CCT:OPERATING_ENTITY_APPLICATION_SUBMISSION:2，使用 UTF-8 RFC 8785/JCS 后的 SHA-256 小写 64 位 Hex",
  "agreement_document_ref.content_hash":
    "Platform 合同文件内容哈希；输入为 Platform File 返回的合同文件正文 SHA-256，与 file_id 成组冻结",
  "compensation_event.payload_hash":
    "输入为 schema_version 对应的非敏感 Canonical Payload，最大 64 KiB",
  "command_idempotency.request_hash":
    "输入为幂等命令的版本化 Canonical Request",
  "agreement.client_id": "agreement_type=CLIENT 时必须非空的本库 Client 标识；PARTNER 时必须为空",
  "agreement.local_partner_id": "agreement_type=PARTNER 时必须非空的本库 Local Partner 标识；CLIENT 时必须为空",
  "agreement.status": "该独立合同当前所处的法律生命周期状态",
  "agreement_document_ref.file_id": "Platform Support 合同文件的逻辑引用，与 content_hash 成组冻结，不建立跨库物理外键",
  "agreement_document_ref.content_hash": "输入为 Platform Support 返回的合同文件 SHA-256，与 file_id 成组冻结",
  "agreement_acceptance.accepted_by_actor_id": "代表签约方完成合同接受的外部 Actor 稳定标识，是逻辑引用且不建立跨库物理外键",
  "agreement_acceptance.evidence_document_id": "必填合同接受方式证据文件引用标识；CLICK 指向 SIGNING_DOCUMENT，E_SIGNATURE 和 MANUAL_SIGNATURE 指向 EXECUTED_DOCUMENT",
  "consumer_legal_document.content_file_id": "Platform Support 消费者法律文档正文的逻辑引用，与 content_hash 成组冻结，不建立跨库物理外键",
  "consumer_legal_document.content_hash": "输入为 Platform Support 返回的消费者法律文档 SHA-256，与 content_file_id 成组冻结",
  "consumer_legal_document.status": "该独立消费者法律文档当前所处的发布生命周期状态",
  "seller_disclosure.disclosure_id": "一套独立获准公开卖方披露的内部唯一标识",
  "seller_disclosure.status": "该独立法律卖方披露当前所处的发布生命周期状态",
});

export const FIELD_NAME_OVERRIDES = Object.freeze({
  "entity_source_relation.source_type": "来源关系类型",
  "application_document.content_hash": "材料文件内容哈希",
  "agreement_document_ref.content_hash": "合同文件内容哈希",
  "business_change_record.history_id": "业务变更记录标识",
  "business_change_version.history_id": "业务变更记录标识",
  "compensation_event.source_version": "补偿来源版本",
});

/**
 * CCT V1 只为确实需要审计、追责或历史留档的业务对象建立 BCR。
 * 这些代码是稳定领域类型，不是可任意扩展的物理表名目录。
 */
export const BCR_RECORD_TYPES = Object.freeze([
  "PARTY",
  "CLIENT",
  "LOCAL_PARTNER",
  "OPERATING_ENTITY",
  "PARTY_PROFILE",
  "CLIENT_BUSINESS_PROFILE",
  "COMMERCIAL_TERM_VERSION",
  "CLIENT_APPLICATION",
  "PARTNER_APPLICATION",
  "OPERATING_ENTITY_APPLICATION",
  "PARTY_RELATION",
  "CLIENT_ENTITY_AFFILIATION",
  "ENTITY_ASSIGNMENT_RULE",
  "CLIENT_ENTITY_ASSIGNMENT",
  "ENTITY_SOURCE_RELATION",
  "AGREEMENT",
  "CONSUMER_LEGAL_DOCUMENT",
  "SELLER_DISCLOSURE",
  "COMMERCIAL_TERM_PLAN",
]);

export const BCR_ROOT_TYPES = Object.freeze([
  "PARTY",
  "CLIENT",
  "LOCAL_PARTNER",
  "OPERATING_ENTITY",
  "CLIENT_APPLICATION",
  "PARTNER_APPLICATION",
  "OPERATING_ENTITY_APPLICATION",
  "PARTY_RELATION",
  "CLIENT_ENTITY_AFFILIATION",
  "ENTITY_ASSIGNMENT_RULE",
  "CLIENT_ENTITY_ASSIGNMENT",
  "ENTITY_SOURCE_RELATION",
  "AGREEMENT",
  "CONSUMER_LEGAL_DOCUMENT",
  "SELLER_DISCLOSURE",
  "COMMERCIAL_TERM_PLAN",
]);

export const BCR_EXCLUDED_BUSINESS_TABLES = Object.freeze([
  "individual_profile",
  "organization_profile",
  "party_identity_key",
  "party_profile_identifier",
  "party_address",
  "party_contact",
  "client_business_classification",
  "application_document",
  "agreement_signatory",
  "agreement_document_ref",
  "agreement_acceptance",
  "agreement_operating_entity",
  "commercial_term_scope",
  "commercial_term_component",
]);

export const CLOSED_ENUM_VALUES = Object.freeze({
  "party.party_type": ["INDIVIDUAL", "ORGANIZATION"],
  "party_profile.profile_status": ["DRAFT", "FROZEN", "ACTIVE", "SUPERSEDED"],
  "party_contact.contact_type": ["EMAIL", "PHONE"],
  "party_relation.relation_type": ["OWNERSHIP", "CONTROL", "DIRECTOR", "SIGNATORY", "UBO"],
  "party_relation.status": ["DRAFT", "ACTIVE", "ENDED"],
  "client.status": ["PENDING", "ACTIVE", "SUSPENDED", "TERMINATED"],
  "local_partner.status": ["PENDING", "ACTIVE", "SUSPENDED", "TERMINATED"],
  "operating_entity.status": ["PENDING", "ACTIVE", "SUSPENDED", "CLOSED"],
  "client_business_profile.status": ["DRAFT", "FROZEN", "ACTIVE", "SUPERSEDED"],
  "client_application.application_type": ["ONBOARDING", "PROFILE_CHANGE"],
  "client_application.status": ["DRAFT", "SUBMITTED", "UNDER_REVIEW", "APPROVED", "REJECTED", "WITHDRAWN"],
  "client_application.qualification_type": ["INDIVIDUAL_KYC", "ORGANIZATION_KYB"],
  "partner_application.status": ["DRAFT", "SUBMITTED", "UNDER_REVIEW", "APPROVED", "REJECTED", "WITHDRAWN"],
  "partner_application.qualification_type": ["PARTNER_DUE_DILIGENCE"],
  "operating_entity_application.source_type": ["PLATFORM", "CLIENT", "PARTNER"],
  "operating_entity_application.client_source_basis": ["SAME_PARTY", "VERIFIED_ASSOCIATION"],
  "operating_entity_application.status": ["DRAFT", "SUBMITTED", "UNDER_REVIEW", "APPROVED", "REJECTED", "WITHDRAWN"],
  "operating_entity_application.qualification_type": ["ENTITY_UNDERWRITING"],
  "client_entity_affiliation.status": ["ACTIVE", "ENDED"],
  "client_entity_assignment.status": ["ACTIVE", "ENDED"],
  "entity_assignment_rule.status": ["ACTIVE", "ENDED"],
  "entity_source_relation.source_type": ["PLATFORM", "CLIENT", "PARTNER"],
  "entity_source_relation.client_source_basis": ["SAME_PARTY", "VERIFIED_ASSOCIATION"],
  "entity_source_relation.status": ["ACTIVE", "ENDED"],
  "agreement.agreement_type": ["CLIENT", "PARTNER"],
  "consumer_legal_document.document_type": ["CONSUMER_TERMS", "REFUND_POLICY", "PRIVACY_NOTICE"],
  "commercial_term_plan.term_type": ["CLIENT_PRICING", "PARTNER_SHARE", "PLATFORM_SERVICE_CHARGE"],
  "commercial_term_version.status": ["DRAFT", "APPROVED", "SCHEDULED", "ACTIVE", "SUPERSEDED", "EXPIRED"],
  "commercial_term_version.rounding_mode": ["HALF_UP"],
  "commercial_term_component.component_type": ["PERCENTAGE", "FIXED"],
  "business_change_record.record_type": BCR_RECORD_TYPES,
  "business_change_record.aggregate_root_type": BCR_ROOT_TYPES,
  "business_change_version.operation_type": ["CREATE", "UPDATE", "DELETE"],
  "compensation_event.execution_type": ["MQ_PUBLISH", "LOCAL_HANDLER"],
  "compensation_event.execute_status": ["INIT", "PROCESSING", "ERROR", "FAILED"],
  "agreement.status": ["DRAFT", "PENDING_SIGNATURE", "SIGNED", "ACTIVE", "EXPIRED", "TERMINATED", "CANCELLED"],
  "agreement_signatory.signatory_role": ["CLIENT", "LOCAL_PARTNER", "PLATFORM_SIGNATORY", "OPERATING_ENTITY"],
  "agreement_document_ref.document_type": ["SIGNING_DOCUMENT", "ANNEX", "EXECUTED_DOCUMENT", "TERMINATION_NOTICE"],
  "agreement_acceptance.acceptance_method": ["E_SIGNATURE", "CLICK_ACCEPTANCE", "MANUAL_SIGNATURE"],
  "consumer_legal_document.status": ["DRAFT", "APPROVED", "ACTIVE", "EXPIRED", "WITHDRAWN"],
  "seller_disclosure.status": ["DRAFT", "APPROVED", "ACTIVE", "EXPIRED", "WITHDRAWN"],
});

export const CONDITIONAL_VALUE_RULES = Object.freeze({
  "party_profile.active_slot": "状态为 ACTIVE 时为 1，其他状态为 NULL",
  "party_relation.active_slot": "状态为 ACTIVE 时为 1，其他状态为 NULL",
  "client_business_profile.active_slot": "状态为 ACTIVE 时为 1，其他状态为 NULL",
  "client_business_classification.primary_slot": "值为 1 时表示一组主要经营分类，其他情况为 NULL",
  "client_application.active_slot": "状态为 DRAFT、SUBMITTED 或 UNDER_REVIEW 时为 1，终态为 NULL",
  "partner_application.active_slot": "状态为 DRAFT、SUBMITTED 或 UNDER_REVIEW 时为 1，终态为 NULL",
  "operating_entity_application.active_slot": "状态为 DRAFT、SUBMITTED 或 UNDER_REVIEW 时为 1，终态为 NULL",
  "client_entity_affiliation.active_slot": "状态为 ACTIVE 时为 1，其他状态为 NULL",
  "client_entity_assignment.active_slot": "状态为 ACTIVE 时为 1，其他状态为 NULL",
  "entity_source_relation.active_source_slot": "状态为 ACTIVE 时为 1，其他状态为 NULL",
  "entity_assignment_rule.active_slot": "状态为 ACTIVE 时为 1，其他状态为 NULL",
  "commercial_term_version.active_slot": "状态为 ACTIVE 时为 1，其他状态为 NULL",
});

export const OPEN_CODE_SOURCES = Object.freeze({
  "agreement_operating_entity.business_calendar_code": "Platform Business Calendar Catalog",
  "organization_profile.organization_type": "司法辖区机构类型目录",
  "party_identity_key.identifier_type": "法律标识类型目录",
  "party_address.address_type": "地址用途目录",
  "client_business_profile.business_model_code": "Client Business Model Catalog",
  "client_business_profile.currency": "ISO 4217",
  "client_business_classification.industry_code": "平台行业目录",
  "client_business_classification.mcc_code": "平台采用的 MCC 目录，MCC 必须为四位数字",
  "application_document.document_type": "申请材料类型目录",
  "application_document.requirement_code": "Risk 冻结材料要求目录",
  "client_entity_affiliation.scope_country": "ISO 3166-1 alpha-2",
  "client_entity_affiliation.scope_currency": "ISO 4217",
  "client_entity_affiliation.industry_code": "平台行业目录",
  "client_entity_affiliation.mcc_code": "平台采用的 MCC 目录，MCC 必须为四位数字",
  "client_entity_assignment.scope_country": "ISO 3166-1 alpha-2",
  "client_entity_assignment.scope_currency": "ISO 4217",
  "client_entity_assignment.industry_code": "平台行业目录",
  "client_entity_assignment.mcc_code": "平台采用的 MCC 目录，MCC 必须为四位数字",
  "commercial_term_plan.status": "Commercial Term Plan 生命周期定义",
  "commercial_term_scope.industry_code": "平台行业目录中的行业代码或批准的 * 通配值",
  "commercial_term_scope.mcc_code": "平台采用的 MCC 目录中的四位 MCC 代码或批准的 * 通配值",
  "commercial_term_scope.country_code": "ISO 3166-1 alpha-2 与批准的 Scope 通配规则",
  "commercial_term_scope.currency": "ISO 4217 与批准的 Scope 通配规则",
  "commercial_term_scope.payment_method": "Payment Method 能力目录与批准的 Scope 通配规则（Partner Share 固定为 *）",
  "commercial_term_component.calculation_base": "Client Pricing 计算基数目录；Partner Share 固定 ACTUAL_CLIENT_FEE",
  "commercial_term_component.trigger_code": "Commercial Term Trigger Catalog",
  "commercial_term_component.currency": "ISO 4217",
  "compensation_event.event_type": "CCT Domain Event Catalog",
  "compensation_event.source_type": "CCT Compensation Source Catalog",
  "compensation_event.last_error_code": "CCT Compensation Error Catalog",
  "command_idempotency.operation_type": "CCT Command Catalog",
  "command_idempotency.status": "命令幂等记录生命周期定义",
  "consumer_legal_document.country_code": "ISO 3166-1 alpha-2",
  "consumer_legal_document.locale": "IETF BCP 47 Locale Catalog",
  "seller_disclosure.country_code": "ISO 3166-1 alpha-2",
  "seller_disclosure.locale": "IETF BCP 47 Locale Catalog",
  "individual_profile.nationality_country": "ISO 3166-1 alpha-2",
  "individual_profile.residence_country": "ISO 3166-1 alpha-2",
  "organization_profile.incorporation_country": "ISO 3166-1 alpha-2",
  "operating_entity.operating_country": "ISO 3166-1 alpha-2",
  "party_identity_key.issuing_country": "ISO 3166-1 alpha-2；全局标识为空",
  "party_address.country_code": "ISO 3166-1 alpha-2",
  "entity_assignment_rule.industry_code": "平台行业目录",
  "entity_assignment_rule.mcc_code": "平台采用的 MCC 目录，MCC 必须为四位数字",
  "entity_assignment_rule.country_code": "ISO 3166-1 alpha-2",
  "entity_assignment_rule.currency": "ISO 4217",
});

export const BUSINESS_IDENTIFIER_CODE_PATHS = Object.freeze(new Set([
  "commercial_term_component.component_code",
  "commercial_term_plan.plan_code",
  "command_idempotency.caller_scope",
  "consumer_legal_document.document_code",
]));

export function controlledValueCandidate(fieldPath, fieldKey) {
  if (
    BUSINESS_IDENTIFIER_CODE_PATHS.has(fieldPath) ||
    fieldPath === "party_address.postal_code"
  ) {
    return false;
  }
  return (
    /(?:^|_)(?:status|type|role|method|source|flag|slot|mode|basis|level|system)$/.test(fieldKey) ||
    ["dimension_code", "calculation_base", "data_class", "result"].includes(fieldKey) ||
    fieldKey.endsWith("_code") ||
    fieldKey.endsWith("_country") ||
    fieldKey.endsWith("_currency") ||
    ["country_code", "currency", "locale", "caller_scope"].includes(fieldKey)
  );
}
