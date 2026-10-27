package com.paytech.compliance.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.compliance.core.dal.dataobject.ProgramCheck;
import com.paytech.compliance.core.dal.dataobject.ProgramRequirement;
import com.paytech.compliance.core.dal.dataobject.ProgramRequirementDocType;
import com.paytech.compliance.core.dal.dataobject.ProgramScreening;
import com.paytech.compliance.core.dal.dataobject.ProgramVersionView;

/** 尽调程序目录（程序、版本、材料槽、允许类型、检查与筛查模板）的只读 SQL；业务 SQL 位于 XML。 */
public interface ProgramMapper {
    /** 可解析候选：程序与版本均 ACTIVE，辖区为指定值或全局，Owner 为指定值或平台；最多 20 行。 */
    List<ProgramVersionView> findCandidates(@Param("partyType") String partyType, @Param("roleType") String roleType,
        @Param("tier") String tier, @Param("jurisdictionCode") String jurisdictionCode, @Param("ownerPartnerId") Long ownerPartnerId);

    /** 运营程序目录：可选辖区与程序状态，按辖区、程序代码、版本号倒序，最多 201 行。 */
    List<ProgramVersionView> findCatalog(@Param("jurisdictionCode") String jurisdictionCode, @Param("status") String status);

    /** 按版本 ID 集合读取程序版本（不限状态，供在途案件读取冻结版本）。 */
    List<ProgramVersionView> findVersions(@Param("versionIds") Collection<Long> versionIds);

    /** 版本集合的材料槽，按版本与代码排序。 */
    List<ProgramRequirement> findRequirements(@Param("versionIds") Collection<Long> versionIds);

    /** 材料槽集合的允许类型，按模板与类型排序。 */
    List<ProgramRequirementDocType> findDocTypes(@Param("requirementIds") Collection<Long> requirementIds);

    /** 版本集合的检查模板，按版本与代码排序。 */
    List<ProgramCheck> findChecks(@Param("versionIds") Collection<Long> versionIds);

    /** 版本集合的筛查模板，按版本、主体角色与类型排序。 */
    List<ProgramScreening> findScreenings(@Param("versionIds") Collection<Long> versionIds);
}
