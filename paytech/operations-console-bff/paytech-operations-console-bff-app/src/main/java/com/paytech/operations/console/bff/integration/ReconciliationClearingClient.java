package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.ReconciliationClearingModels.AddClearingAccountMidRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationClearingModels.ChangeClearingAccountStatusRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationClearingModels.ClearingAccountResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationClearingModels.ClearingAccountSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationClearingModels.CreateClearingAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationClearingModels.UnassignedMidListResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationClearingModels.UpdateClearingAccountSettingsRequestBody;

/**
 * reconciliation 清算账户的本地端口；归属规则、channel-connector 核对与唯一性归 reconciliation
 * <li>命令以会话 Account ID 与 Session ID 作为操作人 Header 转发；查询不携带操作人。</li>
 *
 * @author Claude
 */
public interface ReconciliationClearingClient {

    /**
     * 清算账户分页，按编码升序
     *
     * @param entityNo 可选经营主体编号
     * @param channelCode 可选渠道编码
     * @param currency 可选币种
     * @param status 可选状态
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 清算账户分页
     */
    PagingResponseBody<ClearingAccountSummaryResponseBody> listClearingAccounts(String entityNo, String channelCode,
        String currency, String status, int page, int size);

    /**
     * 清算账户详情（含已挂入 MID，不含 MID 外部编码）
     *
     * @param clearingAccountId 清算账户主键
     * @return 清算账户与 MID
     */
    ClearingAccountResponseBody getClearingAccount(String clearingAccountId);

    /**
     * 已激活但未归入清算账户的 MID + 币种
     *
     * @param entityNo 可选经营主体编号
     * @param channelCode 可选渠道编码
     * @param currency 可选币种
     * @return 未归属列表
     */
    UnassignedMidListResponseBody listUnassignedMids(String entityNo, String channelCode, String currency);

    /**
     * 新建清算账户
     *
     * @param context 操作人会话
     * @param request 编码、渠道、经营主体、币种与配置
     * @return 新清算账户
     */
    ClearingAccountResponseBody createClearingAccount(OperatorContext context, CreateClearingAccountRequestBody request);

    /**
     * 整体替换清算账户三项配置与到账银行账户
     *
     * @param context 操作人会话
     * @param clearingAccountId 清算账户主键
     * @param request 新配置
     * @return 清算账户
     */
    ClearingAccountResponseBody updateClearingAccountSettings(OperatorContext context, String clearingAccountId,
        UpdateClearingAccountSettingsRequestBody request);

    /**
     * 挂入 MID
     *
     * @param context 操作人会话
     * @param clearingAccountId 清算账户主键
     * @param request MID 主键
     * @return 含全部 MID 的清算账户
     */
    ClearingAccountResponseBody addClearingAccountMid(OperatorContext context, String clearingAccountId,
        AddClearingAccountMidRequestBody request);

    /**
     * 摘除 MID
     *
     * @param context 操作人会话
     * @param clearingAccountId 清算账户主键
     * @param midId MID 主键
     * @return 含剩余 MID 的清算账户
     */
    ClearingAccountResponseBody removeClearingAccountMid(OperatorContext context, String clearingAccountId, String midId);

    /**
     * 启用或停用清算账户
     *
     * @param context 操作人会话
     * @param clearingAccountId 清算账户主键
     * @param request 目标状态
     * @return 清算账户
     */
    ClearingAccountResponseBody changeClearingAccountStatus(OperatorContext context, String clearingAccountId,
        ChangeClearingAccountStatusRequestBody request);
}
