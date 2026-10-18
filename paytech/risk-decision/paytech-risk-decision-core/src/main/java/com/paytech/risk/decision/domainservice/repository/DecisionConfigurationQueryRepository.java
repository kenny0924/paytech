package com.paytech.risk.decision.domainservice.repository;

import java.util.List;

import com.paytech.risk.decision.core.dal.dataobject.DecisionPublicationReference;
import com.paytech.risk.decision.core.dal.dataobject.DictionaryRuleReference;
import com.paytech.risk.decision.core.dal.dataobject.RejectionReasonReferenceRow;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.DictionaryType;

/** 历史与字典反查的批量、分页读取，不改变旧活动引用查询集合。 */
public interface DecisionConfigurationQueryRepository {
    /** 按规则根及固定版本上界倒序读取当前页的版本 ID。 */
    List<Long> historyIds(long pointId, String code, int maxVersion, int limit, int offset);
    /** 统计固定版本上界内的历史条数，与分页查询使用相同筛选。 */
    long historyCount(long pointId, String code, int maxVersion);
    /** 分页读取经环境、状态和规则版本筛选的发布引用。 */
    List<DecisionPublicationReference> releases(long ruleSetId, DecisionEnvironment environment, DecisionReleaseStatus status,
        Long versionId, int limit, int offset);
    /** 按相同发布筛选统计引用总数。 */
    long releaseCount(long ruleSetId, DecisionEnvironment environment, DecisionReleaseStatus status, Long versionId);
    /** 统计决策点的断裂发布关系，供查询侧拒绝不完整的使用事实。 */
    long brokenReleaseCount(long pointId);
    /** 按字典类型、编码和配置归属分页反查保存项。 */
    List<DictionaryRuleReference> dictionaryReferences(DictionaryType type, String code, String scope, int limit, int offset);
    /** 统计与字典反查列表同范围的保存项数量。 */
    long dictionaryReferenceCount(DictionaryType type, String code, String scope);
    /** 批量读取当前页规则版本的活动发布，避免逐引用查询。 */
    List<DecisionPublicationReference> activeReferences(List<Long> versionIds);
    List<RejectionReasonReferenceRow> reasonReferences(String code, String scope, int limit, int offset);
    long reasonReferenceCount(String code, String scope);
}
