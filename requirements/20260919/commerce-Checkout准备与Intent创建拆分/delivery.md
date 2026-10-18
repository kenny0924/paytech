# 交付记录

- 最终需求：用户确认删除独立准备阶段，创建 Checkout 完成初始化，首次支付创建 Intent。
- 外部固定 Mock：用户本轮明确授权；边界见[技术设计](technique.md)。
- 本地 SQL：用户确认仅新增四张缺失表；执行来源见[补表 SQL](../../../paytech/acquiring/docs/requirements/20260919-checkout-preparation/local-test-tables.sql)。
- 实现、实际验证、SQL 执行及剩余门禁状态统一记录到[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-preparation-intent-20260919)。
- 不涉及共享测试环境或生产部署；未要求提交/推送。本轮不迁移历史数据、旧命令任务或 PDManer。

- 2026-09-20 后续评审：首次支付改用订单资源锁，Intent 关联和订单资格 CAS 合并为一个本地事务；代码与验证见[本次台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-payment-order-lock-20260920)。
