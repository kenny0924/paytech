# REQ-04 risk-decision 限额档位 · 阶段 6 发布准备报告（2026-10-30 08:20—09:10 UTC）

> 范围：只做发布准备。mor-test 上**只做了只读操作**：`docker inspect / ps / logs / exec sha256sum / cp`（只读出）、root 只读会话 SELECT、`mysqldump --single-transaction` 导出到本机 `~/Code-MoR-backups/rq04-stage6/`（0600 / 0700，不入库）。没有停 / 起容器，没有执行 SQL / DDL / GRANT，没有登录运营 API 写数据，没有调用 Platform / IAM / i18n / data-query / 风控配置写接口。镜像只在本机 docker 构建。未提交、未推送。**阶段 7 须用户明确授权后由主会话执行。**
> 材料：[baseline.md](./baseline.md)（现网读数）、[runbook.md](./runbook.md)（执行顺序、检查、回滚、验收）、`sql/`、`compose-*.yaml`、`harness/`、`scripts/`、`acceptance/`、`i18n-bundles/`、`precheck/`、`evidence/`。

## 1. 结论

| 项 | 结果 |
|---|---|
| 阶段 5 后修正 | F-1（a）已实现：承保批准时 Client 尚无档位就建 VERIFICATION（首次或修订）；risk-decision 全量 1839 / 1839；本机 B 冒烟通过（stage5-report 第 8 节）。回滚 SQL 增加"有引用 TIER 名单的名单变量时拒绝"。配置说明第 5 节改正影子运行的用途（F-2） |
| 基线（终版，09:20—09:45 UTC 重建） | 现网：risk-decision REQ-03、ops-bff 联合查询 b、ops-web 联合查询 c、Platform 1b 多语言发布变更记录（a3003ef6）。候选基线 = main **`022da95a`**（联合查询提交 `d673817f` + 多语言发布变更记录提交 `1c8341a2` = 69f87bbf）；发布 worktree 与它的代码差异只有本需求文件（漂移检查）。ops-bff 3007 项、ops-web 273 个文件与现网相同；Platform 3238 项只差能力清单 1 项。四个服务**现网独有内容为 0** |
| 候选 | 干净 worktree `Code-MoR-rq04-rel` = 基线 + 本需求（217 个源文件，`evidence/rq04-source-files.sha256`；联合查询 241 个，`evidence/jq-source-files.sha256`；漂移检查通过）。4 处冲突与组合树相同处理，**两个注册块各保留自己的 `COMMIT;`**（F-3）。测试（终版树）：risk-decision 1839 / 1839（与组合树同源）、ops-bff **2531 / 2531**、arch-rules 70 / 70、ops-web `tsc --noEmit` 通过、`npm test` **1672 / 1672**；PDManer 结构脚本测试 6 / 12 失败，与基线相同（既有，非本需求）。候选 risk-decision JAR 与本机阶段 5 验证并运行的 F-1 JAR **条目完全相同** |
| 镜像 | 4 个，tag `rq04-20261030b`，钉 sha256（第 2 节）；镜像内 JAR / dist 与构建产物相同；health-probe、nginx.conf、Entrypoint / User / Healthcheck / 端口 / WorkDir 与现网相同（`evidence/image-verify.txt`） |
| env | 候选与回滚 8 个覆盖渲染后与运行容器**逐键逐值相同**（risk 33、ops-bff 21、ops-web 7、Platform 59）；迁移窗口覆盖只多 `RISK_LIMIT_TIER_MIGRATION_CALLERS` |
| mor_risk_decision | 预检：三张新表、名单 ID / 编码、同名索引、过程都不存在，57 张表，应用账号库级 DML 覆盖新表。离线演练（mor-test 只读导出 + 断网 mysql:8.4.11）：前滚 0.33 s，可重跑；回滚在**有档位数据时 SIGNAL 拒绝**，删去夹具后回滚成功，库与导出**逐行相同**；回滚后可再次前滚，与第一次前滚逐行相同 |
| 注册块 145 | 终版复测（09:29 UTC 新导出 + 现网 a3003ef6 Platform 编译器，`evidence/rehearsal-20261030T092920Z/`）结果相同。预检：七表区间、编码、路径都未占用（块 144 联合查询、146 并行需求已占用，145 空闲），依赖都在。离线用现网 Platform JAR 的生产编译器：基线 effective **641** → 休眠 641（loaded 658）→ 激活 **652（+11）** → 重复激活 652 → 停用 641 → 删除 641，删除后 mor_platform 与导出逐行相同；重复插入被主键拒绝 |
| 风控配置 | `scripts/rq04_risk_config.py`：与阶段 5 本机同形的档位分支，支付点在档位层之后原样接现网 `PAY_ATTEMPT_FLOW` v1 的拦截 / 拒绝 / 审核 / 认证 / 3DS 节点（规则集版本原样引用）。`plan` 已在 mor-test 只读跑过：两个点仍是回滚基线、48 个变量编码与拒绝原因未占用、引用的两个规则集 FROZEN。额度为建议值（Q-1） |
| 迁移 | 只读导出：4 个有 ENABLED 开通项的 Client（全是 DEMO 个人，AE）；L / H 两个 REQ-03 验收 Client 有有效承保、无开通项，不迁移，验收时修订批准得到 VERIFICATION（F-1）。迁移经私网探针调用 L12，需临时白名单 → risk-decision 多重启一次（Q-5） |
| data-query | 数据源复用 REQ-03 的 `MOR_RISK_DECISION_REPLICA`；需表级 GRANT `t_client_limit_tier`（`sql/40-…`）；模板 `GOLIVE_LIMIT_TIER` 未占用，`plan` 只读跑过，2 项未满足（缺授权、缺表）属发布前预期 |
| 语言包 | operations-console 现网 **#27**（阶段 6 首次读数 #26，联合查询 c 发布 #27）。计划：新增 210（risk 205、nav 4、master-data 1）、基准变更 1（`risk.feature.metricHint` 加入"日放行合计"）、废弃 / 恢复 0、zh-CN 导入 211、不新建命名空间、`zhChangedNotImported` 0。只读干跑通过；发布号取阶段 7 当时的下一个空闲号 |
| **F-4 评估耗时** | **mor-test 有条件放行，生产 / 并发不放行**（第 4 节） |
| 验收 | `acceptance/rq04_acceptance.py`：`accounts` / `prepare` / `api` / `check`，语法检查通过，写操作需 `--yes`；mor-test 上只能在阶段 7 实跑 |

## 2. 候选镜像

| 服务 | 候选（`rq04-20261030b`） | 内容 | 回滚（现网） |
|---|---|---|---|
| risk-decision-service | `sha256:1414384e28e6964c3e47add9d648fe483fb685624874f867ceaeed9a1f929462`（JAR `34456b37a65a7436`） | 现网 + 限额档位 84 个新增条目、35 个改动（`application.yml`、错误映射、MapperScan、承保三处理器、名单、放行事件与日汇总、Feature 读取） | `sha256:8934f65a…`（rq03-20261029a） |
| operations-console-bff | `sha256:4bd4c3dfb16e92f32900910b98dbcb7c73391b0bcb19927ee284e86585c90606`（JAR `9f660a77af785cbd`） | 现网 + 档位页 54 个新增、18 个改动（Risk 客户端与严格解析、错误码、响应形状校验） | `sha256:2452aaa7…`（jq-20261030b） |
| operations-console-web | `sha256:2f7f8780d5abf1991f51eb2c4db242cde930a13c10bc579f908e5b6b74efac06`（dist 277） | 现网 + 风控 · 限额档位页面 | `sha256:6f8161c2…`（jq-20261030c） |
| api-gateway（D-9） | `sha256:d51daee3c4caecd720a8d512c6c4d7c43821cb2694cf1fa24d598449eac032cf`（JAR `d3dd5aa2`） | Snapshot 响应体上限 1 MiB → 4 MiB（Gateway 自身只差 3 个条目；内嵌 platform-support-api 随 main 前进，非 Gateway 引用类）；Gateway 模块全量测试 **1313 / 1313** | `sha256:270c8ba2…`（card3ds-20261022b） |
| platform-support-service | `sha256:af13e77a05c3d30bf9652523d52ba302dc59d2ee2b2221500ece647215560012`（JAR `9b2a86e18682be60`） | 现网 a3003ef6（源码 c633fd0e = 联合查询 b + 69f87bbf，已提交为 1c8341a2）+ `console-resource-capabilities.json` 页面能力 1 项（3238 项中只有这 1 项不同） | `sha256:a3003ef6…`（1b 多语言发布变更记录，JAR `29d7e804`） |

对比明细：`evidence/jar-candidate-{risk,opsbff,platform}-vs-live.txt`。

## 3. 发布顺序（详见 runbook 第 3 节）

① 复核与通知 → ② 备份 → ③ 只读预检（含风控 plan）→ ④ mor_risk_decision 前滚 → ⑤ risk-decision → ⑥ ops-bff → ⑥b Platform → ⑦ 块 145 休眠 → 编译核对 → 激活 → ⑧ ops-web → ⑨ 语言包 → ⑩ GRANT + 模板 → ⑪ 风控配置与发布（不激活）→ ⑫ 迁移（迁移覆盖重启 → 导出 → L12 → 核对 0 缺口 → 清白名单重启 → 403）→ ⑬ 激活档位发布 → ⑭ 每个迁移 Client 实时冒烟（兼作预热）→ ⑮ 验收。回滚 R13（以旧流程版本新建并激活发布）必须先于镜像回退。

**影响窗口**：risk-decision 两次重启（⑤、⑫，各约 30—60 秒，结账 / 支付风控评估与出款资格检查不可用）；ops-bff 约 30 秒；Platform 约 60 秒；**⑬ 之后没有档位的 Client 结账与支付被拒**（mor-test 只有 DEMO 迁移 Client 在交易）。

## 4. F-4 评估耗时（详见 `evidence/f4-latency.md`）

| 场景 | 支付 p50 / p95 / p99 | 支付超时 |
|---|---|---|
| mor-test 现网（只读，10 次） | 83 / 325 / — ms | 1 次，闲置多日后的冷启动 |
| 副本 · 基线流程 · 串行 | 44 / 142 / 283 | 0 / 200 |
| 副本 · 基线流程 · 4 并发 | 112 / 332 / 642 | 3 / 200 |
| 副本 · 档位流程 · 串行 | 156 / 340 / 434 | 0 / 200 |
| 副本 · 档位流程 · 4 并发 | 313 / 1014 / 1209 | **51 / 200（25.5 %）** |

（副本 = mor-test 导出 + REQ-04 前滚，同 JVM 参数，本机 load 13—17，绝对值偏悲观。结账点档位流程 4 并发也没有超时。）

- 原因：每次评估都从库里重建执行内容（流程、全部规则集、变量定义），计入 500 ms 预算。档位流程下支付评估 205 条 SQL（原 69），其中变量定义**逐个查询** 102 次；Feature 计算只有 7—23 ms；影子运行（预加载内容）p95 61 ms。
- **建议**：mor-test **有条件放行**——mor-test 流量是串行的验收调用，串行下 0 超时。条件：⑭ 激活后对每个迁移 Client 先做一次实时评估（兼作预热）；验收期间 `check` 统计 TIMED_OUT（Payment 把超时当作不放行）。**生产或有并发流量之前不放行**，另立需求：① 变量定义批量读取（改动最小，每次少约 100 条 SQL）；② 按 ACTIVE 发布缓存不可变的执行内容、激活时失效（常量当前值、集合 / 名单成员、累计值仍实时读，配置说明"改常量即时生效"不变）。草图见 `evidence/f4-latency.md` 第 5 节。

## 5. 风控额度建议值（Q-1，须风控确认；激活后改常量即时生效）

| 档位 | USD 单笔 / 当日 / 30 日 | AED | SAR | EGP |
|---|---|---|---|---|
| VERIFICATION | 500 / 2,000 / 10,000 | 1,800 / 7,300 / 36,700 | 1,875 / 7,500 / 37,500 | 25,000 / 100,000 / 500,000 |
| OBSERVATION | 2,000 / 10,000 / 50,000 | 7,300 / 36,700 / 183,500 | 7,500 / 37,500 / 187,500 | 100,000 / 500,000 / 2,500,000 |
| STANDARD | 10,000 / 100,000 / 1,000,000 | 36,700 / 367,000 / 3,670,000 | 37,500 / 375,000 / 3,750,000 | 500,000 / 5,000,000 / 50,000,000 |

VERIFICATION、OBSERVATION 只允许这四种币种（其余拒绝）；STANDARD 不建币种集合（未配置币种放行）；RESTRICTED 一律拒绝。AED / SAR 按联系汇率取整，EGP 按约 50 取整；结账点只做档位、受限与币种集合，不做金额额度。

## 6. 待决（阶段 7 前请用户确认）

| # | 问题 | 建议 |
|---|---|---|
| Q-1 | 额度值（第 5 节）与四种币种 | 按建议值发布，风控给正式值后经变量页面改常量（即时生效，无需新发布） |
| Q-2 | F-4：档位流程在并发下超时率高 | mor-test 按第 4 节条件放行；生产前另立"评估执行内容缓存"需求 |
| Q-3 | 是否一并发布 Platform（只差页面能力清单）。不发布时功能、导航、权限、路由都正常，只是菜单资源管理页把"限额档位"及三个按钮标为不支持 | 一并发布（同 REQ-03；代价是 Platform 重启约 60 秒） |
| Q-4 | 验收 prepare 要对 L、H 两个 REQ-03 验收 Client 各做一次承保修订并批准（新增承保版本，Client 得到 VERIFICATION） | 同意（都是验收合成数据；这也是 F-1 在 mor-test 的验证） |
| Q-5 | 迁移白名单只在迁移窗口设置，risk-decision 因此重启两次 | 保持（白名单常开等于任何能伪造 `X-Caller-Service` 的私网调用方都可迁移） |
| Q-6 | 已解决：联合查询已提交（`d673817f`，main `022da95a`），终版候选按它重建，漂移检查以 `022da95a` 为基线 | — |
| Q-7 | 迁移请求的 Client 名称用 `client_no`（脚本只读 `t_client` / `t_party`，不读个人姓名属性；档位页对这 4 个迁移 Client 显示编号） | 保持（如需显示名称，改为经 client-master 接口取展示名后再迁移） |
| Q-8 | **阶段 7 需要用户明确授权**：停 / 起 4 个容器（risk-decision 两次）、root DDL / DML / GRANT、注册块 145、语言包、data-query 模板、风控配置与发布激活、存量迁移、IAM 合成账号、验收写数据 | — |

## 7. 本机资源

- 发布 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-rq04-rel`（含 node_modules / target），保留到阶段 7 结束，用于漂移后重建。组合树 `Code-MoR-rq04combo` 仍在（本机 e2e JAR 来源）。
- 本机备份 `~/Code-MoR-backups/rq04-stage6/`：现网 JAR / dist / health-probe 取出物（`live/`）、候选 JAR（`candidate/`）、mor-test 只读导出 3 份、演练目录。
- 候选镜像 4 个（`mor-test/*:rq04-20261030b`）。F-4 副本库 `mor_risk_decision_f4` 已删除、授权已回收、进程已停。可用磁盘约 43 GiB。

## 9. 增补：阶段 7 第 ⑦ 步 Snapshot 超限与 Gateway 候选（2026-10-30 09:45—10:00 UTC，D-9）

- 现象：22-activate 后 Gateway 每 5 秒 `Gateway Snapshot 刷新失败: reason=CONTROL_PLANE_UNAVAILABLE`，Platform 无错误；主会话执行 23-deactivate 后恢复（641 条）。
- 根因：Gateway 拉取公开 Snapshot 的 WebClient `maxInMemorySize` = `max-response-bytes` = 1 048 576，且 `PlatformSnapshotProperties.MAX_RESPONSE_BYTES` 写死为同值。现网 641 条 = 1 032 106 字节（探针只读 GET 实测，与离线序列化相同）；激活块 145 后 652 条 = **1 049 930 字节，超 1 354 字节**。原演练只编译不序列化，未发现。
- 处理（用户选 A）：上限改 4 MiB（4 194 304），候选 `mor-test/api-gateway:rq04-20261030b-gw` = `sha256:d51daee3c4caecd720a8d512c6c4d7c43821cb2694cf1fa24d598449eac032cf`；回滚 = 现网 `270c8ba2…`（card3ds-20261022b）。现网基线：Gateway 自身类与 main 构建相同，只有内嵌 `paytech-platform-support-api` 随 main 前进（+127 / −6 / ~31，−6 为参考数据迁移 e2185c1b 删除的查询接口，Gateway 不引用）。Gateway 模块全量测试 1313 / 1313；image_verify、env_check（30 键）一致。
- 演练增强：`harness/BlockSnapshotCheck.java` 序列化并统计字节、超过 `SNAPSHOT_CAP` 即失败；`harness/GatewaySnapshotDecodeCheck.java` 用 Gateway 生产解析（同一严格 JsonMapper）与 `GatewaySnapshotCompiler` 复核。结果（`evidence/rehearsal-gw-*`）：基线 / 休眠 1 032 106 字节，激活 1 049 930 字节；**现网 Gateway JAR：FAILED（> 1 048 576），候选：DECODE+COMPILE OK（652 条路由）**。4 MiB 下余量约 3.1 MB（约 1 900 条路由；Gateway 另有 4 096 条路由上限）。
- 运行卡：新增 ⑦a（Gateway 换镜像，在 22-activate 之前）与回滚 R7a（必须先 R7 停用块 145）。Gateway 重启约 30 秒，期间全部外部入口不可用。
- 附注：main 已前进到 `264ea3d2`（arch-rules 登记 `t_i18n_release_change` 归属），只改 arch-rules，不进任何镜像；提交 REQ-04 时 `table-ownership.yaml` / `TableOwnershipAutoTest` 需三方合并。
