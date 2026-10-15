package com.paytech.finance.accounting.domainservice.service;

import com.paytech.finance.accounting.core.dal.dataobject.AdjustmentApproval;
import com.paytech.finance.accounting.core.dal.dataobject.ManualAdjustment;
import com.paytech.finance.accounting.domain.ApproveManualAdjustmentContext;
import com.paytech.finance.accounting.domain.SubmitManualAdjustmentContext;
import com.paytech.finance.accounting.domain.view.ManualAdjustmentDetailView;

/** 人工会计调整的本地保存、状态流转与详情读取。 */
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
}
