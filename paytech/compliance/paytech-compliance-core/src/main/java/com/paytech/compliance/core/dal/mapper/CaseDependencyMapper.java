package com.paytech.compliance.core.dal.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;

import com.paytech.compliance.core.dal.dataobject.CaseDependency;

/** t_dd_case_dependency 的案件依赖 SQL；业务 SQL 位于 XML。 */
public interface CaseDependencyMapper {
    /** 新增案件依赖。 */
    int insert(@Param("row") CaseDependency row);

    /** 主案件的全部依赖。 */
    List<CaseDependency> findByParent(@Param("parentCaseId") long parentCaseId);
}
