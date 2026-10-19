# commercial 六层产品定价模型 交付记录

## 适用阶段（建议，待确认）

| 阶段 | 安排 |
|---|---|
| 1. 需求评审与范围确认 | 已确认（2026-10-19，"开始吧"）：范围 S1–S5、不做项、AC-01—07、阶段划分 |
| 2. 现状差异与影响分析 | 已确认（2026-10-19，与阶段 1 合并）：design.md 第 2、9 节 |
| 3. 方案、接口与数据设计 | 已确认（2026-10-19，"继续，agent用opus5.5"：本地 Maker-Checker、E 线纳入首切片均按建议）：数据设计已落模；接口设计见 [technique.md 第 2 节](./technique.md)（写入 API 20 条、版本解析规则、价格命中修订、解析响应扩展、激活校验、费率卡读接口、两个事件、控制台注册、S5 收口、验证矩阵）；待确认两点：本地 Maker-Checker 代替未实现的审批引擎、E 线控制台页面是否纳入首切片 |
| 4. 研发实现 | 进行中（2026-10-19）：A → B / C / D → E 五线，Opus 5.5 agent 执行，任务卡在 [tasks/](./tasks/)，主会话写卡、门禁、独立重跑 |
| 5. 综合验证与本地验收 | 本机 local-environment 全链路 |
| 6. test 发布准备 | mor-test 增量 SQL 评审、镜像 |
| 7. test 部署与验收 | 按 release 清单 |
| 8. 提交与归档 | 私有索引提交 |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-19 | 立项前讨论 | "可以，按照这个模型"；"都按照你建议来吧"；多币种"先按照建议"；Profile 改名与字段"这个随你"；商业关系模式"需要支持" | 登记决定记录、设计稿、画板与模型；本机 `mor_commercial` 变更脚本经"允许"后执行 |
| 2026-10-19 | 4–7 | "我准备睡觉了，等下你可以帮我把这个需求做完发到test环境吗？注意别的chat别冲突了"：委托主会话自动推进阶段 4–7 至 mor-test 发布；"授权"：本地提交（不推送） | 按五条护栏执行：worktree 并行与 rebase、D 线不碰 acquiring、mor-test 只动本需求组件与两库、共享组件先通知其他会话、受阻即停在安全点 |
| 2026-10-19 | 4 | "继续，agent用opus5.5"：确认阶段 3（本地 Maker-Checker、E 线纳入） | 阶段 4：任务卡 A 启动；B / C / D 待 A 合入后并行；E 最后 |
| 2026-10-19 | 3 | "开始吧"：确认阶段 1 + 2 与阶段划分 | 阶段 3 接口设计（只写文档，不改业务代码、不执行 SQL）|
| 2026-10-19 | 登记 | "你继续" | 登记需求目录，交付阶段 1 评审稿与阶段划分建议 |

## 当前阶段与下一确认点

阶段 4 进行中（2026-10-19 夜间，用户委托自动推进）：
- A 线完成并经主会话独立复跑（commercial 42、arch-rules 61、BUILD SUCCESS）；基点本地提交 `08c8f794`（186 文件，仅本需求路径，私有索引）。
- 基点后主树补：`t_commercial_term_version.status` 增 PENDING_APPROVAL（技术方案 §2.1 需要）；`t_agreement` / `t_commercial_term_price` 表注释精简到 MySQL 2048 字符上限内（原生成 Schema 的 t_agreement 注释超限，从空库建表会失败；BCR 共享注释 1660 字符未超限、不改）；模型门禁 91/93（2 项为 client-master 既有）。
- T0（消息 / 补偿事件 / Redis 锁 / XXL-Job 基础设施接入 commercial，解除模块 Enforcer 禁用）在主树执行中；D 线在 worktree `Code-MoR-lineD`（分支 `commercial/line-d`，基于 08c8f794）执行中。
- 本机隔离测试库 `mor_commercial_lineb / linec / lined`（各 21 表，commercial_app 授权）已按 `init/ddl.sql` 建立，供三线并行测试，避免共用 `mor_commercial` 互相清库。
- 跨会话协同：code-mor-13 无冲突；code-mor-85 今晚发 billing / channel-connector / account / ledger，互不碰容器；code-mor-9c（ledger 科目分级）也要动 ops-bff / Web / Platform（块 91），约定本需求先发（Platform 注册块 95），发完把镜像 sha256 与改动文件列表给它叠加；code-mor-32（counterparty 改名）今晚重建 acquiring / card，本需求不碰 acquiring。

## 已完成证据（登记时）

- 设计：design.md 第 1–13 节、database.md 第 1–8 节、design.pen 七张画板。
- 模型：`commercial.pdma.json` 20 表 203 字段、28 唯一键、25 索引、21 关系、4 图组；模型门禁 93/96（3 项失败为 client-master、channel-connector 既有问题）。
- 代码：commercial 模块画像类改名，Maven 链 BUILD SUCCESS，commercial 测试 24/24。
- 本机数据库：`mor_commercial` 已按 round2 脚本变更；2026-10-19 阶段 4 A 线再按 `paytech/commercial/docs/database/sql/local/2026-10-19-round3-local.sql` 变更（六张新表、Price `currency` 与三个键重建、Version / Component 新列；审阅：6 CREATE、4 ALTER、无 DML、无 DROP 库表、受影响四表为空），现 21 张表与 `init/ddl.sql` 一致。两次均只针对 local-environment，不含 mor-test。
- 未提交。

## 遗留项

- BCR 共享白名单（PRICING_PROFILE、AGREEMENT_PRODUCT、AGREEMENT_CAPABILITY）待按服务拆分。
- client-master：`partner_role`、`t_client_partner_relation`、候选接口返回推荐伙伴。
- acquiring：Order Context 冻结 `fundMode`、`referralPartnerId`、开通项；D02 冻结字段定义扩展。
- billing / account：Direct Merchant 应收与开票、BUY_RATE_DIFFERENCE 分润、月量阶梯用量。
- 渠道连接：成本配置迁入、路由策略与毛利快照。
- 测试 DDL 尚未包含六张产品表与 Price `currency`（阶段 4 A 线补）。
