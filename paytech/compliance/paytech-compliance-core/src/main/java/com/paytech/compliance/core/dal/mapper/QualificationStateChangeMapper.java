package com.paytech.compliance.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.compliance.core.dal.dataobject.QualificationStateChange;

/** t_qualification_state_change 的追加状态变更 SQL；业务 SQL 位于 XML。 */
public interface QualificationStateChangeMapper {
    /** 追加一条状态变更。 */
    int insert(@Param("row") QualificationStateChange row);

    /** 某资格指定聚合版本的状态变更；不存在返回 null。 */
    QualificationStateChange findByVersion(@Param("qualificationId") long qualificationId, @Param("version") long version);

    /** 某资格的全部状态变更，按聚合版本倒序。 */
    List<QualificationStateChange> findByQualification(@Param("qualificationId") long qualificationId);
}
