package com.paytech.compliance.core.dal.mapper;

import java.util.List;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

import com.paytech.compliance.core.dal.dataobject.CaseRequirement;

/** t_dd_requirement 的案件材料要求 SQL；业务 SQL 位于 XML。 */
public interface CaseRequirementMapper {
    /** 新增案件材料要求。 */
    int insert(@Param("row") CaseRequirement row);

    /** 若干案件的材料要求及其程序模板。 */
    List<com.paytech.compliance.domain.ReviewRequirementView> findViewsByCaseIds(@Param("caseIds") List<Long> caseIds);

    /** 以版本为条件更新要求状态，返回影响行数。 */
    int updateStatus(@Param("requirementId") long requirementId, @Param("version") long version, @Param("status") String status,
        @Param("now") Instant now);
}
