package com.paytech.reconciliation.domainservice.service;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.api.enums.ClearingAccountStatus;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.domain.view.ClearingAccountView;

/** 清算账户查询与按 MID + 币种解析（UC-R01、R03、R17）；只读，不开启事务。 */
public interface ClearingAccountQueryService {

    /**
     * 读取清算账户与其 MID
     *
     * @param clearingAccountId 清算账户主键
     * @return 详情；不存在时抛业务拒绝（400）
     */
    ClearingAccountView getClearingAccount(long clearingAccountId);

    /**
     * 分页查询清算账户（按编码升序），每项带已挂入的 MID
     *
     * @param entityNo 可选经营主体
     * @param channelCode 可选渠道
     * @param currency 可选币种
     * @param status 可选状态
     * @param paging 已校验的分页参数
     * @return 分页结果
     */
    PagingResponseBody<ClearingAccountView> listClearingAccounts(String entityNo, String channelCode, String currency,
        ClearingAccountStatus status, PagingRequestBody paging);

    /**
     * 按 MID + 币种解析 ACTIVE 清算账户（billing 取 CLEARING_ACCOUNT 引用）
     *
     * @param midId channel-connector MID 主键
     * @param currency 交易币种
     * @return 清算账户；未归属或已停用时抛 {@code FailException(CLEARING_ACCOUNT_NOT_RESOLVED)}（400）
     */
    ClearingAccount resolve(long midId, String currency);
}
