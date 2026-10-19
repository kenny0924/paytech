package com.paytech.operations.console.bff.integration;

import com.paytech.operations.console.bff.service.model.FinanceBalanceModels.ClientBalanceResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceBalanceModels.PartnerBalanceResponseBody;

/**
 * account 余额投影的本地端口；余额事实归 account，出款资格归 Risk 与 Finance
 *
 * @author Claude
 */
public interface FinanceBalanceClient {

    /**
     * Client 分类余额
     *
     * @param clientId Client ID
     * @param entityNo Operating Entity 的 entityNo
     * @param currency 币种
     * @return 分类余额
     */
    ClientBalanceResponseBody queryClientBalance(String clientId, String entityNo, String currency);

    /**
     * Local Partner 分类余额
     *
     * @param partnerId Local Partner ID
     * @param entityNo Operating Entity 的 entityNo
     * @param currency 币种
     * @return 分类余额，不含保证金
     */
    PartnerBalanceResponseBody queryPartnerBalance(String partnerId, String entityNo, String currency);
}
