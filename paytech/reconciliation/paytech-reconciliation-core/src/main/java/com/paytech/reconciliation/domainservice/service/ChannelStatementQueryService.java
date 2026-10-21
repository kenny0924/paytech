package com.paytech.reconciliation.domainservice.service;

import java.time.Instant;
import java.time.LocalDate;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.ClearingDirection;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.domain.view.ChannelStatementDetailView;
import com.paytech.reconciliation.domain.view.ChannelStatementView;

/** 渠道账单、账单行与平台流水查询（UC-R17，#16、#17）；只读，不开启事务。 */
public interface ChannelStatementQueryService {

    /**
     * 分页查询渠道账单（结算日降序、主键降序）
     *
     * @param clearingAccountId 可选清算账户
     * @param channelBatchRef 可选批次号
     * @param status 可选状态
     * @param settlementDateFrom 可选结算日起（含）
     * @param settlementDateTo 可选结算日止（含）
     * @param paging 已校验的分页参数
     * @return 分页结果，每项带清算账户
     */
    PagingResponseBody<ChannelStatementView> listStatements(Long clearingAccountId, String channelBatchRef,
        ChannelStatementStatus status, LocalDate settlementDateFrom, LocalDate settlementDateTo, PagingRequestBody paging);

    /**
     * 渠道账单详情（含对平门禁）
     *
     * @param statementId 渠道账单主键
     * @return 详情；不存在时抛业务拒绝（400）
     */
    ChannelStatementDetailView getStatement(long statementId);

    /**
     * 读取渠道账单头
     *
     * @param statementId 渠道账单主键
     * @return 账单头；不存在时抛业务拒绝（400）
     */
    ChannelStatement getStatementHeader(long statementId);

    /**
     * 分页查询账单行（行号升序）
     *
     * @param statementId 渠道账单主键，不存在时抛业务拒绝（400）
     * @param matchStatus 可选匹配状态
     * @param lineType 可选行类型
     * @param paging 已校验的分页参数
     * @return 分页结果
     */
    PagingResponseBody<ChannelStatementLine> listLines(long statementId, LineMatchStatus matchStatus,
        ChannelLineType lineType, PagingRequestBody paging);

    /**
     * 分页查询平台流水（业务时间降序、主键降序）
     *
     * @param clearingAccountId 可选清算账户
     * @param statementId 可选渠道账单：只返回该账单行匹配到的平台流水（决定 I-02）
     * @param status 可选状态
     * @param direction 可选方向
     * @param externalTransactionId 可选外部交易号
     * @param businessTimeFrom 可选业务时间起（含）
     * @param businessTimeTo 可选业务时间止（不含）
     * @param paging 已校验的分页参数
     * @return 分页结果
     */
    PagingResponseBody<PlatformTxn> listPlatformTxns(Long clearingAccountId, Long statementId, PlatformTxnStatus status,
        ClearingDirection direction, String externalTransactionId, Instant businessTimeFrom, Instant businessTimeTo,
        PagingRequestBody paging);
}
