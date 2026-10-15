# 阶段 5 本机综合验证与验收报告

- 日期：2026-10-15
- 范围：本机全链路（Nginx `127.0.0.1:18444` 运营入口 → Gateway → Operations BFF → Risk），只用本机库 `mor_platform_e2e`、`mor_risk`；不涉及 mor-test、Payment
- 结论：**本机验收通过**；验收中发现并修复 5 个缺陷，另有 3 个发现项待用户决定（第 5 节）

## 1. 环境与已执行动作

| 项 | 内容 |
|---|---|
| Risk | 工作区 HEAD（含 `b26e857`）重新打包，`RISK_DECISION_ENVIRONMENT=TEST` 启动于 18104（新版必须设置该变量；与 mor-test 编排一致，未改共用启动脚本） |
| BFF | 本次 jar（含第 4 节修复）重启于 18201 |
| Web | Vite 开发服务器 18301，直接加载工作区源码 |
| 备份 | 执行前备份 `mor_platform_e2e` 8 张配置表、`mor_risk` 全库并记录 84 张表行数（会话临时目录，不入库） |
| Platform SQL（`mor_platform_e2e`） | ① [registration-dml.sql](../db/platform-support-service/registration-dml.sql) 首版 122 行（试跑回滚后提交）；② [activation-local.sql](../db/platform-support-service/activation-local.sql) 11/5/4/1（用户执行）；③ [activation-local-prerequisites.sql](../db/platform-support-service/activation-local-prerequisites.sql) 28/13/8/1（用户授权并执行，开放本机此前关闭的决策流、变量、拒绝原因、规则集历史/删除）；④ [registration-header-fix-local.sql](../db/platform-support-service/registration-header-fix-local.sql) 28 行（缺陷 D2 的本机增量，条件提交） |
| Risk 数据 | 第一轮驱动调试留下数据后，按基线备份恢复 `mor_risk`，84 张表行数逐表一致，再干净地执行最终验收 |

## 2. 接口验收（[证据](./stage5-api-evidence.md)，驱动 [stage5_api.py](./stage5_api.py)）

经运营入口完成全部配置，**44/44 项检查通过**：

| 步骤 | 验证内容 |
|---|---|
| 名单 | 新建卡指纹黑名单；重复编码由 Risk 拒绝并透传错误码；加入成员只返回脱敏键；按原值查询只命中该成员；失效成员；未知查询参数 400；缺字段 400 |
| 拒绝原因、变量 | 新建拒绝原因；实时累计（同卡 1 小时评估次数）与名单命中变量创建成功；详情不回显累计参数；窗口传字符串 400 |
| 规则集、决策流 | 支付点规则集目录（统一变量格式、`*_PAYMENT` 动作）；决策流上下文声明认证要求输出；拦截/认证两个规则集冻结；决策流保存后“放行（需要 3DS）”节点保留 `THREE_DS_REQUIRED` |
| 首发 | 无生效发布 → 创建 DRAFT → 校验 SHADOWING（8 项检查通过）→ 激活 ACTIVE |
| 生效佐证（直连 Risk 统一评估，模拟 Payment 调用） | 100 放行无需认证；1500 放行需要 3DS；6000 审核；黑名单卡拒绝；同卡第 4 次拒绝 |
| 第二个发布 | 3DS 门槛改为 50 → 影子运行完成（失败 0、超时 0，认证要求变化计数 > 0）→ 激活；发布 1 变为 SUPERSEDED；100 改为需要 3DS |
| 回滚与边界 | 以发布 1 的流程版本新建发布并校验；已替代发布不能再激活；影子样本超上限 422；规则集列表 `inUse=true`，删除返回 409 `RISK_RULE_SET_IN_USE` |

## 3. 浏览器验收（截图 [assets/stage5](../assets/stage5/)，脚本 [stage5_browser.py](./stage5_browser.py)）

**26/26 项业务检查通过**；控制台只有既有 `App.tsx`/`Accounts.tsx` 的 antd 弃用警告，不属本需求；页面无 HTTP ≥ 400。页面上的写操作：新建名单、加入成员、新建两个变量、对回滚发布发起影子运行并带确认勾选激活（完成一次回滚）。

| Pencil 画板 | 实际截图 | 对照结论 |
|---|---|---|
| 01 规则集列表｜风控点切换 | [01](../assets/stage5/01-规则集列表-风控点切换.png) | 一致；切到支付尝试列出支付规则集 |
| 02 决策流｜放行结束节点认证要求 | [02](../assets/stage5/02-决策流-放行结束节点认证要求.png) | 一致；画布 3DS 标签、属性面板“认证要求：需要 3DS” |
| 03 / 04 变量新建 | [03](../assets/stage5/03-变量-实时累计新建.png) / [04](../assets/stage5/04-变量-名单命中新建.png) | 一致；口径预览、不可修改提示 |
| 05 / 05b 名单 | [05](../assets/stage5/05-风控名单列表.png) / [05b](../assets/stage5/05b-新建名单.png) | 一致 |
| 06 / 07 名单成员 | [06](../assets/stage5/06-名单成员.png) / [07](../assets/stage5/07-加入成员.png) | 一致；只显示脱敏值，原值查询不进地址栏、提交后清空、页面全文不含原值（截图中原值输入框已遮挡） |
| 08 执行发布列表 | [08](../assets/stage5/08-执行发布列表.png) | 结构一致；差异见 F2、F3 |
| 09 新建执行发布 | [09](../assets/stage5/09-新建执行发布.png) | 一致 |
| 10a / 10 详情·影子运行 | [10a](../assets/stage5/10a-执行发布详情-影子运行中.png) / [10](../assets/stage5/10-执行发布详情-影子运行完成.png) | 一致；页面轮询到完成 |
| 11 激活确认 | [11](../assets/stage5/11-激活确认.png) | 一致；认证要求变化红色醒目，未勾选不能激活 |
| — | [12 回滚发布已生效](../assets/stage5/12-回滚发布已生效.png) | 回滚完成 |

## 4. 验收中发现并已修复的缺陷

| # | 缺陷 | 影响 | 修复与验证 |
|---|---|---|---|
| D1 | BFF 校验规则集目录时把终态动作写死为 `*_CHECKOUT` | 支付点规则集目录 502，规则集页无法切到支付点 | `RiskDecisionConfigurationClientImpl` 改为由动作编码前缀推出决定并与 `decisionCode` 比对；新增支付点目录用例与“前缀不符”变异用例；BFF 全量 1299/1299 |
| D2 | 注册 DML 中 7 条 POST 路由漏登记 `content-type`/`idempotency-key` 的 CORS 允许与转发头 | Gateway 丢弃这两个头，名单与执行发布全部写命令 415 | 生成脚本按既有风控写路由补齐，[registration-dml.sql](../db/platform-support-service/registration-dml.sql) 改为 **150 行**（mor-test 用完整文件）；本机以增量文件补 28 行 |
| D3 | Web 名单与执行发布写命令不带 `Idempotency-Key` | 与其他风控写命令不一致，网络中断重试无 Gateway 防重 | `riskControl.ts` 统一写命令发送器：结果未知后同请求重试沿用原键，成功或确定 4xx 后换新键；新增单测 |
| D4 | Web 风控点下拉读决策流上下文 `supportedPoints`，Risk 只返回所请求的一个点 | 规则集页与决策流列表只能选结账点，页面无法建支付流程 | `useRiskPoints` 改读变量上下文（列出全部已登记点），已知点用中文名；决策流列表筛选改用同一来源；新增单测 |
| D5 | 决策流详情/编辑页顶部仍写“本期仅配置管理，保存不会启用流程执行” | 支付流程上误导 | 支付点改为“需在【执行发布】中发布并激活后对支付评估生效”；补拍截图确认 |

**过程说明：** 阶段 4 对 `ConsolePage.tsx`、`pageCapabilities.ts`、`configurationModel.test.ts`、`riskVariables.css`、`decisionFlow.css` 的改动在工作区被还原为提交版本（文件时间戳为原值，不在任何提交与 stash 中，推测为其他会话在提交前恢复了这些共享文件），导致两个新页面“暂无访问权限”。已按会话记录逐字恢复，Web 全量 1014/1014。

## 5. 待用户决定的发现项

| # | 发现 | 说明 | 建议 |
|---|---|---|---|
| F1 | **按原值查询名单成员时，Nginx 访问日志记录了原值** | Risk 合同把 `subjectValue` 定义为 GET 查询参数；Nginx 默认日志格式记录完整请求行。BFF、Gateway、Risk 日志均已核对不含原值。mor-test Nginx 同样会记录 | 二选一：a) Risk 把成员查询改为 POST 请求体（Risk 改接口，Operations 跟随）；b) 环境 Nginx 对 `/api/operations/risk-lists/` 使用不含查询串的日志格式（部署配置） |
| F2 | 操作人显示账户 ID | 画板上为姓名；Risk 只返回账户 ID，现有风控页面同样显示 ID | 本期保持；如需姓名另立需求由 BFF 聚合账户信息 |
| F3 | 已校验未发起影子的发布显示“待激活” | 画板中 SHADOWING 写作“影子运行中”；实际 SHADOWING 包含“已校验待影子”与“影子运行中”两种情形 | 保持现状，详情页步骤条可区分 |

## 6. 当前数据状态与清理

- `mor_risk` 中保留本次验收数据（名单 2、变量 6、拒绝原因 1、规则集 3、决策流 1、执行发布 3，当前生效为回滚发布 R-3），便于用户在本机页面查看；确认后按基线备份恢复并核对 84 张表行数。
- 用户确认阶段 5 后（2026-10-15 17:02）已清理：恢复前核对差异全部来自本次验收，按基线备份恢复 `mor_risk`，84 张表行数逐表一致，Risk 重新启动。
- `mor_platform_e2e` 的注册与开放保持（本机全链路后续需要）。
- F1 后续核对：mor-test 的边缘 Nginx 和 Web 容器 Nginx 都是 `access_log off`。原值只出现在本机验收用的 Nginx 模板记录里，而且都是合成值。见[发布清单](../release/release.md)第 8 节 D2。
- 遗留：上下文 `outputs` 已上线后，Web 写死的认证要求回退常量可在下次 Operations 变更中删除。
