# Client & Contract Service

client-contract-service 是 Client & Contract 核心服务的独立 Maven Reactor。默认仍启动运维基础；提供标准 `spring.datasource.url` 后装配 CCT 共享持久化及私有 Checkout 查询，查询使用只读事务。

本期查询范围：

- `POST /checkout-context/v1/resolutions`：本地按优先级检查合同、条款版本和法律证据，并在外部资格之后复核相关 Root 和准确候选。Risk 的新身份资格接口尚未接入，默认资格端口返回 `503 OWNER_UNAVAILABLE`，没有跳过资格或默认通过配置。因此不能把本期源码称为最终支付准备已可联调成功。

创建 Checkout 时调用完整解析，Commerce 在本地创建事务中冻结 OE 和商业事实；调用时机遵循 [2026-09-19 决定](../requirements/20260919/commerce-Checkout准备与Intent创建拆分/requirement.md)。接口分层、Java 类型兼容和准确候选复核见 [解析重构说明](docs/requirements/20260921-checkout-context-resolution/design.md)。

完整解析接口只读，不创建 Assignment、订单、补偿事件或解析回执；不提供 Scope 激活、签署命令或数据维护入口。请求及结果字段见 `client-contract-api` 的 `ResolveCheckoutContextRequestBody` / `ResolveCheckoutContextResponseBody`，合同见 [实现切片](../requirements/20260911/client-contract-OE与Client模型梳理/cct-query-implementation.md)。

独立基础预检 `CheckoutEligibilityApi` 及其 DTO、实现和专用查询已删除，旧 `GET /checkout-eligibility/v1/precheck` 返回 404。现行创建流程只使用完整解析。Commerce 源码仍有未参与创建流程的旧适配器和测试类型引用，升级 CCT API JAR 前需在 Commerce 清理；本次仅修改 CCT，不提供旧 API 兼容壳。

模型与完整人工 DDL 分别为 [client-contract.pdma.json](docs/database/client-contract.pdma.json) 和 [client-contract-schema.sql](docs/database/sql/client-contract-schema.sql)。应用和测试均不会建表或执行初始化 DML。运行及验证状态以 [当前实现台账](../agent-docs/CURRENT_IMPLEMENTATION.md#cct-query-20260911) 为准。

## 模块

- client-contract-api：轻量内部 HTTP 契约及 String Wire DTO、错误 Code。
- client-contract-web：Spring MVC 应用、条件启用的只读查询、领域规则及本库映射。
- client-contract-test：通过随机端口真实 Server 验证生产应用的 auto 测试；跳过 install 和 deploy。

依赖方向固定为 test -> web -> api，生产模块不得依赖 test。

## 前置条件

- Java 25。
- 仓库内 Maven Wrapper；它下载并校验 Maven 3.9.16。
- 开发者 Maven Settings/Nexus 能解析 com.paytech:paytech-parent:1.0.0-SNAPSHOT 和 paytech-mvc-web-starter。

默认运维基础不连接业务数据库。启用 Checkout 查询需要人工准备本地 MySQL、Schema 和连接配置；不引入 Nacos、Redis、RabbitMQ 或 XXL-Job。

## 构建与验证

```bash
# 先按审批后的说明准备本地 mor_cct Schema，并安全注入 CCT_TEST_DB_PASSWORD。
./mvnw clean verify
```

Web 模块同时生成普通依赖 JAR 和可执行的 client-contract-web-1.0.0-SNAPSHOT-exec.jar。Test 模块只保存测试证据，不生成可部署 JAR。

## 本地运行

```bash
java -jar client-contract-web/target/client-contract-web-1.0.0-SNAPSHOT-exec.jar
```

默认应用端口为 18101。启动后可检查：

```bash
curl --fail --silent http://127.0.0.1:18101/actuator/health
curl --fail --silent http://127.0.0.1:18101/actuator/health/liveness
curl --fail --silent http://127.0.0.1:18101/actuator/health/readiness
```

Actuator discovery 已关闭，健康详情不会对外披露，管理端点与应用共用监听器。当前没有 Gateway 公共路由。

## 查询配置与私有边界

数据库使用 `spring.datasource.url`、`spring.datasource.username`、`spring.datasource.password`，连接池使用 `spring.datasource.hikari.*`；凭据从受控环境注入。未提供 URL 时不装配持久化和查询入口。已删除 Checkout 专属数据库参数、开关及国家/币种/Locale 部署白名单，旧 `checkout.*` 配置不再生效，已有环境需迁移到标准配置。

国家和币种准确匹配已批准的 OE Scope，Locale 准确匹配批准且适用的法律文件和卖方披露。格式错误返回 `INVALID_CHECKOUT_QUERY`；格式合法但没有完整候选时返回 `NO_ELIGIBLE_ENTITY`，不作国家、币种或语言回退。

只允许可信 Commerce 私有调用，`X-Caller-Service: commerce-service` 仅防止调用范围误用，不能认证调用方；部署网络必须限制为授权 Commerce。Commerce 负责从可信上下文取得 Client/Order 归属，不能把浏览器任意 Client ID 转发为权限依据。本期没有注册 Gateway 公共 Route 或发布服务。

CCT 自动测试仅保留 `CheckoutContextApi#resolve` 的 HTTP 场景，目录与执行方式见[测试模块说明](client-contract-test/README.md)。Checkout 使用 `application-auto.yml` 和 `application-checkout-auto.yml`，只允许仓库 `127.0.0.1:13306/mor_cct`；固定账号 `cct_app`，密码通过 `CCT_TEST_DB_PASSWORD` 安全注入。`ResolveCheckoutHttpTest` 只创建和清理自己成功插入的合成 Fixture；遇到现有同键数据直接失败，不先删除。Schema 缺失时测试失败并提示人工准备步骤。测试通过仅证明保留的解析场景。

暂不执行数据库写入时，可单独运行真实 HTTP 入站校验；需要连接本地 MySQL，但不查询或写入业务数据：

```sh
./mvnw -Dtest=ResolveCheckoutValidationTest -Dsurefire.failIfNoSpecifiedTests=false verify
```

## Client 工作区恢复

`onboarding.recovery-enabled` 默认 false。显式开启需要已有可用 CCT 数据库、`compensation.enabled=true` 及已分配的 `onboarding.id.datacenter/worker`；与 Checkout 共用一个 DataSource/事务管理器，Checkout 查询仍使用只读事务。运行时包含实际使用的 Foundation Compensation、HTTP/JCS 和 Platform API 依赖；应用不创建 Schema。

恢复只处理 CLIENT 生命周期，不读取或保存资料历史。任务引用原始审计明细，提交后调用 Platform PLT-I17，超时后先查询结果，冲突或重试耗尽保留 FAILED。来源与 Hash 的精确合同见[恢复说明](../requirements/20260914/client-console-注册与客户入驻/cct-workspace-recovery-contract.md)。正常 HTTP 使用 Nacos/LoadBalancer；loopback 测试直连只允许 auto Profile。

实现及历史验证状态见[当前台账](../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。当前测试集合不包含 Client 工作区恢复场景。

## 本期未包含

Risk 新资格 Owner 的 HTTP 接入、最终解析成功的完整链路、数据维护/Scope Review/签署写入、定价解析接口、Commerce 接入、数据库迁移、公共入口与部署均不在本切片完成声明中。后续配置写入口必须按已批准合同推进 Client/OE/Agreement Root，不能绕过输出前修订复核。Source Relation 保留当前模型；历史 V5 合同没有被这两条新查询替换或删除。

## 内部服务发现

Web 通过项目统一的 Nacos Discovery 注册为 `client-contract-service`，使用 `NACOS_SERVER_ADDR` 和
`NACOS_NAMESPACE`。Commerce 通过这个服务名调用轻量 API JAR 中的 `@HttpExchange` 合同。
这不启用 Nacos Config，也不替代标准数据源配置。自动测试显式禁用 Nacos Discovery，
服务发现联调与部署状态只在 [当前状态台账](../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-precheck-20260911) 维护。


### Client 当前草稿组件

显式配置 `onboarding.draft-enabled=true` 后提供创建/恢复、Owner 主资料查询和增量保存，私网接口见 `ClientDraftApi`。同时需要现有 CCT 数据源、`onboarding.recovery-enabled=true`、Foundation Redis 协调及 Vault V1；不自动建表或注册公共路由。个人/组织共用一个账号未完成申请占位，KYC/KYB 类型与 Risk 词表一致。创建/保存返回固定命令回执，当前表单另行查询；普通保存没有资料版本或表单历史。详细语义见[需求接口合同](../requirements/20260914/client-console-注册与客户入驻/api-contract.md#7-c04c05c06-的-cct-私网组件)。

组织人员与关系使用 `ClientPeopleApi`，共用上述开关与会话/申请授权。新增人员及首条关系原子保存；人员增量编辑、追加/修改/移除关系返回原命令结果，当前列表由申请 GET 查询。共享批准资料只读，移除关系保留人员；详见[人员与关系合同](../requirements/20260914/client-console-注册与客户入驻/api-contract.md#8-c07c11-的-cct-私网组件)。实现、验证与未完成范围统一见[当前实施台账](../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。

材料引用使用同开关下的 `ClientDocumentApi`：只关联 Risk 真实要求与 Platform 核验的原申请文件，查询由 C05 返回当前引用，删除不删除文件。国家口径、固定回执、关联人员移除和新错误处理见[材料 Owner 合同](../requirements/20260914/client-console-注册与客户入驻/api-contract.md#10-c12c13c05-材料的-owner-组件)。生产通过私网服务发现调用 `risk-service` 与 `platform-support-service`；`onboarding.risk-test-base-url` 仅接受 auto Profile 的 loopback HTTP 根地址，不能用于正式配置。Owner 网络调用均在本地事务外。

当前 `./mvnw clean verify` 仅执行 Checkout 完整解析的 HTTP 测试，需要仓库本机 MySQL，并通过安全环境变量提供 `CCT_TEST_DB_PASSWORD`；测试不读取 `.env.local`。入驻测试及其 Platform/Vault/Risk Simulator 已移除，当前集合不能证明入驻、多 Owner 或浏览器闭环。


## 入驻表单目录

C03 使用同一入驻开关与当前会话门禁，配置为 `onboarding.form-catalog`，默认留空。组织类型按注册国筛选，证件签发机构使用自己的准确国家，不从居住国推导；配置形状、上限与更新方式见[接口合同第 12 节](../requirements/20260914/client-console-注册与客户入驻/api-contract.md#12-c03-受控表单目录)。API/Web 显式声明 provided Lombok 与编译期注解处理器，以支持当前独立 RequestBody/ResponseBody 类约定；不进入运行制品。C03 不创建申请或永久身份键，不替代正式提交校验。
