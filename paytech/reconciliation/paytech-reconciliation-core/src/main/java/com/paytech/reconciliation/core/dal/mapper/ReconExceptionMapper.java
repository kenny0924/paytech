package com.paytech.reconciliation.core.dal.mapper;

import com.paytech.reconciliation.domain.view.ReconSummaryView;
import java.time.Instant;
import java.util.List;

import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.ReconLayer;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** t_recon_exception：对账异常的 MyBatis 映射；SQL 位于 mapper/reconciliation/ReconExceptionMapper.xml。 */
@Mapper
public interface ReconExceptionMapper {
    /** 插入一行；主键由应用分配。 */
    int insert(ReconException record);

    /** 按主键读取，不存在返回 null。 */
    ReconException selectById(@Param("id") Long id);

    /** 读取同一对象同一类型的未结异常。 */
    ReconException selectActive(@Param("sourceType") ReconExceptionSourceType sourceType,
        @Param("sourceId") Long sourceId, @Param("exceptionType") ReconExceptionType exceptionType);

    /** 读取同一对象同一类型最近开立的一条异常（任意状态）。 */
    ReconException selectLatestBySource(@Param("sourceType") ReconExceptionSourceType sourceType,
        @Param("sourceId") Long sourceId, @Param("exceptionType") ReconExceptionType exceptionType);

    /** 多行插入一段（至多 1,000 条）；主键由应用分配。 */
    int insertBatch(@Param("records") List<ReconException> records);

    /** 读取一段对象（至多 1,000 个）上同一类型的未结异常。 */
    List<ReconException> selectActiveBySources(@Param("sourceType") ReconExceptionSourceType sourceType,
        @Param("sourceIds") List<Long> sourceIds, @Param("exceptionType") ReconExceptionType exceptionType);

    /** 读取一段对象（至多 1,000 个）上同一类型各自最近开立的一条异常。 */
    List<ReconException> selectLatestBySources(@Param("sourceType") ReconExceptionSourceType sourceType,
        @Param("sourceIds") List<Long> sourceIds, @Param("exceptionType") ReconExceptionType exceptionType);

    /** 读取一个对象上全部类型的未结异常（OPEN、UNDER_REVIEW），按主键升序。 */
    List<ReconException> selectActiveBySource(@Param("sourceType") ReconExceptionSourceType sourceType,
        @Param("sourceId") Long sourceId);

    /** 读取一张渠道账单的未结异常。 */
    List<ReconException> selectActiveByStatement(@Param("statementId") Long statementId);

    /** 按可选条件分页读取，排序稳定。 */
    List<ReconException> selectPage(@Param("statuses") List<ReconExceptionStatus> statuses,
        @Param("exceptionType") ReconExceptionType exceptionType, @Param("reconLayer") ReconLayer reconLayer,
        @Param("clearingAccountId") Long clearingAccountId, @Param("cashAccountId") Long cashAccountId,
        @Param("statementId") Long statementId, @Param("offset") long offset, @Param("limit") int limit);

    /** 与 selectPage 相同条件的总数。 */
    long countPage(@Param("statuses") List<ReconExceptionStatus> statuses,
        @Param("exceptionType") ReconExceptionType exceptionType, @Param("reconLayer") ReconLayer reconLayer,
        @Param("clearingAccountId") Long clearingAccountId, @Param("cashAccountId") Long cashAccountId,
        @Param("statementId") Long statementId);

    /** 按版本与原状态推进状态，同步活动槽位与结案时间。 */
    int changeStatus(@Param("record") ReconException record, @Param("status") ReconExceptionStatus status,
        @Param("activeSlot") Integer activeSlot, @Param("closedTime") Instant closedTime, @Param("now") Instant now);

    /** 按版本与原状态多行推进到同一状态（一段至多 1,000 条），返回命中行数。 */
    int changeStatusBatch(@Param("records") List<ReconException> records, @Param("status") ReconExceptionStatus status,
        @Param("activeSlot") Integer activeSlot, @Param("closedTime") Instant closedTime, @Param("now") Instant now);

    /** 按账龄段汇总未结异常。 */
    List<ReconSummaryView.ExceptionAgeTotal> sumOpenByAge(@Param("entityNo") String entityNo,
        @Param("currency") String currency, @Param("now") Instant now);
}
