# tax 结账算税与事件登记 · 阶段 6 报告（mor-test 发布准备，2026-10-29 19:30—20:50Z）

> 范围：只读核对 mor-test、本机构建与演练、写发布文件。**未改 mor-test 任何东西**：未停 / 起 / 重启容器，未执行写 SQL，未写 XXL / MQ / Platform，未登录 XXL Admin 或运营 API，未发布语言包，未 `docker cp` 写入容器。本机只用 `mor-local-*`，未动本机 e2e 进程。未提交、未推送；未改主工作区与其他会话的 worktree，未改 `test-environment/compose*.yaml`。阶段 7 的环境变更由主会话执行，须用户授权。
> 材料：[baseline.md](./baseline.md)、[runbook.md](./runbook.md)、`sql/`、12 个 `compose-*.yaml`、`harness/`、`scripts/`、`acceptance/`、`tax_bundles.py` + `i18n-bundles/`、`precheck/`、`evidence/`。

## 1. 结论

| 项 | 结论 |
|---|---|
| 基线 | 6 个服务的现网都可由 main 复现：tax（空壳）、ops-bff、Platform JAR 与 main **0 差异**；acquiring / billing / account 自身模块 0 差异（只差已上线提供方的 api JAR 与 `events.yaml`）；ops-web 现网是**工作流节点编辑体验 `30361be7`**（阶段 6 开始前 4 分钟上线并提交），258 个文件与 `30361be7` 构建逐字节相同。详见 baseline 第 1 节 |
| 候选源码 | `/Users/kenny/Desktop/Workspace/Code-MoR-tax-rel` = **`30361be7`**（不是任务卡写的 `a90e4292`；**主会话已确认此基线**；`a90e4292..main` 只有 `30361be7`，与本需求 379 个文件交集为 0，两次核对）+ tax 研发 worktree 379 项（`git status --short`，M 140 + ?? 238 + D 1，含主会话补的目录测试修正）：366 个原样复制、9 个三方自动合并、3 个冲突取 `Code-MoR-taxcombo` 已做的合并（两边输入与 taxcombo 当时逐字节相同）、1 个删除。清单与哈希 `evidence/candidate-source-files.txt` |
| 测试 | 全 reactor `clean test-compile` **121 模块 SUCCESS**；tax **62 / 0**、acquiring **916 / 0**、billing **142 / 0**、account **68 / 0**、ledger **332 / 0**、ops-bff **2481 / 0**（含 TaxOrderBrowser 20、RiskUnderwritingBrowser 9）、arch-rules `TableOwnershipAutoTest` **7 / 0**；ops-web `npm test` **1631 / 0**、`build` 通过（dist 266）、`i18n:export-source` 通过（26 命名空间 10 473 key，tax 213）；messaging 模块 **73 / 0**（F-1 修正同步后 clean 重跑）。未跑全 reactor 测试。`evidence/test-summary.txt` |
| 镜像（`tax-20261029a`） | tax `sha256:a5616e20…`、acquiring `843382e3…`、billing `adfd0817…`、account `8a21dab8…`、ops-bff `e243d57b…`、ops-web `748ca7b9…`（全 ID 见 runbook 第 1 节）；镜像内 JAR / dist = 构建产物，health-probe、nginx.conf、Entrypoint / User / Healthcheck / 端口 / WorkDir / Env 与现网相同（`evidence/image-verify.txt`）。**tax 回滚镜像** `884728dc…`（现网镜像记录已删，F-3）。候选 JAR 与基线的差异全部来自本需求（`evidence/jar-candidate-*-vs-baseline.txt`） |
| Platform | **不随发**（D-3）：现网能力清单缺 `OPERATIONS_FINANCE_TAX` 只影响"菜单资源管理"页对该菜单的"支持"标注与编辑（`ConsoleResourcePolicy.supported/validate` 只在资源管理的列表 / 详情 / 新建 / 修改 / 启停处调用）；注册块由 SQL 直接写入、导航下发、权限与路由都不受影响。ledger（只改测试）、payment（无改动）不发 |
| SQL | 6 个执行文件与研发仓库文件**逐字节相同**；AE001 Party：mor-test 实际值 **880000000702** = acquiring MOCK 配置值 = 初始化文件原值，无需替换；mor_tax 24 张旧表**全部 0 行**。离线演练（mor-test 只读导出 → 断网 `mysql:8.4.11` → 候选 Platform JAR 的生产 `GatewayConfigurationCompiler`）**全部符合预期**：块 146 基线 / 休眠 / 激活 / 停用 / 删除 5 次编译 OK（effective 631 → 631 → **633** → 631 → 631）、重复注册被拒、激活中删除被拒、有 tax 文件时删除被拒；mor_tax 重建 + 初始化 0.94 s + 0.30 s、重复初始化被拒、回滚后与导出 **0 行差异**；acquiring 迁移 0.64 s、重复迁移被拒、**有新单时回滚被拒且不做改动**、无新单时回滚后与导出 **0 行差异**；billing 注释前滚 / 重跑 / 回退后 0 差异。`evidence/rehearsal-20261029T2040Z-block146/` |
| 注册块 | **146**（跨会话约定：144 联合查询、145 REQ-04、146 本需求；现网最大 143、146 区间为空，F-5）：权限 1、授权 1、菜单 1、路由 2、请求头 8、Guard 12、文件写入路由 1。两条路由与同形现网路由（134411 GET、134412 下载 POST）属性 / 请求头 / Guard 逐项相同；写入路由复制默认私有路由（桶 `mor-platform-files`、扫描策略 `CLIENT_ONBOARDING_FORMAT` 已允许 `application/pdf`、900 s） |
| MQ | 唯一用户 `mor_test` 为 `.* .* .*`，tax 用同一账号即可在启动时声明 `tax.acquiring.hash / p0..p3 / dlx / dlq` 并绑定已存在的 `acquiring.events`；无 policy 冲突 |
| XXL | 新建执行器分组 `tax-service`（自动注册，预计 id 18）+ `compensationEventScan`（参数照抄现有 12 个，预计 id 28）；`scripts/xxl_tax.py plan / apply / trigger / remove` |
| 内部认证 | **无需登记**：tax / acquiring / ops-bff 均为 OFF；Platform 为 DUAL，文件接口没有调用方白名单，按 `X-Caller-Service=tax-service` 识别来源，写入只需块 146 第 C 段的写入路由；tax 入站 OFF 不校验 |
| 存储证书 | MinIO `https://minio.mor-test.localhost:29400` 证书由 `private/test-ca.crt` 签发（`openssl verify` OK）；`private/platform-truststore.p12` 含该 CA，Platform 与 card 已挂载使用。tax 候选覆盖以同样方式挂载并设 `JAVA_TOOL_OPTIONS` |
| compose | 12 个覆盖文件；回滚 6 个与现网**逐键逐值相同**；候选只多：tax 17 键（RabbitMQ 5、Platform 地址、补偿实例、`PAYTECH_INSTANCE_ID`、XXL 8、`JAVA_TOOL_OPTIONS`）+ 信任库只读挂载、acquiring `ACQUIRING_TAX_SERVICE_URL=http://tax-service:18116`（并保留 relsnap 覆盖的 `MOCK`）、ops-bff `OPERATIONS_TAX_BASE_URL=http://tax-service:18116`。Snowflake 沿用 tax 现有 18（0–31 内唯一）；tax 内存沿用 448m / small JVM（account 同构栈实测约 200 MiB） |
| 语言包 | 只发 operations-console：当前 **#23**（REQ-03）→ 新建命名空间 `tax`（"税务"，sort 270）、新增 214（tax 213 含两个 `DONE_BLOCKED`、nav 1 = `nav.resource.OPERATIONS_FINANCE_TAX`）、基准变更 / 废弃 / 恢复 **0**、zh-CN 导入 214、`zhChangedNotImported` 0；**REQ-03 #23 的 10 688 个 key 原样保留**（只读计划 `precheck/i18n-plan-dryrun.out`） |
| 验收脚本 | `acceptance/tax_api.py`（preflight / before / after / scenario / synthetic / check，全部 `--dry-run` 通过）、`acceptance/tax_browser.mortest.mjs`（`node --check` 通过）；mor-test 上只能在阶段 7 实跑 |

## 2. 发现

1. **F-1 messaging 目录测试未更新（已解决）**：`paytech/foundation/paytech-messaging/src/test/java/com/paytech/foundation/messaging/auto/catalog/YamlEventCatalogAutoTest.java` 第 53 行仍断言 `OrderPaymentSucceeded` 的消费方为 `[billing]`，本需求把 `events.yaml` 改成 `[billing, tax]`。主会话已在研发 worktree 改为 `assertEquals(List.of("billing", "tax"), orderPaid.consumers());`，已同步到候选；messaging 模块 clean 重跑 **73 / 0**（同步后首次未 clean 的运行用了旧 class；另有一次 `PartitionQueueBaselineAutoTest` 计数时序失败，clean 重跑通过，属共享本机 RabbitMQ 的偶发）。测试文件不进 JAR，**镜像不变**。
2. **F-2 基线前移**：任务卡基线 `a90e4292` 之后 main 提交了 `30361be7`（工作流节点编辑面板，现网 ops-web `a9d50000` 的源）。若按 `a90e4292` 构建 ops-web 会回退该页面。候选已改为 `30361be7` + 本需求（无重叠文件）。
3. **F-3 tax 现网镜像记录已删除**：容器 `mor-test-tax-service-1` 的镜像 `aeebac7a…` 在本机不存在（2026-10-18 由 `:development` 创建后被强制删除）。容器一旦替换就无法按 ID 起回。已用同一 Dockerfile + 现网 JAR + 现网 health-probe 重建回滚镜像 `884728dc…`（JAR 与现网逐字节相同）。→ D-4。
4. **F-4 `:development` tag 全部缺失**：本机 6 个服务的 `mor-test/<svc>:development` 都已不存在；不带覆盖文件执行 `compose.sh up` 会失败。只认 sha256 的发布方式不受影响，但别的会话若按主 compose 起服务会撞上。
5. **F-5 注册块号冲突**：现网最大 143；**联合查询**会话已在 `Code-MoR-jq` 的 `platform-support-initial-data.sql` 与 `changes/2026-10-29-joint-query-registration-144-*.sql` 中使用 144，risk-decision **限额档位**技术方案（阶段 2—3）也写了"下一块 144"。**已按主会话协调的约定改为 146**：mor-test 144 = 联合查询（design-discuss-19）、145 = REQ-04（design-discuss-b8）、146 = 本需求；限额档位须用 147 或之后。SQL 写死 146（ID 段 `900000000000146xxx`，同既有块号约定），runbook ③ 的 `mor_platform-block-146-precheck.sql` 在执行前再读现网确认 146 区间、编码与路径仍空——不空即停。→ D-6。
6. **F-6 mor-test 没有能成功的卡渠道**：支付尝试 6 次，唯一成功是 10-22 PAYERMAX（3DS 验收后 MID 已 SUSPENDED、能力 DISABLED）；之后路由到 DEMO_NI 的全部 FAILED（到期确认验收记录为 `THREE_DS_CHANNEL_UNAVAILABLE`）。**真实付款无法在 mor-test 完成**，tax 正式事实、税票、billing → account → ledger 只能经合成 `OrderPaymentSucceeded` 验证。→ D-2。
7. **F-7 MOCK 商业配置的价格 / 合同 ID 在 mor-test 不存在**：acquiring MOCK 写死的 `clientPricingVersionId 104 / partnerShareVersionId 108 / platformChargeVersionId 109 / clientAgreementId 105` 在 `mor_commercial` 0 行。即使有真实付款，billing 取价也会失败、重投 5 次后进 `billing.acquiring.dlq`（本机阶段 5 的 B-6 同因）。与本需求无关、发布前就存在，但决定了 billing 端到端只能用合成事件里的 DEMO-CL-001 有效版本。mor-test 迄今**没有任何真实订单形成 PAYMENT 事实**（唯一一条是 billing B 的合成事件）。
8. **F-8 发布后 MOCK 订单的阻断面比"STANDARD 税类"更大**：MOCK 下每笔订单的 TAX_RESPONSIBLE 都是 AE001（有 AE / SA / EG 准入）。tax 判定顺序是"供应地无准入 → MARKET_NOT_ENABLED"先于"未知税类 → UNKNOWN_TAX_CATEGORY"。因此：
   - 声明 / 账单国家不在 AE、SA、EG 的订单，**无论税类**一律 BLOCKED、不可支付；
   - 在 AE / SA / EG 但税类不是 `SAAS_AUTOMATED`（如 `STANDARD`）→ BLOCKED（UNKNOWN_TAX_CATEGORY）；
   - 只有"AE / SA / EG + `SAAS_AUTOMATED`"能得到 READY。
   现网 51 笔订单的 81 个订单行**全部是 `STANDARD`**；下列脚本全部用 **US + `STANDARD`**（只改税类也不够）：

   | 文件（行号） | 所属需求 / 会话 | 环境 |
   |---|---|---|
   | `requirements/20261022/acquiring-到期后支付结果确认/release/acceptance/accept_result_after_expiry.py:276`（US 声明 + US 账单） | acquiring 到期后支付结果确认 | **mor-test** |
   | `requirements/20261023/commercial-商业关系快照与真实解析接入/release/acceptance/stage7_relsnap.py:199`（US） | commercial 商业关系快照 | **mor-test** |
   | `requirements/20261022/acquiring-到期后支付结果确认/verification/stage5_result_after_expiry.py:115`（US + US） | 同上 | 本机 |
   | `requirements/20261022/acquiring-订单过期收尾/verification/stage5_expiry.py:109`（US） | acquiring 订单过期收尾 | 本机 |
   | `requirements/20261023/commercial-商业关系快照与真实解析接入/verification/local-e2e/stage5_backend.py:104`（US） | commercial 商业关系快照 | 本机 |
   | `requirements/20260914/client-console-注册与客户入驻/verification/local-e2e/commerce_webhook_flow.py:122,124`（US） | client-console 注册与入驻 | 本机 |
   | `requirements/20261017/commerce-ClientAPI测试环境联通/verification/client_api_flow.py:203`（US） | commerce Client API 联通 | 本机 / mor-test |
   | `requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/client_api_flow.py:365`（US） | V2 骨架 | 本机 |
   | `requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/webhook_flow.py:123,125`（US + US） | V2 骨架 | 本机 |

   文档示例：`docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md:299,308,652`、`requirements/20260916/commerce-订单查询合并Checkout/order-paid-response.json:17`（只是示例，不执行）。本机 e2e 已在阶段 5 换成新 tax（同样受影响）。产品代码（client-console、checkout-web、BFF）没有任何写死税类的调用方。→ D-1。
9. **F-9 Platform 能力清单**：现网 Platform 缺 `OPERATIONS_FINANCE_TAX`（候选源码有）。不发 Platform 时，"菜单资源管理"页把税务菜单标为不支持、拒绝在页面上编辑 / 启停；导航、权限、路由正常。→ D-3。
10. **F-10 ledger 2026-11 期间为 FUTURE**：合成事件以发布时刻为业务时间；10-31 23:59Z 之后发布，ledger 过账会失败进 DLQ。→ D-5。
11. **F-11 Platform 测试未能运行**：本机共享 `mor_platform` 的测试独占表（`t_permission`、`t_role`、`t_role_permission`、`t_jwt_signing_key`、`t_data_source`）有他人数据，`OperationsRoleManagementTest` 在前置检查处拒绝运行；按规则未清空他人数据。能力清单 JSON 的改动只由该测试启动时加载覆盖，**本阶段未验证**（不发 Platform 时无影响；若 D-3 选随发，需先在干净的本机库跑通）。
12. **F-12 acquiring 发布有"不可回头点"**：迁移后第一笔新订单（快照为空）出现后，`mor_acquiring-rollback.sql` 会被约束拒绝（演练已证），旧镜像又不能写 NOT NULL 的 `amount_status`。runbook 把验收建单放在全部镜像核对之后（⑮），之前任何一步失败都可完整回滚。
13. **F-13 环境状态**：本机磁盘 99%（阶段 6 期间可用 5.0—7.9 GiB，主要被他人镜像与卷占用，未清理他人数据）；XXL Admin 在阶段 6 开始时刚重启（非本卡所为）。
14. 其他已知（不阻断）：tax 上传失败日志无原因（阶段 5 B-3）；阿拉伯语税票未实现（D-AR 方案 A）；底稿全部 PENDING(FX_RATE_MISSING)（D-01，设计如此）。

## 3. 风险

| # | 风险 | 缓解 |
|---|---|---|
| R-1 | 验收建单后 acquiring 不能回滚（F-12） | 验收前完成全部镜像与注册核对；失败处理改为前修（保留新库结构），由主会话评估 |
| R-2 | 发布后其他会话在 mor-test 建的 MoR 订单（US / STANDARD）全部 BLOCKED（F-8），可能被误判为故障 | P-3 通知；D-1 |
| R-3 | 他人在阶段 7 前替换 ops-bff / ops-web / Platform（联合查询、限额档位、REQ-03b 均在进行）或 main 前移 | runbook ① `source_drift_check.sh`；不符即停并重建 |
| R-4 | 合成事件业务时间落入 11 月 | D-5；`tax_api.py synthetic` 内置日期门禁（非 10 月拒绝发布） |
| R-5 | 合成事件让 S1 订单在 tax / billing 已"支付"、在 acquiring 未付（到期 EXPIRED）——验收数据跨系统不一致 | 同 billing B 先例；delivery.md 记录；合成支付 ID 段 `9999000000000003xx` 便于识别 |
| R-6 | 语言包号被他人先占（联合查询计划 #24） | ⑭ 先只读计划；门禁拦截任何非 tax / nav 变化或废弃 |
| R-7 | tax 回滚依赖重建镜像（F-3） | 回滚镜像 JAR 与现网逐字节相同、元数据一致；D-4 |
| R-8 | 新 tax 首次真实运行在 mor-test 的 448m 内存与对象存储直传 | ⑥ 后看 `docker stats` 与日志；税票失败由补偿扫描每 30 秒重试、不换号（阶段 5 已证） |

## 4. 待裁决

| # | 事项 | 选项 | 建议 |
|---|---|---|---|
| D-1 | 发布后 MoR 订单的阻断面（F-8）：现有脚本与演示单（US + STANDARD）都会 BLOCKED、不可支付 | A 接受，按设计 BLOCKED；主会话通知各会话，脚本在下次使用时改为 AE / SA / EG + `SAAS_AUTOMATED`（本卡不改别人的文件）。B 发布前各会话先改脚本。C 加一份演示兼容 DML：AE001 增加 US 准入（`NO_COLLECTION_REQUIRED`，零税、不收）并新增 `STANDARD` 税类，让 US / STANDARD 订单回到零税可付——但这是虚构的"美国不征税"事实，且未经本机验证，需研发补测后另行评审 | **A**（mor-test 当前本就无法完成真实付款，BLOCKED 的实际影响是"订单不可付"提前暴露；B 与 A 不冲突） |
| D-2 | billing → account → ledger 能否在 mor-test 端到端验证（AC-09、AC-10 金额一致） | A 合成事件（runbook A7，billing B 先例）：用 S1 真实订单 / FINAL 计算 + DEMO-CL-001 有效商业字段 + 合成支付 ID，一条事件同时驱动 tax 与 billing，核对 SELLER_TAX 15.00 → TAX_PAYABLE 15.00 与 tax 底稿一致。B 不做，接受 auto 测试（billing 142、account 68、ledger 332）。C 先恢复一个可用卡渠道（重开 PAYERMAX MID）或把 acquiring 切 REAL——都超出本需求、影响他人 | **A**；须在 2026-10-31 23:59Z 前（D-5） |
| D-3 | Platform 是否随发（只差能力清单 1 项） | A 不发（任务卡规则：缺清单不会导致注册或菜单失败）；B 随发（需先在干净的本机库跑通 Platform 测试，F-11，再构建镜像） | **A**；下一次 Platform 发布时顺带 |
| D-4 | tax 回滚目标改用重建镜像 `884728dc…` | 接受 / 不接受（不接受则 tax 实际不可回滚到旧空壳，只能前修） | 接受 |
| D-5 | 合成事件时间窗 | A 阶段 7 在 2026-10-31 23:59Z 前完成 A7；B 之后执行则先由财务把 ledger 2026-11 期间打开（ledger 运营操作，另行授权） | A |
| D-6 | 注册块号 | **已定**：144 联合查询、145 REQ-04、146 本需求（限额档位 147+）；执行前预检确认 146 仍空 | — |
| D-7 | F-1 测试修正 | **已解决**（主会话修正，候选 messaging 73 / 0） | — |

## 5. 需要用户授权的阶段 7 动作

**主发布（一次授权覆盖下列各项，由主会话执行，顺序见 runbook 第 3 节）**
1. mor-test `mor_platform` 执行注册块 146：休眠写入（含 tax-service 私有文件写入路由）→ ops-bff 之后激活。
2. mor-test `mor_tax`：root 执行 `sql/mor_tax-rebuild.sql`（删旧 24 张空表、建新 16 张）与 `sql/mor_tax-initial-data.sql`（演示数据，AE001 Party 880000000702）。
3. 替换 `tax-service`（`a5616e20…`，新增 RabbitMQ / XXL / Platform 地址 / 信任库挂载等 17 个环境变量）。
4. 经 XXL Admin API 新建执行器分组 `tax-service` 与任务 `compensationEventScan`，启用并手动触发 1 次。
5. `mor_billing` 执行注释变更 `sql/mor_billing-comment.sql`；替换 `billing-service`（`adfd0817…`）与 `account-service`（`8a21dab8…`）。
6. 停 `acquiring-service` → `mor_acquiring` 执行 `sql/mor_acquiring-migrate.sql` → 起新镜像（`843382e3…`，新增 `ACQUIRING_TAX_SERVICE_URL`）。之后新建 MoR 订单走真实算税（D-1）。
7. 替换 `operations-console-bff`（`e243d57b…`，新增 `OPERATIONS_TAX_BASE_URL`）与 `operations-console-web`（`748ca7b9…`）。
8. 发布 operations-console 语言包（新建 `tax` 命名空间，新增 214 个 key，预计 #24）。
9. 验收写数据：DEMO-CL-001 下建约 6 笔验收订单（含发布前 1 笔基线单）、保存消费者资料、对科威特单提交一次付款（期望 409）；运营税务页浏览器走查（含 1 次税票下载授权）。
10. （D-2 = A 时）在 2026-10-31 23:59Z 前向 `acquiring.events` 发布 1 条合成 `OrderPaymentSucceeded`（tax 与 billing 消费，产生 tax 正式交易与税票、billing / account / ledger 过账）。
11. 失败时按 runbook 第 5 节回滚（含 `mor_acquiring-rollback.sql`、`mor_tax-rebuild-rollback.sql`、块 146 停用 / 删除、XXL 删除、`mor_billing-comment-rollback.sql`）。

不在授权范围：提交、推送；Platform 镜像（D-3 = B 时另行授权）；删除回滚后积压的 `tax.acquiring.*` 队列；打开 ledger 2026-11 期间（D-5 = B）；修改他人脚本（D-1 = B 由各会话自行处理）；改 `test-environment/compose*.yaml` 基础文件。

## 6. 本机资源

- 发布 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-tax-rel`（HEAD `30361be7` + 379 项，含 `target/`、`operations-console-web/dist/`；`operations-console-web/node_modules` 是指向主工作树的**符号链接**，删除 worktree 前先删链接）。保留到阶段 7 结束，用于漂移后重建。
- 本机备份 `~/Desktop/Workspace/Code-MoR-backups/tax-20261029/`（0700）：`stage6-live/`（现网 JAR / dist / env 键名）、`candidate/`（候选 JAR）、`stage6-readonly-20261029T193746Z/`（六个库只读导出，0600，含数据，不入库）。
- 本机镜像：`mor-test/<6 个服务>:tax-20261029a` 与 `mor-test/tax-service:tax-rollback-20261029a`；未动 `:development`。
- 草稿目录中的基线构建树已删除；演练容器已删除。
