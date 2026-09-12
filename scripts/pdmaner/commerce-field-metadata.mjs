const metadata = (defName, description) => Object.freeze({ defName, description });

/**
 * Commerce PDManer 表中文显示名。物理表名仍由生成器使用 t_ 前缀；这里的
 * logical key 仅承担稳定的中文展示语义。
 */
export const TABLE_DISPLAY_NAMES = Object.freeze({
  commerce_order: "订单",
  order_context: "订单不可变上下文",
  order_legal: "订单法律上下文",
  order_item: "订单商品服务明细",
  order_payment_projection: "订单支付投影",
  order_funds_availability_projection: "订单资金可用性投影",
  order_refund_position: "订单可退款位置",
  checkout_session: "托管收银台会话",
  consumer_context: "消费者当前上下文",
  consumer_contract: "消费者合同呈现",
  checkout_network_evidence: "收银台网络证据",
  consumer_acceptance: "消费者接受证据",
  refund_request: "退款请求",
  command_idempotency: "命令幂等记录",
  compensation_event: "本地补偿事件",
});

/*
 * 每一行显式声明一个物理字段键及其业务语义。禁止按英文单词拆分生成中文
 * 名称或说明；新增字段若未进入本清单，生成器必须直接失败。
 */
const FIELD_METADATA_SOURCE = String.raw`
acceptance_type|接受类型|本条证据对应的消费者条款、退款政策或隐私声明类型
accepted_at|接受时间|消费者完成该项法律接受动作的业务时刻
active_attempt_id|活动支付尝试标识|支付投影中当前仍可能继续推进的支付尝试标识
affiliation_id|客户主体授权标识|CCT批准的Client对整个OE授权关系，支付准备时冻结
as_of|投影截至时点|本投影已应用来源事件 occurredAt 的最大值，迟到事件不得使其回退
attempt_source_version|尝试来源版本|支付尝试投影已经消费的来源聚合版本
available_amount|可用金额|Finance 确认本订单已经成熟进入 Client 可用余额的金额，未成熟时为空
available_at|资金可用时间|Finance 权威确认本订单资金成熟进入 Client 可用余额的时刻
billing_city|账单城市|消费者账单地址规范化后的城市分析维度，不包含完整街道地址
billing_country_code|账单国家代码|消费者账单地址规范化后的 ISO 3166-1 alpha-2 国家代码
billing_postal_prefix|账单邮编前缀|消费者账单邮编按批准粒度截取的分析前缀，不保存完整邮编
billing_region_code|账单行政区代码|消费者账单地址规范化后的州、省或行政区分析代码
browser_family|浏览器族|由 User-Agent 派生且不包含完整原文的浏览器产品族
browser_major_version|浏览器主版本|由 User-Agent 派生的浏览器主版本分析维度
caller_scope|调用方作用域|隔离命令幂等键命名空间的稳定调用方作用域
cancelled_at|取消时间|Checkout 会话进入取消终态的业务时刻
captured_amount|已捕获金额|订单支付投影确认成功捕获的累计金额
channel_refund_id|渠道退款标识|支付系统负责执行退款的稳定渠道退款标识
checkout_session_id|Checkout 会话标识|Hosted Checkout 会话的内部唯一标识
client_agreement_id|客户合同标识|CCT准确Client/OE合同，替代新流程的Client Agreement-OE关联
client_id|Client 标识|订单或退款请求所属平台 Client 的内部唯一标识
client_ip_address|客户端 IP 地址|可信入口解析并保存的完整客户端 IPv4 或 IPv6 地址
client_item_id|Client 商品项标识|Client 在订单请求中提供且在订单内唯一的商品项标识
client_order_id|Client 订单标识|Client 在自身命名空间内提供且不可复用的稳定订单业务标识；不承担接口请求幂等职责
client_pricing_version_id|客户定价版本标识|支付准备时由CCT固定的Client条款版本，后续准确Price只能从此版本解析
client_refund_id|Client 退款标识|Client 在自身命名空间内提供且不可复用的稳定退款业务标识；Operations 发起时为空
command_idempotency_id|命令幂等记录标识|一次受控命令幂等执行记录的内部唯一标识
commercial_context_time|商业上下文时点|CCT 解析经营主体和商业条款时使用并由订单上下文冻结的业务有效时点
completed_at|完成时间|Checkout 会话或退款请求进入完成终态的业务时刻
consumer_acceptance_id|消费者接受证据标识|一条不可变消费者法律接受证据的内部唯一标识
consumer_context_id|消费者上下文标识|订单当前消费者受保护上下文的内部唯一标识
consumer_contract_id|消费者合同标识|订单呈现给消费者的不可变合同文件记录标识
consumer_country_code|消费者国家代码|订单创建时冻结的消费者 ISO 3166-1 alpha-2 国家代码
consumer_profile_vault_token|消费者资料 Vault Token|指向 Vault 中姓名与完整账单地址等消费者资料的稳定不可枚举 Token
consumer_reference|消费者业务引用|Client 命名空间内可选的非 PII、不透明消费者业务引用；当前 Customer 无此输入时为空，不生成假值；不得提交 Email、Phone、证件号等个人原值
consumer_terms_content_hash|消费者条款内容哈希|订单法律上下文冻结的消费者条款文件内容哈希
consumer_terms_document_id|消费者条款文档标识|订单法律上下文冻结的消费者条款业务文档标识
consumer_terms_file_id|消费者条款文件标识|订单法律上下文冻结的消费者条款不可变文件标识
context_format|交易上下文格式|TERM_PRICE使用entityNo和条款版本；LEGACY_SCOPE保留历史身份及原Scope读取，不回写旧订单
create_time|创建时间|数据库记录创建的 UTC 微秒精度时间
currency|币种代码|金额采用的 ISO 4217 三位币种代码
device_type|设备类型|由客户端特征派生的桌面端、移动端、平板、机器人、其他或未知闭合类型
discount_amount|优惠金额|订单创建时冻结的全部商品服务优惠汇总金额
economics_event_key|经济计算事件键|Finance EconomicsCalculated 事实的大小写敏感稳定来源事件键
economics_source_payload_hash|经济计算来源载荷哈希|Finance EconomicsCalculated 规范来源载荷的 SHA-256 小写十六进制完整性哈希
economics_source_version|经济计算来源版本|Finance EconomicsCalculated 事实的来源版本
email_domain|邮箱域名|消费者邮箱规范化后的域名分析维度，不包含邮箱本地部分
email_vault_token|邮箱 Vault Token|指向 Vault 中消费者 Email 原值的稳定不可枚举 Token
entity_no|经营主体编号|CCT唯一不可变OE编号，创建时经营国家二位代码加001–999，如AE001；不是数字代理ID，编号不参与路由
entity_scope_id|经营主体准入范围标识|CCT已批准的准确国家/币种/行业/MCC范围事实
entity_source_relation_id|经营主体来源关系标识|CCT唯一有效OE来源关系，固定后不得换来源
entity_source_type|主体来源类型|订单经营主体来源于平台、Client 或 Local Partner 的闭合类型
event_key|事件幂等键|领域事件或本地恢复动作的全局稳定幂等键
event_type|事件类型|本地补偿 Handler 的全应用唯一类型
evidence_scene|证据场景|网络证据产生于打开 Checkout、提交消费者资料、接受法律条款或开始支付的闭合场景
execute_count|执行次数|补偿事件已经尝试执行的累计次数
execute_status|执行状态|本地补偿事件当前的可恢复执行状态
expires_at|到期时间|订单允许继续 Checkout 或支付处理的业务截止时刻
finance_economics_id|Finance 交易经济标识|Finance PAYMENT Economics 的稳定引用，仅供本库资金可用性投影导航
finance_tax_snapshot_hash|Finance 税务快照哈希|Finance 税务快照 calculation_hash 的精确副本，按 CODE-MOR:FINANCE:TAX_CALCULATION:1 对 Header、全部 Evidence 和 Tax Line 生成 SHA-256 小写 64 位十六进制，用于校验按标识返回的不可变税务结果
finance_tax_snapshot_id|Finance 税务快照标识|Finance 对订单税务计算结果的不可变快照标识
funds_availability_status|资金可用性状态|订单资金是否已成熟进入 Client 可用余额的闭合投影状态
hosting_flag|托管网络标志|网络情报判断当前 IP 是否属于托管网络；1 表示是，0 表示否，NULL 表示未评估
id|补偿事件标识|本地补偿事件记录的内部唯一标识
idempotency_key|幂等键|调用方作用域内稳定识别同一业务命令的键
industry_code|行业代码|订单上下文采用的平台行业目录代码
intent_source_version|支付意图来源版本|支付意图投影已经消费的来源聚合版本
intent_status|支付意图状态|Commerce 消费到的支付意图当前闭合状态
ip_asn|IP 自治系统号|网络情报解析得到的自治系统编号分析维度
ip_city|IP 城市|网络情报解析得到的城市分析维度
ip_country_code|IP 国家代码|网络情报解析得到的 ISO 3166-1 alpha-2 国家代码
ip_region_code|IP 行政区代码|网络情报解析得到的州、省或行政区分析代码
item_description|商品服务说明|订单商品或服务项目在创建时冻结的可选详细说明
item_name|商品服务名称|订单商品或服务项目在创建时冻结的展示名称
last_attempt_event_key|最近尝试事件键|支付投影最近处理的支付尝试事件幂等键
last_attempt_id|最近支付尝试标识|支付投影最近观察到的支付尝试标识
last_attempt_no|最近支付尝试序号|订单支付意图下最近一次尝试的业务序号
last_attempt_payload_hash|最近尝试载荷哈希|最近支付尝试事件规范化载荷的完整性哈希
last_attempt_status|最近支付尝试状态|支付投影最近观察到的支付尝试闭合状态
last_error_code|最近错误代码|补偿事件最近一次执行失败的稳定错误分类代码
last_error_summary|最近错误摘要|补偿事件最近一次执行失败且不含敏感信息的摘要
last_event_time|最近事件时间|支付或退款投影最近一次成功应用来源事实的业务时刻
last_intent_event_key|最近意图事件键|支付投影最近处理的支付意图事件幂等键
last_intent_payload_hash|最近意图载荷哈希|最近支付意图事件规范化载荷的完整性哈希
line_discount_amount|行优惠金额|订单商品服务行的冻结优惠金额
line_gross_amount|行毛金额|优惠和税务处理前的订单商品服务行金额
line_net_amount|行净金额|行毛金额扣除优惠后的冻结净金额
local_partner_id|Local Partner 标识|当经营主体来源为 PARTNER 时对应的 Local Partner 标识
locale|语言区域代码|文档或消费者体验采用的 IETF BCP 47 语言区域代码
lock_owner|锁持有者|当前持有数据库 Claim Lease 的服务实例标识
lock_until|锁截止时间|数据库 Claim Lease 到期并可被其他实例重新认领的时刻
lock_version|锁版本|补偿事件 Claim Lease 并发更新使用的乐观锁版本
maturity_event_key|成熟事件键|Finance PayableBalanceMatured 事实的大小写敏感稳定来源事件键
maturity_journal_id|成熟分录标识|Finance 完成资金成熟时对应的稳定 Journal 标识，仅供本库投影导航
maturity_source_payload_hash|成熟来源载荷哈希|Finance PayableBalanceMatured 规范来源载荷的 SHA-256 小写十六进制完整性哈希
maturity_source_version|成熟来源版本|Finance PayableBalanceMatured 事实的来源版本
mcc_code|MCC 代码|订单上下文采用的四位 Merchant Category Code
merchandise_amount|商品服务金额|订单创建时冻结的优惠前商品服务汇总金额
net_amount|净金额|订单创建时冻结的商品服务金额扣除优惠后的净金额
network_evidence_id|网络证据标识|一条不可变 Checkout 网络与设备证据的内部唯一标识
network_type|网络类型|网络情报目录返回的接入网络分类代码
next_execute_at|下次执行时间|补偿事件下一次具备执行资格的 UTC 微秒精度时间
occurred_at|事实发生时间|网络证据或预占变化事实发生的业务时刻
opened_at|会话打开时间|Hosted Checkout 会话首次进入可交互状态的时刻
operation_context_time|操作上下文时间|幂等命令首次执行时冻结的业务有效时点
operation_type|操作类型|审计版本操作或 Commerce 命令的受控类型
order_context_id|订单上下文标识|订单创建时冻结外部商业、风控和税务证据引用的内部唯一标识
order_funds_availability_projection_id|订单资金可用性投影标识|一条 Order-owned 资金可用性投影的内部唯一标识
order_id|订单标识|Commerce 订单聚合的内部唯一标识
order_item_id|订单商品项标识|订单内一条商品或服务明细的内部唯一标识
order_legal_id|订单法律上下文标识|订单不可变上下文对应法律呈现记录的内部唯一标识
order_no|订单编号|供接口响应、业务查询与人工操作使用的唯一订单编号
order_payment_projection_id|订单支付投影标识|订单唯一支付状态投影的内部标识
order_refund_position_id|可退款位置标识|订单唯一可退款金额位置的内部标识
order_status|订单状态|订单聚合当前的闭合业务生命周期状态
os_family|操作系统族|由 User-Agent 派生且不包含完整原文的操作系统产品族
os_major_version|操作系统主版本|由 User-Agent 派生的操作系统主版本分析维度
paid_at|订单支付时间|订单确认支付成功并进入 PAID 的业务时刻
partner_agreement_id|合作伙伴合同标识|PARTNER来源时固定的CCT合同，仍允许该合同覆盖多个OE
partner_agreement_operating_entity_id|Partner 合同经营主体关联标识|存在 Local Partner 时，CCT 中唯一定位 Partner 合同与订单 Operating Entity 关联记录的稳定标识
partner_share_version_id|合作伙伴分成版本标识|PARTNER来源时固定的CCT分成版本，与平台服务费版本成组非空
payload_json|事件载荷 JSON|补偿事件执行所需的最小非敏感结构化载荷，不超过 4 KiB UTF-8 JSON Object，且不得包含 Vault Token
payment_intent_id|支付意图标识|Payment Orchestration 为订单创建的稳定支付意图标识
phone_country_code|电话国家代码|消费者电话号码规范化后的国家或地区呼叫代码分析维度
phone_vault_token|电话 Vault Token|指向 Vault 中消费者 Phone 原值的可选稳定不可枚举 Token
platform_charge_version_id|平台服务费版本标识|PARTNER来源时固定的CCT服务费版本，与分成版本属于同一Partner合同
platform_service_party_id|平台服务法律主体标识|支付准备从 Partner Agreement 唯一 PLATFORM_SIGNATORY 解析并冻结的法律 Party 稳定标识；Finance 后续映射本地 Finance Entity
preparation_command_id|支付准备命令标识|同一Order的在途或已完成准备命令，跨幂等键重试必须复用，保证固定后不换OE
presented_at|合同呈现时间|不可变消费者合同文件完成呈现的业务时刻
privacy_notice_content_hash|隐私声明内容哈希|订单法律上下文冻结的隐私声明文件内容哈希
privacy_notice_document_id|隐私声明文档标识|订单法律上下文冻结的隐私声明业务文档标识
privacy_notice_file_id|隐私声明文件标识|订单法律上下文冻结的隐私声明不可变文件标识
process_status|命令处理状态|幂等命令当前可恢复处理阶段的生命周期状态
product_type_code|产品类型代码|订单商品服务所属产品类型目录代码
proxy_flag|代理网络标志|网络情报判断当前 IP 是否使用代理；1 表示是，0 表示否，NULL 表示未评估
public_address_text|公开地址文本|订单法律卖方对消费者展示的注册地址或营业地址
public_legal_name|公开法律名称|订单法律卖方对消费者展示的完整法律名称
public_origin|公开来源站点|服务端 allowlist 选定并由会话冻结的无路径 HTTPS Origin，用于组合 Checkout URL
public_registration_number|公开注册编号|订单法律卖方对消费者展示的机构注册编号
public_support_email|公开支持邮箱|订单法律卖方对消费者展示的客户支持邮箱
public_support_phone|公开支持电话|订单法律卖方对消费者展示的可选客户支持电话
public_tax_registration_number|公开税务登记号|订单法律卖方对消费者展示的可选税务登记编号
public_token|公开 Token|大小写敏感的 Hosted Checkout 最小能力 Token，仅用于受限 Checkout 页面动作
quantity|数量|订单商品服务项目采用的冻结计量数量
reason_code|原因代码|退款领域事实采用的受控原因代码
reason_summary|原因摘要|不含敏感信息的退款原因简要说明
recovery_stage|恢复阶段|命令中断后继续执行时采用的逐操作恢复阶段代码
refund_amount|退款金额|本退款请求要求原支付渠道退还给消费者的确定金额
refund_policy_content_hash|退款政策内容哈希|订单法律上下文冻结的退款政策文件内容哈希
refund_policy_document_id|退款政策文档标识|订单法律上下文冻结的退款政策业务文档标识
refund_policy_file_id|退款政策文件标识|订单法律上下文冻结的退款政策不可变文件标识
refund_request_id|退款请求标识|Commerce 退款请求聚合的内部唯一标识
refund_request_no|退款请求编号|供接口响应、查询与人工操作使用的唯一退款编号
refund_status|退款状态|退款请求当前的闭合业务生命周期状态
refundable_base_amount|可退款基准金额|订单成功支付后允许退款计算的冻结金额基准
refunded_amount|已退款金额|订单已经确认成功退款的累计金额
rendered_content_hash|呈现内容哈希|呈现给消费者的合同文件内容完整性哈希
rendered_file_id|呈现文件标识|Platform Support 保存的不可变消费者合同文件标识
request_hash|请求哈希|幂等操作首次请求规范化后的完整性哈希
request_hash_version|请求哈希版本|用于计算幂等语义请求哈希的固定算法版本
request_id|请求追踪标识|Checkout 一次网络证据采集请求的稳定去重与追踪标识
request_origin|请求来源|区分 Client 与内部 Operations 发起退款的闭合来源
request_snapshot_json|请求快照 JSON|在业务 Root 建立前用于恢复 Create Checkout 或 Create Refund 的已验证最小语义快照，最多 16 KiB UTF-8
request_snapshot_schema_version|请求快照模式版本|request_snapshot_json 所遵循的受控语义快照模式版本
reserved_amount|预占金额|尚未完成或释放的退款请求占用金额
resource_id|资源标识|幂等命令成功创建或作用到的本库业务资源标识
resource_no|公开资源编号|幂等命令冻结的大小写敏感公开业务资源编号
resource_type|资源类型|幂等命令返回资源所属的 Commerce 资源目录类型
response_body_json|响应正文 JSON|稳定幂等回执的受控 JSON 正文，最多 4 KiB UTF-8；Create Checkout 回执中的 Checkout URL 具有访问能力，仅用于原命令恢复和重放，禁止进入普通日志、MQ、Webhook 或通用审计
response_code|响应代码|幂等命令可重放响应采用的稳定业务结果代码
response_http_status|响应 HTTP 状态|稳定幂等回执冻结的 HTTP 状态码
response_location|响应 Location|稳定创建回执冻结的大小写敏感相对 Location 值
response_snapshot_schema_version|响应快照模式版本|response_body_json 所遵循的受控回执模式版本
risk_checkout_assessment_id|Checkout 风险评估标识|Risk & Compliance 返回且由订单上下文冻结的不可变评估标识
rounding_amount|舍入调整金额|订单金额计算为保持币种精度而冻结的舍入差额
scheduled_at|预计可用时间|Finance 冻结的本订单余额可用时刻，尚未完成经济计算时为空
seller_disclosure_id|卖方披露标识|按订单 consumer_country_code 与 locale 精确命中并冻结的 CCT 卖方公开披露标识，禁止隐式 Locale 回退
session_status|会话状态|Hosted Checkout 会话当前的闭合生命周期状态
sort_no|排序序号|保持订单商品服务项目原始呈现顺序的稳定序号
status_reason_code|状态原因代码|当前终态原因采用的稳定业务代码，适用于取消、失败、过期等需要说明的结果；没有终态原因时为空
sub_event_type|补偿子事件类型|同一补偿 Handler 下区分具体业务子动作的大小写敏感类型
successful_attempt_id|成功支付尝试标识|最终形成订单成功支付结果的支付尝试标识
tax_amount|税额|订单创建时冻结的消费者实收税额汇总金额
tax_category_code|税务分类代码|订单商品服务在税务目录中的分类代码
termination_requested_at|终止请求时间|取消或到期首次请求的UTC时间，串行阻止新副作用；不表示远端未知Intent已终止
tor_flag|Tor 网络标志|网络情报判断当前 IP 是否属于 Tor 网络；1 表示是，0 表示否，NULL 表示未评估
total_amount|订单总金额|消费者最终应付的订单冻结总金额
trusted_proxy_chain|可信代理链|可信入口解析得到的可选代理链原文，仅记录已验证代理转发路径
unit_amount|单价|订单商品服务项目在创建时冻结的单位金额
update_time|更新时间|数据库记录最近一次持久化更新的 UTC 微秒精度时间
user_agent|用户代理原文|可信入口采集的完整 User-Agent 原文，用于保留该次 Checkout 的网络证据
version|乐观锁版本|可变业务行并发更新使用的单调乐观锁版本
vpn_flag|VPN 网络标志|网络情报判断当前 IP 是否使用 VPN；1 表示是，0 表示否，NULL 表示未评估
`;

function parseFieldMetadataSource(source) {
  const result = {};
  for (const rawLine of source.split(/\r?\n/u)) {
    const line = rawLine.trim();
    if (!line) continue;
    const parts = line.split("|");
    if (parts.length !== 3) {
      throw new Error(`Commerce 字段元数据格式错误：${line}`);
    }
    const [fieldKey, defName, description] = parts;
    if (result[fieldKey]) {
      throw new Error(`Commerce 字段元数据重复：${fieldKey}`);
    }
    if (!/[\u3400-\u9fff]/u.test(defName) || !/[\u3400-\u9fff]/u.test(description)) {
      throw new Error(`Commerce 字段元数据缺少明确中文语义：${fieldKey}`);
    }
    result[fieldKey] = metadata(defName, description);
  }
  if (Object.keys(result).length !== 193) {
    throw new Error(`Commerce 字段元数据必须精确覆盖 193 个唯一字段键，实际 ${Object.keys(result).length}`);
  }
  return Object.freeze(result);
}

export const FIELD_METADATA = parseFieldMetadataSource(FIELD_METADATA_SOURCE);

/** 闭合状态与类型必须在字段注释中列全，顺序也是已批准契约的一部分。 */
export const CLOSED_ENUM_VALUES = Object.freeze({
  "commerce_order.entity_source_type": ["PLATFORM", "CLIENT", "PARTNER"],
  "commerce_order.order_status": ["CREATED", "OPEN", "PAID", "CANCELLED", "EXPIRED"],
  "order_payment_projection.intent_status": ["REQUIRES_PAYMENT", "PROCESSING", "REQUIRES_ACTION", "SUCCEEDED", "FAILED", "EXPIRED", "CANCELLED"],
  "order_payment_projection.last_attempt_status": ["CREATED", "RISK_CHECKING", "RISK_REJECTED", "RISK_FAILED", "LIMIT_REJECTED", "ROUTING", "PROCESSING", "REQUIRES_ACTION", "UNKNOWN", "SUCCEEDED", "FAILED", "SUCCESS_CONFLICT"],
  "order_funds_availability_projection.funds_availability_status": ["NOT_APPLICABLE", "PENDING", "AVAILABLE", "FULLY_OFFSET"],
  "checkout_session.session_status": ["PREPARING", "OPEN", "PAYMENT_PROCESSING", "COMPLETED", "CANCELLED", "EXPIRED"],
  "checkout_network_evidence.evidence_scene": ["CHECKOUT_OPEN", "CONSUMER_CONTEXT_SUBMIT", "LEGAL_ACCEPTANCE", "PAYMENT_START"],
  "checkout_network_evidence.device_type": ["DESKTOP", "MOBILE", "TABLET", "BOT", "OTHER", "UNKNOWN"],
  "consumer_acceptance.acceptance_type": ["CONSUMER_TERMS", "REFUND_POLICY", "PRIVACY_NOTICE"],
  "refund_request.request_origin": ["CLIENT", "OPERATIONS"],
  "refund_request.refund_status": ["PENDING", "PROCESSING", "SUCCEEDED", "FAILED", "CANCELLED"],
  "command_idempotency.recovery_stage": ["CLAIMED", "ORDER_COMMITTED", "PAYMENT_PENDING", "REFUND_COMMITTED", "PAYMENT_REFUND_BOUND", "COMPLETED"],
  "command_idempotency.process_status": ["PROCESSING", "SUCCEEDED", "FAILED"],
  "compensation_event.execute_status": ["INIT", "PROCESSING", "ERROR", "FAILED"],
  "commerce_order.context_format": ["TERM_PRICE", "LEGACY_SCOPE"],
});

/** 开放代码的权威来源；这些字段不是可在模型内封闭的 Java 枚举。 */
export const OPEN_CODE_SOURCES = Object.freeze({
  "order_item.product_type_code": "Product Type Catalog",
  "order_item.tax_category_code": "Tax Category Catalog",
  "commerce_order.status_reason_code": "Commerce Order Status Reason Catalog",
  "checkout_network_evidence.network_type": "Network Type Catalog",
  "refund_request.reason_code": "Commerce Refund Reason Catalog",
  "refund_request.status_reason_code": "Refund Status Reason Catalog",
  "command_idempotency.operation_type": "Commerce Command Operation Catalog",
  "command_idempotency.resource_type": "Commerce Command Resource Type Catalog",
  "command_idempotency.response_code": "Commerce Command Response Code Catalog",
  "compensation_event.event_type": "Commerce Event/Handler Catalog",
});

/**
 * 表级说明显式固定关键不变量；生成器不得从表名或字段名推测约束。
 */
export const TABLE_COMMENTS = Object.freeze({
  "commerce_order": "基础创建时固定商品净额/币种/期限；支付准备选择提交后固定OE，Context建立时一次补齐税额和最终金额；context_format显式区分新旧流程，不延长到期或跨OE恢复。",
  "order_context": "支付准备时一次建立的不可变上下文；外部事实只保存稳定引用；保存CCT批准Scope/Affiliation/Source及固定Term Version，准确Price由完整成功支付事实解析；三份接受证据及原始Context不得覆盖；PARTNER的分成版本/服务费版本/平台Party三项成组非空且Order的local_partner_id非空，非 PARTNER三项成组为空且local_partner_id为空。",
  "order_legal": "固定Client/OE合同、可选Partner合同关联和三份独立法律文件；Client不再新写Agreement-OE关联，PARTNER成组引用保持。",
  "order_item": "创建时固定不可变商品/优惠/净额；新流程最终逐行税额和总额由Finance不可变Tax Snapshot按client_item_id读取，不先写假税额再更新。",
  "order_payment_projection": "Payment 订单支付事实的本地可重建投影；只按来源版本和事件幂等键推进，不拥有 Payment 聚合。",
  "order_funds_availability_projection": "Order-owned 资金可用性投影；只由 Payment 与 Finance 权威事件推进，Commerce 不按本机时钟宣布成熟，最终状态不因 Refund、Payout 或当前余额变化回退。",
  "order_refund_position": "订单可退款金额的强一致本地位置；始终满足 reserved_amount + refunded_amount <= refundable_base_amount，预占、消费和释放必须通过乐观锁原子更新。",
  "checkout_session": "Hosted Checkout 重定向会话；公开 Token 以大小写敏感明文和冻结 Public Origin 保存，状态变更使用乐观锁，网络事实写入独立不可变证据表。",
  "consumer_context": "订单当前消费者上下文；consumer_reference 是 Client 命名空间内的非 PII、不透明业务引用，不得提交 Email、Phone、证件号等个人原值；Email、电话、姓名和完整账单地址仅以分用途 Vault Token 保存，Commerce 保留受控派生分析维度。",
  "consumer_contract": "呈现给消费者的不可变合同文件事实；文件标识、内容哈希、Locale 与呈现时间共同封存。",
  "checkout_network_evidence": "不可变 Checkout 网络与设备证据；Commerce 直接保存可信解析的完整客户端 IP、完整 User-Agent 和可选可信代理链，以及批准的网络和设备派生维度；不保存 Vault Token、盲索引、密钥版本或设备标识。",
  "consumer_acceptance": "消费者对特定法律文档类型的不可变接受证据；不维护 Acceptance Summary，Payment Readiness 依据三种必需接受类型的不可变记录实时判定。",
  "refund_request": "Commerce 拥有的退款聚合；只保存退款业务状态、订单金额位置所需金额以及 Payment Channel Refund 的稳定标识，不复制下游状态、事件版本、事件键、载荷哈希或渠道执行明细。",
  "command_idempotency": "Commerce 同步命令幂等与恢复记录；相同 caller_scope + operation_type + idempotency_key 必须校验 request_hash，Claim Lease 由 lock_owner 与 lock_until 保护。",
  "compensation_event": "业务变更同事务写入的本地可靠执行记录；身份固定为 event_key、event_type、sub_event_type，PROCESSING 必须同时持有 lock_owner 且 lock_until 晚于当前时间，XXL-Job 仅负责唤醒和分片扫描。",
});
