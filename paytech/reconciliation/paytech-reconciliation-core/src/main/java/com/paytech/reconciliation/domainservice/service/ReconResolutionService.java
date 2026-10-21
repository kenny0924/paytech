package com.paytech.reconciliation.domainservice.service;

import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.core.dal.dataobject.ReconResolution;

/**
 * 异常处置的提交与复核保存（UC-R15、R16）
 * <li>事务传播为 REQUIRED；异常状态迁移（ReconExceptionService）与处置应用由调用方在同一事务组合。</li>
 */
public interface ReconResolutionService {

    /**
     * 插入 PENDING_CHECK 处置
     *
     * @param resolution 已准备主键、序号与内容摘要的新处置
     */
    void submit(ReconResolution resolution);

    /**
     * 批准即应用：PENDING_CHECK → APPLIED；来源为平台流水、预期银行流水、银行流水的 CLASSIFY 同一事务登记
     * ReconAdjustmentApproved
     *
     * @param resolution 已读取的待复核处置
     * @param exception 所属异常（决定来源对象）
     * @param checkerId 复核人
     * @param approvedContentHash 复核人确认的内容摘要
     */
    void approve(ReconResolution resolution, ReconException exception, Long checkerId, String approvedContentHash);

    /**
     * 复核拒绝：PENDING_CHECK → REJECTED
     *
     * @param resolution 已读取的待复核处置
     * @param checkerId 复核人
     * @param approvedContentHash 复核人确认的内容摘要
     */
    void reject(ReconResolution resolution, Long checkerId, String approvedContentHash);
}
