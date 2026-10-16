package com.paytech.finance.accounting.domainservice.repository;

import java.util.List;

import com.paytech.finance.accounting.core.dal.dataobject.CloseApproval;

/** t_close_approval：关账审批记录追加与读取。 */
public interface CloseApprovalRepository {

    /** 插入审批记录；同一角色已有记录时按并发冲突拒绝。 */
    void insert(CloseApproval approval);

    /** 读取流程的审批记录。 */
    List<CloseApproval> findByClose(long periodCloseId);
}
