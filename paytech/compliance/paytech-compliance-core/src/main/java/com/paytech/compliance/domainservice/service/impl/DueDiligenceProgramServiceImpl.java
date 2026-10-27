package com.paytech.compliance.domainservice.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.paytech.compliance.core.dal.dataobject.ProgramCheck;
import com.paytech.compliance.core.dal.dataobject.ProgramRequirement;
import com.paytech.compliance.core.dal.dataobject.ProgramRequirementDocType;
import com.paytech.compliance.core.dal.dataobject.ProgramScreening;
import com.paytech.compliance.core.dal.dataobject.ProgramVersionView;
import com.paytech.compliance.domain.ResolvedProgram;
import com.paytech.compliance.domainservice.repository.ProgramRepository;
import com.paytech.compliance.domainservice.service.DueDiligenceProgramService;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.common.error.ServiceUnavailableException;

import lombok.AllArgsConstructor;

/** 程序解析：精确度排序后取最高优先级唯一候选；配置缺失、歧义或未批准一律不可用，不回退。 */
@Service
@AllArgsConstructor
public class DueDiligenceProgramServiceImpl implements DueDiligenceProgramService {
    /** 程序目录单次最多返回的版本数。 */
    private static final int CATALOG_LIMIT = 200;

    private final ProgramRepository programs;

    /** 解析当前生效程序。 */
    @Override
    public ResolvedProgram resolve(String partyType, String roleType, String tier, String jurisdictionCode, Long responsiblePartnerId,
        Instant now) {
        List<ProgramVersionView> candidates = programs.findCandidates(partyType, roleType, tier, jurisdictionCode, responsiblePartnerId);
        if (candidates.isEmpty()) {
            throw new ServiceUnavailableException();
        }
        Comparator<ProgramVersionView> precedence = Comparator.comparingInt(DueDiligenceProgramServiceImpl::rank);
        int best = candidates.stream().map(DueDiligenceProgramServiceImpl::rank).min(Integer::compare).orElseThrow();
        List<ProgramVersionView> top = candidates.stream().filter(row -> rank(row) == best).sorted(precedence).toList();
        if (top.size() != 1 || !top.getFirst().isApprovedAuthorityAt(now)) {
            throw new ServiceUnavailableException();
        }
        return assemble(List.of(top.getFirst())).get(top.getFirst().getProgramVersionId());
    }

    /** 读取冻结的程序版本。 */
    @Override
    public Map<Long, ResolvedProgram> load(Collection<Long> versionIds) {
        return assemble(programs.findVersions(versionIds.stream().distinct().toList()));
    }

    /** 运营程序目录：超过 200 个版本时拒绝，提示缩小范围。 */
    @Override
    public List<ResolvedProgram> listCatalog(String jurisdictionCode, String status) {
        List<ProgramVersionView> rows = programs.findCatalog(jurisdictionCode, status);
        if (rows.size() > CATALOG_LIMIT) {
            throw new InvalidParameterException("程序版本超过 " + CATALOG_LIMIT + " 个，请按辖区或状态过滤");
        }
        Map<Long, ResolvedProgram> assembled = assemble(rows);
        return rows.stream().map(row -> assembled.get(row.getProgramVersionId())).toList();
    }

    /** 精确度排名，越小越优先：Owner 定制在前，辖区精确在前。 */
    static int rank(ProgramVersionView row) {
        return (row.getOwnerPartnerId() == null ? 2 : 0) + (row.getJurisdictionCode() == null ? 1 : 0);
    }

    /** 一次读取全部版本的模板并按版本组装。 */
    Map<Long, ResolvedProgram> assemble(List<ProgramVersionView> versions) {
        List<Long> versionIds = versions.stream().map(ProgramVersionView::getProgramVersionId).toList();
        List<ProgramRequirement> requirements = programs.findRequirements(versionIds);
        Map<Long, List<String>> docTypes = programs.findDocTypes(requirements.stream()
            .map(ProgramRequirement::getProgramRequirementId).toList()).stream()
            .collect(Collectors.groupingBy(ProgramRequirementDocType::getProgramRequirementId, LinkedHashMap::new,
                Collectors.mapping(ProgramRequirementDocType::getDocumentType, Collectors.toList())));
        requirements.forEach(row -> row.setDocumentTypes(docTypes.getOrDefault(row.getProgramRequirementId(), List.of())));
        Map<Long, List<ProgramCheck>> checks = programs.findChecks(versionIds).stream()
            .collect(Collectors.groupingBy(ProgramCheck::getProgramVersionId));
        Map<Long, List<ProgramScreening>> screenings = programs.findScreenings(versionIds).stream()
            .collect(Collectors.groupingBy(ProgramScreening::getProgramVersionId));
        Map<Long, ResolvedProgram> result = new HashMap<>();
        for (ProgramVersionView version : versions) {
            long id = version.getProgramVersionId();
            List<ProgramRequirement> own = new ArrayList<>(requirements.stream()
                .filter(row -> Objects.equals(row.getProgramVersionId(), id)).toList());
            result.put(id, new ResolvedProgram(version, own, checks.getOrDefault(id, List.of()), screenings.getOrDefault(id, List.of())));
        }
        return result;
    }
}
