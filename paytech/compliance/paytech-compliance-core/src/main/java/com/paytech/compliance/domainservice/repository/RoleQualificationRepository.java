package com.paytech.compliance.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.compliance.core.dal.dataobject.RoleQualification;
import com.paytech.compliance.domain.QualificationSubject;

/** t_role_qualification 的角色资格投影持久化。 */
public interface RoleQualificationRepository {
    /**
     * 新增版本 1 的角色资格
     * <li>同一角色与资格类型已存在（uk_role_qualification）时抛出 QUALIFICATION_CHANGED，由外层事务整体回滚。</li>
     */
    void insert(RoleQualification row);

    /** 按资格 ID 读取；不存在返回 null。 */
    RoleQualification findById(long qualificationId);

    /** 按角色与资格类型读取；不存在返回 null。 */
    RoleQualification findByRole(String roleType, String roleId, String qualificationType);

    /** 若干主体的全部资格行；空集合返回空列表。 */
    List<RoleQualification> findBySubjects(List<QualificationSubject> subjects);

    /** 条件分页；条件均可空。 */
    List<RoleQualification> page(String roleType, String roleId, String qualificationType, String status, int offset, int size);

    /** 与分页相同条件的总数。 */
    long count(String roleType, String roleId, String qualificationType, String status);

    /**
     * 以读取时版本为条件切换到目标投影，并把目标的决定、状态、有效期、复审调度与冻结属性同步到原对象
     * <li>版本已变化抛出 QUALIFICATION_CHANGED，原对象不变。</li>
     *
     * @param row 读取时的投影
     * @param target 本次决定对应的目标投影值（只读取其可变字段）
     * @param now 服务端时间
     */
    void applyDecision(RoleQualification row, RoleQualification target, Instant now);
}
