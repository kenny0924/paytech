package com.paytech.finance.accounting.domainservice.service;

import com.paytech.finance.accounting.domain.model.ClientBalance;
import com.paytech.finance.accounting.domain.model.PartnerBalance;

/** 查询 Client 与 Local Partner 分类账务余额。 */
public interface BalanceQueryService {

    /**
     * 查询指定经营主体和币种下的 Client 分类余额。
     *
     * @param clientId
     *            Client 稳定业务标识
     * @param operatingEntityId
     *            Operating Entity 稳定业务标识
     * @param currency
     *            ISO 4217 币种代码
     * @return Client 分类余额
     */
    ClientBalance clientBalance(String clientId, String operatingEntityId, String currency);

    /**
     * 查询指定经营主体和币种下的 Local Partner 分类余额。
     *
     * @param partnerId
     *            Local Partner 稳定业务标识
     * @param operatingEntityId
     *            Operating Entity 稳定业务标识
     * @param currency
     *            ISO 4217 币种代码
     * @return Local Partner 分类余额
     */
    PartnerBalance partnerBalance(String partnerId, String operatingEntityId, String currency);
}
