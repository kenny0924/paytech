# Client API 接入管理：交付记录

当前阶段与跨仓库实现、验证和部署状态统一见 [CURRENT_IMPLEMENTATION.md](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-client-api-access-20260913)。本文保存阶段授权与交付入口，不把需求方案表述为实现事实。

## 2026-09-13 方向确认与阶段 1 开始

前序菜单权限与 API 配置管理已完成本地代码提交。用户询问下一期功能后，收到「Client API 接入管理：凭证生命周期、Scope 目录与授权关联、实际接口验证」建议，并回复“可以”。据此确认继续需求评审，管理入口先在 Operations；不推定详细方案、代码研发、数据库执行、部署或提交已获授权。

按 [逐阶段流程](../../../agent-docs/REQUIREMENT_DELIVERY_WORKFLOW.md)建立本需求目录，读取既有 Credential/Scope 合同、代码及当前实现证据。新增 [需求范围评审稿](./requirement.md)，包含 11 项功能、11 项功能验收和 6 项 UI 验收；[技术入口](./technique.md)仅记录证据与下一阶段清单。

已单独询问轮换旧凭证的失效时机：建议新旧并存，客户切换后由管理员手动撤销；在用户回复前 Q1 保持待确认。其他范围选择随阶段 1 一并评审。

下一确认点：用户确认 Q1、Scope 目录简化范围及整体验收目标后进入阶段 2，完成现状差异、逐系统影响和真实联调前置分析。本轮只处理需求文档，不修改应用实现、模型或数据库，不部署、不提交、不推送。

## 2026-09-13 Client Console 与 Operations 职责修订讨论

用户指出凭证应由 Client Console 处理，Operations 应有查看，并询问是否需要管理能力。已核对入口分离及 Platform Credential Owner 的既有合同；日常生命周期改建议由客户自助完成，Operations 提供非秘密信息查询、Scope 目录与 Route 关联维护，可选保留单独授权并有原因/审计的紧急撤销，不默认获得创建、轮换、修改客户凭证 Scope 或取回完整凭证的权限。

该修订目前为建议，不把用户问句视为已批准紧急撤销或 Client Console 研发。原 Operations 全生命周期稿及原阶段 2 清单暂停；Q1 轮换时机顺延到 Client 自助需求。先确认修订范围，再整理功能和验收条目。修改仅为文档，尚未进入阶段 2、开发或环境操作。


## 2026-09-13 修订范围确认与阶段 2 交付

用户在职责修订建议后回复“可以”，确认本期只交付 Operations 凭证查看、Scope 目录与 Route 关联、独立授权的紧急撤销；Client Console 自助创建、轮换、日常撤销及凭证授权调整另期处理。此前原稿的 Operations 全生命周期、真实业务 Owner 首次开通目标及轮换 Q1 不再是本期前置，以上两段保留为评审历史。

据此将 [requirement.md](./requirement.md) 定稿为 9 项功能、10 项功能验收、6 项 UI 验收，并完成 [阶段 2 差异与逐系统影响](./technique.md)。候选规模为 3 个应用、约 9 项新 API 能力及 4 项既有 API 调整；模型只新增一个 Scope 目录，Credential/Grant 复用旧模型，暂不增加旧业务字段。接口精确统计、新表字段、查询索引和 DML 行数留阶段 3。

只读检查 test 的目标表与计数，确认该环境缺两张旧 Credential/Grant 表（合计 21 字段），且 CLIENT Tenant、API_SCOPE Route 当前均为 0；因此 test 预计需建 3 表，其中 2 表是旧设计补齐，不是新增三套模型。观察范围见 [只读计数证据](./assets/stage2/test-database-observation.json)。没有读取完整凭证或 PHC，没有应用 SQL、创建示例或部署。

同步正式需求目录 PLT-02、PLT-16、PLT-I06、PLT-I19 和 E2E-16 的已确认职责与目标时序；保留 PLT-I03 当前认证 Wire。新增绑定目录校验、旧数据兼容、各自权限及审计对象筛选的精确合同仍待阶段 3 定稿。应用代码、模型和运行配置未改动。

下一确认点：接受阶段 2 分析后，进入阶段 3 精确接口/表字段与关系图/SQL/Pencil 页面方案；确认阶段 2 不等于允许研发、SQL 执行、发布或提交。

阶段 2 文档核验：3 份 Markdown 的 33 个本地链接（含 4 个锚点）、9/10/6 功能与验收编号及空白检查通过；需求站点生产构建和 136/136 检查通过，日志 `/tmp/plt-client-api-stage2-requirements-tests.log`。没有应用级功能测试，本阶段成果仅为已核对的范围与影响分析。

## 2026-09-13 阶段 2 确认与阶段 3 方案交付

用户回复“OK”，确认阶段 2 差异及影响分析，进入阶段 3。已完成 [实施方案](./design.md)、[精确 API](./api.md)、[数据字段与关系](./data-design.md)、[SQL 评审入口](./db/README.md) 及 [Pencil 页面与交互](./ui.md)。本期边界继续为 Operations 查看/Scope 管理/紧急撤销，Client 自助生命周期另期处理。

精确规模：3 个应用新增 9 项 API 能力、调整 4 项既有 API（Platform/BFF 合计 18 新端点、8 改端点）；模型新增 1 表/7 字段/1 主键/1 唯一索引、两旧表各增加 1 个普通查询索引，旧业务字段新增 0；test 另补两张原模型表，共需建 3 表/28 字段。注册文件插入 127 条配置记录、更新两条旧 Route 的版本与修改信息；新 Route/资源/角色授权初始不生效，无 Client/凭证/Scope 业务种子。

A09 直接复用 GATEWAY_CONFIG_READ，不增加通用 selector usage 或 HUMAN_AUTHENTICATED 名单；Gateway 无本期业务代码改动。Route 新/变更 Scope 绑定检查目录，未登记旧值保持原绑定和认证兼容。BFF A06 按用户选择聚合一种引用并保留各自权限。Scope 无状态/删除/历史表，写响应不虚构 ACTIVE；紧急撤销只改变指定凭证，原授权行保留。

Pencil 在原 54 个画板后追加 8 个页面状态与 1 张完整字段关系图，已保存至 Web 的 docs/operations-console.pen，共 63 个顶层画板。导出 [8 页 PDF](./assets/stage3/export.pdf) 与 9 张 PNG；新画板布局检查无裁剪，已检查页面与关系图渲染并修正下拉符号缺字。仅设计源文件发生变动，未改 Web 功能代码。

下一确认点：用户整体确认阶段 3 后进入阶段 4“代码研发及开发回归”。本阶段未生成正式模型、应用 SQL、创建示例、发布制品、部署、提交或推送；后续本机 SQL 若有缺口，另给真实目标与具体文件确认，不把本次设计确认当作环境写入授权。

阶段 3 验证：接口清单、127 条 INSERT 的字段/权限/Route/Query/Header/Guard 关系、两旧 Route 更新目标、两旧表与正式 Schema 逐段一致、3 表字段 10/11/7、功能验收 9/10/6、本地链接及空白检查通过。Pencil 9 个新画板无裁剪，PNG/PDF 导出结构检查通过；证据见 [设计静态检查](./assets/stage3/design-verification.json)。需求站生产构建及 136/136 检查通过，日志 `/tmp/plt-client-api-stage3-requirements-tests.log`。本阶段未执行 MySQL SQL、EXPLAIN、Java/HTTP 或 Web 功能测试。

## 2026-09-13 阶段 3 确认与阶段 4 研发

用户回复“可以，没啥问题”，确认阶段 3 并授权进入代码研发及开发回归。按已确认范围实施 Platform、Operations BFF、Operations Web 及关联模型/Schema/注册工件；复用原 v1 合同，Client Console 日常凭证生命周期不在本期。阶段 3 的“尚未研发/待确认”段落保留为当时交付历史。

本机只读核对已有独立 `mor_platform_config_validation` 验证库，旧 Credential/Grant 表均在；已准备 [本机应用方案](./db/local-application.md)，单独询问是否允许备份两表 Schema 后应用 [ddl.sql](./db/ddl.sql)。该申请只新增 Scope 目录及两索引，不包括注册 DML、test、发布或提交；等待期间继续不依赖新表的实现及检查，不复用其他需求的数据库授权。

随后沿真实测试入口核对发现，OperationsFixtureMocker.open 严格要求完整初始化目录，不能只靠临时业务夹具绕过。因此修订本机方案并准备从正式初始化逐行抽取的 [local-registration.sql](./db/local-registration.sql)，只增加本期 127 条未启用注册基线。该增加范围未执行，需用户明确确认；原仅 DDL 申请不覆盖本机注册。没有修改或取消原初始化目录门禁。

研发入口、已运行检查和仍需完成的数据库回归见 [development.md](./development.md)；当前跨仓库实施/环境状态仅在 [CURRENT_IMPLEMENTATION](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-client-api-access-20260913) 维护。只有阶段 4 必要回归完成后才交付该阶段确认，不提前进入综合验收或发布。

## 2026-09-13 本机 SQL 申请确认与应用

用户针对修订后的 DDL + 127 条未启用注册申请，先询问新增目录表的必要性及其取值。已解释凭证、凭证授权、全局 Scope 目录的职责及 `checkouts.write`、`orders.read` 等既有合同示例；目录注册本身不授予凭证权限。用户随后回复“可以，没问题了”，据此确认按该具体本机申请继续。

已再次核对独立开发库、旧 Schema、空业务数据和精确注册键，无冲突后备份受影响两表 Schema，应用新表/两索引及 127 条本机基线登记。实际文件 Hash、备份位置及提交核对见 [本机执行记录](./assets/stage4/local-database-application.json)，执行前证据见 [preflight](./assets/stage4/local-database-preflight.json)。之前的待授权文字保留为历史。本次不包含 test、部署、制品发布、Git 提交或推送。

## 2026-09-13 阶段 4 开发回归交付

按已确认设计完成三应用、模型和 SQL 工件，并完成获准本机准备后的真实 MySQL/Redis/HTTP 回归。首轮凭证认证用例发现测试时钟固定为 2001 年而 PLT-I03 使用系统时钟，测试有效期随错误时钟设定而已过期；只修正合成凭证有效期，未修改认证业务语义。

完整 auto 首轮 447 项中有 5 个失败：旧 Schema 数量/表名、页面数量、原查询清单以及 Route 最小查询合同未同步。本轮按已确认新增项精确修订，保留完整目录 Oracle；相应用例复验通过。继续补齐有效期区间、真实 Lease/唯一索引、轮换来源/异常历史、筛选文字和幂等引用完整性场景，当前 480 项报告全部通过；最终 Maven verify 返回 0，指令与分支覆盖率均达到项目 90% 门禁。覆盖率采用项目原 append 配置，证据解释见 [verification.json](./assets/stage4/verification.json)，不表述为一次全新的 clean 全套运行。

BFF、Web、模型及需求站此前检查保持通过，逐项数量和范围见同一记录。代码审查与 AC 对照见 [development.md](./development.md)。查询索引验证只用本机回滚合成事务；[回归后核对](./assets/stage4/local-database-post-regression.json)确认 127 条登记完整，Scope、凭证、Operations 账号和活动签名密钥无夹具遗留。

本阶段交付已完成，等待用户确认进入阶段 5 综合验证，范围为浏览器页面、权限及完整调用链。本期尚未进行该浏览器验收，未发布制品、操作 test 或 Git 提交/推送；当前状态只由下方链接的台账维护。

## 2026-09-13 阶段 4 确认与阶段 5 开始

用户在阶段 4 完成报告后回复“继续”，确认进入阶段 5 浏览器、权限及完整调用链综合验证。复用已有开发证据，并使用本机独立验证库与精确归属的临时账号、目录、凭证及 Route 夹具；新增 [验收启动器](./assets/stage5/harness/README.md)说明临时启用和恢复范围。此确认不包括 test、制品发布或 Git 提交。

## 2026-09-13 阶段 5 交付

已完成真实 Web/Gateway/BFF/Platform 及本机 MySQL/Redis 综合验证，交付 [验收报告](./acceptance.md)与[浏览器记录](./assets/stage5/browser.md)。修复查询框零宽和中文 Scope 搜索字符集错误；后者补充真实 HTTP 失败证据后修复，完整管理回归、生产 Web 构建及浏览器复验通过。

本轮自有进程、浏览器和临时数据已清理，[清理证据](./assets/stage5/cleanup.json)核对原未启用配置恢复。当前等待用户确认阶段 5 结果并进入阶段 6 test 发布准备；本次没有操作 test、发布远程制品或 Git 提交。具体后续部署目标、SQL、启用清单与恢复动作由阶段 6 形成可审阅方案后再确认。

## 2026-09-13 直接部署 test 授权

用户在阶段 5 验收交付后明确要求“部署test”。据此本次合并阶段 6 发布准备与阶段 7 执行，不再停留在仅准备方案。范围为已确认的 Platform/BFF/Web、test 所缺三表与索引、127 条注册/两旧 Route 查询合同更新、已有 PLATFORM_ADMIN 的本期入口及权限启用；不创建客户/凭证示例，不发布 Nexus 制品，不提交或推送。执行前核对实际对象并保留一次必要配置前像，具体清单与结果见后续 release.md/test-acceptance.md。

## 2026-09-13 test 发布交付

按用户“部署test”执行了 [发布单](./release.md)。三应用、SQL 和既有管理员的新入口权限已部署，Gateway 自动加载。运行状态及精确镜像/数据库结果以 [CURRENT_IMPLEMENTATION](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-client-api-access-20260913) 为准；轻量部署检查见 [test-acceptance.md](./test-acceptance.md)。

打包过程中发现旧探针二进制不包含 BFF 当前健康端口，重新编译现行探针源码后恢复健康；无业务代码变更。未重复阶段 5 完整验收，未生成 test 业务示例。下一步由用户进行业务验收；验收不自动包含 Git 提交/推送授权。

## 2026-09-14 本地 Git 提交授权与归档

用户明确要求“git提交”，本轮据此提交本期应用源码、模型、需求与发布证据。应用提交为：platform-support-service `3cb5e31f`；operations-console-bff `85927a7a`；operations-console-web `4ebeed0e`；见 [提交清单](./assets/stage8/application-commits.json)。本次不包含推送、再次部署或 SQL 应用，也不据此将未明确验收的业务条目标记完成。

复查 Web 923 项、模型/Schema 38 项、需求站构建及 136 项通过，阶段 5 修复文件 Hash 一致；Java 复用已完成的阶段 4/5 回归及阶段 7 发布证据。共享根台账按段落暂存，其他任务改动保留。修正本期文档链接和过期“待评审/未应用”状态，没有变更业务实现。
