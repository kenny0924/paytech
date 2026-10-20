package com.paytech.operations.console.bff.integration;

import java.time.Instant;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceBillingFactModels.BillingFactResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceBillingFactModels.BillingFactSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceBillingFactModels.SubmitBillingFactRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

/**
 * billing 计费事实人工登记的本地端口；事实内容、Maker-Checker 规则与事件发布归 billing
 * <li>命令以会话 Account ID 与 Session ID 作为操作人 Header 转发；查询不携带操作人。</li>
 *
 * @author Claude
 */
public interface FinanceBillingFactClient {

    /**
     * 按主体与币种分页检索计费事实（含任意状态），按事实 ID 倒序
     *
     * @param entityNo Operating Entity 编号
     * @param currency 币种
     * @param clientId 可选 Client
     * @param status 可选登记状态
     * @param factType 可选事实类型
     * @param createdFrom 可选登记时间下限（含）
     * @param createdTo 可选登记时间上限（不含）
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 事实头摘要分页
     */
    PagingResponseBody<BillingFactSummaryResponseBody> searchBillingFacts(String entityNo, String currency,
        String clientId, String status, String factType, Instant createdFrom, Instant createdTo, int page, int size);

    /**
     * 计费事实详情
     *
     * @param factId 计费事实 ID
     * @return 事实头、引用、明细行与行链接
     */
    BillingFactResponseBody getBillingFact(String factId);

    /**
     * 登记计费事实（DRAFT）：来源系统固定为 OPERATIONS，来源引用为新生成的 OPS- + UUID
     *
     * @param context 编制人会话
     * @param request 事实内容
     * @return DRAFT 事实
     */
    BillingFactResponseBody submitBillingFact(OperatorContext context, SubmitBillingFactRequestBody request);

    /**
     * 批准计费事实
     *
     * @param context 复核人会话
     * @param factId 计费事实 ID
     * @return APPROVED 事实
     */
    BillingFactResponseBody approveBillingFact(OperatorContext context, String factId);

    /**
     * 作废计费事实草稿
     *
     * @param context 操作人会话
     * @param factId 计费事实 ID
     * @return VOIDED 事实
     */
    BillingFactResponseBody voidBillingFact(OperatorContext context, String factId);
}
