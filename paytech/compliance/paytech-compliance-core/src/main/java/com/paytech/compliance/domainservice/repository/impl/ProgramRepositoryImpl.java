package com.paytech.compliance.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.compliance.core.dal.dataobject.ProgramCheck;
import com.paytech.compliance.core.dal.dataobject.ProgramRequirement;
import com.paytech.compliance.core.dal.dataobject.ProgramRequirementDocType;
import com.paytech.compliance.core.dal.dataobject.ProgramScreening;
import com.paytech.compliance.core.dal.dataobject.ProgramVersionView;
import com.paytech.compliance.core.dal.mapper.ProgramMapper;
import com.paytech.compliance.domainservice.repository.ProgramRepository;

import lombok.AllArgsConstructor;

/** 尽调程序目录的只读访问；不创建、修复或回退程序。 */
@Repository
@AllArgsConstructor
public class ProgramRepositoryImpl implements ProgramRepository {
    private final ProgramMapper mapper;

    /** 解析候选。 */
    @Override
    public List<ProgramVersionView> findCandidates(String partyType, String roleType, String tier, String jurisdictionCode,
        Long ownerPartnerId) {
        return mapper.findCandidates(partyType, roleType, tier, jurisdictionCode, ownerPartnerId);
    }

    /** 运营程序目录。 */
    @Override
    public List<ProgramVersionView> findCatalog(String jurisdictionCode, String status) {
        return mapper.findCatalog(jurisdictionCode, status);
    }

    /** 按版本读取。 */
    @Override
    public List<ProgramVersionView> findVersions(Collection<Long> versionIds) {
        return versionIds.isEmpty() ? List.of() : mapper.findVersions(versionIds);
    }

    /** 材料槽。 */
    @Override
    public List<ProgramRequirement> findRequirements(Collection<Long> versionIds) {
        return versionIds.isEmpty() ? List.of() : mapper.findRequirements(versionIds);
    }

    /** 允许类型。 */
    @Override
    public List<ProgramRequirementDocType> findDocTypes(Collection<Long> requirementIds) {
        return requirementIds.isEmpty() ? List.of() : mapper.findDocTypes(requirementIds);
    }

    /** 检查模板。 */
    @Override
    public List<ProgramCheck> findChecks(Collection<Long> versionIds) {
        return versionIds.isEmpty() ? List.of() : mapper.findChecks(versionIds);
    }

    /** 筛查模板。 */
    @Override
    public List<ProgramScreening> findScreenings(Collection<Long> versionIds) {
        return versionIds.isEmpty() ? List.of() : mapper.findScreenings(versionIds);
    }
}
