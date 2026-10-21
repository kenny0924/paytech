package com.paytech.reconciliation.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import com.paytech.reconciliation.api.enums.BankMatchStatus;
import com.paytech.reconciliation.core.dal.dataobject.BankMatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** t_bank_match：银行匹配的 MyBatis 映射；SQL 位于 mapper/reconciliation/BankMatchMapper.xml。 */
@Mapper
public interface BankMatchMapper {
    /** 插入一行；主键由应用分配。 */
    int insert(BankMatch record);

    /** 按主键读取，不存在返回 null。 */
    BankMatch selectById(@Param("id") Long id);

    /** 读取一条银行流水的匹配。 */
    List<BankMatch> selectByLine(@Param("bankStatementLineId") Long bankStatementLineId,
        @Param("status") BankMatchStatus status);

    /** 读取一条预期银行流水的匹配。 */
    List<BankMatch> selectByExpected(@Param("expectedMovementId") Long expectedMovementId,
        @Param("status") BankMatchStatus status);

    /** 按可选条件分页读取，排序稳定。 */
    List<BankMatch> selectPage(@Param("bankStatementLineId") Long bankStatementLineId,
        @Param("expectedMovementId") Long expectedMovementId, @Param("status") BankMatchStatus status,
        @Param("offset") long offset, @Param("limit") int limit);

    /** 与 selectPage 相同条件的总数。 */
    long countPage(@Param("bankStatementLineId") Long bankStatementLineId,
        @Param("expectedMovementId") Long expectedMovementId, @Param("status") BankMatchStatus status);

    /** 按版本确认或拒绝一条建议。 */
    int decide(@Param("record") BankMatch record, @Param("status") BankMatchStatus status,
        @Param("operatorId") Long operatorId, @Param("decisionTime") Instant decisionTime, @Param("now") Instant now);
}
