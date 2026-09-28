package com.paytech.finance.accounting.domain.model;

import com.paytech.foundation.money.Money;

/** Local Partner 在一个经营主体币种账簿下的分类余额。 */
public record PartnerBalance(
    /** Local Partner 的跨系统稳定业务标识。 */
    long partnerId,
    /** 当前余额所属 Operating Entity 的稳定业务标识。 */
    long operatingEntityId,
    /** 当前账簿唯一使用的大写 ISO 4217 币种代码。 */
    String currency,
    /** 尚未达到结算成熟日的 Partner 应付余额。 */
    Money pendingAmount,
    /** 已成熟但不代表实时可出款资格的 Partner 应付余额。 */
    Money availableAmount,
    /** 已被有效业务用途锁定的 Partner 应付余额。 */
    Money lockedAmount,
    /** Partner 已确认但尚未收回的追偿应收余额。 */
    Money recoveryReceivableAmount) {
}
