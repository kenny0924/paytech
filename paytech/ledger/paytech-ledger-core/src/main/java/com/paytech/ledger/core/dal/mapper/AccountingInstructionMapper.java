package com.paytech.ledger.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.ledger.core.dal.dataobject.AccountingInstruction;
import com.paytech.ledger.core.enums.InstructionStatus;

/** t_accounting_instruction：记账指令持久化。 */
@Mapper
public interface AccountingInstructionMapper {

    /** 插入一行。 */
    int insert(AccountingInstruction record);

    /** 按主键读取。 */
    AccountingInstruction selectById(@Param("id") long id);

    /** 按人工调整读取其唯一指令。 */
    AccountingInstruction selectByManualAdjustment(@Param("manualAdjustmentId") long manualAdjustmentId);

    /**
     * 按期望状态和版本迁移指令状态。
     *
     * @return 受影响行数
     */
    int updateStatus(@Param("instruction") AccountingInstruction instruction, @Param("status") InstructionStatus status,
        @Param("now") Instant now);

    /** 关账步骤 2：统计账簿中仍处于 RECEIVED/VALIDATING/VALIDATED 的指令。 */
    long countPendingByBooks(@Param("bookIds") List<Long> bookIds);

    /** 按经济事实读取全部 AUTO 指令，按账簿、主键升序。 */
    List<AccountingInstruction> selectAutoByEconomicFact(@Param("economicFactId") long economicFactId);
}
