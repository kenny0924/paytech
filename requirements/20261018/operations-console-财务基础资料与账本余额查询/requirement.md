# Operations 财务基础资料、账本与余额查询需求

> 状态：阶段 1、2、3 已确认（2026-10-18；阶段 3 补充只读"科目表与币种"标签页与 12 个 Pencil 画板），阶段 4 研发进行中。确认记录见 [delivery.md](./delivery.md)。

## 1. 背景与来源

Finance 在 2026-10-15 至 10-17 连续交付了账务内核、账本流水查询、试算、关账、重开、自动记账规则与渠道成本配置，V2 搬迁后代码位于 `paytech/ledger`、`paytech/account`、`paytech/billing`。这些能力全部标注为"Finance 内部开发接口：未接 Gateway 与统一身份，不得对外发布"，运营后台目前没有任何财务菜单，Operations BFF 也没有接任何 Finance 下游。

用户在 2026-10-18 确认按三批把 Finance 接到运营后台，本需求是第一批：**账务基础资料维护、账本查询、Client/Partner 余额查询**。这一批产出财务主体、账簿、期间三个选择器，后续人工调整、试算关账、规则与成本配置页面都复用。

当前事实（2026-10-18，依据 `*-api` 源码与[状态台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md)）：

| 项 | 现状 |
|---|---|
| ledger 命令接口 | `FinanceEntityCommandApi` 创建财务主体；`FiscalCalendarCommandApi` 创建日历、创建 FUTURE 期间；`AccountingPeriodCommandApi` 期间 FUTURE→OPEN；`LedgerBookCommandApi` 创建账簿、创建账户。均为单步命令，无 Maker-Checker，请求体不含操作人 |
| ledger 查询接口 | `LedgerBookQueryApi`（FIN-I05）：账簿分页、账户分页（含余额投影）、账户流水分页（`balanceAfter` 全量累计）、分录详情；`LedgerReadApi`：账簿详情、按主键批量读科目、账簿 Owner 账户合计 |
| ledger 缺少的查询 | 没有财务主体列表、财务日历与期间列表、科目表列表、按科目表列科目、币种列表接口；`t_finance_entity`、`t_fiscal_calendar`、`t_accounting_period`、`t_chart_of_accounts`、`t_chart_account`、`t_currency_spec` 表与 Repository 都已存在 |
| account 查询接口 | `BalanceQueryApi`（FIN-I01/I02）：按 Client/Partner + `entityNo` + 币种返回分类余额；`BalanceBookApi`：账簿余额一致性、账簿余额列表、从 billing 重放重建 |
| 主体来源 | OPERATING_ENTITY 以 CCT `entityNo`（如 `AE001`）标识，Finance 本期不向 CCT 核对存在性；财务主体的 `legalPartyId` 为 CCT Party 主键。`client-master-api`、`commercial-api` 没有面向运营的 OE 列表或 Party 列表接口 |
| 操作人传递 | Risk 的运营接口接收 `X-Gateway-Actor-Subject` 与 `X-Gateway-Session-Id` 并在 Owner 侧复核授权与记审计；Finance 接口没有这两个 Header，Finance 表没有创建人字段 |
| Operations 现状 | BFF 下游只有 Platform、Risk、CCT、Compliance、Vault；无财务权限码、菜单、Gateway 路由、页面 |
| 环境 | 本机 `mor_ledger`、`mor_account` 已建；mor-test 已于 2026-10-18 按 V2 部署 18 个 Java 服务（含 ledger、account） |

## 2. 使用者与目标

| 使用者 | 目标 |
|---|---|
| 财务配置人员 | 新 OE 上线或平台新增财务主体时，在后台建财务主体、财务日历、会计期间、账簿与统一账户，开放期间；不再依赖开发人员直接调 Finance 内部接口 |
| 财务核对人员 | 按主体、币种找到账簿，查看账户余额、账户流水和每张分录的来源，追溯到人工调整单号或自动指令来源 |
| 运营 / 客服 | 按 Client 或 Local Partner + OE + 币种查当前应付、保证金、追偿余额，回答结算类咨询 |
| 平台管理员 | 通过角色区分只能查看和可以维护基础资料的人员 |

## 3. 本期范围（建议）

1. **账务基础资料菜单**（维护权限）
   - 财务主体：列表（代码、名称、法律 Party、状态、创建时间）；新建（代码、法律 Party ID、显示名称）。
   - 财务日历与期间：按主体类型 + 主体标识查日历；新建日历（主体、代码、时区、财年起始月）；日历下期间列表（财年、期号、起止日期、状态、重开次数）；新建 FUTURE 期间；对 FUTURE 期间执行"开放"（二次确认）。
   - 账簿与账户：账簿列表（主体类型、主体、币种、科目表、状态，可按三者筛选）；新建账簿（主体类型、主体标识、币种、科目表）；账簿下账户列表（科目、核算对象、余额桶、方向、状态、借贷合计、净余额）；新建账户（科目、核算对象类型、标识、余额桶）。
   - 科目表与币种只读标签页（2026-10-18 阶段 3 确认补充）：科目表列表、所选科目表的科目列表（代码、名称、类别、方向、可过账、上级科目，可只看可过账）、币种列表（代码、小数位、舍入、状态）；同时作为新建账簿的币种 / 科目表下拉和新建账户的科目下拉数据源。
2. **账本查询菜单**（只读权限）
   - 选择账簿 → 账户列表（可按科目、核算对象类型/标识、余额桶筛选）→ 账户流水（按过账日期范围筛选，显示分录序号、日期、类型、方向、金额、行后余额、指令来源）→ 分录详情抽屉（分录头、来源、全部行）。
   - 与基础资料的账簿/账户列表共用组件，但查询菜单不出现新建按钮。
3. **余额查询菜单**（只读权限）
   - 选 Client 或 Local Partner、输入 Owner ID、OE `entityNo`、币种 → 显示 PENDING / AVAILABLE / LOCKED 应付；Client 额外显示保证金与追偿应收，Partner 显示追偿应收。页面明示"Available 不等于可出款资格"。
4. **Finance 补齐只读接口**（ledger）：财务主体列表、按主体查日历、日历下期间列表、科目表列表、按科目表列科目、币种列表。全部为无锁只读查询，不改表结构。
5. **操作人传递**：Finance 五个创建命令与期间开放接受运营操作人 Header（与 Risk 同名 `X-Gateway-Actor-Subject`、`X-Gateway-Session-Id`），BFF 从会话填入，浏览器不可传；Finance 本期只校验非空并记入应用日志，持久化审计见 Q-03。
6. **权限、注册与分层**：新增财务只读 / 维护两级权限，注册 Console Resource（一个一级菜单"财务"，三个二级菜单）、Gateway 路由与限流；Gateway `/api/operations/finance/...` → Operations BFF 转发（统一授权拦截、填操作人 Header）→ ledger / account。页面放 Operations Web，交互与现有配置页一致。

## 4. 明确不做

| 项 | 说明 |
|---|---|
| 人工调整、试算、关账与重开、记账规则、渠道成本、经济事实 | 第二、三批 |
| 账簿余额重建（`BalanceBookApi#rebuild`）与一致性检查 | 属于修复 / 关账辅助命令，随第二批关账工作台一起接 |
| 修改、停用、关闭财务主体、日历、账簿、账户 | Finance 没有对应接口（状态台账 Pending 项），本期不新增写能力 |
| 新增科目、新增币种与启停 | Finance 无写接口，科目表被采用后结构不可改；随第三批记账规则配置一起登记 |
| OE 与 Party 选择器 | CCT 没有面向运营的 OE / Party 列表接口；本期 `entityNo` 与 `legalPartyId` 手工输入并做格式校验，见 Q-01 |
| 金额导出、跨账簿汇总、按 Client 跨 OE 汇总 | Finance 查询接口不提供合计，与 FIN-I01/I02 "不提供跨主体或跨币种合计"一致 |
| Client Console / Partner Console 余额页面 | 另属 Client/Partner BFF 需求 |
| 执行 SQL、部署、提交 | 按阶段单独授权 |

## 5. 验收标准

**功能验收**

| 编号 | 验收标准 |
|---|---|
| AC-01 | 有财务只读权限的账号看到"财务"菜单及三个子菜单并能查询；没有的看不到菜单，直接调接口返回 403；只读账号看不到任何新建 / 开放按钮，维护权限账号才可见 |
| AC-02 | 财务主体、日历、期间、账簿、账户五类创建成功后列表立即出现新记录；代码重复、Party 已映射、同主体已有日历、期间日期重叠、同主体同币种已有账簿、违反账户身份矩阵等 Finance 400 错误在表单就地显示中文原因 |
| AC-03 | 期间"开放"后状态变为 OPEN；对非 FUTURE 期间不显示开放按钮；并发创建期间收到 409 时提示重新加载 |
| AC-04 | 新建账簿的币种与科目表只能从 Finance 启用项中选择；新建账户的科目只能从账簿科目表中可过账科目选择，并显示科目名称与正常方向；"科目表与币种"标签页只读展示科目表、科目与币种，无任何新建按钮 |
| AC-05 | 账簿列表可按主体类型、主体标识、币种筛选并分页；账户列表可按科目、核算对象、余额桶筛选并分页；分页大小只允许 10 / 20 / 50 / 100 / 200 |
| AC-06 | 账户流水按过账日期范围筛选时，`balanceAfter` 仍为全量历史累计（与 Finance 语义一致）；点击分录可打开详情，显示来源系统 / 事件键或人工调整单号及全部分录行，借贷合计相等 |
| AC-07 | 余额查询对 Client 显示应付三桶、保证金、追偿应收；对 Partner 显示应付三桶与追偿应收，不显示保证金字段；账簿不存在时显示 Finance 的业务错误而不是零 |
| AC-08 | Finance 命令接口在缺少操作人 Header 时返回 400；BFF 转发时以会话账号填入，浏览器传入的同名 Header 被忽略 |
| AC-09 | 注册 DML（权限、角色授权、Console Resource、Gateway 路由、Header、Query 参数、限流）写入 Platform 初始化 SQL 独立块；SQL 不执行，激活另行授权 |
| AC-10 | ledger 新增只读接口有 `auto` 测试；BFF 测试覆盖转发、错误透传、Header 覆盖、权限按钮控制；Web 测试覆盖三页面的加载、空状态、错误反馈；现有 ledger / account / BFF / Web 测试全部通过 |

**视觉 / 交互验收**

| 编号 | 验收标准 |
|---|---|
| AC-11 | 菜单、列表、表单抽屉、确认弹窗与现有运营配置页面风格一致；有加载、空状态（无主体、无账簿、未选账簿、无流水、未查询）和错误反馈 |
| AC-12 | 金额右对齐、按币种精度补零显示、负数（反向余额）明显标识；状态用统一标签；日期按 ISO 显示，时间按本地时区显示 |
| AC-13 | 账簿 → 账户 → 流水 → 分录为逐级下钻，面包屑或返回可回到上一级并保留筛选条件 |

## 6. 问题与建议

| 编号 | 问题 | 建议 |
|---|---|---|
| Q-01 | 创建账簿 / 日历 / 账户时 OE 怎么选？创建财务主体时 Party 怎么选？CCT 没有运营用的 OE / Party 列表接口 | **建议 A**：本期手工输入 `entityNo`（`[A-Z]{2}[0-9]{3}`）与 `legalPartyId`（正十进制），Finance 保持"本期不向 CCT 核对"。B：本需求顺带给 client-master 加 OE 只读列表接口并在 BFF 做下拉，范围扩到 CCT，建议另立需求 |
| Q-02 | 权限粒度 | **建议两级**：`FINANCE_ACCOUNTING_READ`（三个菜单全部查询）、`FINANCE_ACCOUNTING_MANAGE`（五类创建与期间开放）。不按菜单拆更细；余额查询不单独设权限 |
| Q-03 | 基础资料创建人如何留痕？Finance 表无创建人字段，Finance 也没有审计表 | **建议 A**：本期只在 BFF 访问日志与 Finance 应用日志记录操作人（Header 必填），不改表；第二批引入 Maker-Checker 时 makerId / checkerId 由同一 Header 填入并落库。B：本期给五张基础资料表加 `created_by`，涉及 PDManer、DDL 与两环境迁移，工作量明显增加 |
| Q-04 | 菜单形态 | **建议**一级菜单"财务"，下挂"账务基础资料"（三个标签页：财务主体、日历与期间、账簿与账户）、"账本查询"、"余额查询" |
| Q-05 | 本需求要改 ledger（新增只读接口与 Header）、Operations BFF、Operations Web、Platform 注册 SQL。工作区当前有其他会话未提交的改动（见 `git status`） | 允许本会话在 ledger 新增查询接口与 Controller、在 BFF / Web 新增财务文件；共享文件（菜单注册、路由配置、BFF 客户端配置、Platform 初始化 SQL）只做增量追加；发现他人正在改同一文件时先停下说明 |
| Q-06 | 适用阶段 | 1—5 照常（阶段 3 含页面设计）；6—7 mor-test 部署适用（ledger、account 已在 mor-test）；8 提交单独授权。阶段 4 研发按用户要求用 Opus 5.5 多 agent 并行，主会话负责任务拆分、合并核对与验证 |

## 7. 依据

- [应用架构](../../../agent-docs/APPLICATION_ARCHITECTURE.md) 第 3、4 节：BFF 负责页面聚合与入口授权，核心服务负责事实
- Finance 合同：`paytech/ledger/paytech-ledger-api/src/main/java/com/paytech/ledger/api/`、`paytech/account/paytech-account-api/src/main/java/com/paytech/account/api/`、[余额查询接口说明](../../../paytech/account/docs/balance-query-api.md)
- 前序需求：[Finance 账务内核](../../20261015/finance-账务内核/requirement.md)、[Finance 账本流水查询](../../20261015/finance-账本流水查询/requirement.md)
- 页面与注册先例：[Operations Webhook 管理](../../20261016/operations-console-Webhook管理/requirement.md)
- 需求目录：FIN-09、FIN-I01、FIN-I02、FIN-I05（`requirements-site/content/requirements.ts`）
