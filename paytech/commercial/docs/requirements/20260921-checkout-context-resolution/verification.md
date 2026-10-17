# Checkout 解析重构验证记录

验证日期：2026-09-21。本记录只说明本次 CCT 组件变更及本机验证，不代表 Risk/Commerce 联调、制品发布或部署完成。最终设计及配置迁移见 [design.md](design.md)。

## 环境和构建边界

使用仓库 Maven Wrapper、JDK 25，以及既有本机 MySQL 127.0.0.1:13306/mor_cct 和 Redis。未建表、迁移 Schema 或初始化业务数据。测试仅插入和清理自己成功创建的合成数据。

用户已明确授权本次读取 local-environment/.env.local 中的 CCT 数据库和 Redis 密码，仅注入测试进程的 CCT_TEST_DB_PASSWORD / CCT_TEST_REDIS_PASSWORD，不打印、保存或修改凭据文件；该授权不是后续自动读取凭据的通用许可。测试进程还显式设置本机 CCT_TEST_DB_URL 和 CCT_TEST_DB_USERNAME=cct_app；密码不进入命令行。

原工作区已有 ClientTenantProjectionClientImpl 的 Platform API 类型迁移，本机缓存 API JAR 缺少对应类型，独立构建先在该既有代码处编译失败。本次将当前 Platform API 源码和父 POM 复制到临时目录，与指向 CCT 的链接组成 Reactor；没有修改其他系统源码，没有执行 Maven install/deploy 或刷新缓存制品。下述成功结果基于该临时 Reactor，旧缓存 JAR 的独立构建问题仍需由依赖 Owner 提供匹配版本。

## 仓储拆分阶段的执行结果（删除预检之前）

在安全注入环境变量后，从 CCT 根目录执行以下命令，CCT_VERIFY_REACTOR 指向本次临时聚合目录：

```sh
./mvnw -f "$CCT_VERIFY_REACTOR/pom.xml" -q clean verify
```

该阶段临时目录为 /var/folders/t2/xvr6hr7x4yq2c64fx_216r8c0000gn/T/cct-resolution-build-0aqut4n7。Maven 退出码为 0；该阶段 Surefire 汇总为 **159 tests / 0 failures / 0 errors / 0 skipped**。日志位于本机 /tmp/cct-table-repositories-verify.log，原始报告位于 client-contract-test/target/surefire-reports/；这些临时文件不是仓库持久工件。

| 验证范围 | 用例数 | 结果 |
| --- | ---: | --- |
| CheckoutResolutionContextAutoTest | 16 | 通过；候选顺序、合同/法律/定价、Partner、到期原引用 |
| CheckoutQueryContractAutoTest | 3 | 通过；独立 class 请求的六字段合同 |
| CheckoutQueryHttpAutoTest / CheckoutQueryHttpBoundaryAutoTest | 14 | 通过；真实 HTTP、MySQL 及协议边界 |
| CheckoutResolutionAutoTest | 11 | 通过；批准数据决定支持范围、无候选、跨订单上下文、事务保护和只读 |
| 其余 CCT 启动、配置、草稿、投影和补偿 | 115 | 通过；包含数据库配置迁移后的入驻链路 |

## 单表仓储拆分验证（删除预检之前）

本轮是持久化职责重构，沿用已覆盖行为的入口回归，不增加逐个 Repository 方法的镜像测试。先执行 Checkout 相关 44 项测试，Maven 退出码为 0，日志为 /tmp/cct-table-repositories-test.log；随后执行上述 clean verify。第一次全量运行发现投影恢复的独立配置上下文未引入新增仓储配置，导致 17 项在启动时失败；已由正式 CheckoutResolutionConfiguration 显式导入 ClientContractRepositoryConfiguration，保留原测试装配入口后重新执行全量验证。失败日志为 /tmp/cct-table-repositories-wiring-failure.log，最终结果以上表为准。

拆分前后逐条比对 24 条 SELECT 的 SQL 文本及 23 份 resultMap，内容一致；23 个新增 RepositoryImpl 各自只依赖对应表的一个 Mapper。已删除聚合 Repository、聚合 Mapper 和 DAL Snapshot，生产与测试源码没有残留引用。SQL 子查询仍只限定本 Client 的读取范围，不改变数据库 Schema。

只读检查改为通过正式 CheckoutQueryService.load 比较全部原始事实及补偿身份，不依赖已删除的聚合仓储，也不以候选或响应投影替代原始事实检查。纯规则 Fixture 直接构造 Context。

## 失败证据与修复验证

此前支持范围重构先运行修改后的真实 HTTP/MySQL 回归：原配置白名单错误拒绝已批准的 GB/GBP/en-GB 业务数据；没有批准内容的 fr-FR 又被当成非法参数。调整后的 10 项用例中 3 项按预期失败，证据为 /tmp/cct-context-refactor-red.log。移除白名单并使用业务事实后，完整候选继续进入真实 Risk 不可用端口并返回 503；缺失对应法律内容返回 NO_ELIGIBLE_ENTITY，HTTP 和 Java 两种入口均通过。

首次重构还曾用两个规则回归复现原 Affiliation/Scope 到期后被同 OE 后继记录掩盖的问题，16 项中 2 项失败，证据为 /tmp/cct-checkout-resolution-red.log。这些测试已迁入 CheckoutResolutionContextAutoTest。首次重构为 157 项、仓储拆分为 159 项；删除预检后的最新数量与结果以本页后续记录为准。

本轮增加不同订单候选与当前上下文混用的真实数据库测试，确认返回 CCT_CONTEXT_CHANGED。原有同一查询的正常本地复核、Root 变更、OE 暂停、合同失效降级、低优先级错误隔离、Partner 双条款和原授权到期回归保持通过。不注入成功 Risk 替身，不把本地规则通过称为最终资格通过。

## 接口交付核对（完整解析仍适用）

按公共写接口示例第 11 节核对：

- API/Body 使用普通 class、中文字段合同和 Bean Validation；HTTP 与 AbstractProcessor 内部入口都有校验触发点。Controller/Converter 仅适配协议，没有数据源凭据或支持范围配置。
- Processor 明确编排读取上下文、资格检查、新读取复核和响应；不调用 Mapper，不写数据库。CheckoutResolutionContext 持有原查询及本模块原始事实；CheckoutCandidateContext 一次完整构造，贯穿资格和复核。Context 不进入 Repository，字段没有 Bean、事务或执行回调。
- CheckoutQueryService 保持 interface + Impl，通过 23 个单表仓储编排本库读取；复核是外部调用之后验证并发变化，不重复首次归属检查。两次短只读事务之间执行外部资格，Processor 已有事务的真实调用测试仍通过。
- 本接口只读，原子写入、行数/CAS、对象回写、保存回滚、资源锁和提交后动作不适用；未新增 Assignment、回执、HTTP Idempotency-Key 或旧命令幂等组件消费。订单/OE 原子冻结仍由 Commerce 负责。
- 原业务对象在复核后才生成多表查询 View，再转 ResponseBody，不外泄 DataObject。数据库列映射未改变；本次涉及的仓储及 Mapper 按表归属，多表事实由 Service 加载并直接组装 Context，DAL 不再返回聚合 Snapshot。
- 基础设施使用 Spring 标准 DataSourceProperties，没有把旧类改名为另一份混合配置。国家/币种/Locale 由批准的 Scope 和法律数据决定，不新增配置默认通过、硬编码支持目录或生产 Mock。
- 复用 Foundation Processor、Problem 和 test-scope Test Support。实际验证覆盖入口、真实数据库、配置迁移和每例清理；git diff --check、新文档相对链接及旧配置/类型源码引用检查通过。

最终解析 HTTP 200、真实 Risk 资格调用、配置写入的 Root 推进和 Commerce 创建闭环仍未覆盖。没有提交、推送、发布或部署。

## 删除独立预检：变更与基线

2026-09-21，现行 Checkout 创建入口已走完整商业解析；本轮仅删除 CCT 旧预检链路。旧 API 和 DTO 不保留兼容壳，Commerce 的旧适配器及测试源码需在升级 API JAR 前由其 Owner 清理，本轮没有修改或构建 Commerce。

先把真实 HTTP 回归改为期望旧地址返回 404，在未删除生产实现时得到明确失败：实际 200 且 eligible=true，日志 /tmp/cct-remove-precheck-red.log。删除后，保留旧路由的 404 回归，将主体批准、分类、Scope 变化、Caller、查询参数、格式校验迁到完整解析；只删除旧响应结构的反射测试。完整解析仍使用原来的 23 条 SQL，未改变 Schema、资格逻辑或事务边界。

工作区在本轮开始前已有未提交改动：CheckoutQueryController 删除了 spring.datasource.url 的 ConditionalOnProperty。本轮在改生产代码前单独运行 ClientContractApplicationAutoTest，2 项都因无数据源时缺少 CheckoutQueryService 而启动失败，日志 /tmp/cct-remove-precheck-startup-baseline.log。保留这项已有改动，删除预检依赖后无数据源场景仍会缺少 CheckoutResolutionProcessor；不通过测试替身或回滚用户修改掩盖该配置问题。

删除后执行同一临时 Reactor 的 clean verify，日志 /tmp/cct-remove-precheck-verify.log。Maven 退出码为 1；Surefire 共 **158 项，154 项通过，0 failures、4 errors、0 skipped**。其中 Checkout 五类共 **43 项全部通过**：规则 16、合同 2、HTTP 10、HTTP 边界 4、解析入口 11。旧预检地址实测返回 404，批准资料、分类、Scope、合同、准确候选及 Root 复核通过完整解析验证。

4 个错误全部来自无数据源的基础启动场景：ClientContractApplicationAutoTest 2 项、ClientContractHealthAutoTest 1 项、ClientContractProblemProfileAutoTest 1 项；根因均为上述已有 Controller 无条件注册、CheckoutResolutionProcessor 又按数据源配置装配的差异。没有新增业务断言失败；不能将本轮表述为全量 clean verify 通过。

git diff --check、文档相对链接及生产源码旧 API/方法引用清理检查通过。保留的 23 条查询 SQL 和完整解析字段映射不变；没有数据库迁移、外部接口调用成功替身、提交、发布或部署。
