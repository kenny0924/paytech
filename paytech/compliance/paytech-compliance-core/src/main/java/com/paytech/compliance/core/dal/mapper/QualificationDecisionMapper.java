package com.paytech.compliance.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.compliance.core.dal.dataobject.QualificationDecision;

/** t_qualification_decision 的资格决定 SQL；业务 SQL 位于 XML。 */
public interface QualificationDecisionMapper {
    /** 新增资格决定。 */
    int insert(@Param("row") QualificationDecision row);

    /** 某案件的最终决定；每个 Client 入驻案件至多一条，不存在返回 null。 */
    QualificationDecision findByCase(@Param("caseId") long caseId);

    /** 按决定 ID 集合读取，按决定 ID 升序。 */
    List<QualificationDecision> findByIds(@Param("decisionIds") Collection<Long> decisionIds);
}
