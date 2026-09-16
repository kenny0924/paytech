# Client 注册入驻：阶段 4 首批研发进度

本文是[当前实现台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)引用的组件证据。阶段 4 和首批均未完成，不能作为综合验收或上线通过记录。

| 范围 | 当前结果 | 仍需完成 |
|---|---|---|
| CCT/Risk 数据模型 | 已同步批准字段；CCT 本机十表及独立补偿表已按各自授权迁移并核验 | 原生模型 GUI 核对、Risk 与其他环境迁移；真实 Owner 联调 |
| Platform Schema | 邮件挑战表已在本机验证库执行并核对；Schema 另含文件相关 6 表 | 六张文件表及其他环境仍未执行 |
| Checkout 消费者 | CCT 草稿与 Checkout/恢复回归见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)；未放宽原资格门禁 | Risk 新资格真实 Owner 接入；最终支付准备仍拒绝未就绪依赖 |
| 工作区开通写端 | Platform PLT-I17 写端、CCT 创建事务及恢复 Handler；均默认关闭 | 真实双 Owner 联调、文件及 Tenant Token 门禁 |
| 认证基础 | 注册/验证/登录/刷新/重置/退出及审计已通过本机真实 HTTP/MySQL/Redis 回归，补充并发与失效边界；默认关闭 | 浏览器交互验收、生产 KMS；密码归属优化在对外开放前完成 |
| Gateway / Client BFF | Nginx HTTPS → Gateway → BFF → Platform/MySQL/Redis 协议链已通过，修复 Cookie 与 422 错误互通 | 真实控制面路由注册；浏览器验收；入口仍未启用 |
| Client Web / Pencil | 六类认证页和最小账号信息页已实现；S01/S04 同源画板已同步保存 | 真实 HTTPS 会话回归；后续入驻页、工作区切换及成员检查 |
| 入驻人工审核 | 主资料创建/读取/保存已有 CCT 组件；资格类型遵守既有 KYC/KYB 词表 | 人员/关系、材料、提交、Risk 审核、运营页面及真实 Owner 联调 |

2026-09-15 用户决定继续原邮箱密码注册，邮件验证后登录；[密码归属优化](./registration-password-binding-amendment.md)延至对外开放前。A01/A03 和注册/验证页面保持原合同，研发继续；本期仍不声称预注册防护已解决。

## 验证证据

### 2026-09-15 CCT 当前草稿与固定回执

用户确认固定操作回执后，C04 创建/恢复、C05 的 Owner 主资料查询与 C06 增量保存按[API 合同](./api-contract.md#7-c04c05c06-的-cct-私网组件)落地。个人/组织分区、申请归属、DRAFT/RETURNED 可编辑、批准冻结、同键原回执及事务外 Vault 保护均有真实 HTTP 验证。创建时原子登记 CLIENT 安全审计和恢复任务；普通保存不写资料历史、行业或 MCC。

验证先暴露旧入口 404，随后修正新增 MyBatis 设置影响旧恢复映射、空白资料查询被视为空行、Redis 配置缩进影响原健康入口，以及资格类型沿用测试占位的问题。资格类型另加先失败的 KYC/KYB 对照断言。SQL 屏障验证 Redis 租约到期后的原子回滚与唯一申请，Vault 屏障验证等待期间状态改变时拒绝落库；全字段往返验证覆盖两类主表单。失败留下的合成恢复记录经精确来源核对后回收，最终完整回归恢复空库基线；没有执行 DDL。

最新数量、源码/制品指纹及日志范围见[草稿回归证据](./assets/stage4/cct-draft-verification-20260915.json)和[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。Platform/Vault 为独立协议 Simulator；这不代表 BFF 页面、真实双 Owner、人员、文件或审核闭环已完成。写入口保持默认关闭，没有提交、推送或部署。

### 2026-09-15 CCT 工作区恢复与当时写接口待决项

实现独立的 CCT Source Service、严格 Platform HTTP Client 和 CLIENT_TENANT_PROJECTION Handler：读取原不可变生命周期事实，短事务结束后先查 Platform，再按原身份开通或顺序投影。个人和组织共用协议，Payload 为 NULL；重复、未知结果、跳号、终态晚到、身份/Hash 冲突及失败保留均受控。普通资料保存没有恢复 BCR 或表单版本。

真实 CCT 集成先复现 Foundation 泛型枚举 Handler 绑定冲突，任务停在 INIT；新增最小映射检查同样失败后，在 Compensation Owner 内按目标 Java 类型绑定修复，13 项聚焦检查通过，仅安装本机依赖。之后严格 Problem 检查拒绝了测试 Simulator 的旧格式，修正其 Header/detail/字段集合，生产 Reader 保持严格。25 项聚焦测试通过，最终 **CCT clean verify 62/62、12 Suite**，零失败/错误/跳过，包含原 Checkout 回归；38 张表的测试夹具均回收。未生成 CCT 覆盖率百分比，也未将 Foundation 聚焦测试称作全模块验收。

[恢复合同](./cct-workspace-recovery-contract.md)定义目录与来源 Hash；[验证记录](./assets/stage4/cct-workspace-recovery-verification-20260915.json)保存源码/制品指纹、命令和私有日志。需求站构建通过，135/136，剩余是既有 Risk 数量断言。没有改 Schema、执行 DDL、注册路由、部署或提交。

该轮交付时 C04/C05/C06 尚未实现，TestMapper 来源准备不代表草稿接口；当时提出的响应问题现已由[固定回执决定](./draft-command-response-proposal.md)收口，后续实现见上方草稿记录。真实双 Owner 开通、文件、Risk、浏览器和阶段 4 继续待完成。

### 2026-09-15 Platform 工作区开通与完整回归

按 [PLT-I17 私网合同](./platform-tenant-projection-contract.md)补齐真实 Platform 写端及来源查询。初始 PENDING Tenant、申请人 OWNER Member、入驻角色/授权同事务创建；首次 ACTIVE 撤销入驻授权并授予正式 Owner。当前只开放 CLIENT 类型的实现，独立开关默认关闭，不改 Partner 业务、行业/MCC、资料版本或 Gateway 路由。

12 项真实 HTTP/MySQL/Redis 用例验证重复开通、类型/账号/Hash 边界、丢失前序与跳版本、迟到事件、保留安全/成员暂停、角色切换及审计；SQL 屏障验证 Lease 到期后同账号和不同账号竞争，故障注入验证实际 Member INSERT 后整体回滚。另在普通 Tenant 管理应用中验证写入口默认 404。

初次行为测试复现入口 404。实现后 262 项聚焦测试通过，但过滤测试运行不能满足全局覆盖率门禁。扩大到完整 543 项时，旧本机表集合、主键数量、邮件依赖、继承 HMAC 反射检查及本次日志前缀共 5 项预期需同步。按已批准/已执行事实修正这些检查，保留精确集合、无 CHECK/FK、原子 HMAC、日志保护和 90% 覆盖率门禁；随后 543 项全通过，但分支覆盖率仅 84.75%。补充 Client 公钥、密码参数、邮件重发/证明、身份绑定、刷新代及并发安全状态检查，认证 HTTP 从 3 项增加至 24 项；显式关闭测试客户端自动 503 重试，确保首次拒绝可见。最后 **clean verify 572/572（57 Suite） 通过，指令 97.868386%、分支 90.083988%**。

[检查记录](./assets/stage4/platform-tenant-projection-verification-20260915.json)保留完整源码指纹、制品指纹、逐 Suite 结果及私有日志位置。最终五张 IAM 表与完整 Schema 一致；未执行 DDL，26 张表仍保留，测试账号/来源租户/成员/审计均已清理、目录锁释放。需求站仍为 135/136，失败是既有 Risk 数量断言；未修改无关 Risk 规则。

这是 Platform Owner 侧验证。CCT 创建/保存、真正的业务 Compensation Handler 和 CCT→Platform 未连接；文件上传、Risk 审核、浏览器及生产 KMS 未验收，阶段 4 继续。

### 2026-09-15 CCT 补偿单表执行与真实合同回归

用户明确同意上轮精确单表范围。重新核验环境、源结构及 SQL 指纹后，仅执行 `t_compensation_event` 一条 ALTER：十五列及四个索引（含主键）与模型一致，完整默认值、编码、注释和 EXTRA 属性均已核对。零警告、其余 37 表结构未变、38 表为空，维护锁释放；[执行回执](./assets/stage4/cct-compensation-execution-20260915.json)保存新的 0700/0600 结构备份。首次执行器预检查误将旧 TEXT 的字符集视为 NULL，在 SQL 前停止；修正核验器后执行原授权文件，没有部分迁移或自动回滚。

新增隔离 Schema 合同测试，使用真实 Foundation 注册器、生产 Mapper、Dispatcher/Scanner 与本机 MySQL，通过五项事务、幂等、默认值、恢复、失败保留和租约隔离检查。初次构建缺少 XXL 的传递依赖允许项，仅在不可部署的 Test 模块补齐精确依赖，生产禁用边界保持。测试探针读取合成 Party，不能代表 Client 入驻开通；没有 Platform 调用或业务 Owner 替代实现。

完整回归首次 40 项中 3 项因 Checkout 测试引用旧 `source_id` 报错。保留“查询不登记任务”的校验意图，改为读取请求前后的任务 ID 集合，既不假定未来业务事件键，也不读取 Payload。最终 JDK 25 **clean verify 40/40、9 Suite**，零失败/错误/跳过；每轮夹具均恢复原计数，生产可执行包确认不含 Compensation/XXL 测试依赖。测试、源码与执行证据见[合同回归记录](./assets/stage4/cct-compensation-runtime-verification-20260915.json)。当前构建未产生 JaCoCo 百分比，不声称覆盖率达标。

本次关闭单表数据库前置与技术兼容验证。实际开通 Handler、Platform PLT-I17 和个人/组织草稿接口仍待实现，阶段 4 继续；未启用路由、部署、提交或推送。下方“未执行”的旧候选记录保留当时范围，由本条本机执行结果取代。

交付检查：94 项模型/兼容检查、4 项迁移安全检查、原候选静态检查及差异检查通过；544 个本地链接、95 个源码/配置指纹及实际使用的 Foundation JAR 指纹已核对。需求站构建通过、135/136 项通过，剩余仍是既有 Risk 旧统计断言。本机最终全库计数为零、结构与执行回执相同、无其他 CCT 连接或监听。

### 2026-09-15 CCT 补偿模型与独立迁移候选

用户同意继续开通恢复及个人/组织草稿写链。本轮完成其补偿持久化前置：新增独立 Foundation 合同检查，先复现原二十列结构失败，再同步 CCT Catalog、字段元数据、生成器、PDManer 和完整 Schema。任务采用三元组身份、可空的最小 Payload、安全默认值以及就绪/租约双索引；业务来源版本从领域历史恢复，不进入任务表。其余 37 张表模型逐项相同，此前十表 SQL 指纹保持不变。

模型/原资料投影 **84/84**、迁移安全 **4/4**、原候选静态模拟及重复生成检查通过。生成器现在保护已经执行的十表 SQL 和历史目标指纹，修改这些目标时要求新增迁移；验证脚本只在内存生成，不再回写历史工件。

最终包含四系统兼容门禁的模型回归 **94/94**，另有迁移安全 **4/4**；其余 37 表的完整 Schema 语句也逐字相同。264 个本地链接目标、语法及差异检查通过，21 个源码/工件指纹见[检查记录](./assets/stage4/cct-compensation-verification-20260915.json)。需求站构建通过，136 项中 135 项通过；唯一失败仍为此前的 Risk 78 表/944 字段/115 关系旧统计断言，本轮未改动该断言。

[单表候选](./db/implementation/cct-compensation-local-execution.md)只含一个补偿表 ALTER。只读预检确认 `127.0.0.1:13306/mor_cct` 的原二十列结构、索引、注释、编码与额外列属性完全一致，38 表为空，无其他 CCT 连接或 18101 监听；已保存 0700/0600 私有结构备份，见[预检证据](./assets/stage4/cct-compensation-preflight-20260915.json)。**新 ALTER 尚待该表精确授权，未执行 DDL/DML。**

同时核对到 Platform PLT-I17 仍只有设计合同，尚无真实 Java Provision 写端；[恢复依赖](./cct-write-dependencies.md#4-platform-投影写端与恢复身份)补充 Client Root 历史、首次 Provision 和状态投影的恢复边界。未新增空 Handler、伪造工作区成功或修改业务 Java；未重跑 Maven，草稿创建/保存和实际开通恢复仍待后续完成，阶段 4 未结束。

### 2026-09-15 CCT 十表获准执行与数据库回归

用户对上一轮精确十表候选回复“可以”。重新核验 `desktop-linux/mor-local-mysql-1`、`127.0.0.1:13306/mor_cct`、源/目标模型与 SQL 指纹，确认 38 表为空、无 CCT 应用/连接、其他 Owner 只读盘点未变后，取得本机维护协调锁并保存新的结构备份。按原文件顺序执行 **10/10 ALTER**，十表 99 列/27 索引及完整注释与目标一致，其余 28 表 SHOW CREATE 未变，未执行第十一条迁移或初始化 DML。

首次实际数据库回归 34 项中 3 项失败，定位到预检查的精简 Snapshot 没有装载 Party Profile，已批准客户因此返回 `CLASSIFICATION_NOT_READY`。新增真实 HTTP 反例后，目标 Suite 10 项中 4 项失败；修复为预检查只查询当前 Client 主体资料，完整解析保留原合同参与方读取。没有放宽批准/分类门禁、把所有查询改成完整读取或增加默认通过的 Risk 实现。

最终 JDK 25 **`clean verify` 35/35（8 Suite）通过**，0 失败/错误/跳过。涵盖实际 HTTP/MyBatis/MySQL、草稿与批准事实变化、未确认分类拒绝、Scope 变化、Root 复核、未知持久化状态和原有策略/协议/健康检查。当前 CCT 构建未生成 JaCoCo 百分比，不登记覆盖率达标。三轮测试均完整回收自身夹具，最终 38 表为空、结构与迁移后相同、维护锁释放、无其他 CCT 数据库连接。

MySQL 为原批准 `TINYINT(1)` 返回一条 1681 显示宽度弃用警告，已留证；未临时改变批准 SQL。执行与前后结构证据见[十表回执](./assets/stage4/cct-onboarding-table-execution-20260915.json)，测试、失败证据与 68 个源码/配置指纹见[数据库回归](./assets/stage4/cct-checkout-mysql-verification-20260915.json)。这些证据取代下方“CCT SQL 待授权”和“目标数据库回归未执行”的当时状态，不改写旧预检记录。

交付检查：十条 SQL、68 个源码指纹、385 个本地链接、结构备份指纹/权限、Mapper XML 与差异检查通过。需求站构建通过，136 项中 135 项通过；剩余仍是既有 Risk 统计断言要求 78 表/944 字段/115 关系，与本轮 CCT 变更无关，未修改该断言。

本轮完成十表切换与 Checkout 兼容回归。CCT 补偿表仍为旧结构，草稿创建/保存、人员/材料、审核开通与浏览器交互仍未完成；阶段 4 继续，未启用路由、部署、提交或推送。

### 2026-09-15 CCT 本机预检与 HTTP 边界

用户回复“继续”，本轮核对入驻写链前置并补跑已有边界测试，没有新增草稿写接口。现有 `mor_cct` 38 表均为空，428 列/142 索引及引擎/排序规则与冻结源一致；十张迁移表连同注释完全匹配。其余两张表只存在既有 Provider/Channel 表注释差异，保持不变。16 表保守闭包与 14 项引用检查为零，结构备份限本机用户读取。

各 Owner 使用自己的选定数据库分别只读盘点，未跨库 JOIN。Commerce 三条 FAILED 任务属于现有退款 Handler，保留原样；Risk 非空表是规则目录，Platform Tenant 为 INTERNAL，未发现本机 Client/Partner 投影。此范围不包含其他环境，不证明尚未落地的表或部署能力已经具备。

JDK 25 `CheckoutQueryHttpBoundaryAutoTest` **4/4 通过**，验证非法/未知/强制转换字段、调用方和封闭查询参数、规范 ID/目录及 Risk 不可用时拒绝。未运行完整 Checkout 业务夹具测试、草稿保存、全量覆盖率或浏览器。初次 Maven 选择在无测试的 API 模块停止，添加仅允许非测试模块无匹配的 Surefire 参数后，明确核对目标 Suite 实际执行四项，不把初次失败作为业务 RED。

[只读脚本](./verification/preflight-cct-local.py)、[预检记录](./assets/stage4/cct-onboarding-preflight-20260915.json)和[本机十表候选](./db/implementation/cct-local-execution.md)已准备；证据含 69 个源码指纹、SQL 指纹与备份坐标。当前等待这十条本机 ALTER 的精确授权，未执行任何 DDL/DML；先前 Platform 单表授权不扩大到 CCT。

另发现必须补齐的真实依赖：创建申请要求同事务登记开通投影任务，而 CCT 旧二十列补偿表、依赖门禁与现行 Foundation 不兼容。已在[写链依赖](./cct-write-dependencies.md)记录模型、Handler 和失败恢复测试要求；它不属于已审查十表 SQL，本轮未悄悄扩大迁移。CCT 创建/保存、人员/材料及审核恢复仍未实现，阶段 4 未完成。

### 2026-09-15 本机 HTTPS 协议链联调

用户同意继续浏览器/Gateway/BFF/Platform HTTPS 联调。在既有验证库和仓库 Redis 上启动临时 loopback Nginx 与三个真实应用；只有 Vault、SMTP 和路由 Snapshot 使用协议模拟器，未注册共享控制面或开启公共入口。复现并修复：

- Platform 邮件链接失效使用旧 422 标题，BFF 严格解码将其变为 502。改用当前标准标题，并在真实 HTTP 回归中校验错误标题。
- BFF 的 Spring Cookie 输出额外 `Expires`，Gateway 拒收导致登录 502。共享编码器统一签发、退出和 401 清理的固定属性，保持 Secure、HttpOnly、SameSite=Lax、Host-only、原 Path 与七天上限。
- Gateway 路由测试的 `account-context` 与已批准的 `context` 不同，已统一为实际合同。

最终 **28/28 HTTPS 请求**通过：注册与邮件单次验证、未验证登录拒绝、验证不自动登录、CUSTOMER JWKS/账号上下文、刷新轮换和重用撤销、重置后旧密码/Access/Refresh 失效、重复退出与 Cookie 清理、Origin/伪造身份拒绝及 401/403/422 Problem。每次请求均校验本次证书和 localhost 主机名，不关闭 TLS 验证。该轮 18 条审计结果实际落库；本轮自有账号/密钥/会话/挑战、审计/安全记录及 Redis 前缀已清理，恢复原计数，所有临时应用监听已停止。

针对性回归：Platform 3、BFF 10、Gateway 36，共 **49 项 Java 测试通过**；Web 4 项与生产构建通过。本轮没有重跑完整覆盖率 `clean verify`，此前 82/1,261 项证据保留其当时范围。BFF Cookie 修复前新增断言实际复现 10 项中 1 项失败。

**浏览器仍待验收：** 浏览器连接器不可用后已通过原生 Chrome 打开页面，但停在 `NET::ERR_CERT_AUTHORITY_INVALID`。工具要求用户本人处理证书提示，已发出接管请求，未绕过提示或修改系统信任库。HTTP 协议通过不代表浏览器 Cookie、导航或密码表单交互通过；本轮未创建浏览器账号，临时服务完成回收后需按说明重启再验收。

证据见 [HTTPS 检查记录](./assets/stage4/client-https-verification-20260915.json)，复现入口见[手工运行说明](./verification/README.md)。当前仍为阶段 4；个人/组织入驻、审核和开通恢复尚未完成，未进入阶段 5，未部署、提交或推送。

### 2026-09-15 获准单表执行与 Platform 认证回归

用户针对上轮精确单表执行范围回复“可以”。本轮在 `desktop-linux/mor-local-mysql-1` 的现有 `mor_platform_config_validation` 执行唯一一条 `t_authentication_challenge` CREATE；表数从 25 变为 26。21 列、7 个索引及表/列注释与批准 Schema 一致，原有 25 张表结构不变，结构备份坐标见[执行回执](./assets/stage4/platform-auth-table-execution-20260915.json)。未执行其余六张文件表、CCT/Risk 迁移、全库初始化或共享环境操作。

- 真实 HTTP 首次复现邮箱验证后正确密码仍返回 401；修正密码查询的列顺序，使凭据版本与强制改密标记正确映射到数据对象。
- 增加实际落库审计断言后，复现成功认证缺少审计结果。Client 注册和认证改用已有事务执行器，复用同样的 READ_COMMITTED/15 秒边界并登记真实事务完成通知；业务提交后由原独立写池保存审计。Operations 执行器的原事务语义保留。
- 测试随机 Redis 前缀补充字母开头的 `run-`，消除 UUID 首字符为数字时的偶发启动失败。三个 HTTP 用例核验注册、邮箱链接单次消费、不自动创建会话、登录与账号上下文、刷新轮换/重用撤销、密码重置撤销、十次失败锁定、重复退出，以及 CUSTOMER/CLIENT 审计结果真实落库。
- JDK 25 `clean test` 聚焦回归 82/82（16 Suite），0 失败/错误/跳过，包含原 79 项认证、SMTP、审计规则、配置及架构检查。Vault/邮件使用本机 HTTP/SMTP 协议 Simulator；MySQL/Redis 是仓库实际服务。未运行 Platform 全量覆盖率门禁，不能称为完整 `clean verify`。
- 测试后重新读取核验：本轮 CUSTOMER 账号/密钥、CLIENT 会话、挑战、专用日期审计/安全记录均清理为 0；`client-auth-test:*` Redis 残留为 0，认证依赖 8 张表结构复核通过。

命令、失败证据、82 项结果和 407 个源码/配置指纹见[认证回归记录](./assets/stage4/platform-auth-http-verification-20260915.json)。本轮关闭凭据/挑战表/基础认证持久化前置；完整 HTTPS 浏览器链、后续入驻审核批次仍未完成，阶段 4 首批尚未验收。未发送外部邮件、启用路由、部署、提交或推送。

交付检查：352 个本地链接、407 个源码/配置指纹、结构备份指纹及 0600 权限、两仓相关差异检查通过。需求站构建通过，135/136 项通过；仍为下方前轮记录的 Risk 统计断言不一致，本次未改动该统计。

### 2026-09-15 Platform 本机只读预检

用户回复“下一步”，继续当前认证批次。已通过核验过的仓库 MySQL 容器在内存中注入凭据，未读取 `.env.local`。现有 `mor_platform_config_validation` 缺少注册和密码重置使用的 `t_authentication_challenge`；其余七张认证依赖表的 120 列、30 个索引与当前 Schema 一致。CUSTOMER 账号/密钥、CLIENT 会话及测试专用日期审计链均为空，详见[只读预检记录](./assets/stage4/platform-auth-preflight-20260915.json)。

已形成[本机单表执行候选](./db/implementation/platform-auth-local-execution.md)，仅引用既有 SQL 中这一条 CREATE。当时等待精确 SQL 执行授权，未建表或运行数据库业务测试；凭据入口已解决，实际前置改为缺表。该前置后来由上方获准执行解决，下方历史验证范围保留。

本次文档检查：344 个本地链接、SQL 源/语句指纹及 `git diff --check` 通过；需求站构建通过，136 项测试中 135 项通过。剩余一项是既有 Risk 模型统计断言仍要求 78 表/944 字段，而当前台账已有 81 表/1,003 字段；本轮未改动 Risk 模型或该统计，不将这项失败改写为通过。Gateway 状态行保留明确的“Client 入口未开放”，相应站点断言已通过。

### 2026-09-15 Gateway 真实 HTTP / Redis 增量

用户回复“继续”，本次完成当前认证批次的 Gateway 请求链补齐。证据见 [Gateway 检查记录](./assets/stage4/gateway-http-verification-20260915.json)，该记录取代下方前轮“Gateway 未执行完整 Redis 流程”的状态，不覆盖历史证据。

- 真实 HTTP 先复现 CLIENT 登录返回 502、预检 404、错误 Origin 未拦截，以及错误采用 Operations Cookie；将共享过滤器改为按通道执行固定 Cookie 与 Origin 合同。CLIENT 使用七天上限、SameSite=Lax、固定 Path 的安全 Cookie，Operations 原合同保留。
- CUSTOMER JWKS 验签后分别重建 ACCOUNT/TENANT 身份，丢弃浏览器伪造 Header；明确强制 CLIENT_JWT 的 IP 与认证主体 Guard，不要求 ACCOUNT 携带 Tenant。实际 Redis 请求验证第二次超限请求不会进入 BFF。
- 缺少登录凭证、431 Header 限制、502 上游协议错误和 401 清 Cookie 均保留可信 CLIENT CORS；关闭 Client 开关时仍拒绝请求，取消框架默认 Basic 提示，返回统一 Problem。只有 auto 测试显式打开 Client，部署配置没有启用。
- JDK 25 完整 `clean verify`：70 个 Suite、1,261 项，0 失败/错误/跳过。JaCoCo 指令 96.624745%（19438/20117）、分支 90.051323%（2281/2533），原 90% 门槛及报告完整性通过；边界脚本与 `git diff --check` 通过。包括新增 37 项 CLIENT 路由/配置/HTTP 场景，以及补充凭证时间、身份类型、溢出和 Tenant 投影验证；已有 Operations 与 Client API 全量回归包含在同一次执行内。
- 开启 Netty 详细泄漏诊断后仍观察到 ByteBuf 释放警告。未含本次 Client 修改的 HEAD `a2111df79213107f1697640a0f19466070047409` 隔离副本也复现同类警告，并通过其完整测试。该既有问题本批未修复，不能把测试通过描述为运行无告警。

Redis 使用仓库已有安全测试配置入口，每轮独立 Namespace、限定清理并核验外部哨兵。Gateway 的 BFF、JWKS 和 Snapshot 对端为严格 loopback 协议 Simulator，尚未连接真实 Platform/MySQL。Platform 测试凭据入口仍未提供；没有读取 `.env.local`、执行数据库迁移、配置外部服务、启用路由、部署或提交。完整 HTTPS 浏览器链及后续入驻审核批次仍待完成，阶段 4 首批尚未验收。

### 2026-09-15 四工程认证接入增量

用户回复“可以，开始做当前的任务”，本次继续阶段 4 的当前认证批次。证据见[四工程检查记录](./assets/stage4/client-entry-verification-20260915.json)，源码与启动方式见 [Client Web](../../../client-console-web/README.md) / [Client BFF](../../../client-console-bff/README.md)。

- Platform 79 项聚焦测试通过，覆盖此前认证规则及新增 CUSTOMER 审计投影、既有审计终态处理和模块边界。Client 八个写操作接入原审计流水线；复用物理链及既有 Hash 格式，以 CLIENT action 与 CUSTOMER/CLIENT 属性区分，未新增审计表。审计真实落库测试另一次执行仍在数据库隔离前置失败，未作为通过证据。
- Gateway 56 项聚焦测试通过：CUSTOMER/CLIENT 与原 Operations 的 JWT 合同、规范身份值、真实私网 JWKS 协议，以及同 kid 不同 Realm 公钥/缓存隔离。CUSTOMER issuer 禁止与 Operations 相同；`gateway.client.enabled` 未开启，未执行完整 Gateway/Redis 流程。
- Client BFF 10 项真实 HTTP 测试通过。覆盖登录 Cookie 不泄露、精确 Origin、空 Body、重复 Cookie、401 清理/503 保留、统一 202、退出与管理端口隔离。复现并修复 Foundation Writer 重置响应后丢失清理 Cookie 的问题；Owner 使用 loopback 协议 Simulator。
- Client Web 4 项测试与生产构建通过。检查邮件 fragment、Unicode 密码、单页刷新单飞/晚到响应、多标签 Cookie 请求串行。CUA 实际检查桌面登录、390×844 注册无横向溢出、导航、原生必填焦点、无效恢复链接及验证链接移除 fragment 后等待点击；没有通过浏览器创建真实账号或重置密码。
- Pencil 原文件 S01/S04 更新说明及确认密码，原生保存后无 Edited 标记；无裁剪/占位问题。增量 PNG 在 [auth-ui](./assets/stage4/auth-ui/)，阶段 3 历史导出未覆盖。本次不声称所有旧认证画板或资料页均已迁移；服务说明/隐私说明入口待后续提供目标页面。

该前轮真实注册、刷新重放、数据库锁定/撤销、审计写库与完整 HTTPS 浏览器闭环缺本机测试凭据；Gateway/Redis 的后续验证见上方增量。已编写的 Client HTTP 测试使用独立日期审计链并在回收前核对请求所有权，不清理其他记录。尚未执行数据库迁移、外部邮件配置、路由启用、部署、提交或覆盖率门禁。首批仍处于研发验证中，未进入阶段 5。

### 2026-09-15 认证增量

[检查记录](./assets/stage4/authentication-verification-20260915.json)保留本次命令、报告摘要及源码指纹：

- JDK 25 六模块编译和 47 项聚焦检查通过，覆盖注册/验证字段合同、会话时限、刷新令牌格式、链接策略、密码规则、CUSTOMER/OPERATIONS 密钥命名空间、SMTP 投递、Mapper 参数/列映射和架构边界。新增资源锁按账号/会话分开，旧 Operations 门禁仍保留。
- SMTP 检查通过实际 JavaMailSender 与本机随机端口协议 Simulator，验证注册和密码恢复的 MIME、收件地址与 fragment 链接。不向外部收件人发信，也不代表真实邮件供应商已配置或可投递。
- 已编写 3 个真实 HTTP → Platform → 本地 MySQL/Redis 用例，覆盖注册验证、登录刷新、正确旧令牌重用撤销、密码重置撤销、失败计数和重复退出；Vault 与邮件仅在外围使用 HTTP/SMTP Simulator。执行尝试在数据库隔离前置失败，Surefire 报 1 个类级错误，3 个业务用例均未运行。当前未注入本机测试凭据；测试不会自动读取 `.env.local` 或创建表。
- 注册和验证保持 A01 email/password、A03 challengeNo/secret；GET 不消费邮件链接，验证成功不签发登录会话。会话代码采用 7 天绝对期限、24 小时闲置期限及不超过 15 分钟的访问令牌；重置按 CUSTOMER Account 撤销旧会话。上述数据库写入和并发行为仍待真实回归。

该前轮没有运行完整 Maven `verify`、覆盖率门禁或浏览器链，也未启用 Client 入口；当时的审计与客户端缺口由上方四工程增量继续处理，Tenant 工作区与真实集成仍未完成。

### 2026-09-14 首批证据基线

[原检查记录](./assets/stage4/first-batch-verification.json)保留当时的命令、数量和源指纹，其中密码流程待决项已由 2026-09-15 用户决定关闭，不改写历史证据：

- 120 项模型/Schema/批准投影检查通过，核对字段集合、类型、长度、默认值、可空性与唯一语义。
- CCT 21 项局部策略、合同、应用配置、健康和错误格式测试通过；其中 Checkout 策略 14 项覆盖草稿拒绝和批准分类确认。更广的 25 项尝试中 4 项 HTTP 边界因本地数据库凭据缺失而启动失败，未静默跳过。
- Platform 22 项链接、密码、加密兼容与模块边界检查通过；另 5 项配置和全量 Mapper XML 编译检查通过。邮件 Origin 缺失主机名先复现 NullPointerException，再修正为预期配置拒绝；未连接 SMTP。
- [迁移候选及检查命令](./db/implementation/README.md)通过静态重复生成与 ALTER 结果比对；CCT 10 ALTER、Risk 3 ALTER + 1 CREATE、Platform 7 CREATE。没有执行任何数据库 SQL。

这些历史测试仅证明所列局部能力；新的 SMTP Simulator 结果见上。CUSTOMER 并发注册、预注册防护、真实邮件投递、数据库中的刷新重放/会话撤销、完整入驻审核与浏览器交互仍未通过验收。未重画 Pencil、部署、提交或推送。

## 工件兼容说明

阶段 3 字段投影及源模型指纹继续作为冻结评审基线，不再对新模型运行旧生成器。四个 CCT 索引采用符合现行长度规范的短名，列及唯一语义不变；Risk 使用现有 DSL 审计列置后的物理顺序。当前新增迁移生成器核对 HEAD 源模型指纹后生成文件，实际环境还必须执行既定只读预检、核对 SHOW CREATE、备份并取得精确执行授权。
