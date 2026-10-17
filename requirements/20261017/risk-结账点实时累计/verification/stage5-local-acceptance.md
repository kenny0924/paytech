# 阶段 5 本机验收记录（2026-10-17）

## 环境

| 组件 | 制品 | 说明 |
|---|---|---|
| Risk | 工作区构建的 `risk-web` exec jar，SHA-256 `009c07f2…b4bc` | 18104，经 `run-service.sh risk`（`E2E_JAR_DIR` 指向 scratchpad 副本，`RISK_DECISION_ENVIRONMENT=TEST`）重启；Nacos `mor-local`；健康 200，启动无 ERROR |
| Ops BFF | 工作区构建的 exec jar，SHA-256 `f30ade22…7c55` | 18201，经 `run-service.sh ops-bff` 重启（替换 code-mor-87 的 Webhook 错误码验收 jar，事先确认对方未在使用） |
| Commerce | HEAD `f5614f0`（Commerce 无未提交改动），SHA-256 `b6044559…5452` | 临时在 18102 启动（`command-idempotency.enabled=false`，snowflake worker 5，Nacos `mor-local`），验收后已停止 |
| Operations Web | vite dev server 18301（工作区源码） | 经 Nginx `https://127.0.0.1:18444` → Gateway → Ops BFF → Risk |

**BFF 构建方式：**工作区 BFF 引用 `WebhookEndpointErrorCode`（提交 14381f9），而 `~/.m3` 中的 `platform-support-api` 早于该提交、不含此类。按 code-mor-87 的要求，不把工作区的 platform-support-api 安装进 `~/.m3`，而是用 scratchpad 中的临时聚合 POM 把 `platform-support-api` 与 `paytech-operations-console-bff-web` 放在同一 reactor 中 `clean package`（不 install）。已核对：jar 内的 platform-support-api 含该枚举，risk-api 含 `FeatureCatalog`；`~/.m3` 中 platform-support-api 未变化。jar 包含工作区里其他会话未提交的 BFF/platform-support-api 改动（Webhook、Notification），这就是本机当前运行状态。副作用：`clean` 清空了这两个模块的 `target/`。

## 配置（均经 Risk 接口）

| 对象 | 内容 |
|---|---|
| 基线 | 流程 `CHECKOUT_BASELINE_ALLOW`（开始 → 放行），执行发布 `237107830954409984` 激活（本机 Risk 测试曾清掉原基线） |
| 名单 | `STAGE5V_CLIENT_BLOCK`（Client，含 236617121721757698）；`STAGE5V_OE_BLOCK`（运营主体，含 ZZ999，不命中） |
| 变量 | Client 10 分钟结账评估次数（含本次）、Client 1 小时净额合计（含本次，币种 `INPUT_CURRENCY`）、Client 10 分钟被拦截次数、运营主体 1 天评估次数、Client 名单命中、运营主体名单命中 |
| 规则集 | `STAGE5V_RULES_T3`：Client 名单 → 拒绝；运营主体名单 → 拒绝；Client 10 分钟次数 > 3 → 审核；另 3 条高阈值规则（不命中，确保每个累计变量都被计算） |
| 发布 | `STAGE5V_FLOW_T3`（开始 → 规则集 → 拒绝/审核/放行），执行发布 `237107887854338048`：影子 2 个样本（检查通过）→ 激活 |

## 创建校验（AC-3）

| 请求 | 结果 |
|---|---|
| 结账变量选支付事件 `PAYMENT_ATTEMPT_ASSESSED` | 400 `INVALID_VARIABLE_DEFINITION`“统计事件不属于该决策点” |
| 结账 Client 7 天次数 | 400“该统计主体不支持所选统计方式或时间窗口” |
| 结账 Client 1 天金额合计 | 400，同上 |
| 结账卡指纹主体 | 400“统计主体不适用于该决策点” |
| 结账金额变量绑定 `PAY_INPUT_CURRENCY` | 400“币种来源须为同决策点的真实币种输入” |

## Commerce → Risk 端到端（AC-1、AC-2）

| 场景 | Commerce | Risk 结论 | 累计取值（评估解释） |
|---|---|---|---|
| 样本 Client C 两笔 | 201 / 201 | ALLOW（基线） | — |
| Client A 第 1～3 笔 | 201 ×3 | ALLOW | — |
| Client A 第 4 笔 | 422 `CHECKOUT_NOT_ELIGIBLE` | REVIEW（`CLIENT_BURST`） | Client 10 分钟次数 **4**（含本次）；1 小时净额合计 **171.6**（4 × 42.9）；运营主体 1 天次数 5（不含本次）；名单 false |
| 名单 Client B | 422 `CHECKOUT_NOT_ELIGIBLE` | DENY（`CLIENT_BLOCKED`） | Client 名单 **true**；Client 次数 1 |
| Client A 第 1 笔经 Risk 直接重放 | — | 原结果 ALLOW、原评估 ID | 事件数仍为 9 |

**累计事件（数据库核对）：**7 笔正式评估共 9 条事件。每笔都有 `CHECKOUT_ASSESSED`；REVIEW、DENY 各多一条 `CHECKOUT_REJECTED`。全部满足：`source_type=CHECKOUT_INPUT_SNAPSHOT`，`source_id` 等于快照 ID，金额等于净额 42.9、USD，主体恰为 `CLIENT` 与 `OPERATING_ENTITY=AE001`。

## 影子（AC-4）

候选发布 `237108453749833728`（阈值改为 > 2，不激活）回放 5 个样本：放行 → 审核 1 笔，其余不变。逐笔核对：每个影子评估的评估时间都等于原正式评估时间，回放出的 Client 10 分钟次数依次为 1、2、**3**、4、1，只有原计数 3 的 A-3 结论变化。若按当前时间计算，A-1～A-3 都会变化。影子运行未写入任何事件（仍为 9 条）。

## 时延（AC-6）

| 项 | 本次 | 上一需求（无 Feature 基线） |
|---|---|---|
| Risk 评估耗时（预热后，6 个 Feature） | 19～36 ms（首笔 64 ms） | 4～16 ms |
| 单个 Feature 耗时 | ≤ 3 ms | — |
| Commerce 端到端（预热后） | 50～100 ms | — |

在 Commerce 1 秒读超时与结账点 500 ms 超时内；增量主要是 6 次 Feature 查询加规则执行。

## 浏览器（[stage5_browser.py](./stage5_browser.py)）：11/11 通过

1. 实时累计的“适用决策点”只列“结账准备”“支付尝试”，均显示中文名称。
2. 结账准备：
   - 统计事件只有结账评估、结账被拦截；
   - 统计主体只有 Client、运营主体；
   - Client 没有“不同主体数”，有限制提示，币种为 `INPUT_CURRENCY`；
   - 金额合计的窗口只有 10 分钟、1 小时；
   - 次数没有 7 天窗口。
3. 支付尝试行为不变：事件为支付事件，五种主体含卡指纹；卡指纹有“不同主体数”与 7 天窗口。
4. 名单命中（结账）：下拉只列 STAGE5V Client 名单与运营主体名单，不含卡指纹名单。
5. 评估查询（按订单 ID）：结论为审核，变量分区显示累计值 171.6 与名单命中。
6. 经页面创建结账累计变量 `STAGE5V_WEB_OE_COUNT_1H`（运营主体 1 小时次数）成功，详情显示“结账准备”。口径预览为“同一运营主体 1 小时内的结账评估次数（不含本次）”。
7. 控制台没有报错，没有 HTTP ≥ 400。

## 日志

验收窗口内 Risk、Ops BFF、Commerce 都没有 ERROR。Risk 的 WARN 只有启动期 Bean 提示，以及首次保存流程时请求体缺键产生的 1 条 400。Risk 日志中没有请求体或输入值。

## 清理

**`mor_risk`：**执行 [stage5-cleanup-risk.sql](./stage5-cleanup-risk.sql)，先 ROLLBACK 空跑核对，再 COMMIT。

删除行数：

| 类别 | 内容 |
|---|---|
| 评估族 | 评估 31（正式 13、影子 18），快照 13，Feature 结果 108，规则轨迹 178，流程轨迹 80 |
| 累计事件 | 事件 15，主体 30 |
| 发布 | STAGE5V 执行发布 2，影子批次 3，发布审计事件 28 |
| 流程 | 流程 2，版本 2，节点 10，连线 8 |
| 规则集 | 规则集 2，版本 2，规则 12，节点 12，操作数 24，动作 12 |
| 变量与名单 | 变量 7，Feature 版本 7（累计 5、名单 2），名单 2，成员 2 |

- **删除的影子批次中，有一批属于当前生效的基线发布：**清理前已用基线流程同一版本建新发布 `237109668147642368`，影子运行后激活。这批影子以 STAGE5V 发布为对照基线，为避免留下悬空引用，一并删除。
- **清理后：**
  - 结账评估、累计事件、Feature 版本、名单都是 0；
  - 流程只剩 `CHECKOUT_BASELINE_ALLOW`；
  - 结账执行发布剩 2 条：ACTIVE `237109668147642368`，SUPERSEDED `237107830954409984`。

**`mor_commerce`：**执行 [stage5-cleanup-commerce.sql](./stage5-cleanup-commerce.sql)，同样空跑后提交。删除订单 11、会话 11、Consumer 合同 11、资金投影 11、退款头寸 11、明细 22、`t_order_legal` 11、上下文 11。上一需求的清理脚本漏了 `t_order_legal`，本次已补上。

**进程：**Commerce 已停止。Risk（新 jar）与 Ops BFF（新 jar）保持运行。

## 遗留

- 本机保留的基线流程 `CHECKOUT_BASELINE_ALLOW` 没有测试前缀。在本机跑 Risk 全量测试时，它会让流程、引用相关测试的计数多 1；测试本身又会清掉结账执行发布。这与上一需求的遗留相同：跑全量前需临时移除，跑完后重建基线。
- `mor_commerce.t_order_legal` 有 4 行对应的订单上下文已不存在，很可能是上一需求阶段 5 清理遗漏的（上次删了 3 笔订单，又补跑删了 1 笔）。本次授权范围外，未删除。
