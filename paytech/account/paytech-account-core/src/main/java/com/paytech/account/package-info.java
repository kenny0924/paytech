/**
 * 账户服务（account-service）业务实现：消费 billing 的 EconomicFactRecorded 维护 Owner 余额桶（mor_account），并提供余额查询、
 * 账簿一致性、按经济事实重建与日终核对（design/04 §3.2、§5–§7）。
 * <li>账户只消费经济事实，不消费过账事件；与 ledger 分录的一致性靠同一经济事实的幂等消费加日终核对（D05）。</li>
 * <li>账簿与科目只经 ledger-api 读取，经济事实重放只经 billing-api 读取，不读其他服务的库。</li>
 */
package com.paytech.account;
