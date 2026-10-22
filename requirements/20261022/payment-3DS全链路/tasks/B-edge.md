# 卡 3DS-B：api-gateway CHANNEL_CALLBACK 安全链、Platform 块 133、PayerMax 配置脚本

- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR/Code-MoR-3ds-edge`（嵌套在主工作区内）
- 公共规则：[00-common.md](./00-common.md)（必须先读）
- 设计权威：[technique.md](../technique.md) 第 3 节（3.3 接口中回调公开路径、3.4 数据、3.5 配置）、`db/platform-support/dml.sql`
- 报告：`tasks/B-edge-report.md`

## 范围（technique.md §3.6 顺序 7、8）

1. `paytech/api-gateway`：新增 `CHANNEL_CALLBACK` 安全链（按运行时路由渠道匹配，permitAll，关闭 CSRF / Basic / Form），Body 原样转发，Route Header 白名单放行 `sign`、`content-type`，Body 上限按路由配置；未注册路径仍走兜底拒绝。测试 `GatewayChannelCallbackTest`。
2. `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` 追加块 133（与 `db/platform-support/dml.sql` 一致：Target 1、Route 1、Route Header 2、Guard Rule 3；Target ACTIVE、Route DISABLED；激活与回滚语句写在注释）。确认 Platform 编译器能编译该路由（相关 Platform 测试只跑涉及 Gateway 配置编译的类）。
3. 配置脚本 `requirements/20261022/payment-3DS全链路/assets/harness/configure_3ds.py`（写到**主工作区**该路径，参照 `requirements/20261014/payment-PayerMax卡直付对接/assets/harness/configure_card.py`）：标准 API PAY 增 `payment.threeDsRequired`；PayerMax ORDER_AND_PAY 映射 `cardInfo.dynamic3DS`（要求时 `do3DS`，否则不传）；PAY_QUERY 输出 CLOSED / NOT_FOUND；`PAYERMAX_MOCK` 同步。只写脚本与使用说明，**不对任何环境执行**。

## 本机 SQL

本卡不需要执行 SQL（块 133 只追加到 initial-data 文件；本机 `mor_platform` 是自动测试库，测试自建数据）。

## 不在本卡

channel-connector、payment、acquiring（卡 3DS-A）。
