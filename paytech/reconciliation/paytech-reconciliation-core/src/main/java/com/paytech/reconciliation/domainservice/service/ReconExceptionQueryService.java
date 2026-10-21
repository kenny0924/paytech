package com.paytech.reconciliation.domainservice.service;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.ReconLayer;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.domain.view.ReconExceptionDetailView;
import com.paytech.reconciliation.domain.view.ReconSummaryView;

/** 对账异常与在途汇总查询（UC-R17、F5，#25、#28）；只读，不开启事务。 */
public interface ReconExceptionQueryService {

    /**
     * 分页查询对账异常（开立时间升序，即账龄从长到短）
     *
     * @param status 可选状态；为空时只查 OPEN、UNDER_REVIEW（决定 I-04）
     * @param exceptionType 可选类型
     * @param reconLayer 可选对账层
     * @param clearingAccountId 可选清算账户
     * @param cashAccountId 可选银行账户
     * @param statementId 可选渠道账单
     * @param paging 已校验的分页参数
     * @return 分页结果
     */
    PagingResponseBody<ReconException> listExceptions(ReconExceptionStatus status, ReconExceptionType exceptionType,
        ReconLayer reconLayer, Long clearingAccountId, Long cashAccountId, Long statementId, PagingRequestBody paging);

    /**
     * 对账异常详情
     *
     * @param exceptionId 异常主键
     * @return 详情；不存在时业务拒绝
     */
    ReconExceptionDetailView getException(long exceptionId);

    /**
     * 在途汇总：在途渠道账单、开放异常账龄段、未匹配银行流水
     *
     * @param entityNo 可选经营主体编号
     * @param currency 币种
     * @return 汇总
     */
    ReconSummaryView summarize(String entityNo, String currency);
}
