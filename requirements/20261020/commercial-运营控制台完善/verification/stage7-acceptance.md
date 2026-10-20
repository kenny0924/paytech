# 阶段 7 mor-test 验收记录（commercial 运营控制台完善 · 第二切片）

> 执行时间：2026-10-20 05:43–05:53Z，阶段 7 agent（commercial-release）。环境为 mor-test，运营入口 `http://localhost:28080`。清单见 [release/acceptance.md](../release/acceptance.md)。
> 结论：**通过**。
> - 接口验收首轮 101/102。唯一不符的 F9-3 是用例选错了期望版本号，不是缺陷，补测通过（见第 3 节）。
> - 页面回归 46/46：42 个菜单页加 4 个深链接。冒烟缺陷 D1（版本对比页直接链接显示无权限）已确认修复。

## 1. 被验版本

| 组件 | 镜像 ID | 内容 |
|---|---|---|
| commercial-service | `sha256:59bb000da5c0115e1cb3f5021c76322d4bf214a3dbf61cee8304942f43684adb` | 基线 `19ccd78a`；JAR `437a46e83ba6dd90…` |
| operations-console-bff | `sha256:e294a4ae90bec06bd3e276cb0ab181269fbbc60af7912b22103ce781bd76505b` | JAR `54c9bf0ca0ef0df1…` |
| operations-console-web | `sha256:f00801f59482069dfd99d309c1538d82b622e5696be644a4c47469991471675e` | 入口 `assets/index-D5UHJXEd.js` |

`mor_platform`：I1 / I2 共 11 条路由和 5 个菜单为 ACTIVE，PLATFORM_ADMIN 版本 34。`mor_commercial` 没有 SQL 变更。

## 2. 账号与数据

- **账号 A：** `private/access.md` 的 PLATFORM_ADMIN。
- **账号 B：** 合成审批人，角色只含合同读、价目读、价目审批。
- **账号 C：** 合成只读人员，只有 COMMERCIAL_PRICING_READ。
- B 和 C 都经 IAM 接口新建并在首登后改口令；验收结束已停用，两个合成角色的权限已清空。没有用 SQL 授权。
- **合成数据（RUN `395AEC`）：**
  - 画像 3 个：具体 VISA / CREDIT / DOMESTIC、兜底 CARD、APPLE_PAY；
  - 合同 `G8-CLIENT-395AEC`，开通 CARD_VISA（AED、USD）和 APPLE_PAY（AED）；
  - 方案 `G8-CLIENT-PRICING-395AEC`：V1 有 4 条 CARD 价格；V2 由 V1 复制，共 5 条价格，具体画像 AED 的 CAPTURE_PCT 为 0.022，另有 APPLE_PAY AED 一条。
- 运行记录：`local-environment/e2e/runtime/commercial-ops2-stage7-20261020T054329Z.json`。
- 真实合同 CL-F1105E 只读。

## 3. 接口验收（[stage7_acceptance.py](./stage7_acceptance.py)）

| 项 | 结果 |
|---|---|
| F1 合同列表 | 按 Client 和合同号前缀 `CL-F11` 都命中 CL-F1105E。`page=0`、`size=1`、`size=201` 均 400。未知参数 `foo` 返回 400，由真实 Gateway 查询参数白名单拒绝。`status=ACTIVE&agreementType=CLIENT&entityNo=AE001&page=1&size=10` 返回 200，`total` 2，全部符合筛选条件。重复的 `status` 参数被接受。列表行进入详情的结果与列表一致 |
| F2 工作台 | 5 项指标，基线 ACTIVE_AGREEMENTS 1。新建合同后 DRAFT +1，提交签署后 PENDING_SIGNATURE +1。A 提交 V1 后 PENDING_APPROVAL +1，审批队列含 V1，带 makerAccountId 和 effectiveFrom。B 批准后计数回到基线。B 驳回 V2 后 V2 回到 **DRAFT**，队列不再含 V2。V2 重新提交、批准、排期后 SCHEDULED +1，版本列表带生效时间。只有价目读的 C 读工作台返回 **403** |
| F3 价目与对比 | 方案列表（按合同）和版本列表（APPROVED + SCHEDULED，`page=1&size=50`）都返回 200，V1 带 maker / checker 和 `priceCount=4`。V1 → V2 对比：added 1（APPLE_PAY），changed 1（具体 AED 的 CAPTURE_PCT 0.025 → 0.022），removed 0，unchanged 3，`versionDifferences` 为 `[]`。V2 → V1 方向相反：removed 1、added 0。对比不存在的版本返回 404 |
| F4 目录 | 7 个产品；可开通项共 11，ACQUIRING 下 6，CARD_VISA = CARD_BRAND / VISA；计费事件共 21，ACQUIRING / CLIENT_PRICING 过滤后 6。C 读目录返回 **403** |
| F5 变更历史 | 合成合同的历史按 versionNo 倒序，每条带 createBy、createTime、operationType、meta，并有 changedFields。方案历史含 V1 / V2 各步，带 previousVersionNo 和 previousData。CL-F1105E 的历史可读。缺 aggregateRootId 返回 400 |
| F6 费率卡 | CL-F1105E 返回 200：合同块在，没有条款版本。V2 激活后，合成 Client 的 AED 费率卡含 V2（ACTIVE、CURRENT）和三个画像。未知参数 `bad` 返回 400，由 Gateway 白名单拒绝 |
| F7 试算 | VISA / CREDIT / DOMESTIC 命中具体画像，`fractionDigits` 2，CAPTURE 示例金额 3.50（100 × 0.025 + 1.00）。缺 FUNDING_TYPE 返回 **422** `PRICING_FACTS_INCOMPLETE`。AMEX 命中兜底画像。请求体超过 8192 字节返回 **413** |
| F8 导航 | A 在「商务」下看到 8 个菜单：工作台 5、合同 10、价目 20、费率预览 22、价格试算 24、定价画像 30、产品目录 40、变更历史 50，外加首切片的 4 个按钮。B 只看到 8 个菜单和审批按钮，没有维护按钮。Web 入口与商务 8 页均为 200（第 4 步替换时核对） |
| F9 错误透传 | **F9-1** V1 排期返回 400 `BUSINESS_CHECK_FAILED`，detail 为 Owner 原文，含「激活校验」和 `AE001/APPLE_PAY/AED`。**F9-2** A 自批 V2 返回 400，detail 含「审批人不能是提交人」。**F9-3** 首轮用 `expectedChangeVersion=1` 对已 ACTIVE 的画像再次激活，返回 200，与清单预期不符。原因是 commercial 的 `ActivatePricingProfileProcessor` 按设计把「已 ACTIVE 且版本 = 期望 + 1」当作原请求重放直接返回（类注释），用例选到了这个值；不是缺陷。补测 `expectedChangeVersion=5` 返回 **409** `CONCURRENT_REQUEST`，detail 为「业务版本已变化，请刷新后重试: PAYMENT_METHOD_PROFILE …，当前版本 2，期望版本 5」；脚本已改为 5。**F9-4** 采用代码核对：发布的 BFF JAR 中只有 `CommercialClientConfiguration.class` 引用 `withOwnerProblemDetail`，其他客户端保持默认关闭 |
| 直连 | commercial `/actuator/health` 为 UP，经一次性探针容器访问 |

## 4. 页面回归（[stage7_regression.py](./stage7_regression.py)）

在 slice2 worktree（`19ccd78a`，`pageCapabilities.ts` 有 42 页）中运行，**46/46 通过**：42 个菜单页，加 4 个深链接——V1 → V2 版本对比页、V2 版本工作区、合成合同详情、CL-F1105E 详情。深链接页额外检查页面中没有出现「无权限」，即 D1 的症状，结果都没有出现。全程没有 ≥ 400 响应、失败请求或控制台错误。截图在 [assets/stage7/](../assets/stage7/)：商务 8 页、财务 3 页、4 个深链接。对比页显示 15 行，其中修改 1、新增 3（APPLE_PAY 三个组件）、未变 11。

F9-1 的错误提示截图没有拍，因为它需要在页面上做写操作，这一项以接口层的 detail 为证据。

## 5. 运行观察

- 自 05:39Z 替换起，commercial、ops-bff、ops-web、Gateway、Platform 的日志 ERROR 都是 0，没有管理员口令或 Bearer Token 明文。
- `t_compensation_event` 为 0 行，事件都已发布。
- XXL 任务 16 运行 3 次，全部 200 / 200。任务 17 运行 27 次，其中 26 次 200 / 200；05:39:30 的 1 次 trigger 500，当时 commercial 正在重启（05:39:20 停止，05:39:47 healthy），之后全部正常。

## 6. 清理

[acceptance-cleanup-test.sql](./acceptance-cleanup-test.sql)（`commercial_app`，05:53:09Z）先 SELECT 核对，结果全部等于期望：3 / 3 / 1 / 2 / 1 / 1 / 2 / 3 / 0 / 1 / 2 / 9 / 27 / 10 / 42。随后在同一事务内删除：BCR 版本 42、记录 10、组件 27、价格 9、版本 2、方案 1、开通约束 3、开通 2、产品 1、文件 1、参与方 2、画像条件 3、画像 3。清理后逐表行数与备份中的 `commercial-rowcounts-before.tsv` 一致；剩下目录数据和 CL-F1105E 及其 BCR。Platform 的合成人员已停用、合成角色已清空，审计记录不回滚。
