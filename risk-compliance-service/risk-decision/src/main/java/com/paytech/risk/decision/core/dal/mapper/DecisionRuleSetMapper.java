package com.paytech.risk.decision.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.risk.decision.core.dal.dataobject.*;
import com.paytech.risk.decision.domain.enums.DecisionPointVersionStatus;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;

/** Decision Point 与 Rule Set 聚合的表形 Mapper。 */
public interface DecisionRuleSetMapper {

    /** 只读定位根，包含已删除根以支持原成功命令回放。 */
    RuleSet selectRuleSet(@Param("ruleSetId") long ruleSetId);

    /** 按不可变业务坐标定位根。 */
    RuleSet selectRuleSetByCode(@Param("decisionPointId") long decisionPointId, @Param("ruleSetCode") String ruleSetCode);

    /** 在决策点行锁之后锁定根；首次创建可为空。 */
    RuleSet lockRuleSet(@Param("decisionPointId") long decisionPointId, @Param("ruleSetCode") String ruleSetCode);

    /** 批量读取当前分页对应的管理根。 */
    List<RuleSet> selectRuleSetsByCurrentVersions(@Param("versionIds") List<Long> versionIds);

    /** 与首个完整版本在同一事务内建立管理身份。 */
    int insertRuleSet(@Param("value") RuleSet value);

    /** 对根进行期望版本和状态 CAS，保存及冻结递增版本。 */
    int advanceRuleSet(@Param("value") RuleSet value, @Param("currentVersionId") long currentVersionId,
        @Param("time") Instant time);

    /** 一次形成终态删除事实，不删除历史配置。 */
    int deleteRuleSet(@Param("value") RuleSet value, @Param("actorId") long actorId,
        @Param("triggerId") String triggerId, @Param("time") Instant time);

    /** 读取活动引用与异常槽位；写路径使用当前锁定读而非事务旧快照。 */
    List<RuleSetReleaseReference> selectReleaseReferences(@Param("pointIds") List<Long> pointIds,
        @Param("ruleSetIds") List<Long> ruleSetIds, @Param("lock") boolean lock);


    int insertRuleActionParameters(@Param("values") List<RuleActionParameter> values);

    DecisionPoint selectDecisionPointByCode(@Param("pointCode") String pointCode);

    DecisionPoint selectDecisionPointById(@Param("decisionPointId") long decisionPointId);

    List<DecisionPoint> selectDecisionPointsByIds(@Param("decisionPointIds") List<Long> decisionPointIds);

    DecisionPoint lockDecisionPoint(@Param("decisionPointId") long decisionPointId);

    RuleSetVersion selectRuleSetVersion(@Param("ruleSetVersionId") long ruleSetVersionId);

    RuleSetVersion lockRuleSetCodeRange(
        @Param("decisionPointId") long decisionPointId,
        @Param("ruleSetCode") String ruleSetCode
    );

    RuleSetVersion lockRuleSetVersion(@Param("ruleSetVersionId") long ruleSetVersionId);

    List<RuleSetVersion> selectRuleSetVersions(
        @Param("pointCode") String pointCode,
        @Param("ruleSetCode") String ruleSetCode,
        @Param("status") RuleSetVersionStatus status,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    /** 防止缺失根、悬空或非最高当前指针在分页 JOIN 中被静默过滤。 */
    long countMalformedRuleSets(@Param("pointCode") String pointCode, @Param("ruleSetCode") String ruleSetCode);

    long countRuleSetVersions(
        @Param("pointCode") String pointCode,
        @Param("ruleSetCode") String ruleSetCode,
        @Param("status") RuleSetVersionStatus status
    );

    List<DecisionPointRuleSet> selectCompanionBindingsByRuleSetVersionIds(
        @Param("ruleSetVersionIds") List<Long> ruleSetVersionIds
    );

    DecisionPointVersion selectPointVersion(@Param("pointVersionId") long pointVersionId);

    List<DecisionPointVersion> selectPointVersionsByIds(@Param("pointVersionIds") List<Long> pointVersionIds);

    List<RuleSetVersion> selectRuleSetVersionsByIds(@Param("ruleSetVersionIds") List<Long> ruleSetVersionIds);

    DecisionPointVersion lockPointVersion(@Param("pointVersionId") long pointVersionId);

    List<DecisionPointInputField> selectPointInputFields(@Param("pointVersionId") long pointVersionId);

    List<DecisionPointRuleSet> selectPointRuleSets(@Param("pointVersionId") long pointVersionId);

    List<DecisionPointRuleSet> selectPointRuleSetsByPointVersionIds(
        @Param("pointVersionIds") List<Long> pointVersionIds
    );

    List<DecisionPointFeature> selectPointFeatures(@Param("pointVersionId") long pointVersionId);

    List<DecisionPointObligation> selectPointObligations(@Param("pointVersionId") long pointVersionId);

    List<RiskRule> selectRules(@Param("ruleSetVersionId") long ruleSetVersionId);

    List<RuleNode> selectNodes(@Param("ruleIds") List<Long> ruleIds);

    List<RuleOperand> selectOperands(@Param("nodeIds") List<Long> nodeIds);

    List<RuleAction> selectRuleActions(@Param("ruleIds") List<Long> ruleIds);

    List<ActionVersion> selectActionVersions(@Param("actionVersionIds") List<Long> actionVersionIds);

    List<ActionParameterDefinition> selectActionParameterDefinitions(
        @Param("actionVersionIds") List<Long> actionVersionIds
    );

    List<RuleActionParameter> selectRuleActionParameters(@Param("ruleActionIds") List<Long> ruleActionIds);

    /** 按决策点与版本号读取点版本。 */
    DecisionPointVersion selectPointVersionByNo(@Param("decisionPointId") long decisionPointId,
        @Param("versionNo") int versionNo);

    List<FeatureVersion> selectFeatureVersions(@Param("featureVersionIds") List<Long> featureVersionIds);

    List<FeatureDependency> selectFeatureDependencies(@Param("featureVersionIds") List<Long> featureVersionIds);

    int insertRuleSetVersion(@Param("value") RuleSetVersion value);

    int insertRules(@Param("values") List<RiskRule> values);

    int insertNodes(@Param("values") List<RuleNode> values);

    int insertOperands(@Param("values") List<RuleOperand> values);

    int insertRuleActions(@Param("values") List<RuleAction> values);

    int retireRuleSetVersion(
        @Param("ruleSetVersionId") long ruleSetVersionId,
        @Param("expectedStatus") RuleSetVersionStatus expectedStatus,
        @Param("updateTime") Instant updateTime
    );

    int retirePointVersion(
        @Param("pointVersionId") long pointVersionId,
        @Param("expectedStatus") DecisionPointVersionStatus expectedStatus,
        @Param("updateTime") Instant updateTime
    );

    int freezeRuleSetVersion(
        @Param("ruleSetVersionId") long ruleSetVersionId,
        @Param("expectedStatus") RuleSetVersionStatus expectedStatus,
        @Param("expectedContentHash") String expectedContentHash,
        @Param("frozenBy") long frozenBy,
        @Param("frozenTime") Instant frozenTime,
        @Param("updateTime") Instant updateTime
    );

    int freezePointVersion(
        @Param("pointVersionId") long pointVersionId,
        @Param("expectedStatus") DecisionPointVersionStatus expectedStatus,
        @Param("expectedContentHash") String expectedContentHash,
        @Param("updateTime") Instant updateTime
    );
}
