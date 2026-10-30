# REQ-04 限额档位 · 阶段 5 本机联调报告

> 只操作本机：`local-environment` MySQL 13306（`mor_risk_decision_v2e2e` 正向 SQL 与一行测试数据、`mor_platform_v2e2e` 注册块 145），本机 risk-decision / ops-bff JAR 进程，风控配置经真实运营 API 创建并激活。未碰 mor-test，未提交、未推送；本机 Platform JAR 未替换（见第 1 节）。**新 JAR 与带档位分支的发布保持运行。** 浏览器走查由 rq04-web 另行进行（第 7 节）。
> 方法：运营请求经 Nginx 18444 → Gateway（块 145 路由）→ ops-bff → compliance / commercial / risk-decision；额度矩阵经 risk-decision 统一评估入口（Payment / acquiring 调用的同一私网入口）；支付全链路以 acquiring 身份直调 Payment 内部接口（Intent → 卡工具 → pay），Payment 真实调用 risk-decision。脚本与过程数据在 [stage5/](./stage5/)。时间均为 UTC（2026-10-30）。

## 1. 构建、测试与路径选择

| 项 | 结果 |
|---|---|
| 组合工作树 | `/Users/kenny/Desktop/Workspace/Code-MoR-rq04combo`（detached 6eaf1bd3）= main + 联合查询未提交改动（只读复制自 `Code-MoR-jq-rel`）+ REQ-04（rq04 工作树相对 30361be7 的差异）。4 处冲突按"两边都保留"解决：arch-rules 表归属头注释与计数、`TableOwnershipAutoTest`、`configurationModel.test.ts` 页面权限数 83、Platform 初始化 DML 两个块顺序追加（144 联合查询，145 REQ-04） |
| 合并修正（F-3） | 第一次解决冲突时丢了块 144 末尾的 `COMMIT;`，`DataQueryJointRegistrationAutoTest` 4 例失败（按 `COMMIT;` 截块，读到了块 145）。已在组合树补回；只影响合并，rq04 工作树本身无此问题。**正式合并两个需求时须保留两个块各自的 `COMMIT;`** |
| 构建 | risk-decision、operations-console-bff、platform-support、arch-rules `-am install -DskipTests`：BUILD SUCCESS（46 s） |
| 测试（组合树） | risk-decision **1838 / 1838**；ops-bff **2529 / 2529**（补 `COMMIT;` 后失败类 5 / 5，其余首轮已通过）；arch-rules **70 / 70** |
| Platform | 采用主会话给出的退路：**本机 Platform 不替换**（pid 14518 为 design-discuss-1b 的联合查询 + 多语言变更记录构建）。块 145 只以 DB 行进入 `mor_platform_v2e2e`。`console-resource-capabilities.json` 的条目只影响菜单资源管理页，**本机未验证** |
| 运行中 JAR 不新于组合源码 | risk-decision 与 ops-bff：在运行 JAR 的全部条目、以及内嵌 `paytech-*` 库的全部类，均在组合 JAR 中存在（无"运行中独有"的类） |

## 2. 环境变更（时间、进程、回滚）

| 时间 | 变更 | 回滚 |
|---|---|---|
| 07:17:04 | 备份 `mor_risk_decision_v2e2e`、`mor_platform_v2e2e`：`/Users/kenny/Desktop/Workspace/Code-MoR-backups/rq04-stage5/local-20261030T071704Z/`（含 SHA256SUMS） | — |
| 07:17 | `mor_risk_decision_v2e2e` 执行 `2026-10-30-client-limit-tier.sql`：3 张表、注释、TIER 名单 300001—300004（57 → 60 张表） | `2026-10-30-client-limit-tier-rollback.sql`（须先回滚发布与 JAR） |
| 07:17:40 | 块 145 插入 `registration-145.sql`（DISABLED），快照照常刷新 routeCount 552 | `registration-145-rollback.sql` |
| 07:17:59 | 块 145 激活 `registration-145-activate.sql`：**routeCount 552 → 563（+11）**，之后每 5 秒刷新成功 | 同上 |
| 07:23:36 | risk-decision 46315 → **13106**（组合 JAR，`.pre-rq04` 备份，临时开 `RISK_LIMIT_TIER_MIGRATION_CALLERS=rq04-stage5-migration`） | `swap.py install risk-decision <jar>.pre-rq04` → `swap.py restart risk-decision` |
| 07:24:46 | 存量迁移 L12：4 个 Client CREATED；重放全部 SKIPPED_EXISTING；核对 **0 缺口** | 档位数据随 risk-decision 回滚 SQL 删除 |
| 07:25:09 | risk-decision 按原 argv / 环境重启（清空白名单）→ **pid 14475**；L12 返回 403 `RISK_LIMIT_TIER_MIGRATION_FORBIDDEN` | — |
| 07:27:57 | 已通知 design-discuss-19 / -24 后，ops-bff 73742 → **17066**（组合 JAR，`.pre-rq04` 备份） | `swap.py install ops-bff <jar>.pre-rq04` → `swap.py restart ops-bff` |
| 07:29—07:31 | 经运营 API 建拒绝原因 `LIMIT_TIER_DENIED`、30 个变量、9 个冻结规则集、2 个流程、2 个执行发布（SHADOWING），见 `stage5-risk-setup.json` | 变量 / 规则集为配置数据，可留存；不留存则在回滚发布后逐个删除 |
| 07:32:10 | 影子运行（PASSED）后**激活**：支付尝试发布 241826265252773888、结账发布 241826266834026496。回滚基线：支付 239045324600000512（流程版本 239045324239290369 `S5_CARD3DS`）、结账 239113350301106176（流程版本 239113349432885249） | `python3 -I stage5/stage5_risk_setup.py rollback`：以基线流程版本新建发布 → 校验 → 影子 → 激活（已作废发布不能重新激活） |
| 07:38 | IAM 新建 4 个角色 / 账号（req、appr、elev、ro，均含 `RISK_DECISION_CONFIG_READ`），凭据 `~/Code-MoR-backups/rq04-stage5/accounts.json`（0600） | 可在 IAM 停用 |
| 07:46 | 测试数据 `stage5/10-e-yesterday.sql`：E 昨日 USD 300 / 6 笔放行汇总（验证近 30 日额度） | `stage5/10-e-yesterday-rollback.sql` |
| 07:41—07:56 | 承保数据变化（真实运营 API）：AUTO 自动承保 v1 + 修订 v2；B 修订 v2、v3（各自直接 APPROVED）；E 草稿 v2 批准 | 不回滚（备份可恢复） |

**整体回滚顺序**：`stage5_risk_setup.py rollback`（先让基线流程重新生效）→ 恢复 ops-bff、risk-decision 的 `.pre-rq04` JAR 并重启 → `10-e-yesterday-rollback.sql` → risk-decision 回滚 SQL → 块 145 回滚 SQL。

当前进程：risk-decision 14475、ops-bff 17066（readiness 均 UP），Platform 14518 未动，Gateway 快照 routeCount 563。

## 3. 验收结果

最终一轮全量运行（07:53）148 PASS / 3 FAIL / 3 SKIP；3 个 FAIL 是首轮专用断言在重跑时的口径问题，已改为可重跑并单独复跑 U 场景 21 / 21 PASS。首轮证据见 `stage5/stage5-e2e-result-iterations.json`，最终结果见 `stage5/stage5-e2e-result.json`。

| 编号 | 结论 | 证据要点 |
|---|---|---|
| AC-01 | 通过（另见 F-1） | AUTO 自动承保 201 后立即 VERIFICATION，来源 UNDERWRITING_INIT，历史记来源承保 241829057128316928；修订批准后档位版本 1 → 1、历史仍 1 条；E 已有档位时批准承保不改档位 |
| AC-02 | 通过 | 清单系统项由 BFF 带出（资格 APPROVED、承保、合同 ACTIVE、ENABLED 开通项）；人工项缺一或缺上线说明 → `CHECKLIST_NOT_SATISFIED`；LOW 由普通审批人批准；无 CRR / MEDIUM 为 ELEVATED，普通审批人批准 → `ELEVATION_REQUIRED`，高级审批人批准生效；本人审批 → `SELF_APPROVAL`；非申请人撤回 → `NOT_REQUESTER`；撤回、驳回、高级驳回均生效；权限不足 403 |
| AC-03 | 通过 | 每次批准后当前档位与四张 TIER 名单一致（任何时刻至多一张），每次批准后都核对；终态 5 个有档位 Client 全部一致 |
| AC-04 | 通过 | VERIFICATION → STANDARD、RESTRICTED → 高于先前档位均 `TRANSITION_INVALID`；第二个待审批 `PENDING_EXISTS`；RESTRICTED 可申请目标 = 先前档位或更低 |
| AC-05 | 通过 | E 进入 OBSERVATION：目标 30 天 / 2 笔、放行 0；两笔放行后 `due=true`，档位不变；延长后重新开始（放行 0、目标 5） |
| AC-06 | 通过 | 无档位（D）支付 / 结账走 `deny_no_tier`；RESTRICTED 走 `deny_restricted`、不进额度规则集；VERIFICATION：USD 60 拒、50 × 5 放、再 10 拒（当日 260 > 250），AED 250 拒、200 × 5 放、再 10 拒（1010 > 1000），SAR 拒；近 30 日：昨日 300 + 今日 50 + 50 放行（= 400），第三笔拒（450 > 400，当日 150 ≤ 250）；OBSERVATION：USD 2500 拒、1500 放、SAR 拒；STANDARD：USD 9000 放、10001 拒、AED 36001 拒、SAR 放；结账点只做档位与币种集合（USD 999 放、SAR 拒）；同一业务键重放返回同一评估、不重复累计；**Payment 全链路**：STANDARD 放行（attempt 继续 / 成功），RESTRICTED → 风控 DENY（`deny_restricted`）→ attempt FAILED，还原后再次放行成功 |
| AC-07 | 通过 | 迁移 4 个 Client 为 STANDARD（来源 MIGRATION、执行人 236033080844881920），0 缺口；激活后 3 个 commercial 导出 Client 与夹具 Client 实时评估均放行；L12 不经 Gateway（外部入口 404），白名单清空后 403 |
| AC-08 | 通过 | 运营名单页对 `TIER_STANDARD` 加成员、停用成员均为业务校验失败（400 `BUSINESS_CHECK_FAILED`），成员保持生效 |
| AC-09 | 通过（只读试跑） | `GOLIVE_LIMIT_TIER` 正文在 `mor_risk_decision_v2e2e` 只读事务试跑：无档位 `LIMIT_TIER_NOT_SET`、RESTRICTED `LIMIT_TIER_RESTRICTED`、VERIFICATION 对 OBSERVATION `BELOW_TARGET`、OBSERVATION 对 OBSERVATION / STANDARD 对 STANDARD satisfied=1。未在 data-query 登记（发布时由主会话执行） |
| AC-10（API 部分） | 通过 | 列表按档位、待审批、关键字筛选；统计与库一致；只读账号 canRequest=false；未声明参数被 Gateway 拒绝。"到期待确认"筛选与统计按设计只看天数（S-10）——E 按笔数到期但不在筛选结果中，见第 5 节 O-1 |
| AC-11（API 部分） | 通过 | 额度页四档位、`configurationIssues` 为空、未配置币种 VERIFICATION / OBSERVATION / RESTRICTED = DENY、STANDARD = ALLOW；工作台"已用"与日汇总一致（AUTO USD 250.00 / 250.00、AED 1000.00；E 当日 100.00、近 30 日 400.00） |
| 日汇总核对 | 通过 | 新 JAR 上线后所有放行的支付评估，按 Client × 币种的笔数与金额和 `t_client_allowed_daily_total` 当日行完全一致（8 组）；被拒、超时、重放不计入 |
| 日志 | 通过 | 本次运行期间 risk-decision、ops-bff 无 ERROR；ops-bff 档位路由访问日志只记元数据（97 行，无请求 / 响应体） |

## 4. 发现（需要处理）

| 编号 | 发现 | 建议 |
|---|---|---|
| **F-1** | REQ-04 之前已批准承保、又没有 ENABLED 开通项（因此不在迁移名单）的 Client **永远拿不到档位**：修订批准不初始化（`LimitTierCommands.underwritingInitialization` 在 `superseded != null` 时返回空，符合 B2"修订批准不改档位"字面），档位申请又要求已有档位。本机 B 修订批准两次后仍无档位。mor-test 上 REQ-03 验收批准的 2 个 Client 若无 ENABLED 开通项会落入此情况 | 需用户决定：（a）批准时只要 Client 没有档位就初始化 VERIFICATION（首次或修订），改一处条件 + 一个自动测试；或（b）迁移同时把"有有效承保、无开通项"的 Client 设为 VERIFICATION。建议（a） |
| **F-2** | 执行发布的影子运行按样本原评估时间判断名单成员，迁移之后仍把迁移前样本全部判为"无档位拒绝"（支付 36 / 37、结账 23 / 23 放行 → 拒绝，路径全部 `deny_no_tier`），不能用来核对迁移。激活后的实时评估对迁移 Client 均放行 | 已改配置说明第 5 节：迁移覆盖以 SQL 核对为准，激活后对迁移名单逐个做实时冒烟；影子运行只作激活的技术门槛 |
| **F-3** | 合并联合查询与 REQ-04 时块 144 的 `COMMIT;` 容易丢（见第 1 节） | 合并时保留两个块各自的事务边界；以 `DataQueryJointRegistrationAutoTest` 与 REQ-04 注册测试同时通过为准 |
| **F-4** | 性能风险：激活后本机（load average 10—12）支付评估 90 次中 8 次 `TIMED_OUT`（决策点预算 500 ms），成功评估平均 182 ms、最大 457 ms；Payment 对超时按不放行处理（本机一笔 attempt 因此 FAILED）。原因：每次评估都从库里重建整个执行内容（流程、全部规则集、变量），不缓存；档位流程的规则集从 2 个增到 6 个，读取量随之增加。同批 Feature 计算只有几毫秒，影子回放（预加载内容）p95 38 ms | 发布 mor-test 前先在 mor-test 测评估耗时；按发布缓存执行内容（ACTIVE 发布不可变）应另立需求。本机脚本对超时记 INFO 后重试 |

## 5. 观察（不阻塞）

- O-1：列表"到期待确认"筛选与统计按天数判定（S-10，为可索引），工作台详情按"天数或笔数"判定。按笔数先到期的 Client 在详情显示到期，但不出现在列表筛选和统计里。建议页面在筛选旁注明"按天数"，由 rq04-web 走查时确认文案。
- O-2：ops-bff 变量写接口要求请求体包含全部键（不适用的显式 null），配置说明里的请求体示例是直连 risk-decision 的最小形态；经运营页面创建不受影响。
- O-3：本机 Payment 建 Intent 偶发 503（下游 3 秒超时），与 REQ-04 无关；脚本重试后通过。
- O-4：结账全链路（acquiring 准备支付）未单独走；结账点经同一评估入口验证。
- O-5：E 的档位历史因多轮走查较长（REQUEST 26 版）；E 当前仍为 STANDARD，与迁移后一致。

## 6. 产物

`stage5/`：`common.py`、`swap.py`（`.pre-rq04`、抓取目录在 scratchpad）、`stage5_migrate.py`（导出 / 迁移 / 核对 / 白名单拒绝）、`stage5_risk_setup.py`（build / activate / rollback，含影子运行）、`stage5_e2e.py`（场景 S P U Q M N G O F T L X）、`10-e-yesterday*.sql`；过程数据 `stage5-risk-setup.json`、`stage5-migration-export.json`、`stage5-migration-result.json`、`stage5-e2e-result.json`、`stage5-e2e-result-iterations.json`。文档修订：`paytech/risk-decision/docs/limit-tier-configuration.md` 第 5 节（F-2）。

## 7. 浏览器走查数据（交给 rq04-web）

运营入口 `https://127.0.0.1:18444`；账号凭据 `~/Code-MoR-backups/rq04-stage5/accounts.json`（不打印）：

| 账号 | 权限 | 用途 |
|---|---|---|
| req（241828050176577536） | READ + REQUEST + 风控目录 | 申请、撤回 |
| appr（241828061476032512） | READ + APPROVE + 风控目录 | LOW 审批；对 ELEVATED 申请应看到"须高级审批" |
| elev（241828068321136640） | READ + APPROVE_ELEVATED + 风控目录 | 高级审批 / 驳回 |
| ro（241828160700682240） | READ + 风控目录 | 只读视角 |
| 管理员 | 全部 | 本人申请后看到不能审批自己的申请 |

| Client | 当前状态 | 适合走查 |
|---|---|---|
| AUTO 241533217348268033（LOW） | VERIFICATION；USD、AED 当日额度已用满 | 额度区"已用"满格；上线复核清单"开通项"系统项未满足（无 ENABLED 开通项）；可演示 VERIFICATION → RESTRICTED（appr 可批） |
| E 240674482170114049（无 CRR） | STANDARD；历史很长 | 历史时间线；STANDARD → RESTRICTED → VERIFICATION → 上线复核全流程（需 elev） |
| 880000000901 | STANDARD（迁移） | 迁移来源展示；受限往返 |
| RELSNAP-CLIENT-MOR / -PSP | STANDARD（迁移，辖区快照 UNSPECIFIED） | 列表关键字 RELSNAP |
| B 241532599334350849、D 241533481480368129 | 无档位 | 工作台无档位态（B 见 F-1） |

没有留下待审批申请或 OBSERVATION 状态，以免锁住 Client；需要时用 req 现建。

## 8. 浏览器走查（rq04-web，2026-10-30 +04）

> 只操作本机：rq04combo 的运营 Web（Vite **18315**）、本机 Platform 语言包、走查需要的档位申请与审批（写入范围见 8.3）。未碰 mor-test，未提交、未推送。**Vite 18315 保持运行。** 脚本与产物在 [stage5-web/](./stage5-web/)。

### 8.1 环境变更

| 时间（+04） | 动作 | 回滚 / 停止 |
|---|---|---|
| 11:5x | 运营 Web：rq04combo（main 6eaf1bd3 + 联合查询 + REQ-04，含方案 B 改名）的 Vite 跑在 **18315**（`stage5-web/stage5-vite.rq04-ops.config.mjs`，`/api/operations` → Nginx 18444，`cacheDir` 在 combo 的 `operations-console-web/.vite-cache`，`node_modules` 为已有的指向主工作树的符号链接），**pid 49391**，日志 `stage5-web/logs/vite-18315.log`；18311—18314、18316 未动 | `kill 49391`，删除 combo 的 `operations-console-web/.vite-cache` |
| 11:5x | 语言包 **operations-console #20**（`stage5-web/stage5_i18n_local.py`，由 REQ-03 同名脚本改写，命名空间 risk、nav、master-data）：新增 210、改基准 1（`risk.feature.metricHint` 加入按日金额合计）、废弃 0，zh-CN 更新 211；READY 并为当前版本（之前 #19 为联合查询）。日志 `stage5-web/logs/i18n-publish.log` | 切回 #19 |

语言包 CORS 同 REQ-03：Vite 端口不在 MinIO 放行列表，脚本在浏览器内为 19400 的语言包补 `Access-Control-Allow-Origin`。

### 8.2 走查结果（Playwright 无头 Chromium，`stage5-web/stage5_browser.mjs`）

zh-CN / en × 1440 / 390，账号 req / appr / elev / ro / 管理员；分三阶段，每阶段在四个组合上截图，写操作只在 zh-CN 1440 点一次。截图 `stage5-web/screens/<语言>-<宽度>-<账号>-<步骤>.png`（121 张），结果 `stage5-browser-result-A.json`（60 步）、`stage5-browser-result-BC.json`（71 步，含 10 次写）。

| 步骤 | 原型 | 账号 | 内容 | 结果 |
|---|---|---|---|---|
| A01 | 01 | req / ro | 统计卡（验证 1、观察 0、到期 0、标准 4、受限 0、待审批 0）、5 个页签、共 5 条；关键字 RELSNAP 2 行；验证档页签 1 行（操作"申请上线"，ro 为"查看"）；只看到期待确认为空 | 通过 |
| A02 | 02 | req / ro | AUTO 验证档：USD、AED 当日（UTC）已用满 → 两条红色满格，近 30 天正常；复核清单资格、承保 OK，合同、开通项"未满足"（commercial），四个人工项可勾、说明必填、"提交申请"禁用；下一步"降为受限档"；ro 无提交区与下一步 | 通过 |
| A05 / A06 | — | req / ro | E 标准档长历史（建档、迁移、申请、驳回含意见、撤回）；880000000901 来源"存量迁移"；ro 显示"没有申请变更的权限" | 通过 |
| A07 | — | req | B、D 无档位：提示"承保批准前额度为零…"，显示有效承保与空历史 | 通过 |
| E1 / E2 | — | 管理员 | Client 详情页头"限额档位"按钮；已批准承保详情的档位提示与"查看限额档位"链接 | 通过（E1 在 en 390 修复后，见 8.4） |
| V1 | — | 管理员 | 变量编辑器新建实时累计：支付尝试点 + 放行事件 + 客户主体，统计方式含"按日金额合计（UTC 自然日）"，窗口只有"当日（UTC）""近 30 天（UTC 自然日，含今日）"，口径预览正确；未保存 | 通过 |
| B04 | 04 | req / appr / elev / ro | AUTO（LOW，普通审批）：req 本人"撤回"可用、批准 / 驳回禁用；appr 批准 / 驳回可用；E（无 CRR，高级审批）：appr 按钮禁用并提示"须高级审批人"，elev 可用，ro"等待高级审批"；待审批筛选 2 行，appr 行操作"查看 / 处理"；elev 空意见点驳回 → 本地"驳回须填写意见"，不发请求 | 通过 |
| C05 | 05 | req / ro | E 受限档：受限效果卡、"申请恢复"；恢复弹窗目标只列验证档 / 观察档 / 标准档（受限前为标准档）；ro 只读 | 通过 |
| C02 | 02 | req | E 回到验证档后系统项全部 OK，勾四项、填说明 → "提交申请"可用并提交（请求体为说明 + 四个人工项，系统项由 ops-bff 重读） | 通过 |
| C04 | 04 | elev / req | 上线复核与升标准档两次待审批：冻结申请内容（申请时观察期 0 天 · 0 笔、审批级别"高级审批 · 无 CRR"）；req 本人可撤回、不能审批 | 通过 |
| C03 | 03 | req | E 观察档：天数与放行笔数进度条（0 / 30 天、0 / 100 笔）、额度表、三项下一步；列表观察档页签显示进度；延长弹窗带当前目标（只截图后取消） | 通过 |
| C06 | — | req | E 经高级审批回到 STANDARD | 通过 |

- 未翻译 key：0；390 横向滚动：0（修复后）；HTTP ≥ 400：只有 1 次 `POST /auth/v1/login` 429（20 个会话集中登录触发 Gateway 登录限流，脚本重试后成功，非页面问题）。
- 控制台：只有运营既有 antd `Spin tip` / `Space direction` 弃用告警与脚本 `pushState` 跳转引起的 react-router 提示（同 REQ-03）。
- 观察期"到期待确认"态未在本机构造（需改观察开始时间），由单测与阶段 4 的 mock 走查覆盖。

### 8.3 写入范围

| Client | 动作 | 结果 |
|---|---|---|
| AUTO 241533217348268033 | req 申请 VERIFICATION → RESTRICTED，随后 req 撤回 | 档位仍为 VERIFICATION、version 5 不变；历史多一条"已撤回" |
| E 240674482170114049 | STANDARD → RESTRICTED → VERIFICATION → 上线复核 → OBSERVATION → STANDARD（req 申请，elev 高级审批，共 4 次批准） | 回到 STANDARD（version 26 → 30）；历史多 4 条；期间 E 的新交易按受限 / 验证 / 观察档判断（本机） |

### 8.4 发现与处理

| # | 发现 | 处理 |
|---|---|---|
| 1 | 验证档页面只有上线复核卡，不能申请"降为受限档"，与 B3"任意档位可切受限"不符；且下一步卡对验证档会把"延长观察"误算为可选 | 验证档在上线复核卡下加"下一步：降为受限档"；升标准 / 延长只对观察档出现（`LimitTierDetail.tsx`） |
| 2 | en 390 Client 详情页头四个按钮撑出 69px 横向滚动（REQ-04 加了"限额档位"按钮） | 页头按钮换行（`ClientDetail.tsx` `Space wrap`、`masterData.css`） |
| 3 | 390 下复核清单来源标签断词（"complianc e"）；系统核对标签在未满足时仍为绿色 | 来源标签不断行；未满足时系统核对标签改为灰色 |
| 4 | 待审批提示与审批级别徽标沿用长文案"高级审批（CRR 为 MEDIUM / HIGH）"，对无 CRR 的 Client 不准确 | 改用短文案"高级审批 / 普通审批"，CRR 情况在旁边单独显示（"无 CRR：须高级审批"） |
| 5（观察） | 历史很长时（E 40 条）档位页很长 | 不处理：真实 Client 历史条数少；需要时另加折叠 |

以上修改同步在 REQ-04 worktree 与 rq04combo（两边文件一致）。重测：REQ-04 worktree `npm test` 1629 / 1629、combo 1666 / 1666，两边 `tsc --noEmit`、`vite build`、i18n 导出通过；修复后重拍 A 阶段全部 60 步与 AUTO 390 两张。#4 的文案修改未重拍（需要再造待审批）。

## 8. 增补：F-1 处理（2026-10-30 08:40 UTC）

用户选（a）：承保批准（首次或修订）时 Client 尚无档位就建 VERIFICATION；已有档位的 Client 修订批准仍不改档位。
- 代码：`LimitTierCommands.underwritingInitialization` 去掉"被替代版本非空即跳过"的条件与参数，三个承保 Processor 同步；注释、表 / 列注释（`change_type` 的 UNDERWRITING_INIT 说明，DDL、schema、PDManer、测试 DDL 同步）、需求 B2 / 第 7 节 / AC-01、技术方案 S-03 等处与物理决策文档同步。
- 测试：`ClientLimitTierEstablishApiAutoTest.test_edgeCase_revisionEstablishesTierWhenAbsent`（REQ-04 之前已批准、无档位 → 修订批准建 VERIFICATION，记修订版本）；原 `test_edgeCase_revisionKeepsTier`（已有档位不改）保留。组合树 risk-decision 全量 **1839 / 1839**。研发 worktree 与组合树同改。
- 本机：risk-decision 换为 F-1 构建（pid **87640**，`.pre-rq04` 仍是阶段 5 之前的原始 JAR）。冒烟：B 241532599334350849 修订 v4 由承保员直接批准 → VERIFICATION（来源 UNDERWRITING_INIT，记 v4 承保 241843833183158272）、只在 TIER_VERIFICATION；随后支付评估走 `tier_verification` 放行。
- 本机表注释（`mor_risk_decision_v2e2e` / `mor_risk_decision`）仍是旧文字（只差注释，不影响功能）；mor-test 用新的前滚 SQL。
