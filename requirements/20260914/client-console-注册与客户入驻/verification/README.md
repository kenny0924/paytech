# 本机 HTTPS 认证联调

## 入驻文件目标与模型增量

文件合同已确认，见[合同补充](../file-upload-closure-proposal.md)。CCT 原申请＋主体查询位于 `ClientFileTargetApi`，从真实 HTTP 入口覆盖个人/组织主体、关系移除/结束、跨账号/申请隔离及状态冻结；新增场景集中在 `ClientDraftHttpAutoTest` 的 `fileTarget*` 方法。沿下文安全注入本机凭据运行 CCT `clean verify`，不需要新增 CCT 表。

Platform 七表的授权、执行和只读复核从[精确执行清单](../db/implementation/platform-files-local-execution.md)路由；已有表不可重跑 CREATE。以下静态检查不连接或修改数据库：

```sh
node --test scripts/pdmaner/platform-file-upload-model.test.mjs scripts/pdmaner/platform-support-model.test.mjs scripts/pdmaner/platform-support-schema.test.mjs
```

运行结果与当前文件链范围从[当前台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)读取；协议及模型检查不能替代真实文件服务验证。

仅用于阶段 4 的显式本机验证。启动真实 Client Web 静态产物、Nginx、Gateway、Client BFF、Platform，以及仓库 MySQL/Redis。Vault、SMTP 和路由 Snapshot 使用协议模拟器；不验证真实邮件服务或控制面路由注册，不创建/迁移表、不修改 Nacos、不开公共入口。

## 前置

- JDK 25、各仓库 Maven Wrapper、Python 3（`pymysql`、`redis`）、Homebrew Nginx/OpenSSL。
- `desktop-linux` 上运行仓库 `mor-local-mysql-1` / `mor-local-redis-1`，分别仅映射 `127.0.0.1:13306/16379`。
- 使用已批准的 `mor_platform_config_validation`；认证挑战表及已有认证/审计表须人工准备。CUSTOMER 账号/密钥及审计链须为空。运行器持有 `platform-support:auto:global-catalog`，防止与 Platform 数据库测试重叠；不能与其他使用该库的手工写入同时执行。
- 凭据从经核验的运行容器环境读取，只在内存中传给子进程；不读取 `.env.local`，不写入凭据文件或日志。

## 准备与运行

以下命令均在工作区根目录执行。classpath 文件是本次构建产生的本机临时文件，不能从其他工作区复用。

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home platform-support-service/mvnw -f platform-support-service/pom.xml test-compile dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=/tmp/client-https-platform-cp
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home client-console-bff/mvnw -f client-console-bff/pom.xml test-compile dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=/tmp/client-https-bff-cp
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home api-gateway/mvnw -f api-gateway/pom.xml test-compile dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=/tmp/client-https-gateway-cp
npm --prefix client-console-web run build
python3 requirements/20260914/client-console-注册与客户入驻/verification/run-local-https.py
```

运行器打印本轮随机地址、邮箱和私有临时目录；另写 `/tmp/client-https-current.json`。地址仅监听 loopback。Platform 使用既有 `auto,test` 配置满足 Vault 直连限制，BFF/Gateway 使用 `auto`；这些手工启动类不是 JUnit 用例，不随自动测试或生产制品启动。Nginx 固定覆盖 `X-Entry-Channel=CLIENT` 和 `X-Real-IP`，保留 Cookie/Origin/JWT 正常校验。

在另一个终端执行一次：

```sh
python3 requirements/20260914/client-console-注册与客户入驻/verification/verify-local-https.py
```

脚本使用本轮唯一的 `protocolEmail`，实际接收模拟 SMTP 邮件，再沿 HTTPS 链消费链接。28 次请求覆盖注册验证、登录、上下文、刷新轮换与重用撤销、密码重置撤销、退出和错误合同。TLS 仅信任本次生成的证书，主机名校验保持开启。脚本不保存密码、邮件秘密、JWT 或 Refresh Cookie，只保存请求状态、请求编号和检查结论。它需要全新夹具，失败修复后应停止旧轮并重新启动再执行，不能直接用已消费的账号/邮件重跑。

## 浏览器与停止

浏览器交互是独立验收项，不能用上述 HTTP 结果替代。浏览器可能提示本机临时证书不受信任；必须由用户本人处理提示，不修改系统信任库或关闭浏览器证书校验。当前工具还要求新凭据的填写/修改由用户本人完成。邮件查看端口只在 loopback 返回本轮模拟 SMTP 收件，不对公网开放。

完成后在运行器终端按 `Ctrl+C`，或在其打印的本轮临时目录创建空的 `stop` 文件。运行器先停止自有进程，再核对审计事件的请求编号均存在于本轮 Nginx 日志，只删除自己创建的账号、密钥、审计链及随机 Redis 前缀；`cleanup.json` 记录恢复前后计数。若所有权核对失败，会保留数据并报错，不能改成按日期/全表删除。

调试时可用 `CLIENT_HTTPS_PORT` 指定空闲本机端口，并通过 `CLIENT_HTTPS_CERT_DIR` 复用同一系统临时目录下此前 `client-https-*` 轮次的一日证书，减少用户重复确认。不能引用生产证书或其他目录。每轮业务密钥、HMAC、账号、审计链与 Redis 前缀仍重新生成。

本轮实际结果从[当前实现台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)与[HTTPS 记录](../assets/stage4/client-https-verification-20260915.json)读取，不因脚本存在而视为已验收。

## CCT 本机只读预检

在工作区根目录运行 `python3 requirements/20260914/client-console-注册与客户入驻/verification/preflight-cct-local.py`。脚本核对同一仓库 MySQL 容器，只读比较冻结源与实际表结构、盘点各 Owner 本机库的聚合计数，并在私有临时目录保存 CCT SHOW CREATE 及 JSON 记录。只读连接使用内存中的本机管理员凭据以核对其他活动连接；不读取业务原值、不运行 DDL/DML、不跨库 JOIN、不修改其他 Owner 数据。

该记录是瞬时盘点，不能替代停写、精确授权及执行回执。后续范围见[CCT 十表候选](../db/implementation/cct-local-execution.md)，补偿表仍需按[写链依赖](../cct-write-dependencies.md)另行补齐。运行脚本不启动 CCT，也不意味着创建/保存申请已可使用。

本机十表执行后，脚本将因实际结构不再匹配旧源而停止，这是保护行为；不要用旧源回写或再次执行 ALTER。已执行目标的核验见[执行回执](../assets/stage4/cct-onboarding-table-execution-20260915.json)与[35 项回归记录](../assets/stage4/cct-checkout-mysql-verification-20260915.json)。数据库测试仍用 CCT 仓库的 `./mvnw clean verify`，显式注入本机 `CCT_TEST_DB_PASSWORD`，不在应用或测试启动时创建/迁移表。

## CCT 补偿单表预检

当前补偿依赖使用独立命令 `python3 requirements/20260914/client-console-注册与客户入驻/verification/preflight-cct-compensation.py`。它只检查本机 CCT 原补偿表、全库空表计数、其他连接与监听，保存私有结构备份，不执行 SQL。源结构已改变或存在数据时停止；不能改成宽松匹配或反向回写旧源。

文件级检查为 `python3 requirements/20260914/client-console-注册与客户入驻/verification/test-cct-compensation-migration.py`，验证唯一迁移目标、列转换、指纹及已执行十表工件的不可改写。精确文件和本机执行结果见[单表记录](../db/implementation/cct-compensation-local-execution.md)。本机切换后旧预检会因不再匹配二十列源而停止；不可重跑 ALTER。

`CompensationSchemaAutoTest` 在 CCT Test 模块中装配真实 Foundation 注册器、Mapper、Dispatcher 和 Scanner，并使用本机实际表验证技术合同；该探针仍只存在于 Test 模块；实际恢复切片现在通过生产依赖接入 Foundation，见下方恢复回归。复用上方 CCT 凭据入口运行 `./mvnw clean verify`，不连接 Platform、配置 XXL-Job 或制造真实业务开通。各场景只清理自身随机事件类型/业务键和合成 Party，停止执行器后核验计数恢复。

## Platform 工作区开通回归

[PLT-I17 合同](../platform-tenant-projection-contract.md)的 Owner 写端通过 `TenantProjectionHttpAutoTest` 验证。使用人工准备的 `mor_platform_config_validation` 及仓库 Redis，不执行建表或目录初始化；当前 Schema 准备范围从实现台账读取。按上述环境规则在内存中安全注入 `PLATFORM_SUPPORT_TEST_DB_URL/USERNAME/PASSWORD` 和 `PLATFORM_SUPPORT_TEST_REDIS_PASSWORD`；测试自身持有目录隔离锁，只清理各自夹具。

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home platform-support-service/mvnw -f platform-support-service/pom.xml clean verify
```

单独调试可使用 `test -Dtest=TenantProjectionHttpAutoTest -Dsurefire.failIfNoSpecifiedTests=false`，但过滤测试结果不能代替完整 `verify` 的全局覆盖率门禁。本轮[验证记录](../assets/stage4/platform-tenant-projection-verification-20260915.json)保存源码及制品指纹、完整 Suite 结果和私有日志位置。没有启用正式环境开关，也不证明 CCT 补偿调用、文件上传或审批链已经完成。

## CCT 工作区恢复回归

使用已有本机 `mor_cct` 和上方安全注入的 CCT_TEST_DB_URL/PASSWORD 运行 CCT Reactor `clean verify`，不需要 Redis、真实邮件、Vault 或真实 Platform 账号。`ClientTenantProjectionAutoTest` 使用生产配置、Source Service、Mapper、HTTP Client、Handler 与真实 Foundation Dispatcher/Scanner；外围 Platform 仅为独立 loopback 协议 Simulator。测试准备恢复所需的合成事实，不代表 C04/C06 已实现。

每个场景先核对库中无同事件类型的外部任务，结束时停止自有执行器后精确回收事件、Client、Party、申请和历史。不得把失败改为 skip、创建表或删除其他数据。默认关闭和测试 URL 限制由 `ClientOnboardingRecoveryConfigurationAutoTest` 验证。来源算法由 `ClientProjectionSnapshotAutoTest` 校验。实际全量结果与源码/依赖指纹见[本轮证据](../assets/stage4/cct-workspace-recovery-verification-20260915.json)。

CCT 集成同时暴露 Foundation 泛型 CodeEnum 映射复用错误。修复在 Compensation Owner 模块中按具体枚举注册并由 javaType 查找；`CompensationMapperEnumBindingAutoTest` 先失败后通过。仅在本机 Maven 缓存构建该修复，不表示发布了远程制品。Foundation 聚焦检查和 CCT 真库回归的范围分别记入证据。


## CCT 当前草稿接口回归

当前完整 CCT Reactor 新增 Redis 依赖；运行 `./mvnw clean verify` 前通过已批准的本机安全入口提供 CCT_TEST_DB_URL/USERNAME/PASSWORD 与 CCT_TEST_REDIS_PASSWORD，复用 mor_cct 和本机 Redis 16379。不要读取 local-environment/.env.local，也不要将凭据写入 YAML 或日志。`application-onboarding-auto.yml` 固定本机基础设施和 300ms 测试锁租约，运行时只注入 Platform/Vault Simulator 的随机端口；生产默认租约保持 30s，写入口默认关闭。

`ClientDraftHttpAutoTest` 通过真实 HTTP、Processor、Service、MyBatis、MySQL、Redis 和 Foundation 恢复机制验证申请与当前资料。SQL 屏障仅暂停真实 INSERT，放行后仍执行原 Mapper，用于复现租约到期后两个创建竞争；不替换持久化结果。Vault 屏障用于验证外部调用期间状态变化后的落库重验。只有外部 Platform/Vault 为协议 Simulator，不能称为真实双 Owner 联调。

每次测试只删除自身记录；先等待其恢复任务结束，再回收准确账号/申请/客户/主体及命令行。失败导致任务保留时，应核对状态和本次来源后精确处理，不能无条件清空表或放宽断言。当前结果和源指纹见[草稿回归证据](../assets/stage4/cct-draft-verification-20260915.json)，旧恢复证据仍保留当时的独立范围。

## Platform 文件上传入口回归

人工 SQL 的精确授权、执行记录及独立只读校验见[七表执行清单](../db/implementation/platform-files-local-execution.md)。`apply-approved-platform-files.py` 拒绝已存在执行记录或任何候选表，不可作为可重复迁移工具；后续核验只运行 `verify-platform-files-local.py`，且不能与持有目录锁的测试同时运行。可用 `--output` 指定独立证据文件，保留前次观察记录；此参数只改变输出位置。

使用现有 Platform 安全凭据入口运行 Reactor `clean verify`。本机 URL 固定为 `jdbc:mysql://127.0.0.1:13306/mor_platform_config_validation?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`，不要添加 Operations 测试白名单以外的连接参数。程序从不读取 `.env.local`。聚焦文件入口可使用 `test -Dtest=OnboardingUploadHttpAutoTest,OnboardingCompletionHttpAutoTest,FileScanHandlerAutoTest,PlatformDatabaseAutoTest -Dsurefire.failIfNoSpecifiedTests=false`。

`OnboardingUploadHttpAutoTest` 验证 F01 的真实注册/会话、IAM、MySQL 事务与 Redis 协调；CCT 查询使用真实私网协议的 Simulator，存储仅在 Test 模块提供通过 loopback HTTP 调用的合成适配器。失败注入在真实回执 SQL 执行后抛错，用于验证 Upload 与回执原子回滚；并发屏障验证租约到期后的行锁保护。所有策略、账号和文件均为用例夹具，按原 ID 回收。

`OnboardingCompletionHttpAutoTest` 覆盖 F02 实际字节长度/SHA-256、准确不可变版本与保护信息、原申请/主体/租户授权、原成功回执、文件/Owner/回执/事件原子性，以及角色变化、会话撤销和过期租约。空 Body 在拦截器读取首字节时拒绝，不加载客户端文件内容。

`FileScanHandlerAutoTest` 是独立扫描入口测试；通过生产 Foundation Scanner/Dispatcher 验证 due/未到期、未知结果恢复、精确版本/摘要、全部要求通过、拒绝和版本门禁、Lease 接管、旧工作者晚返回及耗尽 FAILED。外部检查固定 `fileId/requirementId/attempt=1`；本期不提供显式重扫命令，恢复不能把补偿执行次数作为新扫描尝试。所有存储/扫描协议实现只在 Test 模块，未选定真实供应商。

此处上传/扫描套件不替代后续材料查询、关联与下载检查，后者见下节；真实供应商不属于协议模拟器验收。当前已实现范围与全量结果统一见 [CURRENT_IMPLEMENTATION](../../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。

上传开始的源码/制品指纹、完整测试与覆盖率记录见[本轮证据](../assets/stage4/platform-upload-start-verification-20260916.json)。覆盖率核验包含 File/Document 的生产类，不调低原有指令和分支门槛；实际当前数值从实现台账读取。

完成与扫描的独立源码/制品指纹及完整结果见[完成链验证记录](../assets/stage4/platform-upload-completion-verification-20260916.json)；新的只读数据库观察保存在[独立复核记录](../assets/stage4/platform-files-post-completion-verification-20260916.json)，保留先前执行及 F01 观察记录。

## 文件读取与材料关联回归

三套服务各自运行 `./mvnw clean verify`，仍使用各自 Owner 本机库、Redis 与安全注入的测试环境变量。Platform 和 CCT 的前置见上文；Risk 使用 `RISK_TEST_DB_PASSWORD`、`RISK_TEST_REDIS_PASSWORD` 及仓库既有 auto 配置。先在本机 Maven 安装最新轻量 `platform-support-api`、`risk-api` 后再构建 CCT 消费者；`install` 不代表远程发布，不使用 `deploy`。不要同时运行使用同一个库的写测试。

`OnboardingFileReadHttpAutoTest` 经真实注册/登录、IAM 和文件完成/扫描链验证 F03、F04、私网 verification：精确不可变版本、批准后原 Tenant 角色、各不可用状态、会话/权限撤销、错误原坐标、无递归授权、临时授权格式/时限，以及存储等待后的再次授权。只使用 Test 模块下载协议适配器，生产没有合成成功适配器。

`MaterialRequirementsHttpAutoTest` 经真实 Risk HTTP/Service/Mapper/MySQL 验证精确类型/国家映射、有效 Program/修订、必需及可选材料、缺配置、歧义、格式错误和调用者边界。只有标注为合成的 ZZ/YY 程序夹具，结束后按准确 ID 回收；无真实国家规则初始化。该只读查询不是正式提交、尽调任务或审核批准。

`ClientDraftHttpAutoTest` 的材料场景沿 C12/C13/C05 真实链核对主体/文件归属、主主体与相关人国家来源、摘要只取自 Platform、当前材料列表、关系移除后的只读访问、终态冻结、原键重放、SQL 回滚及租约过期的单引用。外部 Platform/Risk/Vault 使用严格 HTTP Simulator；会话、状态、国家在外部查询等待期间改变必须重新校验。

三套测试分别证明各 Owner 组件的实际行为，不等于真实多服务端到端或浏览器验收。[材料链记录](../assets/stage4/material-read-association-verification-20260916.json)保存源码/制品、完整 Suite、失败先例及恢复计数；数据库只读观察见[Platform](../assets/stage4/platform-files-post-read-verification-20260916.json)和[CCT/Risk](../assets/stage4/material-owner-read-model-verification-20260916.json)。最新结果与 BFF/Web 剩余范围统一见[台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。

## BFF/Web 入驻接线验证

先本机安装最新 `client-contract-api`、`platform-support-api` 和 `risk-api` 轻量 JAR，再在 Client BFF 执行 `./mvnw clean verify`，在 Web 执行 `npm test` 和 `npm run build`。BFF 测试不使用数据库；CCT/Risk 各自继续真实本机 MySQL/Redis 测试并核对合成夹具恢复，不能以 BFF 模拟器替代 Owner 证明。

浏览器独立复现：

```sh
npm --prefix client-console-web run build
node requirements/20260914/client-console-注册与客户入驻/verification/preview-onboarding.mjs
```

打开脚本打印的随机 loopback 登录地址；需要固定端口时可追加 `--port=52649`。使用合成演示账号 `demo@example.test` / `Preview-only-2026!` 登录。未登录直达入驻页应返回登录页，错误密码应被拒绝，成功登录后刷新可恢复会话；从“账号”退出后再次直达应返回登录页，原 Access Token 和 Cookie 均失效。

该工具只提供合成身份、申请和材料协议，不连接 BFF/Owner、对象存储或共享环境；生产前端不导入模拟器，也不提供生产旁路开关。会话有效期为一小时，使用独立的 `client-preview-session-<port>` HttpOnly / SameSite=Strict Cookie；它只用于 loopback HTTP 预览，不替代正式 Secure Refresh Cookie，也不证明真实认证、Token 轮换或跨账号申请归属。页面和会话数据只在进程内存，结束使用 Ctrl+C。它有意在首次新增人员写入后返回 503，可验证“重试原操作”只恢复同一人员。检查主表单保存回显、人员编辑、关系面板、材料面板、已通过个人只读、390px 无横向溢出和浏览器无异常。刷新会保留模拟器内存事实，但不会产生真实审核或身份绑定。

预览认证回归（每个用例启动独立 loopback 进程）：

```sh
node --test requirements/20260914/client-console-注册与客户入驻/verification/preview-onboarding.test.mjs
```

覆盖匿名读写/刷新拒绝、演示密码校验、Cookie 与 Bearer 分工、退出撤销及不同浏览器会话隔离。Web 的 `auth.test.ts` 另检查刷新会话失败时不会发出入驻数据请求。最新验证结论见[台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。

浏览器结果与生产源码/制品指纹记录在[本轮独立证据](../assets/stage4/client-browser-materials-verification-20260916.json)，截图在 `assets/stage4/onboarding-ui/`。这套浏览器验证不绕过证书提示，也不宣称真实多 Owner/供应商端到端通过。
