package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.ReconciliationCashModels.CashAccountResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationCashModels.CreateCashAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationCashModels.ReviewCashAccountRequestBody;

/**
 * reconciliation 银行账户的本地端口；Vault Protect、复核与唯一有效账户规则归 reconciliation
 * <li>新建请求含银行账号明文：只原样转发一次，不缓存、不记录；对应浏览器路径只记元数据日志。</li>
 *
 * @author Claude
 */
public interface ReconciliationCashClient {

    /**
     * 银行账户分页
     *
     * @param entityType 可选主体类型
     * @param entityId 可选主体标识
     * @param currency 可选币种
     * @param accountPurpose 可选资金用途
     * @param status 可选状态
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 银行账户分页（只含末四位）
     */
    PagingResponseBody<CashAccountResponseBody> listCashAccounts(String entityType, String entityId, String currency,
        String accountPurpose, String status, int page, int size);

    /**
     * 银行账户详情
     *
     * @param cashAccountId 银行账户主键
     * @return 银行账户（只含末四位）
     */
    CashAccountResponseBody getCashAccount(String cashAccountId);

    /**
     * 提交新建银行账户
     *
     * @param context 提交人会话
     * @param request 主体、用途、银行与账号明文
     * @return 待复核的银行账户
     */
    CashAccountResponseBody createCashAccount(OperatorContext context, CreateCashAccountRequestBody request);

    /**
     * 申请关闭银行账户
     *
     * @param context 提交人会话
     * @param cashAccountId 银行账户主键
     * @return 带关闭申请的银行账户
     */
    CashAccountResponseBody requestCashAccountClosure(OperatorContext context, String cashAccountId);

    /**
     * 复核银行账户新建或关闭申请
     *
     * @param context 复核人会话
     * @param cashAccountId 银行账户主键
     * @param request 决定与确认摘要
     * @return 复核后的银行账户
     */
    CashAccountResponseBody reviewCashAccount(OperatorContext context, String cashAccountId,
        ReviewCashAccountRequestBody request);
}
