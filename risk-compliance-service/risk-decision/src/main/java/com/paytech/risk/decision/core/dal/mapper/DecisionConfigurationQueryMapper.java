package com.paytech.risk.decision.core.dal.mapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.paytech.risk.decision.core.dal.dataobject.*;
import com.paytech.risk.decision.domain.enums.*;
/** 分页与批量引用 SQL，所有表名固定。 */
public interface DecisionConfigurationQueryMapper {
    /** 按规则根及固定版本上界倒序读取当前页的版本 ID。 */
    List<Long> historyIds(@Param("pointId") long pointId, @Param("code") String code, @Param("maxVersion") int maxVersion, @Param("limit") int limit, @Param("offset") int offset);
    /** 统计固定版本上界内的历史条数，与分页查询使用相同筛选。 */
    long historyCount(@Param("pointId") long pointId, @Param("code") String code, @Param("maxVersion") int maxVersion);
    /** 分页读取经环境、状态和规则版本筛选的发布引用。 */
    List<DecisionPublicationReference> releases(@Param("ruleSetId") long ruleSetId, @Param("environment") DecisionEnvironment environment, @Param("status") DecisionReleaseStatus status, @Param("versionId") Long versionId, @Param("limit") int limit, @Param("offset") int offset);
    /** 按相同发布筛选统计引用总数。 */
    long releaseCount(@Param("ruleSetId") long ruleSetId, @Param("environment") DecisionEnvironment environment, @Param("status") DecisionReleaseStatus status, @Param("versionId") Long versionId);
    /** 统计决策点的断裂发布关系，供查询侧拒绝不完整的使用事实。 */
    long brokenReleaseCount(@Param("pointId") long pointId);
    /** 按字典类型、编码和配置归属分页反查保存项。 */
    List<DictionaryRuleReference> dictionaryReferences(@Param("type") DictionaryType type, @Param("code") String code, @Param("scope") String scope, @Param("limit") int limit, @Param("offset") int offset);
    /** 统计与字典反查列表同范围的保存项数量。 */
    long dictionaryReferenceCount(@Param("type") DictionaryType type, @Param("code") String code, @Param("scope") String scope);
    /** 批量读取当前页规则版本的活动发布，避免逐引用查询。 */
    List<DecisionPublicationReference> activeReferences(@Param("versionIds") List<Long> versionIds);
    /** 原因的两种来源先合并，再统一排序分页。 */
    List<RejectionReasonReferenceRow> reasonReferences(@Param("code") String code, @Param("scope") String scope,
        @Param("limit") int limit, @Param("offset") int offset);
    long reasonReferenceCount(@Param("code") String code, @Param("scope") String scope);
}
