package com.paytech.reconciliation.core.dal.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.paytech.reconciliation.api.enums.ExpectedMovementStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementType;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** t_expected_bank_movement：预期银行流水的 MyBatis 映射；SQL 位于 mapper/reconciliation/ExpectedBankMovementMapper.xml。 */
@Mapper
public interface ExpectedBankMovementMapper {
    /** 插入一行；主键由应用分配。 */
    int insert(ExpectedBankMovement record);

    /** 按主键读取，不存在返回 null。 */
    ExpectedBankMovement selectById(@Param("id") Long id);

    /** 按主键集合读取，按主键升序。 */
    List<ExpectedBankMovement> selectByIds(@Param("ids") List<Long> ids);

    /** 按来源读取（唯一）。 */
    ExpectedBankMovement selectBySource(@Param("movementType") ExpectedMovementType movementType,
        @Param("sourceId") Long sourceId);

    /** 读取银行账户下未确认完的预期流水。 */
    List<ExpectedBankMovement> selectOpen(@Param("cashAccountId") Long cashAccountId);

    /** 按可选条件分页读取，排序稳定。 */
    List<ExpectedBankMovement> selectPage(@Param("cashAccountId") Long cashAccountId,
        @Param("movementType") ExpectedMovementType movementType, @Param("sourceId") Long sourceId,
        @Param("status") ExpectedMovementStatus status, @Param("offset") long offset, @Param("limit") int limit);

    /** 与 selectPage 相同条件的总数。 */
    long countPage(@Param("cashAccountId") Long cashAccountId,
        @Param("movementType") ExpectedMovementType movementType, @Param("sourceId") Long sourceId,
        @Param("status") ExpectedMovementStatus status);

    /** 按版本更新确认与归类金额及状态。 */
    int updateProgress(@Param("record") ExpectedBankMovement record,
        @Param("confirmedAmount") BigDecimal confirmedAmount, @Param("classifiedAmount") BigDecimal classifiedAmount,
        @Param("status") ExpectedMovementStatus status, @Param("now") Instant now);
}
