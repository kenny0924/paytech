# 结账点实时累计：mor-test 发布与验收记录（阶段 7）

- 日期：2026-10-17（UTC 07:26～07:42）
- 授权：用户确认阶段 6（D1～D4 按建议），授权 test 构建、备份、执行 `db/ddl.sql`、依次替换 Risk / Ops BFF / Ops Web、写入验收数据并清理、浏览器验收
- 范围外未动：Platform、Gateway、Nginx、Commerce 及其他服务；未提交、未推送

## 1. 通知

部署前通知 code-mor-76、code-mor-36、code-mor-ca、code-mor-ff：76、36、ca 回复不在使用 test；ff 未回复，按承诺等满 10 分钟（07:26 通知，07:36 才替换 Risk）。

## 2. 复核与备份

- 部署前复核与清单第 1 节一致：19 个容器全部 healthy；`mor_risk` 85 张表，旧的 4 列索引；累计事件 7 条（全部支付），Feature 版本 2，名单 1（`PAY_CARD_BLOCK`）；正式评估：支付 5、结账 6（Commerce 订单），快照 6；生效发布：支付 `236518950932942848`、结账基线 `236943228086095872`；Risk 在 Nacos 中 1 个实例。
- 备份：`~/Code-MoR-backups/20261017-risk-checkout-velocity-test/20261017T072650Z/`，目录 0700、文件 0600，含 SHA256SUMS，不入库。内容为：`mor_risk` 全库 dump、85 张表的行数、19 个容器的镜像清单、三个容器的 inspect、三份 Compose 文件副本。
- 部署前回归基线：经临时代理（`nginx:1.30.4-alpine`，`127.0.0.1:28114` → `risk-service:18104`，网络 `mor-test-backend`）读取 35 个只读接口，覆盖支付与结账的变量列表、2 个支付 Feature 变量的详情、执行发布列表与两个生效发布的详情、名单、流程与规则集列表，以及 11 条正式评估的查询与解释。

## 3. SQL

| 时间 | 文件 | 核对（information_schema） |
|---|---|---|
| 07:27:46Z（旧 Risk 运行中） | [../db/ddl.sql](../db/ddl.sql) | 85 张表；`idx_velocity_subject` 为 `subject_type, subject_key, event_code, occurred_time, velocity_event_id`，注释一致；`event_code` 注释列出 4 个允许值；`t_list_feature_definition` 表注释为“风控点输入字段”；主体表仍为 35 行；旧 Risk 健康检查 200 |

## 4. 替换

| 服务 | 时间 | 镜像 | 检查 |
|---|---|---|---|
| Risk | 07:36:20～07:36:43Z | `725f7d12aef4`（`checkout-velocity-20261017`） | healthy；Nacos 1 个实例；启动日志无 ERROR，WARN 只有启动期 Bean 提示 |
| Ops BFF | 07:37:00～07:37:18Z | `4b702a37c151` | healthy；Nacos 1 个实例；无 ERROR |
| Ops Web | 07:37:18～07:37:30Z | `03668fd01a22` | healthy；容器内 `/usr/share/nginx/operations-console` 的 96 个文件与构建 `dist/` 逐文件 SHA-256 一致；运营入口 200 |

- 其他 16 个容器的镜像不变，全部 healthy。
- Risk 替换的 23 秒内 test 没有 Commerce 建单；从部署到验收结束，Commerce 没有新的结账评估。

## 5. 验收

| 类别 | 结果 |
|---|---|
| 支付与结账回归（只读） | 35 个接口全部 200。部署后与部署前逐项比较，除读取时刻 `valuesAsOf` 外完全一致，包括支付 Feature 变量、两个生效发布、11 条评估的查询与解释 |
| 变量上下文 | 不带点时返回结账、支付两项目录；带结账点或支付点时只返回该点一项。经 BFF（test 运营入口登录后调用）与直调 Risk 的响应完全一致。结账：事件为 `CHECKOUT_ASSESSED`（可计入本次）和 `CHECKOUT_REJECTED`；Client 与运营主体可选次数、最近一次、距上次秒数（最长 1 天）和金额合计（最长 1 小时）；无去重主体；名单主体为 Client、运营主体；币种变量 `INPUT_CURRENCY`。支付：卡指纹等其他主体为五种指标乘四个窗口；Client 与运营主体受同样的 D1 限制；名单主体 6 种 |
| 创建校验（AC-3） | 5 类非法创建都返回 400 `INVALID_VARIABLE_DEFINITION`：结账选支付事件（统计事件不属于该决策点）；Client 7 天次数、Client 1 天金额合计（该统计主体不支持所选统计方式或时间窗口）；卡指纹主体（统计主体不适用于该决策点）；绑定 `PAY_INPUT_CURRENCY`（币种来源须为同决策点的真实币种输入） |
| 验收配置 | 拒绝原因 `STAGE7V_CHECKOUT_BLOCKED`；名单 `STAGE7V_CLIENT_BLOCK`（含合成 Client 991017100000000102）、`STAGE7V_OE_BLOCK`（含 ZZ999，不会命中）；6 个变量：Client 10 分钟次数（含本次）、Client 1 小时净额合计（含本次）、Client 10 分钟被拦截次数、运营主体 1 天次数、两个名单命中；规则集 `STAGE7V_RULES_T3`（名单拒绝；Client 次数 > 3 审核）；流程 `STAGE7V_FLOW_T3` |
| 发布 | 执行发布 `237117060860186624` 校验通过；影子回放 Commerce 的 6 笔订单，结论全部不变，检查通过；随后激活 |
| **审核规则生效窗口** | **07:38:32～07:38:44Z，共 12 秒**；切回默认放行基线：用原基线同一流程版本 `236943227222069249` 新建发布 `237117104682274816`，影子运行后激活 |
| 结账累计（AC-1、AC-2） | 合成订单 991017100000000001～005，运营主体 ZT901，净额 42.9 USD。Client A 第 1～3 笔 ALLOW，第 4 笔 REVIEW；名单 Client B 为 DENY；第 1 笔同内容重放返回原评估 ID。累计事件 7 条：每笔 1 条 `CHECKOUT_ASSESSED`，REVIEW、DENY 各多 1 条 `CHECKOUT_REJECTED`。`source_type` 为 `CHECKOUT_INPUT_SNAPSHOT`，`source_id` 等于快照 ID，金额等于净额 42.9 USD，主体恰为 Client 与 `OPERATING_ENTITY=ZT901` |
| 解释 | 第 4 笔：Client 10 分钟次数 **4**、1 小时净额合计 **171.6**、名单 false、运营主体 1 天次数 3（不含本次）。名单 Client：名单命中 **true** |
| 影子（AC-4） | 候选 `237117099300982784`（阈值 > 2，未激活）回放 5 个样本：每个影子评估的时间都等于原评估时间，只有第 3 笔（原计数 3）由放行变为审核，其余不变。影子运行没有写入累计事件（仍为 7 条） |
| 时延（AC-6） | 合成结账评估 11～17 ms（6 个 Feature）；同环境下 Commerce 订单之前的结账评估为 3～33 ms（无 Feature） |
| 浏览器（D3）：[test_browser.py](./test_browser.py) **13/13 通过** | 变量页：适用决策点只列结账准备、支付尝试（中文名称）。结账点只有结账事件和 Client、运营主体；Client 无“不同主体数”、有限制提示、币种 `INPUT_CURRENCY`；金额合计窗口只有 10 分钟、1 小时；次数无 7 天窗口。支付点行为不变。名单下拉只列 STAGE7V Client 与运营主体名单。评估查询按订单 ID 显示审核与累计值 171.6。经页面创建 `STAGE7V_WEB_OE_COUNT_1H` 成功。**随带上线的 `87fd57f`：**执行发布页可选结账准备、有生效发布、详情无“认证要求变化”；决策流结账点提示执行发布后生效。控制台无报错，无 HTTP ≥ 400 |
| 日志 | 窗口内 Risk、BFF、Web 都没有 ERROR；日志中没有合成 Client ID、运营主体编号、名单值或金额（唯一的“42.9”匹配是时间戳 `07:38:42.978`） |

## 6. 清理（D1：不保留验收数据）

[test-acceptance-cleanup.sql](./test-acceptance-cleanup.sql) 先执行到末尾 ROLLBACK 核对，再改为 COMMIT 执行，两次行数完全一致。

| 类别 | 删除行数 |
|---|---|
| 评估族 | 评估 21（合成正式 5；影子 16，属于 3 个验收影子批次），快照 5，Feature 结果 96，规则轨迹 154，流程轨迹 58 |
| 累计事件 | 事件 7，主体 14（只删合成快照的事件） |
| 发布 | STAGE7V 执行发布 2，影子批次 3，发布审计事件 28 |
| 流程 | 流程 2，版本 2，节点 10，连线 8 |
| 规则集 | 规则集 2，版本 2，规则 12，节点 12，操作数 24，动作 12；拒绝原因 1 |
| 变量与名单 | 变量 7（含页面创建的 1 个），Feature 版本 7（累计 5，名单 2），名单 2，成员 2 |

- **清理后：**
  - 合成评估、STAGE7V 配置、**结账 Feature 变量都是 0**，回退能力恢复（清单第 6 节）。
  - 支付数据未动：事件 7、Feature 版本 2、评估 5。
  - Commerce 的 6 笔结账评估与快照保留。
- **与部署前相比，85 张表中只有两处差异：**
  - `t_decision_release` 多 1 行：切回时新建的同版本基线 `237117104682274816`，ACTIVE。
  - `t_release_audit_event` 多 15 行：这条新基线的创建、校验、影子、激活记录，以及原基线被替代的记录。
- **新基线的影子批次已删除：**它以 STAGE7V 发布为对照基线，保留会成为悬空引用。
- 回归接口再读一次：只有原基线 `236943228086095872` 的状态（现为 SUPERSEDED）和结账发布列表有变化，其余与部署前一致。
- **当前生效发布：**支付 `236518950932942848`；结账 `237117104682274816`（`CHECKOUT_BASELINE_ALLOW`，与原基线同一流程版本）。
- **临时资源：**代理容器 `s7v-risk-proxy` 已删除；构建 worktree `scratchpad/s6-wt` 已 `git worktree remove` 并 prune；临时聚合 POM 目录已删除。

## 7. 当前 test 镜像

Risk `725f7d12aef4`、Ops BFF `4b702a37c151`、Ops Web `03668fd01a22`（均为 `checkout-velocity-20261017`）；回退文件 [compose-rollback.yaml](./compose-rollback.yaml)（三者一起回退）。

## 8. 遗留

- 原结账基线 `236943228086095872` 已被同版本的新发布 `237117104682274816` 替代。执行发布不能停用、只能被替代，这是预期结果；上一需求记录里引用的基线发布 ID 以本记录为准。
- 证据文件（回归快照、上下文对比、日志、清理输出）在 scratchpad `s7v/`，不入库。
