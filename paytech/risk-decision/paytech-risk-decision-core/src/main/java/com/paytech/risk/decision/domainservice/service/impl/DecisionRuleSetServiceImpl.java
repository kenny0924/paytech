package com.paytech.risk.decision.domainservice.service.impl;

import static com.paytech.risk.decision.domainservice.policy.RuleSetManagementPolicy.managementResourceKey;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.core.dal.dataobject.RuleSet;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetVersion;
import com.paytech.risk.decision.domain.enums.RuleSetManagementStatus;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAssembler;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService;
import lombok.AllArgsConstructor;

/** 供流程与发布引用的规则读取能力；不承担入口写入编排。 */
@Service
@AllArgsConstructor
public class DecisionRuleSetServiceImpl implements DecisionRuleSetService {
    private final DecisionRuleSetAssembler assembler;
    private final DecisionRuleSetRepository repository;

    /** 读取未删除根下的准确版本并验证内容。 */
    @Override
    public DecisionRuleSetAggregate detail(long ruleSetVersionId) {
        requirePositiveRequestId(ruleSetVersionId, "ruleSetVersionId");
        RuleSetVersion version = repository.findVersion(ruleSetVersionId).orElseThrow(DecisionRuleSetServiceImpl::missing);
        RuleSet root = repository.findRuleSet(version.decisionPointId(), version.ruleSetCode())
            .orElseThrow(() -> corruption("规则集管理根缺失，请先完成迁移"));
        root.verifyIntegrity();
        if (root.getStatus() == RuleSetManagementStatus.DELETED) throw missing();
        return requireAggregateAfterKnownReference(ruleSetVersionId);
    }

    /** 真实历史引用允许读取已删除根的不可变版本。 */
    @Override
    public DecisionRuleSetAggregate detailForPersistedReference(long ruleSetVersionId) {
        requirePositiveInternalId(ruleSetVersionId, "ruleSetVersionId");
        return requireAggregateAfterKnownReference(ruleSetVersionId);
    }

    /** 旧多绑定点的已核验绑定只作为引用，规则版本自身的 Companion 校验保持不变。 */
    @Override
    public DecisionRuleSetAggregate detailForLegacyOrchestration(long ruleSetVersionId, long bindingId) {
        requirePositiveInternalId(ruleSetVersionId, "ruleSetVersionId");
        requirePositiveInternalId(bindingId, "bindingId");
        return aggregateAfterKnownReference(() -> repository.requireOrchestratedConfiguration(ruleSetVersionId, bindingId));
    }

    /** 按已存在版本的不变坐标准备资源锁。 */
    @Override
    public String resourceKeyForVersion(long ruleSetVersionId) {
        requirePositiveRequestId(ruleSetVersionId, "ruleSetVersionId");
        RuleSetVersion version = repository.findVersion(ruleSetVersionId).orElseThrow(DecisionRuleSetServiceImpl::missing);
        return resourceKeyForPoint(version.decisionPointId(), version.ruleSetCode());
    }

    /** 读取准确事件点以组成规则集锁键。 */
    private String resourceKeyForPoint(long decisionPointId, String ruleSetCode) {
        DecisionPoint point = repository.findPoint(decisionPointId)
            .orElseThrow(() -> corruption("规则集所属事件点缺失"));
        if (point.decisionPointId() != decisionPointId) throw corruption("规则集所属事件点身份不一致");
        return managementResourceKey(point.pointCode(), ruleSetCode);
    }

    /** 读取已定位规则完整事实并验证原内容。 */
    private DecisionRuleSetAggregate requireAggregateAfterKnownReference(long ruleSetVersionId) {
        return aggregateAfterKnownReference(() -> repository.requireConfiguration(ruleSetVersionId));
    }

    /** 已定位引用的闭包在同一快照内消失视为数据损坏。 */
    private DecisionRuleSetAggregate aggregateAfterKnownReference(Supplier<DecisionRuleSetRepository.DecisionRuleSetAggregate> loader) {
        try {
            return assembler.toAggregate(loader.get());
        } catch (NoSuchElementException exception) {
            throw corruption("已定位的 Rule Set 闭包消失", exception);
        }
    }

    /** 入口标识必须为正数，否则返回参数错误。 */
    private static void requirePositiveRequestId(long value, String name) {
        if (value <= 0) {
            throw new InvalidParameterException(name + " 格式无效");
        }
    }

    /** 内部引用标识非法视为数据损坏。 */
    private static void requirePositiveInternalId(long value, String name) {
        if (value <= 0) {
            throw corruption(name + " 无效");
        }
    }

    /** 返回配置不存在的领域结果。 */
    private static DecisionConfigurationNotFoundException missing() {
        return new DecisionConfigurationNotFoundException();
    }

    /** 将持久化不变量损坏报告为内部错误。 */
    private static ErrorException corruption(String message) {
        return new ErrorException(message);
    }

    /** 将持久化不变量损坏报告为内部错误。 */
    private static ErrorException corruption(String message, Throwable cause) {
        return new ErrorException(message, cause);
    }

}
