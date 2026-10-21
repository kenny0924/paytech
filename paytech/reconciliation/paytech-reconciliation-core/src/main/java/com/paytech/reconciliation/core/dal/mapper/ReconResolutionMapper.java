package com.paytech.reconciliation.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import com.paytech.reconciliation.api.enums.CheckerDecision;
import com.paytech.reconciliation.api.enums.ResolutionStatus;
import com.paytech.reconciliation.core.dal.dataobject.ReconResolution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** t_recon_resolution：异常处置的 MyBatis 映射；SQL 位于 mapper/reconciliation/ReconResolutionMapper.xml。 */
@Mapper
public interface ReconResolutionMapper {
    /** 插入一行；主键由应用分配。 */
    int insert(ReconResolution record);

    /** 按主键读取，不存在返回 null。 */
    ReconResolution selectById(@Param("id") Long id);

    /** 按主键集合读取，按主键升序。 */
    List<ReconResolution> selectByIds(@Param("ids") List<Long> ids);

    /** 读取一个异常的全部处置。 */
    List<ReconResolution> selectByException(@Param("exceptionId") Long exceptionId);

    /** 按可选条件分页读取，排序稳定。 */
    List<ReconResolution> selectPage(@Param("status") ResolutionStatus status, @Param("offset") long offset,
        @Param("limit") int limit);

    /** 与 selectPage 相同条件的总数。 */
    long countPage(@Param("status") ResolutionStatus status);

    /** 按版本复核一条待复核处置。 */
    int review(@Param("record") ReconResolution record, @Param("status") ResolutionStatus status,
        @Param("checkerId") Long checkerId, @Param("checkerDecision") CheckerDecision checkerDecision,
        @Param("approvedContentHash") String approvedContentHash, @Param("decisionTime") Instant decisionTime,
        @Param("now") Instant now);
}
