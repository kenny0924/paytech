# commercial：开通项前置门禁 · 交付记录

| 日期 | 阶段 | 内容 | 状态 |
|---|---|---|---|
| 2026-10-28 | 来源 | design-discuss/client-activation 讨论：用户确认 Client 状态只表示合作关系、可收款 / 可出款由开通项控制（Q1），复核后门禁收窄到 ①资格 ②合同价格 ④渠道身份；登记为 [2026-10-28 客户上线决定](../../../agent-docs/decisions/2026-10-28-client-go-live-orchestration.md) REQ-01 | 已确认（讨论） |
| 2026-10-28 | 1 需求评审 | [需求](./requirement.md) 评审稿：范围 R1—R9、规则 B1—B7、逐系统变动点、验收 AC-01—AC-11、待确认 Q1—Q3 | 已交付 |
| 2026-10-28 | 1 确认 | 用户指示三个需求在本会话按 REQ-01 → REQ-05 → REQ-12 逐个推进；Q1—Q3 未逐条答复，按建议执行（Q1 开关关闭视为满足并标注；Q2 5 分钟可配置；Q3 经阶段 2 核对不适用：合同激活不创建开通项） | 已确认（按指示） |
| 2026-10-28 | 2 差异与影响 | [technique.md](./technique.md) 评审稿（只读核对）：发现 F-01—F-13、需求项差异、逐系统影响、待决 S-01—S-08。关键：价格激活依赖开通项 ENABLED，与门禁 ② 形成循环依赖（F-04/05 → S-02）；手动状态切换需与门禁区分（F-02 → S-01）；资格查询继承结算冻结语义（F-06 → S-03）；commercial 首个 MQ 消费者（F-07） | 已交付 |
| 2026-10-28 | 2 确认（部分） | 用户：循环依赖是设计问题 → 同意方案 A（status 拆为成员资格 + execution_status）；检查结果 JSON 是过度设计 → 不存，改为现场核对接口返回。S-03、S-05—S-08 待确认 | 部分确认 |
| 2026-10-28 | 2 确认 | 用户确认 S-03、S-05—S-08 按建议 | 已确认 |
| 2026-10-28 | 3 方案 | [technique.md](./technique.md) 第 7—16 节评审稿：单一门禁 Processor + 补偿任务 / MQ / Job / HTTP 四类触发；表 `t_agreement_capability` 加 `execution_status`、`status` 改为 ACTIVE / DISABLED、存量映射与回滚、部署顺序；状态迁移表；3 个新接口与 4 个枚举；`AgreementCapabilityStatusChanged` 按合同版本一条（修订 REQ-00 3.1）；`QualificationChanged` 首个消费者；2 个补偿类型；data-query 单行约定（修订 REQ-00 3.3）与 3 条模板；测试矩阵与发布顺序。④ 本需求固定 SKIPPED，不写无消费者的客户端 | 已确认 |
| 2026-10-28 | 3 确认 / 4 启动 | 用户回复"继续"：接受阶段 3，进入阶段 4。worktree `/Users/kenny/Desktop/Workspace/Code-MoR-gate`（基于 `663e5e4a`），私有 Maven 仓库 `~/.m3-gate`；[任务卡](./verification/task-card-stage4.md)；允许对本机 `mor_commercial` 执行迁移 / 回滚脚本以支撑 auto 测试；不提交、不部署 | 进行中 |
| 2026-10-28 | 4 补设计 | 用户指出未做 Pencil 页面设计即开始写前端。暂停 ops-web 改动，补画 `operations-console.pen` 商务 · 14—17 四张画板；用户确认原型与只读权限建议（查看权限可看"还差什么"并检查前置，写操作需维护权限）；technique.md 新增 13.3；ops-web 按原型返工 | 已确认 |
| 2026-10-28 | 4 暂停 | 用户下班要求暂停；agent 未及时响应暂停指令，主会话在无构建 / 测试进程时停止 agent，核对现场并写 [中间报告](./verification/stage4-report.md)：test-compile 与 ops-web tsc 通过；commercial 246 例、ops-bff 2440 例最近一次全通过；ops-web 返工进度与第 15.1 节矩阵覆盖待核对；worktree `/Users/kenny/Desktop/Workspace/Code-MoR-gate` 与本机 `mor_commercial`（已迁移）保持现状，未提交 | 暂停 |
| 2026-10-29 | 4 恢复 | 用户回复"继续"：派发新开发 agent 按中间报告续做（ops-web 按原型返工、ops-bff 权限与重测、测试矩阵补齐、事件目录核对、本机回滚验证后重迁移、全量验证、最终报告） | 进行中 |
| 2026-10-29 | 4 完成 | [最终报告](./verification/stage4-report.md)：commercial 246 例（基线 149，新增 97）、ops-bff 2440(+1) 例、ops-web 1576 例与 build / i18n 导出、全 reactor test-compile 121 模块、arch-rules 70 + 5 通过；本机 `mor_commercial` 回滚 + 迁移验证（含 PENDING → SUSPENDED）；ops-web 按原型 14—17 返工 | 已完成 |
| 2026-10-29 | 4 确认 / 5 授权 | 用户"按建议，开始本机联调"：接受进入合同详情自动核对一次；编辑保留抽屉；费率卡本期按成员资格展示；恢复弹窗暂停人改显示姓名（复用 Platform 运营账号显示名，阶段 5 前补）；授权阶段 5 本机联调（替换本机 e2e commercial / ops-bff JAR、迁移 `mor_commercial_v2e2e`、worktree ops-web 另起 18311、演示数据、浏览器走查；不含 mor-test、提交） | 已确认 |
| 2026-10-29 | 5 本机联调 | [stage5-report](./verification/stage5-report.md)：本机 e2e 替换 commercial / ops-bff JAR（备份 *.pre-gate）、`mor_commercial_v2e2e` 迁移、worktree ops-web 起于 18311；本机 Gateway 加 3 条本机专用路由（有回滚 SQL）；本机语言包 #8 / #9；演示合同 GATE-CLIENT-MOR-AE001 四种执行状态；zh-CN / en × 1440 / 390 走查 32 张截图通过，5 处页面问题已修；暂停人显示姓名；data-query 三条模板满足 / 未满足两侧通过。影响：主工作区 18301 旧 ops-web 打开含开通项的合同详情会报响应无效（新旧须相邻发布） | 已完成 |
| 2026-10-29 | 5 裁决 | 用户按建议：本机 MinIO 跨域加入 18311；"价格未覆盖"带出具体币种（扩展原因参数）；不补块 124 dimensions 路由；新版本保留在本机运行 | 已完成 |
| 2026-10-29 | 5 裁决后修改 | stage5-report 第 11 节：PRICE_NOT_COVERED 新增参数 `currencies`（字母序、逗号分隔），三处页面文案显示币种；commercial auto 247 例、ops-web 1576 例与 build / i18n 通过；本机 commercial JAR 再替换（23:01）；本机 `.env.local` 追加 `MOR_MINIO_CORS_ALLOW_ORIGIN`（含 18311）并只重建 minio（23:03，回滚：删行重建）；普通浏览器 zh-CN 验证通过；补拍截图 | 已完成 |
| 2026-10-29 | 5 确认 / 6 启动 | 用户回复"继续"：接受阶段 5，进入阶段 6（mor-test 发布准备，只读核对 + 本机构建）。[任务卡](./release/task-card-stage6.md)：基线、候选源码（663e5e4a + 国家包法务修订线上未提交改动 + 本需求）、镜像 `gate-20261029a`、迁移 / 注册块 SQL 与编译演练、MQ / XXL、compose 候选与回滚、语言包、data-query 附录（单独授权）、runbook、验收脚本 | 进行中 |
| 2026-10-29 | 6 基线更正 | 国家包法务修订会话通知已提交 main `ac3145f4` + `365c0e5e`（未推送）；候选基线改为 `365c0e5e` + 本需求改动 | 已更正 |
| 2026-10-29 | 6 发布准备 | [stage6-report](./release/stage6-report.md)、[runbook](./release/runbook.md)、[baseline](./release/baseline.md)：现网 commercial e5c1ee6b / ops-bff 769edbce / ops-web 45ee11cb；候选 = `ac3145f4` + 本需求 130 个文件（无重叠），基线逐字节等于现网；clean 构建 commercial 247、ops-bff 2442、messaging 12、ops-web 1581 + build / i18n、test-compile 121 模块通过；镜像 `gate-20261029a`（commercial 806043a3、ops-bff eca2df82、ops-web 7a053544）；注册块 141（3 路由 / 18 头 / 18 限流）离线编译 6 次通过；迁移 / 回滚演练通过；语言包 #19 → #20（只 commercial）；XXL 2 任务参数；验收脚本；待裁决 D-1—D-5 | 已确认 |
| 2026-10-29 | 6 确认 / 7 授权 | 用户"按建议"：D-1 接受门禁暂停无价格覆盖的演示开通项，全量任务验收后再启用；D-2 接受废弃 4 个 key；D-3 模板编码改为 `GOLIVE_*`（决定、REQ-01 / REQ-05 文档、模板文件已改）；D-4 data-query 复用 `MOR_COMMERCIAL_REPLICA`；D-5 03:00 UTC；授权主发布（data-query 附录暂不做）。首次执行停容器被 auto 模式拦截，用户随后明确"你帮我执行吧" | 已确认 |
| 2026-10-29 | 7 mor-test 发布 | 20:01Z ① 基线一致、发布前记录；② env 6 / 6 一致；③ 备份 `~/Desktop/Workspace/Code-MoR-backups/gate-20261029/mortest-*`；④ 预检一致；⑤ 20:09:25Z 停 commercial → 迁移（15 行 ACTIVE + ENABLED、violations 0、索引与消费表正确）；⑥ commercial `806043a3` healthy，`commercial.compliance.p0..p3` 各 1 消费者；⑦ ops-bff `eca2df82`、⑧ ops-web `7a053544` healthy（窗口约 3 分钟）；⑨ 15 项 ACTIVE + ENABLED、7 个 Client 中 6 个 Checkout 一致，DEMO-CL-001 由发布前 503 OWNER_UNAVAILABLE 变为 200（APPLE_PAY、CARD，有生效价格，判定为改善非回归）；⑩ 块 141 休眠写入快照 613 正常 → 激活 616，新路由未登录 401 与既有路由一致；⑪ XXL 任务 25（scope=PENDING，启用，手动触发 handle 200）、26（scope=ALL，登记停用）；⑫ 语言包 #20 READY 且为当前（发布请求 504 但 Platform 已完成，未重发）；⑬ A2 门禁场景 13 项通过，**1 项发现**：DEMO2-CL-103 原有 GOOGLE_PAY、STC_PAY 被门禁改为前置失效暂停（PRICE_NOT_COVERED，AED 无价格），按 runbook 停止验收、不回滚，待用户确认 | 已完成 |
| 2026-10-29 | 7 验收完成 | 用户"按建议"：接受 DEMO2-CL-103 的 GOOGLE_PAY / STC_PAY 因 AED 无价格被门禁暂停（同 D-1）。A3 浏览器 zh-CN / en × 1440：合同详情、还差什么、暂停弹窗、编辑 / 新增抽屉与回归页（入驻配置、卡 BIN、参考数据、对账）通过，console 错误 0、HTTP 错误 0、原始 key 0；A4 `commercial.compliance.*` 各 1 消费者、DLQ 0；A5 验收新增 TOKEN_STORAGE、APPLE_PAY → DISABLED；随后启用 XXL 任务 26（scope=ALL，03:00 UTC）。mor-test 当前：commercial `806043a3`、ops-bff `eca2df82`、ops-web `7a053544`、块 141 ACTIVE、语言包 #20、XXL 25 / 26 启用。验收遗留：DEMO2-CL-103 两行 DISABLED 开通项与 BCR 记录；GOOGLE_PAY / STC_PAY 前置失效暂停（补 AED 价格后自动恢复） | 已完成 |
| 2026-10-29 | 7 旁证 | design-discuss-0e 只读复核：新 ops-bff 下财务币种下拉 4 种查询与发布前逐字节相同、非法 status 仍 400；ledger / billing / card / client-master readiness UP，20:00Z 起刷新失败与 ERROR 为 0；旧接口仍 404；参考数据条数 4/249/47/42/298 | 通过 |
| 2026-10-29 | 7 追加修改 | 用户看页面后指出区头「新增开通项」与产品栏「+ 新增开通项」重复（两者均为既有按钮）→ 去掉区头按钮（`AgreementDetail.tsx` 一行，两个 worktree 同步；文案 key 仍被抽屉标题使用，不动语言包）；ops-web `npm test` 1581 通过、build 通过；镜像 `gate-20261029b` `sha256:8aec3df8…`，新候选 `release/compose-candidate.operations-console-web.b.yaml`；00:33（+04）替换 mor-test ops-web，healthy，浏览器 zh-CN 1440 复核区头只剩「修改订阅」。回滚目标仍为 `7a053544`（a 版）或法务修订 `45ee11cb`（需连同 commercial 回滚） | 已完成 |
| 2026-10-29 | 7 确认 / 8 授权 | 用户回复"没有，继续阶段 8"（不需要门禁可配置）：接受阶段 7，授权本地提交（不推送）、更新台账与决定状态、清理 worktree；本机联调环境保留 | 已确认 |
| 2026-10-29 | 8 提交 | 提交 `ba0e15b5`（300 个文件，未推送）：以 main `d0b45d54` 为父经独立 index 提交：代码 130 个文件（= mor-test 运行的 `gate-20261029a` + ops-web `gate-20261029b` 源码，基于 `ac3145f4`）、本需求目录（不含 `verification/stage5/backup/` 本机库导出）、REQ-05 / REQ-12 阶段 1 评审稿、客户上线决定、design-discuss/client-activation、requirements / design-discuss README 与 CURRENT_IMPLEMENTATION 本需求条目。`operations-console.pen` 的商务 · 14—17 画板因该文件含其他会话未提交改动，未随本提交 | 已完成 |
| 2026-10-29 | 8 收尾 | 主工作区 130 个代码文件已同步为提交内容（原为基线、无他人改动）；共享 index 已按提交同步；本记录与 `release/harness/rehearse.sh`、`release/scripts/env_check.sh` 可执行位修正另行提交；开发 worktree 待清理 | 已完成 |

## 适用阶段

默认八阶段（待用户确认）。SQL、mor-test 部署、提交、推送分别授权。

## 当前阶段与下一确认点

阶段 1—8 已完成（2026-10-29）。推送另行授权。本机 e2e 保留本需求 JAR、18311 与 MinIO 跨域（用户决定）。阶段 4 参考 `verification/stage4-report.md` 中间报告，按其"明天继续时的第一步"续做；完成后交付最终报告，等待用户确认后进入阶段 5（本机联调与浏览器验收）。
