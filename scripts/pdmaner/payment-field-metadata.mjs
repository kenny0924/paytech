const metadata = (defName, description) => Object.freeze({ defName, description });

/**
 * Payment Orchestration PDManer 表中文显示名。logical key 不携带物理表前缀，
 * 物理生成器负责把它映射到对应的 t_ 表。
 */
export const TABLE_DISPLAY_NAMES = Object.freeze({
  "payment_intent_binding": "支付来源绑定",
  "payment_channel": "支付服务商",
  "channel_account": "支付服务商账户",
  "channel_mid": "服务商商户号",
  "mid_capability": "商户号静态能力",
  "mid_health_state": "商户号动态健康状态",
  "routing_policy": "路由策略",
  "routing_policy_version": "路由策略版本",
  "routing_policy_mid": "路由策略候选商户号",
  "routing_decision": "路由决策",
  "route_candidate_eval": "路由候选评估",
  "payment_intent": "支付意图",
  "payment_attempt": "支付尝试",
  "payment_instrument_token": "交易级支付工具 Token",
  "payment_action": "支付渠道动作",
  "channel_transaction_ref": "服务商交易引用",
  "channel_interaction": "服务商交互记录",
  "duplicate_payment_case": "重复支付处置",
  "payment_state_transition": "支付状态流转",
  "payment_refund_position": "渠道可退款位置",
  "channel_refund": "渠道退款",
  "channel_callback": "服务商回调",
  "channel_callback_item": "服务商回调项",
  "channel_event_exception": "服务商事件异常",
  "channel_dispute_notice": "服务商争议通知",
  "channel_dispute_event": "服务商争议事件",
  "channel_dispute_action": "服务商争议动作",
  "command_idempotency": "命令幂等记录",
  "compensation_event": "本地补偿事件",
  "payment_pricing_fact": "支付定价事实",
});

/*
 * 每一行显式声明一个物理字段键及其业务语义。相同字段键在不同 Payment 表中
 * 必须保持同一种基础含义；表级差异由闭合枚举或开放目录契约进一步限定。
 */
const FIELD_METADATA_SOURCE = String.raw`
payment_instrument_token_id|交易工具绑定标识|交易级工具 Token 的 Snowflake 内部主键
payment_instrument_token|交易工具 Token|安全随机生成的不透明内部工具定位符；只持有此值不具有扣款权限
instrument_type|工具类型|已批准的工具 Owner 类型
instrument_ref|工具记录引用|Owner 返回的稳定内部记录引用，不携带原始卡数据或临时材料
revoked_at|撤销时间|单向撤销新使用资格的 UTC 时间，不表示渠道资金撤销
sub_event_type|补偿业务子类型|同一 Handler 下的受控业务子类型，最长 64 个 Java 字符；与 event_key、event_type 共同组成稳定唯一身份
authentication_valid_until|认证证明截止时间|成功 AUTHENTICATE 证明可供原资金动作使用的 UTC 微秒截止时间；成功认证必填，其他情形为空
challenge_performed|实际执行挑战|可信证据确认是否实际执行挑战：1 是、0 否；未知或非认证动作时为空，不能由浏览器回跳推断
authentication_outcome|认证结论|AUTHENTICATE 获得的可信标准化认证结论。允许值：AUTHENTICATED、ATTEMPTED、FAILED、UNAVAILABLE；无确定结论或非认证动作时为空
interaction_expires_at|交互截止时间|原消费者交互可继续等待的 UTC 微秒截止时间；REQUIRES_ACTION 时必填，完成后保留历史值，不等于认证证明有效期
interaction_type|交互类型|原 Action 的消费者交互类型，无交互时为空。允许值：REDIRECT、THREE_DS_CHALLENGE、SDK、APP_SWITCH、QR_CODE；本期只写 THREE_DS_CHALLENGE
channel_operation_ref|渠道稳定操作引用|渠道返回的稳定非 bearer 操作或认证上下文引用；未取得时为空；不得保存临时 URL、SDK Token、Client Secret 或持有即可继续支付的参数
authentication_action_id|原认证动作标识|需要认证的 AUTHORIZE/SALE 绑定同 Attempt 的成功 AUTHENTICATE 标识；要求金额、币种、Card 和渠道坐标一致，其他动作为空
target_payment_action_id|被查询动作标识|QUERY 查询的原 Payment Action 标识；QUERY 必填并与原动作同父级、同渠道，其他动作为空
authentication_requirement|认证要求|内部 Risk 允许本次支付后冻结的认证要求。允许值：NOT_REQUIRED、THREE_DS_REQUIRED、CHALLENGE_REQUIRED；尚无确定允许结果时为空
payment_goal|支付目标|本次支付尝试冻结的业务目标；新执行必填且不可修改。允许值：PAY_NOW、AUTHORIZE_ONLY；历史未确定时为空
source_submission_id|原支付提交标识|Commerce 原支付提交命令标识；新执行必填且全局唯一绑定一个 Attempt，历史无可靠身份时为空，不得猜测回填
payment_intent_binding_id|支付来源绑定标识|来源订单与支付意图创建资格绑定的本库 Snowflake 唯一标识
acceptance_completed_at|法律接受完成时间|Commerce 确认三项必需法律接受均已完成的业务时刻
account_code|Channel Account 代码|同一支付服务商内稳定识别一个技术认证账户或租户的业务代码
account_status|Channel Account 状态|技术认证账户或租户从草稿、激活、暂停到关闭的当前状态
action_no|渠道动作序号|同一支付尝试、渠道退款或重复支付处置内单调递增的动作序号
action_status|渠道动作状态|渠道动作从创建、提交到权威结果收敛的当前状态
action_type|渠道动作类型|本次向服务商执行的资金、查询或争议处理动作类型
activated_at|激活时间|商户号首次进入可承接生产交易状态的业务时刻
active_attempt_id|活动支付尝试标识|当前仍占用支付意图且可能继续推进或等待权威结果的支付尝试标识
adapter_code|适配器代码|实现该服务商协议、签名与结果映射的受控适配器代码
aggregate_id|状态对象标识|发生本次状态流转的 Payment 白名单业务对象标识
aggregate_type|状态对象类型|发生本次状态流转的 Payment 白名单业务对象类型
amount|业务金额|当前支付目标或渠道资金动作采用的确定金额
attempt_no|支付尝试序号|同一支付意图内从 1 开始单调递增的尝试序号
attempt_status|支付尝试状态|一次支付尝试从创建、处理到权威终态的当前状态
authentication_class|认证分类|Channel验证的实际3DS分类，不能由没有Challenge推断NON_THREE_DS
authorized_amount|已授权金额|该支付尝试已获得服务商权威授权的累计金额
boolean_value|布尔能力值|BOOLEAN 能力的类型化值，1 表示启用，0 表示未启用，其他类型时为 NULL
callback_status|回调处理状态|服务商回调信封从接收、处理到归档或异常的当前状态
caller_scope|调用方作用域|隔离同步命令幂等键命名空间的稳定调用方作用域
candidate_priority|候选优先级|路由策略中候选商户号的优先顺序，数值越小优先级越高
canonical_action_id|首笔成功动作标识|支付意图首次权威成功对应的渠道动作标识
canonical_attempt_id|首笔成功尝试标识|支付意图首次权威成功对应的支付尝试标识
canonical_transaction_ref_id|首笔成功交易引用标识|支付意图首次权威成功对应的服务商交易引用标识
capability_code|能力代码|MID 固定五维 Scope 下受控的静态路由能力代码
capability_status|支付能力状态|商户号支付能力配置当前是否参与路由匹配
captured_amount|已捕获金额|该支付尝试已经确认成功捕获的累计金额
card_level|卡等级|Payment接入时批准的卡等级代码目录；未确认不猜测
card_network|卡组织|经可信Channel证据标准化的卡组织；未知不作为普通卡，CCT不接收原始值
case_status|重复支付处置状态|第二笔真实渠道成功从发现、补偿到人工处置的当前状态
channel_refund_id|渠道退款标识|Payment 为一个 Commerce 退款请求创建的唯一渠道退款标识
circuit_status|熔断状态|商户号支付方式当前处于关闭、打开或半开探测的熔断状态
client_id|Client 标识|来源订单所属平台 Client 的稳定业务标识
client_pricing_version_id|客户定价版本标识|支付准备时由CCT固定的Client条款版本，后续准确Price只能从此版本解析
closed_at|关闭时间|商户号永久进入关闭状态的业务时刻；未关闭时为空
code_value|代码能力值|CODE 能力采用的受控代码值，其他类型时为空
command_idempotency_id|命令幂等记录标识|一次 Payment 同步写命令幂等执行记录的内部唯一标识
command_status|命令处理状态|同步命令当前处于处理中、成功或失败的闭合状态
commerce_refund_request_id|Commerce 退款请求标识|Commerce 拥有且在 Payment 中唯一对应渠道退款的业务请求标识
compensation_action_id|补偿退款动作标识|为处置第二笔真实渠道成功而创建的 REFUND 动作标识；尚未创建时为空
consecutive_failure_count|连续失败次数|当前健康窗口末尾连续发生技术失败的次数
consumer_contract_id|消费者合同标识|Commerce 已呈现且通过支付准备度校验的不可变消费者合同标识
consumer_country_code|消费者国家|由不可变Commerce Context带入并核验的消费者国家
consumer_terms_acceptance_id|消费者条款接受标识|Commerce 中与本次尝试同订单、同会话的消费者条款接受证据标识
content_hash|受控内容哈希|通过安全扫描后留存文件内容的 SHA-256 完整性哈希；没有受控文件时为空
context_format|交易上下文格式|TERM_PRICE使用entityNo和条款版本；LEGACY_SCOPE保留历史身份及原Scope读取，不回写旧订单
country_code|国家代码|支付能力或路由条件采用的国家维度；精确值遵循 ISO 3166-1 alpha-2，配置 Scope 允许控制通配符 *
create_by|创建操作者|产生本次状态流转的非空人员、系统或服务操作者标识
create_time|创建时间|数据库记录创建的 UTC 微秒精度时间
currency|币种代码|金额事实采用 ISO 4217 三位币种；配置 Scope 允许控制通配符 *，交易事实必须使用精确币种
current_channel_status|争议渠道状态|服务商争议在渠道侧的当前权威生命周期状态
current_version_id|当前配置版本标识|稳定配置根当前唯一允许用于生产解析的已发布版本标识；尚未首次发布或未启用时可为空
decimal_value|小数能力值|DECIMAL 能力采用的精确数值，其他类型时为空
decision_reason_code|路由结论原因代码|路由选中、拒绝或配置冲突采用的稳定原因代码；无需原因时为空
decision_status|路由决策状态|一次支付尝试最终得到选中、拒绝或配置冲突的闭合结论
dispute_amount|争议金额|服务商通知并由 Payment 冻结的争议资金金额
dispute_case_id|Risk 争议案件标识|Risk & Compliance 拥有并指示本次渠道争议动作的案件标识
dispute_evidence_package_id|争议证据包标识|Risk & Compliance 为本次提交冻结的证据包标识；查询动作可为空
dispute_evidence_package_version|争议证据包版本|本次提交严格使用的 Risk 证据包版本；查询动作可为空
dispute_submission_request_id|争议提交请求标识|Risk & Compliance 下达的精确争议提交请求标识；查询动作可为空
dispute_submission_request_version|争议提交请求版本|本次动作冻结使用的 Risk 争议提交请求版本；查询动作可为空
duplicate_action_id|重复成功动作标识|第二笔真实渠道成功对应且需要补偿处置的渠道动作标识
duplicate_amount|重复成功金额|第二笔真实渠道成功形成且需要退回的确定金额
duplicate_attempt_id|重复成功尝试标识|第二笔真实渠道成功对应并进入冲突终态的支付尝试标识
duplicate_payment_case_id|重复支付处置标识|第二笔真实渠道成功处置根的内部唯一标识
duplicate_transaction_ref_id|重复成功交易引用标识|第二笔真实渠道成功对应的服务商交易引用标识
eligible_flag|候选可用标志|1 表示该商户号满足本次路由条件，0 表示被排除
entity_no|经营主体编号|CCT唯一不可变OE编号，创建时经营国家二位代码加001–999，如AE001；不是数字代理ID，编号不参与路由
event_key|事件幂等键|领域事件或本地恢复动作在重试期间保持不变的全局稳定幂等键
event_no|争议事件序号|同一服务商争议内从 1 开始单调递增的不可变事件序号
event_type|事件类型|当前记录表达的服务商事件、业务事件或本地恢复任务类型
exception_status|事件异常状态|服务商事件异常从发现、调查到解决或忽略的当前状态
exception_type|事件异常类型|服务商回调在签名、匹配、时序、状态或载荷安全方面的异常分类
exclusion_reason_code|候选排除原因代码|该商户号未进入最终可选集合的主要稳定原因代码；可用时为空
execute_count|执行次数|本地补偿事件已经开始执行的累计次数
execute_status|执行状态|本地补偿事件当前的可恢复执行状态
expires_at|业务到期时间|支付意图或消费者后续动作允许继续处理的最晚业务时刻；消费者动作未提供期限时为空
external_account_id|服务商外部账户标识|服务商命名空间内标识该技术认证账户或租户的可选外部标识
external_dispute_id|服务商争议编号|服务商命名空间内稳定且不可复用的争议业务编号
external_event_id|服务商事件编号|同一 Channel Account 内由服务商提供的回调事件唯一编号；未提供时为空并使用账户内载荷指纹去重
external_mid_code|服务商商户号代码|服务商侧识别该 MID 的稳定代码，不保存凭据或 Secret
external_transaction_id|服务商交易编号|服务商命名空间内标识一笔授权、捕获、扣款、撤销或退款的交易编号
fact_schema_code|事实格式代码|Payment标准化定价事实的固定格式，不用于推断未知卡属性
failure_stage|失败阶段|支付尝试被前置条件阻断、渠道失败或需要恢复时所在的业务处理阶段
file_id|回调文件标识|Platform Support 保存的受控加密回调原文文件标识；未留存原文时为空
finished_at|交互结束时间|服务商 HTTP 交互收到响应、发生网络错误或超时的业务时刻
from_status|流转前状态|状态变更发生前的聚合或服务商争议状态；对象创建时可为空
funding_type|卡资金类型|经Channel确认的信用、借记或预付分类；必要事实未知时不得生成可定价快照
health_observed_at|健康快照观察时间|路由候选评估冻结所读取健康状态的观察时间；未形成快照时为空
health_score|健康评分|当前窗口技术健康度的零到一百分评分
health_status|商户号健康状态|路由评估时读取或冻结的商户号健康结论
http_status|HTTP 状态码|服务商 HTTP 响应状态码；无有效 HTTP 响应时为空
id|补偿事件标识|本地补偿事件记录的内部唯一标识
idempotency_key|幂等键|调用方作用域与操作类型内稳定识别同一业务命令的键
industry_code|行业代码|Commerce 从 Client & Contract 冻结并传入的平台行业目录代码
integer_value|整数能力值|INTEGER 能力采用的整数值，其他类型时为空
integration_config_key|集成配置定位键|服务商适配器用于解析非敏感运行配置的稳定定位键，不保存端点或凭据内容
intent_status|支付意图状态|一个来源订单支付目标当前的闭合业务生命周期状态
interaction_no|交互序号|同一渠道动作内从 1 开始递增的真实 HTTP 交互次数
interaction_status|交互状态|一次服务商 HTTP 交互从已开始到响应、网络错误或超时的当前状态
item_no|回调项序号|同一批量回调信封内从 1 开始递增的项目序号
last_error_code|最近错误代码|补偿事件最近一次执行失败的稳定错误分类代码；尚无错误时为空
last_error_summary|最近错误摘要|补偿事件最近一次执行失败且不含敏感信息的摘要；尚无错误时为空
latency_ms|交互耗时毫秒数|服务商 HTTP 交互从开始到结束的总耗时毫秒数；尚未结束时为空
latest_event_id|最新争议事件标识|当前争议状态所对应的最新不可变服务商争议事件标识；尚无事件时为空
latest_event_no|最新争议事件序号|争议通知已经原子应用的最大事件序号
limit_reservation_group_id|Risk 限额预占组标识|Risk & Compliance 为本次支付尝试建立的限额预占组标识；未建立时为空
lock_owner|锁持有者|当前持有本地补偿事件数据库 Claim Lease 的服务实例标识；未被认领时为空
lock_until|锁截止时间|本地补偿事件 Claim Lease 到期并可被其他实例接管的时刻；无活动 Lease 时为空
lock_version|锁版本|本地补偿事件 Claim 与 Lease 并发更新使用的单调乐观锁版本
mapping_status|回调映射状态|回调项被映射、无法匹配、匹配歧义或无需处理的闭合结论
match_priority|策略匹配优先级|同一作用域匹配层级内用于稳定排序的优先级，数值越小优先
mcc_code|MCC 代码|精确值为 Commerce 从 Client & Contract 冻结的四位 Merchant Category Code；配置 Scope 允许控制通配符 *
mid_capability_id|商户号支付能力标识|一条商户号支付方式、卡组织、地域与动作能力配置的内部唯一标识
mid_health_state_id|商户号健康状态标识|一个商户号与支付方式当前短窗口健康聚合的内部唯一标识
mid_id|服务商商户号标识|Payment 管理且唯一归属于一个 Operating Entity 的服务商商户号标识
mid_status|商户号状态|服务商商户号从草稿、激活、暂停到关闭的当前状态
next_execute_at|下次执行时间|本地补偿事件下一次具备 Claim 资格的 UTC 微秒精度时间
normalized_status|归一化状态|服务商适配器把回调原始结果映射出的标准状态代码；无法归一化时为空
observed_at|健康观察时间|健康结论所依据观测窗口的业务时刻；只有更晚观测才能覆盖当前值
operation_type|操作类型|Payment 同步写命令采用的受控业务操作代码
order_version|Commerce 订单版本|COM-I10 校验法律接受准备度时返回并由支付尝试冻结的订单版本
original_amount|原支付金额|成功支付对应且可用于渠道退款的原始金额上限
original_channel_txn_ref_id|原服务商交易引用标识|退款或争议所指向的原始成功服务商交易引用标识
p95_latency_ms|P95 延迟毫秒数|当前健康窗口服务商交互延迟的第九十五百分位毫秒数
partner_share_version_id|合作伙伴分成版本标识|PARTNER来源时固定的CCT分成版本，与平台服务费版本成组非空
payload_fingerprint|回调载荷指纹|服务商未提供外部事件编号时，对同一 Channel Account 的规范化回调信封生成的去重指纹
payload_json|事件载荷 JSON|本地补偿执行所需的有界、不可变且不含敏感信息的结构化载荷
payment_action_id|支付渠道动作标识|一次真实服务商渠道动作的内部唯一标识
payment_attempt_id|支付尝试标识|同一支付意图内一次顺序支付尝试的内部唯一标识
payment_fact_id|定价事实标识|Payment在必要事实齐备后一次插入的最终事实快照标识，不复制Channel原始数据
payment_intent_id|支付意图标识|一个来源订单唯一支付目标的内部稳定标识
payment_method_code|支付方式代码|支付尝试冻结本次使用的精确支付方式目录值；商户号 Capability 与 Health State 使用精确值，Routing Policy 可使用精确值或控制通配符 *
payment_refund_position_id|渠道可退款位置标识|Payment 对原成功交易并发控制预占与已退款金额的位置标识
payment_state_transition_id|支付状态流转标识|一条不可变 Payment 资金主链白名单对象状态流转的内部唯一标识
payment_succeeded_time|支付成功时间|与成功Payment Action及PaymentSucceeded逐值一致的UTC微秒时间
platform_charge_version_id|平台服务费版本标识|PARTNER来源时固定的CCT服务费版本，与分成版本属于同一Partner合同
policy_status|路由策略状态|路由策略当前可参与解析、暂停或永久关闭的状态
privacy_notice_acceptance_id|隐私声明接受标识|Commerce 中与本次尝试同订单、同会话的隐私声明接受证据标识
channel_account_id|Channel Account 标识|Payment 管理的服务商技术认证账户或租户内部唯一标识
channel_callback_id|服务商回调标识|一次服务商回调信封的内部唯一标识
channel_callback_item_id|服务商回调项标识|批量服务商回调中一条不可变项目的内部唯一标识
channel_code|服务商代码|平台稳定识别支付服务商的唯一业务代码
channel_deadline_at|服务商举证截止时间|服务商要求完成争议举证提交的最晚业务时刻；未给出时为空
channel_dispute_action_id|服务商争议动作标识|一次按 Risk 指令提交举证或查询服务商争议的动作标识
channel_dispute_event_id|服务商争议事件标识|一条不可变服务商争议事件的内部唯一标识
channel_dispute_notice_id|服务商争议通知标识|一个服务商争议身份、金额与当前渠道状态根的内部唯一标识
channel_error_count|服务商错误次数|当前健康窗口由服务商明确返回技术错误的样本数
channel_error_rate|服务商错误率|当前健康窗口服务商技术错误样本占全部样本的比例
channel_event_exception_id|服务商事件异常标识|一个服务商回调签名、映射、时序、状态或安全异常处置记录标识
channel_event_time|服务商事件时间|服务商声明渠道交易、回调项或争议事件发生的业务时刻；未提供时为空
channel_id|支付服务商标识|Payment 管理的支付服务商内部唯一标识
channel_idempotency_key|服务商幂等键|同一渠道动作网络重发时保持不变并由服务商识别的幂等键
channel_interaction_id|服务商交互标识|一次真实服务商 HTTP 交互记录的内部唯一标识
channel_name|服务商名称|供运营配置和审阅展示的支付服务商正式名称
channel_result_code|服务商结果代码|服务商适配器从响应中提取的原始业务结果代码；无响应时为空
channel_result_time|服务商结果时间|渠道动作获得当前服务商结果的业务时刻；尚无结果时为空
channel_status|服务商状态|支付服务商从草稿、激活、暂停到关闭的当前运行状态
channel_transaction_ref_id|服务商交易引用标识|一条不可变服务商外部资金交易引用的内部唯一标识
reason_code|业务原因代码|当前状态流转、异常、健康或争议事实采用的稳定原因代码；无需原因时为空
received_at|接收时间|Payment 首次收到服务商回调或争议通知的业务时刻
reference_type|交易引用类型|服务商交易引用对应授权、捕获、扣款、撤销或退款的闭合类型
refund_amount|渠道退款金额|本次渠道退款要求服务商退还给消费者的确定金额
refund_policy_acceptance_id|退款政策接受标识|Commerce 中与本次尝试同订单、同会话的退款政策接受证据标识
refund_status|渠道退款状态|渠道退款从创建、处理、未知到权威终态的当前状态
refunded_amount|已退款金额|原成功支付已经确认通过渠道退回消费者的累计金额
request_content_hash|请求内容哈希|通过安全扫描后留存服务商请求文件的 SHA-256 完整性哈希；未留存时为空
request_file_id|请求文件标识|Platform Support 保存的受控加密服务商请求文件标识；未留存时为空
request_hash|请求哈希|幂等命令首次规范化请求的完整性哈希，用于拒绝同键不同请求
reserved_amount|退款预占金额|已创建但尚未成功或释放的渠道退款占用金额
resolved_at|异常解决时间|服务商事件异常进入解决或忽略终态的业务时刻；未解决时为空
resolved_by|异常解决操作者|完成服务商事件异常处置的人员或系统标识；未解决时为空
resource_id|命令资源标识|幂等命令创建或作用到的 Payment 本库业务资源标识；无资源时为空
resource_type|命令资源类型|幂等命令结果所指 Payment 业务资源的目录类型；无资源时为空
response_code|命令响应代码|幂等命令可重放响应采用的稳定业务结果代码；尚无结果时为空
response_content_hash|响应内容哈希|通过安全扫描后留存服务商响应文件的 SHA-256 完整性哈希；未留存时为空
response_file_id|响应文件标识|Platform Support 保存的受控加密服务商响应文件标识；未留存时为空
risk_result|交易风控结论|Risk & Compliance 对本次支付尝试返回并由 Payment 冻结的权威结论
root_id|时间线根标识|跨子对象查询状态时间线时使用的 Payment 聚合根标识
root_type|时间线根类型|跨子对象查询状态时间线时使用的 Payment 聚合根类型
route_candidate_eval_id|路由候选评估标识|一次路由决策中一条不可变候选商户号评估的内部唯一标识
routing_decision_id|路由决策标识|一次支付尝试唯一不可变路由结论的内部标识
routing_policy_id|路由策略标识|按固定交易作用域匹配的稳定路由策略根标识
routing_policy_mid_id|路由候选商户号标识|一个路由策略版本内候选商户号配置的内部唯一标识
routing_policy_version_id|路由策略版本标识|路由策略不可变候选集合发布版本的内部唯一标识
sample_count|健康样本数|当前健康窗口纳入成功、服务商错误和超时聚合的总样本数
scheme_code|卡组织代码|支付工具使用精确卡组织目录值；Capability Scope 允许精确值或控制通配符 *
scope_id|策略作用域标识|按 scope_type 解释的稳定字符串；CLIENT 为规范十进制ID，OPERATING_ENTITY 为五位entityNo，GLOBAL 固定字符串0，不允许跨类型强制转换
scope_type|策略作用域类型|路由策略适用 Client、Operating Entity 或全局层级的闭合类型
selected_mid_id|选中商户号标识|路由最终选中的服务商商户号标识；拒绝或冲突时为空
selected_channel_account_id|选中 Channel Account 标识|路由最终选中并要求首个资金动作逐值一致的 Channel Account 标识；未选中时为空
selected_channel_id|选中服务商标识|路由最终选中的支付服务商标识；拒绝或冲突时为空
signature_status|回调验签状态|服务商回调签名已验证、被拒绝或无法确定的当前结论
source_action_id|成功来源动作标识|产生本次支付成功权威事实的Payment Action
source_id|来源业务标识|支付意图或补偿事件所引用来源事实的稳定业务标识
source_order_context_id|来源订单上下文标识|Commerce 在订单创建时冻结并传入的不可变 Order Context 标识
source_type|来源类型|支付意图来源或补偿事件来源业务事实采用的受控目录类型
started_at|交互开始时间|向服务商实际发起 HTTP 交互前记录的业务时刻
status_reason_code|状态原因代码|支付尝试当前失败、取消、未知或冲突结果采用的稳定原因代码；无需原因时为空
successful_attempt_id|成功支付尝试标识|支付意图首次形成权威成功结果的支付尝试标识
target_id|回调目标标识|回调项映射到的 Payment 本库业务对象标识；未匹配时为空
target_channel_txn_ref_id|目标交易引用标识|VOID、REFUND 或 QUERY 所指向的既有服务商交易引用标识；无目标引用时为空
target_type|回调目标类型|回调项映射到渠道动作、退款或争议对象的闭合类型
technical_success_count|技术成功次数|当前健康窗口成功完成服务商技术交互的样本数
technical_success_rate|技术成功率|当前健康窗口技术成功样本占全部样本的比例
timeout_count|超时次数|当前健康窗口服务商交互超时的样本数
timeout_rate|超时率|当前健康窗口超时样本占全部样本的比例
to_status|流转后状态|状态变更完成后形成的聚合或服务商争议状态
transaction_assessment_id|交易风控评估标识|Risk & Compliance 为本次支付尝试返回的不可变交易评估标识；未形成时为空
transition_no|状态流转序号|同一状态对象内从 1 开始单调递增的流转序号，不作为并发版本
trigger_id|流转触发标识|引起本次状态变化的命令、回调、查询或系统任务的非空稳定标识
trigger_type|流转触发类型|引起本次状态变化的命令、回调、查询或系统任务目录类型
update_time|更新时间|数据库记录最近一次持久化更新的 UTC 微秒精度时间
valid_until|健康结论有效截止时间|当前商户号健康结论失效并必须重新评估的业务时刻
value_type|能力值类型|MID 静态能力值采用 BOOLEAN、DECIMAL、INTEGER 或 CODE 的闭合类型
version|乐观锁版本|可变 Payment 业务根并发命令与乱序结果条件更新使用的单调版本
version_no|配置版本序号|同一路由策略根内从 1 开始递增的业务发布版本序号
version_status|配置版本状态|不可变配置版本从草稿、激活或验证到退役的当前状态
weight_value|候选权重|同一候选优先级内执行稳定加权选择的正数权重
window_ended_at|健康窗口结束时间|当前健康聚合窗口结束且不晚于观察时间的业务时刻
window_started_at|健康窗口开始时间|当前健康聚合窗口开始且早于结束时间的业务时刻
`;

function parseFieldMetadataSource(source) {
  const result = {};
  for (const rawLine of source.split(/\r?\n/u)) {
    const line = rawLine.trim();
    if (!line) continue;
    const parts = line.split("|");
    if (parts.length !== 3) {
      throw new Error(`Payment 字段元数据格式错误：${line}`);
    }
    const [fieldKey, defName, description] = parts;
    if (result[fieldKey]) {
      throw new Error(`Payment 字段元数据重复：${fieldKey}`);
    }
    if (!/[\u3400-\u9fff]/u.test(defName) || !/[\u3400-\u9fff]/u.test(description)) {
      throw new Error(`Payment 字段元数据缺少明确中文语义：${fieldKey}`);
    }
    result[fieldKey] = metadata(defName, description);
  }
  if (Object.keys(result).length !== 238) {
    throw new Error(`Payment 字段元数据必须精确覆盖 238 个唯一字段键，实际 ${Object.keys(result).length}`);
  }
  return Object.freeze(result);
}

export const FIELD_METADATA = parseFieldMetadataSource(FIELD_METADATA_SOURCE);

/** 闭合状态与类型；数组顺序是已批准数据库契约的一部分。 */
export const CLOSED_ENUM_VALUES = Object.freeze({
  "payment_instrument_token.instrument_type": ["CARD"],
  "payment_channel.channel_status": ["DRAFT", "ACTIVE", "SUSPENDED", "CLOSED"],
  "channel_account.account_status": ["DRAFT", "ACTIVE", "SUSPENDED", "CLOSED"],
  "channel_mid.mid_status": ["DRAFT", "ACTIVE", "SUSPENDED", "CLOSED"],
  "mid_capability.value_type": ["BOOLEAN", "DECIMAL", "INTEGER", "CODE"],
  "mid_capability.capability_status": ["ENABLED", "DISABLED"],
  "mid_health_state.health_status": ["HEALTHY", "DEGRADED", "UNHEALTHY", "UNKNOWN"],
  "mid_health_state.circuit_status": ["CLOSED", "OPEN", "HALF_OPEN"],
  "routing_policy.scope_type": ["CLIENT", "OPERATING_ENTITY", "GLOBAL"],
  "routing_policy.policy_status": ["ACTIVE", "SUSPENDED", "CLOSED"],
  "routing_policy_version.version_status": ["DRAFT", "ACTIVE", "RETIRED"],
  "routing_decision.decision_status": ["SELECTED", "REJECTED", "CONFIGURATION_CONFLICT"],
  "route_candidate_eval.health_status": ["HEALTHY", "DEGRADED", "UNHEALTHY", "UNKNOWN"],
  "route_candidate_eval.circuit_status": ["CLOSED", "OPEN", "HALF_OPEN"],
  "payment_intent.context_format": ["TERM_PRICE", "LEGACY_SCOPE"],
  "payment_intent.intent_status": ["REQUIRES_PAYMENT", "PROCESSING", "REQUIRES_ACTION", "SUCCEEDED", "FAILED", "EXPIRED", "CANCELLED"],
  "payment_attempt.attempt_status": ["CREATED", "PROCESSING", "REQUIRES_ACTION", "AUTHORIZED", "UNKNOWN", "SUCCEEDED", "FAILED", "CANCELLED", "VOIDED", "CONFLICTED"],
  "payment_attempt.failure_stage": ["READINESS", "EXECUTION_GATE", "RISK", "LIMIT", "ROUTING", "CHANNEL", "RECOVERY", "SYSTEM"],
  "payment_attempt.risk_result": ["ALLOW", "REVIEW", "DENY", "FAILED", "TIMED_OUT"],
  "payment_attempt.payment_goal": ["PAY_NOW", "AUTHORIZE_ONLY"],
  "payment_attempt.authentication_requirement": ["NOT_REQUIRED", "THREE_DS_REQUIRED", "CHALLENGE_REQUIRED"],
  "payment_pricing_fact.fact_schema_code": ["PAYMENT_PRICING_FACTS_1"],
  "payment_pricing_fact.funding_type": ["CREDIT", "DEBIT", "PREPAID"],
  "payment_pricing_fact.authentication_class": ["THREE_DS_AUTHENTICATED", "THREE_DS_ATTEMPTED", "NON_THREE_DS"],
  "payment_action.action_type": ["AUTHENTICATE", "AUTHORIZE", "CAPTURE", "SALE", "VOID", "REFUND", "QUERY"],
  "payment_action.action_status": ["CREATED", "SUBMITTED", "REQUIRES_ACTION", "UNKNOWN", "SUCCEEDED", "FAILED"],
  "payment_action.interaction_type": ["REDIRECT", "THREE_DS_CHALLENGE", "SDK", "APP_SWITCH", "QR_CODE"],
  "payment_action.authentication_outcome": ["AUTHENTICATED", "ATTEMPTED", "FAILED", "UNAVAILABLE"],
  "channel_transaction_ref.reference_type": ["AUTHORIZE", "CAPTURE", "SALE", "VOID", "REFUND"],
  "channel_interaction.interaction_status": ["STARTED", "RESPONSE_RECEIVED", "NETWORK_ERROR", "TIMED_OUT"],
  "duplicate_payment_case.case_status": ["OPEN", "COMPENSATING", "COMPENSATION_UNKNOWN", "COMPENSATED", "MANUAL_REVIEW"],
  "payment_state_transition.aggregate_type": ["PAYMENT_INTENT", "PAYMENT_ATTEMPT", "PAYMENT_ACTION", "CHANNEL_REFUND", "DUPLICATE_PAYMENT_CASE"],
  "payment_state_transition.root_type": ["PAYMENT_INTENT", "PAYMENT_ATTEMPT", "PAYMENT_ACTION", "CHANNEL_REFUND", "DUPLICATE_PAYMENT_CASE"],
  "channel_refund.refund_status": ["CREATED", "PROCESSING", "UNKNOWN", "SUCCEEDED", "FAILED", "CANCELLED"],
  "channel_callback.signature_status": ["VERIFIED", "REJECTED", "UNKNOWN"],
  "channel_callback.callback_status": ["RECEIVED", "PROCESSING", "PROCESSED", "REJECTED", "EXCEPTION"],
  "channel_callback_item.target_type": ["PAYMENT_ACTION", "CHANNEL_REFUND", "CHANNEL_DISPUTE_NOTICE", "CHANNEL_DISPUTE_ACTION", "UNKNOWN"],
  "channel_callback_item.mapping_status": ["MAPPED", "UNMATCHED", "AMBIGUOUS", "IGNORED"],
  "channel_event_exception.exception_type": ["SIGNATURE", "MATCHING", "OUT_OF_ORDER", "STATE_CONFLICT", "PAYLOAD_SECURITY"],
  "channel_event_exception.exception_status": ["OPEN", "INVESTIGATING", "RESOLVED", "IGNORED"],
  "channel_dispute_notice.current_channel_status": ["RECEIVED", "EVIDENCE_REQUIRED", "SUBMITTED", "UNDER_REVIEW", "WON", "LOST", "CLOSED"],
  "channel_dispute_event.event_type": ["NOTICE_RECEIVED", "STATUS_CHANGED", "OUTCOME_RECEIVED", "DEADLINE_UPDATED"],
  "channel_dispute_action.action_type": ["SUBMIT_EVIDENCE", "QUERY"],
  "channel_dispute_action.action_status": ["CREATED", "SUBMITTED", "UNKNOWN", "SUCCEEDED", "FAILED"],
  "command_idempotency.command_status": ["PROCESSING", "SUCCEEDED", "FAILED"],
  "compensation_event.execute_status": ["INIT", "PROCESSING", "ERROR", "FAILED"],
});

/** 开放代码的精确权威来源；这些字段不能被生成器虚构为固定枚举。 */
export const OPEN_CODE_SOURCES = Object.freeze({
  "payment_instrument_token.payment_method_code": "Payment Method Catalog",
  "payment_intent_binding.source_type": "Payment Source Type Catalog",
  "payment_action.status_reason_code": "Reason Catalog",
  "compensation_event.sub_event_type": "Payment Compensation Sub Event Catalog",
  "payment_channel.channel_code": "Channel Catalog",
  "payment_channel.adapter_code": "Adapter Catalog",
  "channel_account.account_code": "Channel Account Catalog",
  "channel_mid.external_mid_code": "Channel Adapter Catalog",
  "mid_capability.payment_method_code": "Payment Method Catalog",
  "mid_capability.scheme_code": "Scheme Catalog",
  "mid_capability.country_code": "ISO 3166-1 alpha-2",
  "mid_capability.currency": "ISO 4217",
  "mid_capability.mcc_code": "CCT/Commerce frozen classification",
  "mid_capability.capability_code": "MID Capability Catalog",
  "mid_health_state.payment_method_code": "Payment Method Catalog",
  "routing_policy.country_code": "ISO 3166-1 alpha-2",
  "routing_policy.currency": "ISO 4217",
  "routing_policy.payment_method_code": "Payment Method Catalog",
  "routing_policy.industry_code": "CCT/Commerce frozen classification",
  "routing_policy.mcc_code": "CCT/Commerce frozen classification",
  "routing_decision.decision_reason_code": "Reason Catalog",
  "route_candidate_eval.exclusion_reason_code": "Reason Catalog",
  "payment_intent.source_type": "Payment Source Type Catalog",
  "payment_intent.industry_code": "CCT/Commerce frozen classification",
  "payment_intent.mcc_code": "CCT/Commerce frozen classification",
  "payment_intent.currency": "ISO 4217",
  "payment_attempt.payment_method_code": "Payment Method Catalog",
  "payment_attempt.status_reason_code": "Reason Catalog",
  "payment_pricing_fact.payment_method_code": "Payment Method Catalog",
  "payment_pricing_fact.consumer_country_code": "ISO 3166-1 alpha-2",
  "payment_pricing_fact.currency": "ISO 4217",
  "payment_pricing_fact.card_network": "Payment normalized card network catalog",
  "payment_pricing_fact.card_level": "Payment normalized card level catalog",
  "payment_action.currency": "ISO 4217",
  "channel_transaction_ref.currency": "ISO 4217",
  "channel_interaction.channel_result_code": "Channel Adapter Catalog",
  "duplicate_payment_case.currency": "ISO 4217",
  "payment_state_transition.from_status": "Payment Aggregate Status Catalog",
  "payment_state_transition.to_status": "Payment Aggregate Status Catalog",
  "payment_state_transition.reason_code": "Reason Catalog",
  "payment_state_transition.trigger_type": "Trigger enum",
  "payment_refund_position.currency": "ISO 4217",
  "channel_refund.currency": "ISO 4217",
  "channel_callback_item.event_type": "Channel Adapter Catalog",
  "channel_callback_item.normalized_status": "Channel Adapter Catalog",
  "channel_event_exception.reason_code": "Reason Catalog",
  "channel_dispute_notice.currency": "ISO 4217",
  "channel_dispute_event.from_status": "Channel Dispute Status Catalog",
  "channel_dispute_event.to_status": "Channel Dispute Status Catalog",
  "channel_dispute_event.reason_code": "Reason Catalog",
  "command_idempotency.caller_scope": "Payment Caller Scope Catalog",
  "command_idempotency.operation_type": "Payment Command Operation Catalog",
  "command_idempotency.resource_type": "Payment Command Resource Type Catalog",
  "command_idempotency.response_code": "Payment Command Response Code Catalog",
  "compensation_event.event_type": "Payment Event/Handler Catalog",
  "compensation_event.last_error_code": "Payment Error Catalog",
});

/** 表级说明显式固定每张表的职责与关键不变量。 */
export const TABLE_COMMENTS = Object.freeze({
  "payment_intent_binding": "Execution 来源绑定与单向关闭事实；关闭不代表资金终止，不承载工作流状态。",
  "payment_channel": "支付服务商的稳定身份、适配器和当前运行状态。",
  "channel_account": "支付服务商的技术认证账户或租户；只保存非敏感运行配置定位键。",
  "channel_mid": "服务商商户号与唯一 Operating Entity 的稳定归属及当前 Channel Account 绑定；归属创建后不得改写。",
  "mid_capability": "商户号按固定五维 Scope 配置的静态、类型化路由能力。",
  "mid_health_state": "路由读取的商户号当前短窗口健康聚合，不保存原始指标时序。",
  "routing_policy": "按固定交易作用域匹配的路由策略根，当前发布版本只由 current_version_id 指针确定。",
  "routing_policy_version": "不可变路由候选集发布版本。",
  "routing_policy_mid": "路由策略版本中的候选商户号、优先级和权重。",
  "routing_decision": "一次支付尝试的不可变最终路由结论，冻结服务商、Channel Account 和商户号。",
  "route_candidate_eval": "一次路由决策中全部候选的不可变资格评估证据。",
  "payment_intent": "从已提交Commerce Context取得固定entityNo、金额及Term Version，所有Attempt只能在同一OE下执行；新写去Assignment。Risk未接入新身份时不得伪造资格或跨OE降级。",
  "payment_attempt": "同一支付意图内顺序发生的一次支付尝试，冻结支付方式、法律准备与风控结果。",
  "payment_instrument_token": "Execution 拥有的交易级支付工具 Token 与不可变使用范围，不存卡数据或会话状态。",
  "payment_action": "真实服务商渠道动作；网络重发复用同一动作、幂等键和冻结 Channel Account。",
  "channel_transaction_ref": "授权、捕获、扣款、撤销或退款的不可变服务商外部交易引用。",
  "channel_interaction": "一次真实服务商 HTTP 交互的受控通信记录，可选文件引用必须通过安全扫描。",
  "duplicate_payment_case": "第二笔真实渠道成功的处置根，不能替换支付意图的首笔成功指针。",
  "payment_state_transition": "Payment 资金主链白名单聚合的统一追加状态流转证据，不复制 Callback 或 Dispute 事件历史。",
  "payment_refund_position": "Payment 侧渠道可退款金额位置，预占、消费和释放由乐观锁原子保护。",
  "channel_refund": "一个 Commerce Refund Request 的唯一渠道退款根，UNKNOWN 继续占用退款位置。",
  "channel_callback": "服务商回调信封的 Channel Account 上下文、验签、去重和批次处理状态。",
  "channel_callback_item": "批量服务商回调中逐项归一化和目标映射的不可变证据。",
  "channel_event_exception": "签名、匹配、乱序或非资金状态冲突的独立处置记录。",
  "channel_dispute_notice": "服务商争议身份、金额事实和当前渠道状态根。",
  "channel_dispute_event": "服务商争议的不可变事件流。",
  "channel_dispute_action": "按 Risk 精确请求提交举证或查询并冻结 Channel Account 的服务商动作。",
  "command_idempotency": "Payment 同步写命令的幂等记录与结果定位。",
  "compensation_event": "业务变化同事务登记的本地可靠执行记录；成功后删除，FAILED 保留并可受控重开。",
  "payment_pricing_fact": "成功Attempt的不可变标准化定价事实；必要字段齐备才插入一次。未知不得按零费率入账，成功支付状态不回退；CCT不接收PAN/BIN/Channel原始Payload。",
});
