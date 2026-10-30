# REQ-02 · 阶段 6 W3 报告（发布准备：基线、候选树、测试、镜像、compose、巡检、语言包、data-query、验收脚本）

> 2026-10-30 17:50—18:30Z，两轮：第一轮取研发树 17:57Z 快照；Q-4 修订（用户确认"任一币种就绪即满足"，部分缺口 `CHANNEL_IDENTITY_PARTIAL`）定稿后第二轮重新迁入，只重建 commercial 与 ops-web（tag `req02-20261030b`）。
> mor-test 只读：只用了 `docker inspect / ps / logs / exec（printenv 键名、sha256sum）`、`docker cp` 取出、root 只读 SELECT（READ ONLY 会话）、Nacos 实例列表、XXL Admin 分组 / 任务查询、私网探针对**尚不存在的**新接口发只读请求（404）。未停 / 起容器，未写 SQL / XXL / Nacos / MQ / Platform。本机测试只用 `mor-local-*`。未提交、未推送；主工作区只写了本需求 `release/`；`Code-MoR-plops-rel` 只读（差异指纹 `78ccfd1dc42e8c62` 用临时 `GIT_INDEX_FILE` 计算）；未改 `test-environment/compose*.yaml`。未调用 Superpowers skill。

## 1. 结论

| 项 | 结论 |
|---|---|
| 基线 | main 仍是 `331d8e0e`；4 个现网镜像与任务卡一致。三个后端 JAR **现网独有 0**（只落后 main：差异全在提供方 / 基础 JAR，源码提交均晚于各镜像构建）；ops-web 现网 `5784fa81`（plops）291 个文件与"331d8e0e + plops 18 个 ops-web 文件"构建**逐字节相同**。→ [baseline.md](./baseline.md) |
| 候选源码 | `/Users/kenny/Desktop/Workspace/Code-MoR-req02-rel` = `331d8e0e` + plops ops-web 18 + 本需求 69 = **87** 个文件（第二轮；Q-4 修订变动 9 个），**0 个三方合并**（两边 ops-web 文件无交集）；漂移检查 NO DRIFT（覆盖 main、候选树、研发树 69 文件、plops 18 文件、4 个现网 ID）。→ [evidence/merge-notes.md](./evidence/merge-notes.md)、[evidence/source-files.sha256](./evidence/source-files.sha256)、`scripts/source_drift_check.sh` |
| 测试 | 第一轮：channel-connector **404/0**、client-master **181/0**、commercial 252/0、arch-rules 70/0 + 5/0。第二轮（修订后）：commercial **253/0**、arch-rules **70/0 + 5/0**、reactor `test-compile` 126 模块；ops-web `tsc` 0 错误、`npm test` 1704/1705（唯一失败为读仓库内 Platform 能力清单的对照测试，plops 把新菜单加在 Platform 文件里；放入 plops 该 JSON 复跑 **1705/1705** 后已还原，不影响 dist）、`build` 291 文件、`i18n:export-source` 11 291 键。channel-connector 源码未变，client-master 只多一处 commercial-api Javadoc，不重跑。→ [evidence/test-summary.txt](./evidence/test-summary.txt) |
| 镜像 | channel-connector `sha256:f330c4ae93bf…`（a）、client-master `df13cc47d936…`（a）、**commercial `2be844e7a163…`（b）**、**ops-web `bbc03d33854e…`（b）**；未动 `:development` 等 tag；修订前的 commercial a / ops-web a 只留在本机，不发布。镜像内 JAR / dist = 构建产物；health-probe = 现网；Entrypoint / Cmd / User / Healthcheck / 端口 / WorkDir / Env 元数据 = 现网同服务镜像；nginx.conf 相同。→ [evidence/candidate-images.txt](./evidence/candidate-images.txt)、[evidence/image-verify.txt](./evidence/image-verify.txt) |
| client-master 沿用 a 的依据 | 用修订后的树重建 client-master，与 a 只差内嵌 commercial-api 的 `CapabilityPrerequisiteCheckResponseBody$Check.class` 一个类；`javap -c -p -constants` 相同，只是 Javadoc 改动带来的调试属性 / 行号差异。→ [evidence/jar-candidate-b.txt](./evidence/jar-candidate-b.txt) |
| 条目差异（候选 vs 现网，0 删除） | channel-connector +17、~5（本需求 +16 就绪查询与共用筛选、~1 `QueryMidCandidatesProcessor`；其余为 main 已有的提供方 / 基础 JAR）；client-master +39、~11（本需求 +6 经营分类查询、~2；其余为 main 已有）；commercial b +157、~27（本需求新依赖 `paytech-channel-connector-api` 148 项、④ 接入 +9、~10；其余 ~17 为 main 已有；**无新增第三方库**）；ops-web 291 → 291，逻辑文件 0 新增 / 0 删除，其余仅 hash 换名。→ [evidence/jar-candidate-vs-live-and-main.txt](./evidence/jar-candidate-vs-live-and-main.txt)、[evidence/jar-candidate-b.txt](./evidence/jar-candidate-b.txt)、[evidence/ops-web-dist-candidate-vs-live.txt](./evidence/ops-web-dist-candidate-vs-live.txt) |
| compose | 8 个文件（commercial / ops-web 候选已指向 b）；候选与回滚的 env 与现网**逐键逐值相同**（8 行 `missing=[] extra=[] valuediff=[]`）；**不新增 env 键**。commercial 必须带覆盖文件（base 只有 11 键，现网 37 键）。→ [evidence/env-check.txt](./evidence/env-check.txt) |
| 内部调用 | 三服务均在 Nacos `mor-test` 注册且 healthy；commercial 经 `LoadBalancerClient` 按服务名调用（与现有 client-master 结账客户端同法），近 6 小时无发现失败；internal-auth 三方 OFF、新 Controller 无调用方白名单 → **无需任何登记**。→ baseline 第 4 节 |
| 巡检（修订口径） | `sql/01-precheck-capabilities-to-suspend.sql` 只读执行：**暂停 2 条**：DEMO-CL-001 APPLE_PAY（`PAYMENT_METHOD_NOT_SUPPORTED`）、DEMO2-CL-101 CARD（只有 AED 价格，`CURRENCY_NOT_SUPPORTED`）；**DEMO-CL-001 CARD、DEMO2-CL-103 CARD 保持 ENABLED**，SATISFIED + `CHANNEL_IDENTITY_PARTIAL`，提醒 AED 缺 MID；8 条门禁暂停项 ④ 为 SKIPPED/AWAITING_PRICE，状态不因 ④ 改变。→ [precheck/01-…-20261030T182225Z.out](./precheck/01-precheck-capabilities-to-suspend-20261030T182225Z.out)（旧口径读数保留为 `*.old-*.out`） |
| 语言包 | `req02_bundles.py` 干跑通过 13 项：基于现网 **#30**，commercial **新增 17、基准变更 3、废弃 2**（恰好两个 `CHANNEL_IDENTITY_NOT_CONNECTED`，需 `--accept-deprecations`）、zh-CN 导入 20，不新建命名空间；发布号运行时取下一个：**预计 #31，若 REQ-06 先发则 #32**。→ [precheck/i18n-plan-dryrun-operations-console.out](./precheck/i18n-plan-dryrun-operations-console.out)、`i18n-bundles/` |
| data-query | 数据源 `MOR_CHANNEL_CONNECTOR_REPLICA` ACTIVE（账号对 `mor_channel_connector.*` SELECT），`GOLIVE_MID_READY` 未占用。主会话修订后的模板（任一列出币种就绪即可，`#{currencies}` 已包 `COALESCE`）只读试跑 8 组 + NULL 绑定 1 组全部符合（plan 14/14）；阶段 6 发现的"currencies 为 NULL 时误判满足"已在模板修复，最新正文同步为 `sql/03-golive-mid-ready-template.sql`，登记时仍声明 `default ""` 作双保险。→ `acceptance/req02_data_query.py`、[acceptance/dryrun-dq-plan-…](./acceptance/) |
| XXL | 重查用现有任务 26（`scope=ALL`），不新建任务；`scripts/xxl_recheck.py plan` 读到分组 14 在线、任务 25 / 26 RUNNING |
| 验收脚本 | `acceptance/req02_acceptance.py`（plan / prepare / api / check / cleanup，写操作需 `--yes`；G 段补了 partial 场景，Q 段补了 `USD,AED` 一组）、`acceptance/req02_browser.mortest.mjs`（partial 提醒与链接）、`acceptance/req02_data_query.py`；语法检查通过；只读干跑 `plan` 10/0；`check` 15/0（预测暂停集合恰好为预期 2 条，发布前二者仍 ENABLED）；`dq plan` 14/0 |

## 2. 产出清单（`release/`）

`baseline.md`、`runbook.md`、本报告；`compose-{candidate,rollback}.{channel-connector-service,client-master-service,commercial-service,operations-console-web}.yaml`；`scripts/`（`source_drift_check.sh`、`env_check.sh`、`run_tests.sh`、`xxl_recheck.py`）；`harness/`（`image_verify.sh`、`envcmp.py`、`jarcmp.py`）；`sql/`（`01-precheck-capabilities-to-suspend.sql`、`02-precheck-data-query.sql`、`03-golive-mid-ready-template.sql` 登记材料）；`precheck/`；`req02_bundles.py` + `i18n-bundles/`；`acceptance/`（三个脚本 + `dryrun-*.json`）；`evidence/`。大文件在 `~/Code-MoR-backups/req02-stage6/`（0700 / 0600：`live/`、`base/`、`candidate/`（第一轮）、`candidate-b/`（第二轮 JAR + dist + SHA256SUMS）、`ctx/`、`probe/`、`logs/`、`i18n/`）。

## 3. 风险

| # | 风险 | 缓解 |
|---|---|---|
| R-1 | 发布后 DEMO-CL-001 APPLE_PAY 与 DEMO2-CL-101 CARD 预期暂停，DEMO2-CL-101 的 Client 失去唯一的 ENABLED 支付方式（运行期本就选不到 MID） | 写入 runbook 作为预期结果，由用户审批阶段 7 时决定；补齐真实 MID 能力后下一轮重查自动恢复 |
| R-3 | 三个服务各约 40 s 中断（payment 选 MID、结账解析、合同页） | 低峰；按提供方 → 消费方顺序，逐个替换 |
| R-4 | 阶段 7 前 main 前移、REQ-06 先发 ops-web、研发树再改 | runbook 第 0 步 `source_drift_check.sh`；DRIFT 就停下；REQ-06 先发时按主会话通知重建 ops-web 候选（REQ-06 只动 workflow 文件，合并不冲突） |
| R-5 | 候选树里 ops-web 能力清单对照测试在没有 plops Platform 文件时失败（测试环境问题，不影响 dist） | 提交时与 plops 的 Platform 改动一起才完全成立；提交顺序由主会话安排 |

## 4. 待裁决 / 已裁决

| # | 事项 | 状态 |
|---|---|---|
| U-1 | 巡检暂停范围 | **已裁决**：不为 AE001 补 AED 能力行（等于虚构渠道能力）；DEMO2-CL-101 CARD 与 DEMO-CL-001 APPLE_PAY 两条暂停作为预期写进本报告与 runbook，**由用户审批阶段 7 时决定**；验收 `check` 对这 2 条按"预期暂停"判定（发布前 ENABLED + 预测 UNSATISFIED，候选上线后须为 SUSPENDED_BY_PREREQUISITE），并核对预测暂停集合恰好是这 2 条 |
| U-2 | `GOLIVE_MID_READY` 的 `currencies` 为 NULL 时误判满足 | **已修复**：主会话已在 `db/data-query-templates.sql` 三处改为 `COALESCE(#{currencies}, '')`；最新正文已同步为登记材料 `sql/03-golive-mid-ready-template.sql`（sha256 `8ce705ea18dc23bb…`，与 db 文件逐字节相同，plan 与 apply 都先核对）；登记仍声明 `default ""`，apply 先试跑"不传 currencies"一组再建模板 |
| U-3 | 运营语言包号与 REQ-06 的先后 | **已约定**：谁先进阶段 7 谁先发；脚本运行时读现网最新号取下一个，不写死；预计 #31，若 REQ-06 先发则 #32 |
| U-4 | AC-09（Client 无 MCC）/ AC-10（channel-connector 不可用）、AC-07（MID 关闭）在 mor-test 不演练 | 建议接受，由 auto 测试覆盖（7 个 Client 均已确认 5999；停 channel-connector 会影响支付；AE001 两个 MID 都有 CARD × USD，关一个不会触发暂停） |
| U-5 | 合成数据：只有 1 个无 MID 菜单运营账号（只含 `COMMERCIAL_CONTRACT_READ`，供 AC-V2）；不改任何渠道 / MID / 能力配置 | 账号验收后停用（`cleanup` 生成草稿） |
| U-6 | 研发树在第一轮快照后修订 | **已处理**：第二轮迁入、重测、重建 b，compose / env_check / 漂移清单 / 语言包 / 验收脚本均已更新 |

## 5. 阶段 7 动作清单（主会话执行，须用户授权；详见 [runbook.md](./runbook.md) 第 2 节）

| 步 | 命令 | 预期 / 失败即停 | 回滚 |
|---|---|---|---|
| 0 | `scripts/source_drift_check.sh`；`scripts/env_check.sh`；`acceptance/req02_acceptance.py plan` | NO DRIFT；8 行全空；4 个镜像均为"发布前" | — |
| 1 | 巡检 `sqlro < sql/01-…`，告知用户（2 条预期暂停由用户审批） | 2 × WILL_SUSPEND（DEMO2-CL-101 CARD、DEMO-CL-001 APPLE_PAY）、2 × STAYS_ENABLED_WITH_REMINDER | — |
| 2 | `up compose-candidate.client-master-service.yaml client-master-service` | healthy，约 40 s | 回滚文件 |
| 3 | `up compose-candidate.channel-connector-service.yaml channel-connector-service` | healthy，约 40 s；就绪查询 200 | 回滚文件 |
| 4 | `up compose-candidate.commercial-service.yaml commercial-service`（b） | healthy；无发现失败 | 回滚文件（已暂停项在 5 分钟任务中自动恢复） |
| 5 | `up compose-candidate.operations-console-web.yaml operations-console-web`（b）；`req02_bundles.py --accept-deprecations` 干跑 → `--apply` | 约 5 s；新增 / 基准变更 / 废弃 / zh 导入 = 17 / 3 / 2 / 20、只在 commercial；发布号预计 #31（REQ-06 先发则 #32） | ops-web 回滚文件；语言包以 plops 源码重发 |
| 6 | `scripts/xxl_recheck.py trigger`（任务 26，`scope=ALL`） | handle 200；DEMO-CL-001 APPLE_PAY、DEMO2-CL-101 CARD → `SUSPENDED_BY_PREREQUISITE`，其余不变 | 回滚 commercial |
| 7 | `req02_acceptance.py prepare --yes` → `api --yes` → `check`（2 条按预期暂停判定）；`node acceptance/req02_browser.mortest.mjs` | 0 FAIL（含 partial 场景）；浏览器无 5xx / 原始键 / 溢出 | 按服务回滚 |
| 8 | （单独授权）`req02_data_query.py plan` → `apply --yes`；`api --yes` 复跑 Q 段 | 试跑全部一致后才建模板；模板 ACTIVE | 模板 INACTIVE |
| 9 | `req02_acceptance.py cleanup`；停用合成账号 | — | — |

## 6. 本机资源

- 候选树 `/Users/kenny/Desktop/Workspace/Code-MoR-req02-rel`（含 `target/`、`operations-console-web/node_modules`（真实目录）、`dist/`（= b）），保留到阶段 7 结束；研发树与候选树 69 个文件逐字节相同，漂移检查会核对。
- 本机镜像 tag：`mor-test/{channel-connector-service,client-master-service}:req02-20261030a`、`mor-test/{commercial-service,operations-console-web}:req02-20261030b`（另有不发布的 commercial / ops-web `:req02-20261030a`）。构建时 zsh 把 `$s:req02…` 的 `:r` 当修饰符吞掉，曾产生 3 个 `<svc>eq02-20261030a:latest` 错名 tag，已重打正确 tag 并删掉错名 tag（镜像 ID 不变）。现网 channel-connector 的 `…serviceard3ds-20261022` 错名 tag 是同一原因（`:c`）。脚本里一律写 `${s}:tag`。
