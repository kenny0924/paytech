package com.paytech.risk.decision.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.risk.decision.core.dal.dataobject.DecisionPublicationReference;
import com.paytech.risk.decision.core.dal.dataobject.DictionaryRuleReference;
import com.paytech.risk.decision.core.dal.dataobject.RejectionReasonReferenceRow;
import com.paytech.risk.decision.core.dal.mapper.DecisionConfigurationQueryMapper;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.DictionaryType;
import com.paytech.risk.decision.domainservice.repository.DecisionConfigurationQueryRepository;

import lombok.AllArgsConstructor;
/** 屏蔽 SQL 映射实现，不增加其他系统数据访问。 */
@Repository
@AllArgsConstructor
public class DecisionConfigurationQueryRepositoryImpl implements DecisionConfigurationQueryRepository {
    private final DecisionConfigurationQueryMapper mapper;
    @Override
    public List<Long> historyIds(long pointId, String code, int maxVersion, int limit, int offset) {
        return mapper.historyIds(pointId, code, maxVersion, limit, offset);
    }
    @Override
    public long historyCount(long pointId, String code, int maxVersion) {
        return mapper.historyCount(pointId, code, maxVersion);
    }
    @Override
    public List<DecisionPublicationReference> releases(long ruleSetId, DecisionEnvironment environment,
        DecisionReleaseStatus status, Long versionId, int limit, int offset) {
        return mapper.releases(ruleSetId, environment, status, versionId, limit, offset);
    }

    @Override
    public long releaseCount(long ruleSetId, DecisionEnvironment environment, DecisionReleaseStatus status,
        Long versionId) {
        return mapper.releaseCount(ruleSetId, environment, status, versionId);
    }

    @Override
    public long brokenReleaseCount(long pointId) {
        return mapper.brokenReleaseCount(pointId);
    }
    @Override public List<DictionaryRuleReference> dictionaryReferences(DictionaryType type, String code, String scope, int limit, int offset) { return mapper.dictionaryReferences(type, code, scope, limit, offset); }
    @Override public long dictionaryReferenceCount(DictionaryType type, String code, String scope) { return mapper.dictionaryReferenceCount(type, code, scope); }
    @Override public List<DecisionPublicationReference> activeReferences(List<Long> versionIds) { return mapper.activeReferences(versionIds); }
    @Override public List<RejectionReasonReferenceRow> reasonReferences(String code, String scope, int limit, int offset) {
        return mapper.reasonReferences(code, scope, limit, offset);
    }
    @Override public long reasonReferenceCount(String code, String scope) { return mapper.reasonReferenceCount(code, scope); }
}
