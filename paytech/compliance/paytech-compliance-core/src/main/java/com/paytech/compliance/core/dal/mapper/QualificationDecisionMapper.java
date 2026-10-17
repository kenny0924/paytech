package com.paytech.compliance.core.dal.mapper;

import org.apache.ibatis.annotations.Param;

import com.paytech.compliance.core.dal.dataobject.QualificationDecision;

/** t_qualification_decision 的最终决定 SQL；业务 SQL 位于 XML。 */
public interface QualificationDecisionMapper {
    /** 新增最终决定。 */
    int insert(@Param("row") QualificationDecision row);

    /** 某案件的最终决定；每个 Client 入驻案件至多一条，不存在返回 null。 */
    QualificationDecision findByCase(@Param("caseId") long caseId);
}
