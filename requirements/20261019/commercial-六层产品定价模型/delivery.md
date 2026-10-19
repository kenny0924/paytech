# commercial 六层产品定价模型 交付记录

## 适用阶段（建议，待确认）

| 阶段 | 安排 |
|---|---|
| 1. 需求评审与范围确认 | 已确认（2026-10-19，"开始吧"）：范围 S1–S5、不做项、AC-01—07、阶段划分 |
| 2. 现状差异与影响分析 | 已确认（2026-10-19，与阶段 1 合并）：design.md 第 2、9 节 |
| 3. 方案、接口与数据设计 | 已确认（2026-10-19，"继续，agent用opus5.5"：本地 Maker-Checker、E 线纳入首切片均按建议）：数据设计已落模；接口设计见 [technique.md 第 2 节](./technique.md)（写入 API 20 条、版本解析规则、价格命中修订、解析响应扩展、激活校验、费率卡读接口、两个事件、控制台注册、S5 收口、验证矩阵）；待确认两点：本地 Maker-Checker 代替未实现的审批引擎、E 线控制台页面是否纳入首切片 |
| 4. 研发实现 | 进行中（2026-10-19）：A → B / C / D → E 五线，Opus 5.5 agent 执行，任务卡在 [tasks/](./tasks/)，主会话写卡、门禁、独立重跑 |
| 5. 综合验证与本地验收 | 已完成（2026-10-19 夜间，用户委托）：真实 commercial 进程 @ `0ecc5713` + 本机 MySQL / RabbitMQ / Redis，9 步 HTTP 流程全部通过，两类事件到达 Broker，见 [verification/stage5-smoke.md](./verification/stage5-smoke.md)；无业务缺陷。偏差：Snowflake worker 只允许 0–31（卡上 33 有误，改 26）；冒烟数据留在 `mor_commercial_lineb` |
| 6. test 发布准备 | 已完成（2026-10-20，任务卡 G）：只读核对、干净 worktree @ `cf39a1ee` 构建三镜像（tag commercial-20261020，钉 sha256）、compose-candidate.yaml、test-activate / deactivate.sql，见 [release/release.md](./release/release.md) |
| 7. test 部署与验收 | 已完成（2026-10-20）：备份 `~/Code-MoR-backups/20261019T213228Z-commercial-test/`；`mor_commercial` DDL + DML（20 表，7 / 11 / 21）；`mor_platform` 块 95 试跑回滚后提交（5 / 5 / 8 / 24 / 128 / 5 / 144）；三容器替换 healthy；XXL 执行器 commercial-service 与任务 commercialTermVersionActivation；test-activate 24 / 8 / 5 / 1；验收接口 46/47（脚本缺参一处，复测 200）、页面回归 37/37，见 [verification/stage7-acceptance.md](./verification/stage7-acceptance.md)；合成数据已清理 |
| 8. 提交与归档 | 进行中（2026-10-20）：代码已随各线合入 main（`0ecc5713`、`cf39a1ee`）；需求目录、台账、索引与决定记录状态由主会话私有索引提交 |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-19 | 立项前讨论 | "可以，按照这个模型"；"都按照你建议来吧"；多币种"先按照建议"；Profile 改名与字段"这个随你"；商业关系模式"需要支持" | 登记决定记录、设计稿、画板与模型；本机 `mor_commercial` 变更脚本经"允许"后执行 |
| 2026-10-20 | 6–7 | （委托执行）阶段 6 / 7 按任务卡 G 完成；无被拒命令；一处共享 compose 文件编辑被分类器拒绝，留待用户 | 阶段 8 归档提交 |
| 2026-10-19 | 4–7 | "我准备睡觉了，等下你可以帮我把这个需求做完发到test环境吗？注意别的chat别冲突了"：委托主会话自动推进阶段 4–7 至 mor-test 发布；"授权"：本地提交（不推送） | 按五条护栏执行：worktree 并行与 rebase、D 线不碰 acquiring、mor-test 只动本需求组件与两库、共享组件先通知其他会话、受阻即停在安全点 |
| 2026-10-19 | 4 | "继续，agent用opus5.5"：确认阶段 3（本地 Maker-Checker、E 线纳入） | 阶段 4：任务卡 A 启动；B / C / D 待 A 合入后并行；E 最后 |
| 2026-10-19 | 3 | "开始吧"：确认阶段 1 + 2 与阶段划分 | 阶段 3 接口设计（只写文档，不改业务代码、不执行 SQL）|
| 2026-10-19 | 登记 | "你继续" | 登记需求目录，交付阶段 1 评审稿与阶段划分建议 |

## 当前阶段与下一确认点

阶段 1–7 完成，阶段 8 归档提交进行中（2026-10-20）。**待用户处理：** (1) `test-environment/compose.applications.yaml` 的 commercial-service 需合入 [release/compose-candidate.yaml](./release/compose-candidate.yaml) 中的 environment 与 depends_on（主会话编辑该共享文件被权限分类器拒绝；合入前 `deploy.sh` / 普通 `compose.sh up` 会因缺 `COMMERCIAL_SNOWFLAKE_*` 失败，当前容器按候选文件运行正常）；(2) mor-test `mor_commercial` 11 处表 / 列注释仍为旧文本，需单独授权的仅注释 ALTER；(3) 本机 `mor_commercial_lineb / linec / lined` 隔离库保留待你决定是否删除；(4) `messaging.publisher.unroutable-policy` 在 acquiring 绑定 commercial.events 后改回 FAIL；(5) 推送与 PR 未做，按你的授权再定。

## 已完成证据（登记时）

- 设计：design.md 第 1–13 节、database.md 第 1–8 节、design.pen 七张画板。
- 模型：`commercial.pdma.json` 20 表 203 字段、28 唯一键、25 索引、21 关系、4 图组；模型门禁 93/96（3 项失败为 client-master、channel-connector 既有问题）。
- 代码：commercial 模块画像类改名，Maven 链 BUILD SUCCESS，commercial 测试 24/24。
- 本机数据库：`mor_commercial` 已按 round2 脚本变更；2026-10-19 阶段 4 A 线再按 `paytech/commercial/docs/database/sql/local/2026-10-19-round3-local.sql` 变更（六张新表、Price `currency` 与三个键重建、Version / Component 新列；审阅：6 CREATE、4 ALTER、无 DML、无 DROP 库表、受影响四表为空），现 21 张表与 `init/ddl.sql` 一致。两次均只针对 local-environment，不含 mor-test。
- 未提交。

## 发布后环境动态（2026-10-20，其他会话）

- code-mor-9c 随后发布 ledger 科目分级：基于 `cf39a1ee`（含本需求 E 线）重建 ops-bff / ops-web，运行镜像改为 ops-bff `971c9238…`、ops-web `db35544f…`（tag chart-20261020），并回归了本需求的合同、价目页面正常；Platform 块 91 启用后 PLATFORM_ADMIN 版本 32 → 33。本需求的 ops 镜像 `2bd2afee` / `edf140b9` 已被取代，候选文件中的 ID 仅作历史记录。
- 22:53Z mor-test RabbitMQ 被 OOM Kill 并由 code-mor-9c 重启；commercial 容器未重启，仍 healthy。
- 23:30Z 起 commercial 日志出现 XXL 执行器注册读超时（xxl-job-admin 本就不稳定，重启计数 17+，发布 Agent 已记录）；属共享基础设施问题，不是本需求引入，注册线程会自动重试。

## 遗留项

- BCR 共享白名单（PRICING_PROFILE、AGREEMENT_PRODUCT、AGREEMENT_CAPABILITY）待按服务拆分。
- client-master：`partner_role`、`t_client_partner_relation`、候选接口返回推荐伙伴。
- acquiring：Order Context 冻结 `fundMode`、`referralPartnerId`、开通项；D02 冻结字段定义扩展。
- billing / account：Direct Merchant 应收与开票、BUY_RATE_DIFFERENCE 分润、月量阶梯用量。
- 渠道连接：成本配置迁入、路由策略与毛利快照。
- 测试 DDL 尚未包含六张产品表与 Price `currency`（阶段 4 A 线补）。
