package com.paytech.payment.execution.domainservice.repository;

import com.paytech.payment.execution.core.dal.dataobject.PaymentInstrumentToken;

/** 支付工具数据访问 */
public interface PaymentInstrumentTokenRepository {

    /** 按原提交范围查询支付工具 */
    PaymentInstrumentToken findTokenBySubmission(long clientId, long intentId, long sourceSubmissionId);

    /** 插入支付工具绑定 */
    void insertToken(PaymentInstrumentToken token);

    /** 读取首次受理请求中的工具凭据。 */
    PaymentInstrumentToken findTokenByValue(String token);

    /** 读取原 Attempt 冻结的工具；幂等比较不要求工具仍有效。 */
    PaymentInstrumentToken findToken(long tokenId);

    /** 事务内锁定仍可用的工具，阻止撤销与新受理穿透。 */
    void lockAvailableToken(PaymentInstrumentToken token);

}
