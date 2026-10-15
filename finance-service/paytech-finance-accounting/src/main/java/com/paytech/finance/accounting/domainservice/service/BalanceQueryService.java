package com.paytech.finance.accounting.domainservice.service;

import com.paytech.finance.accounting.domain.model.ClientBalance;
import com.paytech.finance.accounting.domain.model.PartnerBalance;

/** 查询 Client 与 Local Partner 分类账务余额。 */
public interface BalanceQueryService {

    /**
     * 查询指定经营主体和币种下的 Client 分类余额。
     *
     * @param clientId Client 规范十进制标识
     * @param entityNo Operating Entity 的 entityNo
     * @param currency ISO 4217 币种代码
     * @return Client 分类余额；未建立的账户按零返回
     */
    ClientBalance clientBalance(String clientId, String entityNo, String currency);

    /**
     * 查询指定经营主体和币种下的 Local Partner 分类余额。
     *
     * @param partnerId Local Partner 规范十进制标识
     * @param entityNo Operating Entity 的 entityNo
     * @param currency ISO 4217 币种代码
     * @return Local Partner 分类余额；未建立的账户按零返回
     */
    PartnerBalance partnerBalance(String partnerId, String entityNo, String currency);
}
