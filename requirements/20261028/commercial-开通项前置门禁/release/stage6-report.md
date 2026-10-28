# REQ-01 开通项前置门禁 · 阶段 6 报告（mor-test 发布准备，2026-10-28 23:00—23:55 +04）

> 范围：只读核对 mor-test、本机构建与演练、写发布文件。**未改 mor-test 任何东西**（未停 / 起容器、未执行写 SQL、未写 XXL / MQ / Platform、未发布语言包、未 `docker cp` 写入容器）；未提交、未推送；未改主工作区代码与 `test-environment/compose*.yaml`。阶段 7 的环境变更由主会话执行，须用户授权。

## 1. 结论

| 项 | 结论 |
|---|---|
| 基线 | commercial = relsnap `e5c1ee6b`；ops-bff / ops-web = 国家包法务修订 `769edbce` / `45ee11cb`；Platform / Gateway 不换。详见 [baseline.md](./baseline.md) |
| 候选源码 | `/Users/kenny/Desktop/Workspace/Code-MoR-gate-rel` = `663e5e4a` + 法务修订 93 个文件 + 本需求 130 个文件；**重叠文件 0**，无冲突。法务修订在本阶段期间提交为 `ac3145f4`（main，未推送），93 个文件与候选所含逐字节相同，候选代码 = `ac3145f4` + 本需求 130 个文件 |
| 基线证明 | 只加法务修订时：ops-web dist 250 个文件与现网逐字节相同；ops-bff JAR 2800 项 0 差异；Platform JAR 0 差异；commercial 自身模块 0 差异（差异只在已上线的 client-master-api / compliance-api / events.yaml）→ 候选不回退任何现网内容 |
| 测试 | commercial auto **247 / 0**；ops-bff 全量 **2442 / 0**（商务相关 149：Browser 109、VersionDiffHistory 11、Aggregation 7、ConsoleQuery 7、PricePreview 7、TemplateCatalog 8；法务修订 ClientOnboardingReviewBrowser 30）；messaging `YamlEventCatalogAutoTest` **12 / 0**；ops-web `npm test` **1581 / 0**、`build` 通过（dist 253）、`i18n:export-source` 通过（25 命名空间 9880 key，commercial 876）；全 reactor `clean test-compile` **121 模块 SUCCESS**。未跑全 reactor 测试 |
| 镜像（tag `gate-20261029a`） | commercial `sha256:806043a320cfce7eaaf0eed19d0ef202487ec0a6559aaa5b74f8fe3d1085f728`（JAR `40be77e24894562b`）；ops-bff `sha256:eca2df8231f999f3f977a830ad8baa758449aa8bceee12234da28d77ae3762b8`（JAR `2534a1fa42686768`）；ops-web `sha256:7a053544076e82e1f2a016f3b64cd4324d26a6df65c64177a7f627cb306b80ad`（dist 253）。镜像内 JAR / dist / health-probe 与构建产物逐字节相同，Entrypoint / User / Healthcheck / 端口与现网镜像相同（`evidence/image-verify.txt`）；`:development` 未动 |
| 注册块 | **141**（现网最大 140）：3 路由（暂停 / 恢复 = `COMMERCIAL_CONTRACT_MANAGE`，检查前置 = `COMMERCIAL_CONTRACT_READ`）+ 18 header + 18 guard，形状照抄 95405 / 95409。离线演练（mor-test 只读导出 → 断网 MySQL 8.4.11 → 候选 Platform JAR 的生产 `GatewayConfigurationCompiler`）：基线 / 休眠 / 激活 / 激活中拒绝回退 / 停用 / 回退后 **6 次编译全部 COMPILE OK**（effective 613 → 613 → 616 → 616 → 613 → 613）；重复注册被拒（1062）、激活时回退被拒（1242）；回退后 mor_platform 与导出 **0 行差异** |
| mor_commercial SQL | 迁移 / 回滚与仓库 change 文件逐字节相同。演练：迁移 0.32 s → 15 行 `ACTIVE + ENABLED`、violations 0、索引正确、消费表 2；重跑无变化；回滚 → 重跑回滚 → 再迁移均通过；回滚后与导出只差两张保留的消费表与一处索引顺序（DROP / ADD 后 `idx_agreement_capability_lookup` 排到 `idx_agreement_capability_code` 之后，定义不变） |
| compose | 6 个覆盖文件，渲染后与运行容器 env **逐键逐值相同**（commercial 37、ops-bff 20、ops-web 7）。commercial **不需新增 env**：消费端 `messaging.consumer.service: commercial` 写在 `application.yml`，分区默认 4，XXL 执行器已启用 |
| MQ | vhost `/mor-test` 唯一用户 `mor_test` 为 `.* .* .*`，commercial 用该账号，可在启动时声明 `commercial.compliance.hash / p0..p3 / dlx / dlq` 并绑定 `compliance.events`（已存在）；无 policy 冲突 |
| XXL | 分组 14 已在线；两个任务的登记参数见 runbook 第 4 节（Handler `capabilityPrerequisiteRecheck`，`scope=PENDING` 每 5 分钟、`scope=ALL` 每日 03:00 UTC） |
| 语言包 | 只发 operations-console：当前 #19 → 计划 #20：新增 95、英文基准变更 2、废弃 4，**全部在 commercial 命名空间**；zh-CN 只导入 commercial 97 条；#19 的其余 10 216 条 zh-CN 不动、不新建命名空间、`zhChangedNotImported` 0（只读计划通过，`precheck/i18n-plan-dryrun-accept-deprecations.out`）；key 清单 `i18n-bundles/commercial-key-changes.tsv` |
| data-query 附录 | 专用账号 SQL（5 张表的表级 SELECT）离线演练通过（无口令时拒绝执行、可重跑、越权表 ERROR 1142）；数据源 / Vault / 模板登记步骤见 runbook 第 8 节 |

## 2. 发现

1. **四份 ACTIVE 合同没有 ACTIVE 的 CLIENT_PRICING 版本**（CL-F1105E 无方案、DEMO-CL-002 PENDING_APPROVAL、DEMO2-CL-102 APPROVED、DEMO2-CL-901 CLIENT_SIGNED），其上 6 个支付方式开通项（CL-F1105E·CARD、DEMO-CL-002·CARD / GOOGLE_PAY、DEMO2-CL-102·APPLE_PAY / CARD、DEMO2-CL-901·CARD）在旧模型下是 ENABLED。迁移后仍为 `ACTIVE + ENABLED`（不回溯），但**任何一次全量核对**（scope=ALL 每日任务首次运行、对这些合同点「检查前置」、相关 Client / AE001 的 `QualificationChanged`）都会按 technique 9.2 把它们改为 `SUSPENDED_BY_PREREQUISITE`（`TERM_VERSION_NOT_ACTIVE`），Checkout 解析不再输出。其余 3 份合同（DEMO-CL-001、DEMO2-CL-101、DEMO2-CL-103）的价格覆盖无法离线判定（需运行门禁），验收合同选 DEMO2-CL-103。mor-test acquiring 为 MOCK，实际收单不受影响。→ 待裁决 D-1。
2. **data-query 模板编码不符合 Platform 规则**：`golive.agreement_drafted` 等含小写与点，Platform `CreateQueryTemplateRequestBody` 要求 `[A-Z][A-Z0-9_]{1,63}`，登记会被拒。阶段 5 只在 MySQL 直接执行了 SQL，没有经过 data-query 的模板解析与只读守卫。→ 待裁决 D-3。
3. **mor-test 已有 `MOR_COMMERCIAL_REPLICA` 数据源**（`data_query_demo_reader`，库级 `SELECT mor_commercial.*`，ACTIVE / PASSED）。→ 待裁决 D-4。
4. **注册块未写入 Platform 初始化数据**：本需求研发 worktree 没有把块 141 追加到 `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql`（同类需求在阶段 4 追加）。本阶段不改研发代码，留给研发 / 阶段 8。
5. **`-am test` 会读到 messaging 测试配置**：在 reactor 里只跑到 `test` 阶段时，`paytech-messaging` 的 test-jar 解析为整个 `target/test-classes`，其 `application-mock.yaml` 的 `messaging-consumed-event-test.sql`（含 DROP）被 commercial 测试加载，因 `commercial_app` 无 DROP 权限而全部上下文失败；改用 `package`（test-jar 只打包 testkit）后正常。阶段 4 用 `-pl …-test test` 依赖 `~/.m3-gate` 已安装的 jar，所以没遇到。不影响产物。
6. **复制文件保留旧 mtime 会让增量编译漏编**：首次在基线树上构建后再 `cp -p` 叠加本需求文件，`YamlEventCatalogAutoTest` 用了旧 class（失败 1 例）；全部改为 `clean` 后重编重测，上述数字均来自 clean 构建。发布构建应先 `clean`。
7. **现网镜像 tag 名被截断**：法务修订与 3DS 的镜像 tag 为 `operations-console-bffegal-…`、`api-gatewayard3ds-…`（zsh 把 `$svc:legal` 的 `:l` 当修饰符）。只认 ID，不影响；本需求构建用 `"${s}:${TAG}"`，tag 正常。
8. **XXL Admin 不稳定**：当天累计重启 127 次（最近 18:41Z），不是本卡所为；登记前须确认可登录。
9. 语言包需废弃 4 个旧 key（旧执行状态枚举 3 个、旧数量文案 1 个），refdata 发布脚本对废弃默认拦截。→ 待裁决 D-2。
10. `prerequisite-checks` 是 POST + 查看权限，进入有"等前置 / 前置失效暂停"开通项的合同页会自动调用一次；照抄写路由的每人 10 次 / 分钟、并发 2。正常浏览足够；若运营在一分钟内连续打开 10 份以上此类合同会 429（页面显示核对失败，可重试）。

## 3. 风险

| # | 风险 | 缓解 |
|---|---|---|
| R1 | 发现 1：上线后首次全量核对暂停 4 份演示合同的 6 个支付方式开通项 | D-1 裁决前 scope=ALL 任务只登记不启用；验收不在这 4 份合同上点「检查前置」 |
| R2 | ⑤—⑧ 窗口内旧 ops-bff / ops-web 读新取值，合同详情页报错（约 2—3 分钟）；commercial 停机期间商业页 503 | 三步紧接执行；通知并行会话；acquiring 为 MOCK，Checkout 不受影响 |
| R3 | 回滚后验收新增的开通项变为 SUSPENDED；`commercial.compliance.p0..p3` 无消费者后积压 `QualificationChanged` | runbook R3 / R4 说明；队列删除另行确认 |
| R4 | 法务修订会话若再执行其 `compose-candidate.operations-console-{bff,web}.yaml`，会回退本需求的 ops-bff / ops-web | P-3 通知；其阶段 8 记录改指本需求镜像 |
| R5 | 有人在阶段 7 前替换这三个服务 | P-2：执行前重读镜像，不符即停并重建 |
| R6 | 价格覆盖判定只能上线后由门禁得出；验收合同 DEMO2-CL-103 的原有项若被暂停 | `gate_api.py scenario` 第 4 项检查原有项不变，不符即停并报告（属业务事实，不自动回滚） |

## 4. 待裁决

| # | 事项 | 建议 |
|---|---|---|
| D-1 | 4 份没有 ACTIVE 价格版本的演示合同：接受首次全量核对把其 6 个支付方式开通项暂停（这是门禁的设计行为），还是先给这些合同激活价格版本 / 暂缓启用 scope=ALL？ | scope=ALL 先登记不启用；由用户决定补价格或接受暂停后再启用 |
| D-2 | 语言包废弃 4 个旧 commercial key（回滚到旧 Web 时这 4 处退回内置英文） | 接受（`--accept-deprecations`）；旧 key 已无新代码引用 |
| D-3 | data-query 模板编码改为 `GOLIVE_AGREEMENT_DRAFTED`、`GOLIVE_AGREEMENT_ACTIVE`、`GOLIVE_COLLECTION_CAPABILITIES_ENABLED`（REQ-00 契约 3.3 / REQ-05 引用的名称同步改） | 按此改；登记前在本机 data-query 后台先校验三条模板 |
| D-4 | data-query 用新建表级只读账号（任务卡要求），还是复用现有 `MOR_COMMERCIAL_REPLICA`（库级只读，已 PASSED） | 新建表级账号（最小权限）；若只为演示可复用 |
| D-5 | scope=ALL 的 03:00 按 UTC（= 07:00 +04）还是 +04 | 按 UTC（容器与 XXL 均 UTC） |

## 5. 需要用户授权的动作

**主发布（阶段 7，一次授权覆盖下列各项，由主会话执行）**
1. 停 / 起 mor-test `commercial-service`（换 `806043a3…`），并紧接执行 `sql/mor_commercial-migrate.sql`（root，`mor_commercial`）。
2. 替换 `operations-console-bff`（`eca2df82…`）与 `operations-console-web`（`7a053544…`）。
3. 在 `mor_platform` 执行注册块 141（休眠写入 → 激活）。
4. 经 XXL Admin API 登记 2 个任务（启用任务 A；任务 B 按 D-1）。
5. 发布 operations-console 语言包 #20（含 D-2 的 4 个废弃）。
6. 验收写数据：DEMO2-CL-103 新增 / 暂停 / 恢复 / 关闭两个开通项（runbook 第 6 节列出留下的数据）。
7. 失败时按 runbook 第 5 节回滚（含 `mor_commercial-rollback.sql` 与块 141 回退）。

**data-query 附录（另行单独授权，主发布完成后）**
1. 在 mor-test MySQL 创建 `dq_commercial_golive_reader`（`sql/appendix-data-query-reader.sql`，口令存 `test-environment/private/` 0600 文件）。
2. 运营后台新建数据源（口令写入 Vault）并登记 3 条模板（编码按 D-3）。

不在授权范围：提交、推送；删除回滚后积压的 `commercial.compliance.*` 队列；改 `test-environment/compose*.yaml` 基础文件。

## 6. 文件

`release/` 下：[baseline.md](./baseline.md)、[runbook.md](./runbook.md)、本报告；`compose-{candidate,rollback}.{commercial-service,operations-console-bff,operations-console-web}.yaml`；`sql/`（9 个主发布 SQL + 2 个附录 SQL + `SHA256SUMS`）；`harness/`（`rehearse.sh`、`BlockSnapshotCheck.java`、`jarcmp.py`、`envcmp.py`）；`scripts/env_check.sh`；`gate_bundles.py`、`i18n-bundles/`；`acceptance/gate_api.py`、`acceptance/gate_browser.mortest.mjs`；`precheck/`；`evidence/`（基线比对、候选范围、镜像核对、env 比对、测试摘要、演练日志 `rehearsal-20261028T1945Z/`、两组源文件 SHA-256 清单）。
mor-test 只读导出（含数据，不入库）：`~/Desktop/Workspace/Code-MoR-backups/gate-20261029/stage6-readonly-20261028T194134Z/`（`mor_platform.sql.gz`、`mor_commercial.sql.gz`）。
