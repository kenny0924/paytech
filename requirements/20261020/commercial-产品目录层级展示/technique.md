# commercial 产品目录层级展示：现状与方案

> 状态：阶段 1–8 完成（2026-10-20），已部署 mor-test，已本地提交（未推送）。

本次方案：1 个系统（operations-console-web），新增 / 修改接口 0，表与字段 0，DML 0，Platform 注册 0；改 1 个页面（产品目录），另改 1 处设计文档说明。

## 1. 现状与差异

| 需求项 | 当前行为及证据 | 目标 | 处理 |
|---|---|---|---|
| 归属关系 | `operations-console-web/src/pages/CommercialCatalog.tsx` 三个平级页签，子项只有「产品」列 | 产品主表 + 两组平级子表 | 重写页面 |
| 事件展示 | 计费事件 21 行，同产品同事件按条款类型重复 | 合并 13 行，条款类型为标签 | 页面内分组 |
| 条款类型标签 | `commercial/commercial.ts` `termTypeLabels` 只有 4 个，PAYOUT_FEE 等显示原代码 | 8 个全覆盖 | 补标签 |
| 目录缺口 | 页面不提示；mor-test 实测 DATA_ANALYTICS 有开通项无事件，PARTNER_BUY_RATE 无登记（建方案被拒「条款类型未在计费事件目录登记」） | 页面直接提示 | 页面推导 |
| 设计稿 | `design-discuss/commercial/design.md` 第 4 节六层表 L0 / L1 / L2 连续编号 | 注明 L1、L2 平行 | 文档一句 |

## 2. 影响

| 系统 | 影响 | 改代码 / 模型 / 配置 |
|---|---|---|
| operations-console-web | 产品目录页、目录分组纯函数、标签 | 改代码 + 单元测试 |
| operations-console-bff / commercial / Platform | 接口 `GET /commercial-admin/v1/products`、`/products/capabilities`、`/products/fee-events` 原样使用 | 不改 |
| 合同订阅 / 价格抽屉 | 继续用 `useCatalog` 与现有下拉函数 | 不改 |

## 3. 实现

1. 新文件 `commercial/catalogTree.ts`：纯函数 `groupCatalog(products, capabilities, feeEvents, termType?)` 返回每个产品的开通项、合并后的事件行（`feeEventCode`、`termTypes[]`、`billingFactTypes[]`）、计数与缺口；另出 `catalogTermTypeOptions`、`catalogGaps`，以及目录页用的标签（条款类型在 `termTypeLabels` 基础上补 PAYOUT_FEE 出款费、FX_SPREAD 外汇点差、DISPUTE_FEE 争议费、PLATFORM_FEE 平台费；开通项种类；业务模式范围）。页面只渲染。
2. `pages/CommercialCatalog.tsx` 重写：antd `Table` 可展开行，展开区上下两个可折叠分组（标题行 + 子 `Table`）；`Select` 条款类型筛选；`Alert` 目录提示；保留 `CommercialNote`。
3. 新文件 `commercial/catalog.css`：分组标题与展开区样式。
4. 不改 `commercial/commercial.ts`、`commercial/commercial.css`：两文件有「operations 控制台体验优化」需求的未提交改动，本需求只新增文件，避免交叉；`termTypeLabels` 本体补标签留给后续统一处理。
5. 设计稿第 4 节表后加一句「L1 能力开通与 L2 计费事件都挂在 L0 产品下，彼此平行，在 L3/L4 价格里交叉」。

## 4. 验证计划

- 单元测试（`node --test`，新文件 `commercial/catalogTree.test.ts`）：用与 commercial 初始化数据一致的目录样本覆盖 AC-01—AC-06 的分组、合并、筛选与缺口。
- `npm --prefix operations-console-web test`、`run build`、lint。
- 浏览器：本地 Vite 接 mor-test BFF 或 mock，核对 AC-08—AC-10（1440 / 1024 / 390 宽，浅色与深色）。
- mor-test 部署（ops-web 单镜像替换）属阶段 6—7，需单独授权。
