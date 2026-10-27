package com.paytech.compliance.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.compliance.core.dal.dataobject.RoleQualification;
import com.paytech.compliance.domain.QualificationSubject;

/** t_role_qualification 的角色资格投影 SQL；业务 SQL 位于 XML。 */
public interface RoleQualificationMapper {
    /** 新增角色资格。 */
    int insert(@Param("row") RoleQualification row);

    /** 按资格 ID 读取；不存在返回 null。 */
    RoleQualification findById(@Param("qualificationId") long qualificationId);

    /** 按唯一键读取；不存在返回 null。 */
    RoleQualification findByRole(@Param("roleType") String roleType, @Param("roleId") String roleId,
        @Param("qualificationType") String qualificationType);

    /** 若干角色的全部资格行，按资格 ID 升序。 */
    List<RoleQualification> findBySubjects(@Param("subjects") List<QualificationSubject> subjects);

    /** 按条件分页，按更新时间倒序、资格 ID 倒序稳定排序。 */
    List<RoleQualification> page(@Param("roleType") String roleType, @Param("roleId") String roleId,
        @Param("qualificationType") String qualificationType, @Param("status") String status, @Param("offset") int offset,
        @Param("size") int size);

    /** 与分页相同条件的总数。 */
    long count(@Param("roleType") String roleType, @Param("roleId") String roleId,
        @Param("qualificationType") String qualificationType, @Param("status") String status);

    /** 以读取时版本为条件切换到目标投影的决定、状态、有效期、复审调度与冻结属性。 */
    int applyDecision(@Param("row") RoleQualification row, @Param("target") RoleQualification target, @Param("now") Instant now);
}
