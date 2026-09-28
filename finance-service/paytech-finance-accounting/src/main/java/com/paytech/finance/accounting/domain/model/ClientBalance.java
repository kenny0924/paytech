package com.paytech.finance.accounting.domain.model;

import com.paytech.foundation.money.Money;

/** Client 在一个经营主体币种账簿下的分类余额。 */
public record ClientBalance(
    /** Client 的跨系统稳定业务标识。 */
    long clientId,
    /** 当前余额所属 Operating Entity 的稳定业务标识。 */
    long operatingEntityId,
    /** 当前账簿唯一使用的大写 ISO 4217 币种代码。 */
    String currency,
    /** 尚未达到结算成熟日的 Client 应付余额。 */
    Money pendingAmount,
    /** 已成熟但不代表实时可出款资格的 Client 应付余额。 */
    Money availableAmount,
    /** 已被有效业务用途锁定的 Client 应付余额。 */
    Money lockedAmount,
    /** Client 预存、尚未使用或退回的保证金负债余额。 */
    Money securityDepositAmount,
    /** Client 已确认但尚未收回的追偿应收余额。 */
    Money recoveryReceivableAmount) {
}
