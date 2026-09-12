# Customer Checkout 接口设计

状态：页面和基础路径已认可；首个 Provider 为 NI。后续已确认 Payment 统一接收卡与协议确认的一次 payments 请求；当前支付字段/恢复由[Payment Card 合同](../payment-自建CardVault/api-contract.md)拥有。本次不实施业务代码、模型、SQL 或入口配置，确认记录见 [delivery.md](./delivery.md)。

## 目标与范围

为 [Checkout 页面](../../../checkout-web/docs/design-index.md) 定义消费者从加载、填写信息、准备到点击支付及查询结果的接口。Commerce 拥有订单、Consumer 和法律事实，保留查询、保存 Consumer、准备三个公开操作；Payment 接收统一支付提交并提供原提交查询。全部复用 Client 创建的同一 Order / Checkout Session。只考虑 Card，legal 返回文档链接，点击支付时提交卡与三份文档的确认；内部职责不拆成两次前端提交。

## 验收目标

- AC-01：统一查询可驱动初次加载、信息填写、准备中、支付主页面、动作继续、在途、未知、授权、成功、明确失败及终态页面；不按画板各建一个接口。
- AC-02：消费者保存信息与支付准备不扣款；只有准备完成、法律确认有效、权威检查通过后才能提交支付。
- AC-03：支付按钮旁展示三份文件链接和明确说明，实际点击才提交确认；服务端绑定同一冻结合同的三份准确文件、分别记录接受/知悉事实及可信时间和网络证据。没有独立 acceptances 接口。
- AC-04：Card 采集与受控用卡归 Payment 独立模块，原始卡不进入 Commerce/非卡 Vault；CVV 不持久化。Payment 必须取得 Commerce 已提交接受事实后才执行支付，3DS 保持同一订单/原动作，不能承诺 Provider 支持尚未验证的中断恢复。
- AC-05：双击、刷新、超时和换幂等键不会创建并行支付；UNKNOWN 不允许再付款，AUTHORIZED 不展示已完成。
- AC-06：Token 仅有同一 Checkout 的最小权限；终态、到期及所有关联资源均在 Commerce 校验。页面渲染权限不替代命令端校验。
- AC-07：所有外部服务调用位于数据库事务外；需要可靠恢复的命令在本地事务保存业务状态和恢复任务，提交后执行。
- AC-08：Customer 不管理 Order Version；准备前以后一次成功保存为准，准备开始后锁定资料，最终金额和合同不可被后续卡输入或重试改变。

## 权威入口

- [四系统决定](../../../agent-docs/decisions/2026-09-11-cct-commerce-oe-pricing-model.md)及[阶段 3 合同](../../20260911/client-contract-OE与Client模型梳理/stage3-contracts.md)。
- [应用架构](../../../agent-docs/APPLICATION_ARCHITECTURE.md)、[Public Token 合同](../../../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md#6-checkout-public-token)。
- [Consumer 隐私及接受证据](../../../agent-docs/plans/2026-08-24-commerce-privacy-fulfillment-simplification-design.md)、[Payment 消费者动作](../../../agent-docs/plans/2026-08-25-payment-execution-simplification-design.md)。

设计入口见 [technique.md](./technique.md)，详细字段与错误见 [HTTP 合同](./customer-api-contract.md)，缺口和研发门槛见 [影响分析](./interface-impact.md)。
