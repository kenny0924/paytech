package com.paytech.finance.accounting.domainservice.repository;

import java.util.List;

import com.paytech.finance.accounting.core.dal.dataobject.AccountingInstruction;
import com.paytech.finance.accounting.core.enums.InstructionStatus;

/** t_AccountingInstruction：记账指令读写。 */
public interface AccountingInstructionRepository {

    /** 插入记账指令；同一来源已有指令时按并发冲突拒绝。 */
    void insert(AccountingInstruction instruction);

    /** 按人工调整读取其唯一指令，不存在返回空。 */
    AccountingInstruction findByManualAdjustment(long manualAdjustmentId);

    /** 按原状态与版本迁移指令状态，成功后同步原对象。 */
    void updateStatus(AccountingInstruction instruction, InstructionStatus status);

    /** 统计账簿中尚未过账的指令。 */
    long countPendingByBooks(List<Long> bookIds);
}
