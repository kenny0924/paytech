package com.paytech.account.domainservice.service;

import com.paytech.account.domain.model.ClientBalance;
import com.paytech.account.domain.model.PartnerBalance;

/** 查询 Client 与 Local Partner 在一个经营主体与币种下的分类余额；只读本库 Owner 账户。 */
public interface BalanceQueryService {

    /**
     * 查询指定经营主体和币种下的 Client 分类余额。
     *
     * @param clientId Client 规范十进制标识
     * @param entityNo Operating Entity 的 entityNo
     * @param currency ISO 4217 币种代码
     * @return Client 分类余额，缺少的桶为零；该 Client 在此主体与币种下没有任何账户时为空
     */
    ClientBalance clientBalance(String clientId, String entityNo, String currency);

    /**
     * 查询指定经营主体和币种下的 Local Partner 分类余额。
     *
     * @param partnerId Local Partner 规范十进制标识
     * @param entityNo Operating Entity 的 entityNo
     * @param currency ISO 4217 币种代码
     * @return Local Partner 分类余额，缺少的桶为零；该 Partner 在此主体与币种下没有任何账户时为空
     */
    PartnerBalance partnerBalance(String partnerId, String entityNo, String currency);
}
