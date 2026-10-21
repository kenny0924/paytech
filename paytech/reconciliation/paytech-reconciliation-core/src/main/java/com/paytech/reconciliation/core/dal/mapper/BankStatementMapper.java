package com.paytech.reconciliation.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import com.paytech.reconciliation.api.enums.BankStatementStatus;
import com.paytech.reconciliation.core.dal.dataobject.BankStatement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** t_bank_statement：银行对账单头的 MyBatis 映射；SQL 位于 mapper/reconciliation/BankStatementMapper.xml。 */
@Mapper
public interface BankStatementMapper {
    /** 插入一行；主键由应用分配。 */
    int insert(BankStatement record);

    /** 按主键读取，不存在返回 null。 */
    BankStatement selectById(@Param("id") Long id);

    /** 按银行账户 + 对账单编号读取（唯一）。 */
    BankStatement selectByRef(@Param("cashAccountId") Long cashAccountId, @Param("statementRef") String statementRef);

    /** 读取同一银行账户期间结束日最晚的已导入对账单。 */
    BankStatement selectLatestImported(@Param("cashAccountId") Long cashAccountId);

    /** 按可选条件分页读取，排序稳定。 */
    List<BankStatement> selectPage(@Param("cashAccountId") Long cashAccountId,
        @Param("status") BankStatementStatus status, @Param("offset") long offset, @Param("limit") int limit);

    /** 与 selectPage 相同条件的总数。 */
    long countPage(@Param("cashAccountId") Long cashAccountId, @Param("status") BankStatementStatus status);

    /** 接收中按版本更新已接收行数、重复行数与待拒绝的逐行原因。 */
    int updateImportProgress(@Param("record") BankStatement record,
        @Param("importedLineCount") Integer importedLineCount,
        @Param("duplicateLineCount") Integer duplicateLineCount, @Param("rejectReason") String rejectReason,
        @Param("now") Instant now);

    /** 已拒绝的对账单按版本重置为接收中（同编号更正重传）。 */
    int restart(@Param("record") BankStatement record, @Param("replacement") BankStatement replacement,
        @Param("now") Instant now);

    /** 按版本与原状态推进导入状态。 */
    int changeStatus(@Param("record") BankStatement record, @Param("status") BankStatementStatus status,
        @Param("rejectReason") String rejectReason, @Param("now") Instant now);
}
