package com.paytech.compliance.core.dal.mapper;

import org.apache.ibatis.annotations.Param;

import com.paytech.compliance.core.dal.dataobject.CaseStateChange;

/** t_dd_case_state_change 的案件状态变更 SQL；业务 SQL 位于 XML。 */
public interface CaseStateChangeMapper {
    /** 新增案件状态变更。 */
    int insert(@Param("row") CaseStateChange row);
}
