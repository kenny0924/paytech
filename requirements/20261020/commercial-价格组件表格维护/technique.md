# commercial 价格组件表格维护：现状与方案

> 状态：阶段 1–8 完成（2026-10-20），已部署 mor-test，已本地提交（未推送）。

本次方案：1 个系统（operations-console-web），新增 / 修改接口 0，表与字段 0，DML 0，Platform 注册 0；改 1 个抽屉（编辑价格），新增 1 张设计画板。

## 1. 现状与差异

| 需求项 | 当前行为及证据 | 目标 | 处理 |
|---|---|---|---|
| 组件布局 | `PriceEditorDrawer.tsx` `ComponentEditor`：每组件一张 3 列网格卡片 | 每行一组件的可编辑表格 | 重写组件部分 |
| 金额币种 | 手填，帮助文字「须等于价格币种」；不一致时排期才被 commercial 拒绝 | 自动推导 | 保存前推导 |
| 费率 | 小数手填（0.029） | 百分比输入，保存换算 | 输入层换算 |
| 选项 | 计算基数、退款处理等下拉显示原代码 | 中文名 + 代码 | 抽屉内标签 |
| 价格卡片 | 全部展开，无摘要 | 可折叠 + 摘要 | 卡片头改造 |

## 2. 影响

| 系统 | 影响 | 改代码 / 模型 / 配置 |
|---|---|---|
| operations-console-web | 编辑价格抽屉 | 改代码 + 单元测试 |
| commercial / ops-bff / Platform | `PUT /commercial-admin/v1/commercial-terms/versions/{id}/prices` 请求体不变 | 不改 |

## 3. 实现

1. 新文件 `commercial/componentTable.ts`（纯函数）：百分比 ↔ 小数换算（字符串精确换算，不经浮点）、组件币种推导、行复制命名、价格摘要、计算基数 / 退款处理 / 阶梯基数 / 模式中文标签、类型 → 可编辑列映射。
2. 新文件 `commercial/ComponentTable.tsx`：基于 `Form.List` 的可编辑表格（antd `Table` + 单元格 `Form.Item noStyle`），阶梯行可展开档位子表（复用现有 `bandIssues` 校验）。
3. `commercial/PriceEditorDrawer.tsx`：价格卡片改为可折叠（摘要头 + 两行紧凑字段 + `ComponentTable`）；保存前把百分比换回小数并推导组件币种，再调用原 `pricesBody`；校验失败时展开出错价格并 `scrollToField`。
4. 新文件 `commercial/componentTable.css`：表格单元格紧凑样式、错误行、折叠头。
5. 不改 `commercial/commercial.ts`、`commercial.css`（其他需求有未提交改动），只 import 其已导出的类型与函数。

## 4. 验证计划

- 单元测试（`node --test`，`commercial/componentTable.test.ts`）：换算往返（含 0、1、10 位小数边界）、币种推导四种情况、AC-01 请求体与改版前一致（用 DEMO-CL-001 的 11 条价格样本对比 `pricesBody` 输出）。
- `npm --prefix operations-console-web test`、`run build`。
- 浏览器：本地 Vite 接 mor-test 数据，用 DEMO-CL-001 的 V1/V2 打开抽屉核对 AC-05—AC-10（不保存或只在新建的 DRAFT 版本上保存）。
- mor-test 部署属阶段 6—7，需单独授权；可与「产品目录层级展示」同一次 ops-web 发布。
