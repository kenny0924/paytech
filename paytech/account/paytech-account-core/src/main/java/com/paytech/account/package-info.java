/**
 * 账户服务（account-service）业务实现：明细账 Owner（2026-10-18 决定）。消费 billing 的 EconomicFactRecorded，在 mor_account 写入
 * Owner 账户、入账流、入账批次与流水，经补偿事件发布 AccountPosted 给 ledger 记控制科目；另提供余额桶移动、到期转可用、按流重建与查询。
 * <li>余额只由本库流水维护并可从流水重算；不调用 ledger 或 billing 的 HTTP 接口，也不读其他服务的库。</li>
 * <li>同一主体 + 币种 + 分片内的全部入账在入账流行锁内串行分配连续序号，即 AccountPosted 的聚合版本。</li>
 */
package com.paytech.account;
