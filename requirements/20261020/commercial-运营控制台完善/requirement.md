# commercial 运营控制台完善（商务第二切片）

> 状态：阶段 1 已确认（2026-10-20，"可以，都按建议来，登记需求开始做"）。前序：[commercial 六层产品定价模型](../../20261019/commercial-六层产品定价模型/requirement.md)（已部署 mor-test）。确认记录见 [delivery.md](./delivery.md)，方案见 [technique.md](./technique.md)。

## 1. 背景

首切片的运营控制台只有合同、定价画像、价目三页，且 commercial 只提供"按 ID 读"接口：合同只能按 ID 打开、没有审批工作台、产品目录码在前端硬编码、业务拒绝原因显示为通用 400。mor-test 上已有一份真实 Client 的 ACTIVE 合同（CL-F1105E），运营无法从列表找到它。

## 2. 目标

运营按"每天要做什么"使用商务模块：找合同、处理待办、看价目变化、查目录、追历史、预览 Client 费率、试算价格；页面显示真实的业务拒绝原因。

## 3. 范围

| 编号 | 功能 | commercial 接口 |
|---|---|---|
| F1 | 合同列表与搜索（Client、合同号、类型、状态、OE，分页）；建合同时从客户主档选 Client / Party / OE | 新增 `GET /agreements/v1` 列表；复用 client-master 查询 |
| F2 | 审批工作台：待审批版本、已排期待生效、待签署合同 | 新增版本 / 合同按状态列表 |
| F3 | 价目总览与版本对比：按合同的 Plan / Version 列表；两版本价格矩阵差异 | 新增 Plan / Version 列表；对比在 BFF |
| F4 | 产品目录页：产品、可开通项、计费事件只读；前端目录改接口获取 | 新增目录读接口 |
| F5 | 变更历史：合同 / 画像 / 价目的 BCR 时间线 | 新增按聚合根读 BCR |
| F6 | 费率卡预览：按 Client 预览 | 复用 `rate-cards` |
| F7 | 价格试算：输入事实 → 命中价格与费用示例 | 复用 `commercial-prices` |
| F8 | 导航与仪表盘：工作台、合同、价目、定价画像、产品目录、变更历史；首页卡片 | 复用列表 |
| F9 | 错误信息透传：业务拒绝 detail 到页面 | 阶段 2 定：commercial 错误码或 Foundation 客户端保留 detail |

## 4. 不做

合作伙伴合同（PARTNER 多 OE、分润 / 平台服务费 Plan 的页面）、费率卡 PDF 导出、Client 门户费率页、风控门禁接通、acquiring 冻结新字段。

## 5. 验收标准（草案）

- AC-01 合同列表按 Client / 状态 / 类型 / OE / 合同号筛选分页，点击进入详情；真实 Client 的合同 CL-F1105E 可在列表找到。
- AC-02 工作台三个清单与数量正确，审批人可直接 approve / reject，排期版本显示生效时间。
- AC-03 版本列表与对比：同 Plan 两版本价格差异高亮，新增 / 删除 / 修改三类。
- AC-04 目录页三表只读，前端表单下拉来自接口，与 `commercial-initial-data.sql` 一致。
- AC-05 变更历史按聚合根展示操作者、时间、原因、版本号与字段级变化。
- AC-06 费率卡预览与 Client 门户数据同源；价格试算返回命中画像、组件与示例费用，事实缺失显示 422 原因。
- AC-07 业务拒绝在页面显示 Owner 的 detail（如"激活校验：开通项 AE001/APPLE_PAY/AED 无价格"）。
- AC-08 commercial、BFF、Web 自动测试与构建全绿；mor-test 发布后页面回归通过。
