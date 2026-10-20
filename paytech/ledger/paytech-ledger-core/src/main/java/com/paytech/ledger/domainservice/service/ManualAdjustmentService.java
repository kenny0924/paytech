package com.paytech.ledger.domainservice.service;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.core.dal.dataobject.AdjustmentApproval;
import com.paytech.ledger.core.dal.dataobject.ManualAdjustment;
import com.paytech.ledger.domain.ApproveManualAdjustmentContext;
import com.paytech.ledger.domain.ManualAdjustmentQuery;
import com.paytech.ledger.domain.SubmitManualAdjustmentContext;
import com.paytech.ledger.domain.view.ManualAdjustmentDetailView;
import com.paytech.ledger.domain.view.ManualAdjustmentView;

/** 人工会计调整的本地保存、状态流转、列表与详情读取。 */
public interface ManualAdjustmentService {

    /** 在一个事务内保存 SUBMITTED 调整头、明细与 Maker 记录。 */
    void submit(SubmitManualAdjustmentContext context);

    /**
     * 批准调整并生成 VALIDATED 人工指令
     * <li>必须加入调用方事务；调用方随后过账并调用 {@link #markPosted}。</li>
     */
    void approve(ApproveManualAdjustmentContext context);

    /** 过账成功后把调整从 APPROVED 改为 POSTED；必须加入调用方事务。 */
    void markPosted(ApproveManualAdjustmentContext context);

    /** 在一个事务内把 SUBMITTED 调整改为 REJECTED 并保存 Checker 驳回记录。 */
    void reject(ManualAdjustment adjustment, AdjustmentApproval checkerApproval);

    /**
     * 读取调整详情
     *
     * @param manualAdjustmentId 调整主键
     * @return 详情；不存在时返回空
     */
    ManualAdjustmentDetailView findDetail(long manualAdjustmentId);

    /**
     * 分页读取账簿的调整摘要
     *
     * @param query 账簿与可选的期间、状态、编制人过滤
     * @return 按提交时间、主键降序的摘要，金额按账簿币种精度补齐；账簿不存在时 LEDGER_BOOK_NOT_FOUND
     */
    PagingResponseBody<ManualAdjustmentView> pageAdjustments(ManualAdjustmentQuery query);
}
