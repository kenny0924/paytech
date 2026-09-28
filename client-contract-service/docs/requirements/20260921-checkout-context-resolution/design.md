# Checkout 商业上下文解析重构

本次仅修改 CCT 的 API/Web/Test 和本服务说明，新增 HTTP 接口、表、字段、索引、DDL/DML 和页面均为 0；删除独立基础预检 HTTP 接口。保留工作区已有修改，不修改 Commerce、Risk 或共享规范，不提交、发布或部署。

用户在首次重构后进一步要求删除混杂职责的 CheckoutQueryProperties、补齐真正的业务上下文，并明确确认国家/币种/Locale 支持范围以已批准业务数据为准。本轮继续按用户要求拆除聚合 Repository，将跨表加载交还 Service。本文替代本目录首次重构中依赖部署目录、CheckoutSelection 和聚合 Repository 的设计描述。

业务合同读取 [四系统合同](../../../../requirements/20260911/client-contract-OE与Client模型梳理/stage3-contracts.md)的最终解析部分；调用时机遵循 [9 月 19 日创建初始化决定](../../../../requirements/20260919/commerce-Checkout准备与Intent创建拆分/requirement.md)。本次确认的支持范围规则替代早期查询切片中的三项部署白名单，不恢复独立 preparation 阶段、Assignment 或解析回执。

## 配置和支持范围

删除 CheckoutQueryProperties 及混合职责的 CheckoutQueryConfiguration：

- ClientContractPersistenceConfiguration 装配 CCT 共用 DataSource、MyBatis 和事务管理器，复用 Spring DataSourceProperties；连接使用 spring.datasource.*，连接池使用 spring.datasource.hikari.*。
- ClientContractWebConfiguration 处理应用级 JSON、时钟和既有 HTTP 错误映射。
- ClientContractRepositoryConfiguration 按表装配本查询实际使用的 23 个 Repository，每个实现只依赖同表 Mapper。
- CheckoutResolutionConfiguration 显式导入单表仓储配置，并装配本查询的只读 Service、Processor 和资格端口；独立加载查询配置也能取得同一组仓储。

未提供 spring.datasource.url 时仍只启动运维基础；配置 CCT 数据源后提供私有查询，不再有独立的 checkout.enabled。入驻与查询共用数据源，入驻不再依赖 Checkout 开关。旧 checkout.jdbc-url/username/password 必须迁移到标准数据源键；不设置默认业务数据库地址或凭据。

删除国家/币种/Locale 的部署白名单，不以硬编码目录或另一份配置替代。输入继续做规范身份、代码格式、Locale 和时间校验。国家、币种和 Client 分类必须命中批准的准确 Scope；Locale 必须匹配三份法律文件及卖方披露。格式合法但业务不支持时返回 422 NO_ELIGIBLE_ENTITY；格式错误保持 422 INVALID_CHECKOUT_QUERY。空候选在调用 Risk 前结束，存在完整候选仍必须通过真实 Risk 资格。

## 上下文和主流程

CheckoutResolutionProcessor 继续使用 Foundation AbstractProcessor.process，拒绝调用方事务并验证内部请求。主方法顺序为：

1. 本地只读事务取得事实，构造 CheckoutResolutionContext。
2. 按授权优先级取得完整本地候选；无完整候选直接业务拒绝。
3. 事务外检查 Client 资格，再逐候选检查准确 OE/Partner 资格。技术失败立即传播，明确候选不合格才继续。
4. 在新的本地一致读事务中，复核原查询、原候选和所有相关 Root。
5. 从已复核候选构造多表查询 CheckoutContextView，Controller/Converter 转换为 HTTP 响应。

CheckoutResolutionContext 直接持有原 Client 及同一次读取的授权、OE、Scope、来源、合同、条款、法律资料和 Root 对象集合，没有 Repository、Bean、事务对象或回调。它封装本地准入、候选构造、共签证据和一致性判断，替代静态 CheckoutCandidateRules 与只有结果和 Map 的 CheckoutSelection。

CheckoutCandidateContext 一次构造完整，持有原查询、Client、Primary、准确授权/OE/来源/Scope、Client 合同与条款、Partner 合同与双条款、三份法律文件、卖方披露、参与方资料和相关 Root。资格调用只取 OE/Partner 身份，后续复核和输出沿用这些对象，不逐层补字段或提前组装响应。CheckoutPricingContext 持有原计划与不可变版本，集中验证该版本的 Price/Component/Band/Profile 配置。

候选按需验证，低优先级合同错误不会提前影响已胜出者。Root 使用原记录集合比较，不依赖数据库返回顺序；原查询也必须一致，另一订单的候选不能与当前上下文拼接。当前准入精确检查原 OE、Affiliation ID/优先级、Scope ID 和 Source Relation ID，原授权或范围到期后不能以同 OE 后继记录代替。

CheckoutQueryService 保持 interface + Impl，只负责本地一致读取和复核，不调用其他 Service/Processor 或外部依赖。读事务保持 REQUIRED、REPEATABLE READ、只读和有界超时；Processor 入口没有事务，两个读事务之间调用外部资格。SQL 的筛选、排序及数据库列映射保留，不改变 Schema。

删除 CheckoutQueryRepository / CheckoutQueryRepositoryImpl 及跨表 CheckoutQueryMapper。23 张表各自拥有 Repository interface + Impl、Mapper interface 和 XML；每个仓储只返回自己表的对象，不加载其他仓储、不接收 Context、不管理事务。原有用于限定 Client 查询范围的本库子查询保留。仓储拆分时原 24 条 SELECT 及 23 份字段映射内容一致；本轮删除预检独占的 PartyProfileMapper.findByClientId，完整解析使用的 23 条 SELECT 及字段映射不变。Client 主键读取返回单个对象，其余集合继续保持不可变。

CheckoutQueryServiceImpl 仅保留 load / verify，两者通过同一个 loadContext 加载全部准入事实、合同、合同参与方资料、定价、法律披露和 Root。所有读取都加入该次 Service 的同一只读事务；不再构造空合同集合的预检 Context。Service 直接用具名 Builder 一次构造 CheckoutResolutionContext，删除 DAL 中的多表 CheckoutRecords.Snapshot 和原 complete 布尔开关，不新增中间聚合仓储或同义数据副本。

本接口没有本地写入、资源锁、提交后动作、请求幂等或重放记录。CCT 只负责解析，Commerce 在自己的创建事务中冻结 OE；结果不是跨服务原子快照，后续支付仍执行新鲜阻断检查。

## HTTP 和 Java 兼容

继续使用 POST /checkout-context/v1/resolutions 和 X-Caller-Service: commerce-service。Header 只限制调用范围，真正身份依赖既定私有网络。完整解析的方法、路径、六个输入字段和成功响应字段不变；支持范围改变的错误语义见上文。

用户本轮确认现行创建流程不再需要独立预检：删除 CheckoutEligibilityApi / CheckoutEligibilityModels、Controller.precheck、Service.precheck、CheckoutQuery.precheck、Context.precheck 和专用 Party Profile SQL；旧 GET 路由返回 404。Controller 只依赖 Clock 和 CheckoutResolutionProcessor，参数拦截器仅处理完整解析的 JSON Body 入口。Client、分类、Affiliation、Scope、来源、批准证据等规则继续由完整解析执行，不因删除接口放宽准入。

Commerce CreateCheckoutProcessor 已调用 CheckoutCommercialPort.resolve，未再调用 CctPrecheckClient；其旧适配器、装配配置、Mock 和响应 Builder 仍引用被删除的 API 类型。删除属于 Java API 不兼容变更，Commerce 升级 CCT API JAR 前必须清理这些旧源码。本轮没有修改 Commerce、发布 API JAR 或部署服务；不宣称跨系统升级完成。

请求/响应为独立 class：ResolveCheckoutContextRequestBody / ResolveCheckoutContextResponseBody，不恢复原 CheckoutContextModels 嵌套 record。Java 签名调整需要消费者使用新 API JAR 编译；仓库检索未发现 CCT 外引用原嵌套类型的 Java 消费者。本次不发布制品。

输入商业时点原样回显，尚未完成创建的同次重试保留原值，订单提交后不重选 OE。CCT 不接受请求方指定 OE/MID/行业/MCC/支付方式。ID 保持字符串，优先级为整数，非 Partner 分支保持 null；金额、税务、最终 Price 及卖方文件正文仍由原 Owner/合同处理。

## 未接入边界与验证

Risk 尚无符合新 entityNo 的 Checkout 资格 API，正式不可用端口继续返回 503 OWNER_UNAVAILABLE；不自定义 Risk HTTP 合同、不以入驻资格代替、不注入成功替身。因此不能声明最终解析 HTTP 200 或 Commerce 创建闭环完成。配置写入推进 Root 的合同保持，本次不新增管理写入口。

先用真实 HTTP/MySQL 回归复现旧白名单错误拒绝批准业务数据，再验证范围扩展、缺失 Locale、原证据到期、跨查询混用候选、事务入口保护和查询只读；全量测试覆盖数据源迁移影响的入驻及补偿能力。实际结果见 [verification.md](verification.md)。全局实现/部署状态仍以 [当前台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准，本次不修改共享文件。
