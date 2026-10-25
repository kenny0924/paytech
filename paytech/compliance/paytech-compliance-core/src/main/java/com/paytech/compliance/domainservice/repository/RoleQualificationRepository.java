package com.paytech.compliance.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.compliance.core.dal.dataobject.QualificationDecision;
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
     * 以读取时版本为条件切换到新决定，并同步原对象
     * <li>状态取决定结论，有效期取决定有效期；APPROVED 为 NOT_REQUIRED，其余为 COMPLETED，复审调度清空。</li>
     * <li>版本已变化抛出 QUALIFICATION_CHANGED，原对象不变。</li>
     *
     * @param row 读取时的投影
     * @param decision 本次新决定
     * @param now 服务端时间
     */
    void applyDecision(RoleQualification row, QualificationDecision decision, Instant now);
}
