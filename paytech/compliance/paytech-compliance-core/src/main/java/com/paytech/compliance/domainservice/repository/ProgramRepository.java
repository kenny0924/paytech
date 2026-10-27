package com.paytech.compliance.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.compliance.core.dal.dataobject.ProgramCheck;
import com.paytech.compliance.core.dal.dataobject.ProgramRequirement;
import com.paytech.compliance.core.dal.dataobject.ProgramRequirementDocType;
import com.paytech.compliance.core.dal.dataobject.ProgramScreening;
import com.paytech.compliance.core.dal.dataobject.ProgramVersionView;

/**
 * 尽调程序目录的只读访问（程序、版本与其模板表）
 * <li>程序目录只由初始化 DML 维护、作为一个整体被读取，故按目录聚合为一个只读 Repository，不提供写入。</li>
 */
public interface ProgramRepository {
    /** 解析候选；见 Mapper 条件。 */
    List<ProgramVersionView> findCandidates(String partyType, String roleType, String tier, String jurisdictionCode, Long ownerPartnerId);

    /**
     * 运营程序目录
     *
     * @param jurisdictionCode 可选辖区代码
     * @param status 可选程序状态
     * @return 程序版本，按辖区、程序代码、版本号倒序，最多 201 行
     */
    List<ProgramVersionView> findCatalog(String jurisdictionCode, String status);

    /** 按版本 ID 集合读取；空集合返回空列表。 */
    List<ProgramVersionView> findVersions(Collection<Long> versionIds);

    /** 版本集合的材料槽；空集合返回空列表。 */
    List<ProgramRequirement> findRequirements(Collection<Long> versionIds);

    /** 材料槽集合的允许类型；空集合返回空列表。 */
    List<ProgramRequirementDocType> findDocTypes(Collection<Long> requirementIds);

    /** 版本集合的检查模板；空集合返回空列表。 */
    List<ProgramCheck> findChecks(Collection<Long> versionIds);

    /** 版本集合的筛查模板；空集合返回空列表。 */
    List<ProgramScreening> findScreenings(Collection<Long> versionIds);
}
