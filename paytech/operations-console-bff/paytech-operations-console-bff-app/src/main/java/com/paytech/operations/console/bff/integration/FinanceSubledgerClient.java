package com.paytech.operations.console.bff.integration;

import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSubledgerModels.AccountPostingResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSubledgerModels.OwnerAccountEntryResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSubledgerModels.OwnerAccountResponseBody;
import com.paytech.operations.console.bff.service.model.FinancePostingOpsModels.PostingListResponseBody;
import com.paytech.operations.console.bff.service.model.FinancePostingOpsModels.RebuildPostingStreamsRequestBody;
import com.paytech.operations.console.bff.service.model.FinancePostingOpsModels.RebuildPostingStreamsResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

/**
 * account 明细账（Owner 账户、明细流水、过账批次）的本地端口；明细事实归 account
 * <li>只读查询之外，只有明细账运维的按流重建余额一个命令，以会话 Account ID 作为 account 的 operatorId。</li>
 *
 * @author Claude
 */
public interface FinanceSubledgerClient {

    /**
     * 主体与币种下的 Owner 账户分页
     *
     * @param entityType 主体类型
     * @param entityId 主体标识
     * @param currency 币种
     * @param ownerType 可选核算对象类型
     * @param ownerId 可选核算对象标识
     * @param accountCode 可选科目代码
     * @param balanceBucket 可选余额桶
     * @param accountNo 可选账户号，精确匹配
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return Owner 账户分页
     */
    PagingResponseBody<OwnerAccountResponseBody> listOwnerAccounts(String entityType, String entityId, String currency,
        String ownerType, String ownerId, String accountCode, String balanceBucket, String accountNo, int page,
        int size);

    /**
     * Owner 账户明细流水分页，按批次序号、行号升序
     *
     * @param accountId Owner 账户主键
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 流水分页
     */
    PagingResponseBody<OwnerAccountEntryResponseBody> listOwnerAccountEntries(String accountId, int page, int size);

    /**
     * 过账批次详情
     *
     * @param postingId 批次主键
     * @return 批次头与全部流水
     */
    AccountPostingResponseBody getPosting(String postingId);

    /**
     * 按过账流与起始序号读取批次摘要（明细账运维），按序号升序
     *
     * @param stream 已校验的过账流身份
     * @param fromSequence 起始序号（含）
     * @param limit 本页批次数
     * @return 批次摘要（不含流水，带流水条数）与下一页起始序号
     */
    PostingListResponseBody listStreamPostings(AccountPostedPayload.StreamKey stream, long fromSequence, int limit);

    /**
     * 按过账流重建主体与币种下的余额（明细账运维）
     *
     * @param context 操作人；Account ID 作为 operatorId
     * @param request 主体与币种
     * @return 重算的账户数、余额有变化的账户数、流水数与最大批次序号
     */
    RebuildPostingStreamsResponseBody rebuildPostingStreams(OperatorContext context,
        RebuildPostingStreamsRequestBody request);
}
