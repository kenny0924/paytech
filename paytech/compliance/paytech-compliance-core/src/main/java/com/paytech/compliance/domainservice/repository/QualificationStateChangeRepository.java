package com.paytech.compliance.domainservice.repository;

import java.util.List;

import com.paytech.compliance.core.dal.dataobject.QualificationStateChange;

/** t_qualification_state_change 的追加持久化。 */
public interface QualificationStateChangeRepository {
    /** 追加一条状态变更；影响行数不为 1 时失败。 */
    void insert(QualificationStateChange row);

    /** 某资格指定聚合版本的状态变更；不存在返回 null。 */
    QualificationStateChange findByVersion(long qualificationId, long version);

    /** 某资格的全部状态变更，按聚合版本倒序。 */
    List<QualificationStateChange> findByQualification(long qualificationId);
}
