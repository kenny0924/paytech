# commercial 合同页交互优化：现状与方案

> 状态：阶段 1–3 合并评审稿（2026-10-20），待用户确认。

本次方案：1 个系统（operations-console-web），新增 / 修改接口 0，表与字段 0，DML 0，Platform 注册 0；改 2 个页面（合同列表、合同详情）与新建合同交互，新增 3 张设计画板。

## 1. 现状与差异

| 需求项 | 当前行为及证据 | 目标 | 处理 |
|---|---|---|---|
| 进度与下一步 | `AgreementDetail.tsx` 只按 `agreementActions` 在页头给状态按钮 | 六步进度 + 下一步卡片 | 新增纯函数推导步骤，新组件渲染 |
| 页签 | 4 个平级页签，默认第一个 | 3 个按流程排序，默认当前步骤，带标记 | 改 `AgreementDetail.tsx` |
| 新建 | `AgreementModals.tsx` `CreateAgreementModal` 单屏表单 | 三步抽屉 | 新组件，复用 `agreementBody` |
| 签署 → 激活 | 需手动回页头 | 登记后询问激活 | `AcceptanceModal` 成功回调判断状态 |
| 开通依赖 | 可开通项下拉含全部产品 | 只列已订阅产品 | `capabilityOptions(items, productCodes)` 已支持产品过滤 |
| 定价 | 只显示 rate-card 的当前 / 已排期版本 | 全部方案与版本 + 覆盖表 | 复用 `/commercial-terms/plans?agreementId=`、`/commercial-terms/versions?planId=`、`/rate-cards` |
| 列表 | 仅筛选表单 | 状态快捷切换 + 数量 | 复用列表接口按状态查 total（6 次 size=10 查询或现有 dashboard 计数） |

## 2. 影响

| 系统 | 影响 | 改代码 / 模型 / 配置 |
|---|---|---|
| operations-console-web | 合同列表、详情、新建、签署、开通、方案弹窗 | 改代码 + 单元测试 |
| ops-bff / commercial / Platform | 只用已有只读与写接口 | 不改 |
| opsux 会话 | 三个文件有其未提交已上线改动 | 在其版本上叠加，已通知暂停改这三个文件 |

## 3. 实现要点

1. 新文件 `commercial/agreementJourney.ts`：纯函数 `agreementJourney(agreement, plans, versions, rateCard)` → 六步状态、当前步骤、下一步动作列表、页签默认值与标记、覆盖缺口；方案编号建议、条款类型按合同类型过滤、中文标签补齐（模式、资金模式、定价权、文件用途、PAYOUT_FEE 等）。
2. 新组件 `commercial/AgreementJourney.tsx`（进度 + 下一步卡片）、`commercial/CreateAgreementDrawer.tsx`（三步抽屉）、`commercial/AgreementPricing.tsx`（方案版本 + 覆盖表）。
3. 改 `AgreementDetail.tsx`（页签重排、默认页签、标记、接入卡片动作）、`AgreementModals.tsx`（开通项产品过滤、签署后激活询问、方案弹窗默认值）、`pages/CommercialAgreements.tsx`（状态快捷切换、改用抽屉）。
4. 不改 `commercial.ts` / `commercial.css`（opsux 未提交改动所在）；新样式放 `commercial/agreementJourney.css`。

## 4. 验证计划

- 单测：`agreementJourney` 覆盖 AC-01 五个样本合同的步骤判定、覆盖缺口、方案编号建议、条款类型过滤。
- `npm --prefix operations-console-web test`、`run build`。
- 浏览器：本地 Vite + mor-test DEMO 合同，核对 AC-01—AC-10；新建 / 签署 / 激活的写操作只在新建的 `DEMO-UX-*` 合同上进行。
- mor-test 部署属阶段 6—7，需单独授权，按共享工作区发布方式（干净 worktree + 基线比对）。

## 5. 与 opsux 会话的依赖（code-mor-b2，2026-10-20 确认）

- 三文件中须保留的已上线 opsux 改动：列表「新建合同」在筛选栏右侧（`.commercial-toolbar`）；详情条款「口径」用 `versionBasisLabel`；弹窗按钮组 `buttonOptions` 只显示中文、代码放 title。
- 这三处依赖 `commercial.ts` / `commercial.css` 中 opsux 未提交的新增内容。**提交（阶段 8）前须先定顺序**：建议请用户先授权 opsux 提交其全部文件，本需求再基于其提交结果提交；否则只提交本需求文件会使 HEAD 编译失败或丢样式。
