package com.paytech.compliance.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.compliance.core.dal.dataobject.CaseClassification;

/** t_dd_case_classification 的任务经营分类 SQL；业务 SQL 位于 XML。 */
public interface CaseClassificationMapper {
    /** 新增任务经营分类。 */
    int insert(@Param("row") CaseClassification row);

    /** 案件的全部分类。 */
    List<CaseClassification> findByCase(@Param("caseId") long caseId);
}
