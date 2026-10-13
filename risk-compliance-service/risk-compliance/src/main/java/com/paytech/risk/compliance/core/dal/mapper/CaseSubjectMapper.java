package com.paytech.risk.compliance.core.dal.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;

import com.paytech.risk.compliance.core.dal.dataobject.CaseSubject;

/** t_dd_case_subject 的案件主体 SQL；业务 SQL 位于 XML。 */
public interface CaseSubjectMapper {
    /** 新增案件主体。 */
    int insert(@Param("row") CaseSubject row);

    /** 若干案件的全部主体。 */
    List<CaseSubject> findByCaseIds(@Param("caseIds") List<Long> caseIds);
}
