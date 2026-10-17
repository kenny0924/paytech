package com.paytech.risk.decision.domain.authoring;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.decision.domain.enums.ActionCategory;
import com.paytech.risk.decision.domain.enums.ConfigurationStatus;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.MergePolicy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 各决策点规则终态动作的 Handler 目录；未知或漂移的持久化目录一律 fail closed。 */
public final class DecisionActionRegistry {

    private static final DecisionActionRegistry INSTANCE = new DecisionActionRegistry();

    private final Map<ActionKey, DecisionPointAuthoringDescriptor.DecisionActionDescriptor> byKey;

    /** 汇总全部决策点的终态动作；动作编码全局唯一。 */
    private DecisionActionRegistry() {
        Map<ActionKey, DecisionPointAuthoringDescriptor.DecisionActionDescriptor> index = new HashMap<>();
        for (DecisionPointAuthoringDescriptor descriptor : DecisionPointAuthoringDescriptor.all()) {
            descriptor.actions().forEach(value -> {
                if (index.put(new ActionKey(value.actionCode(), value.versionNo()), value) != null) {
                    throw new IllegalStateException("决策动作编码重复: " + value.actionCode());
                }
            });
        }
        byKey = Map.copyOf(index);
    }

    /** 进程内唯一目录。 */
    public static DecisionActionRegistry instance() {
        return INSTANCE;
    }

    /** 取已注册的终态动作；不区分决策点。 */
    public DecisionPointAuthoringDescriptor.DecisionActionDescriptor require(ActionKey key) {
        DecisionPointAuthoringDescriptor.DecisionActionDescriptor value = byKey.get(key);
        if (value == null) {
            throw new InvalidParameterException("不支持的决策动作版本");
        }
        return value;
    }

    /**
     * 核对一个决策点的持久化终态动作目录
     *
     * @param descriptor 决策点合同
     * @param persisted 按动作版本 ID 读取的持久化行
     *         <li>数量、编码、Handler、摘要、合并策略、状态及连续 ID 任一漂移都视为数据损坏。</li>
     */
    public void verifyPersistedCatalog(DecisionPointAuthoringDescriptor descriptor, List<PersistedActionVersion> persisted) {
        Objects.requireNonNull(persisted, "persisted");
        List<DecisionPointAuthoringDescriptor.DecisionActionDescriptor> actions = descriptor.actions();
        if (persisted.size() != actions.size()) {
            throw new IllegalStateException("持久化动作目录不完整");
        }
        Set<ActionKey> seen = new HashSet<>();
        Set<Long> seenIds = new HashSet<>();
        for (PersistedActionVersion row : persisted) {
            if (row == null || !seenIds.add(row.actionVersionId())
                || !seen.add(new ActionKey(row.actionCode(), row.versionNo()))) {
                throw new IllegalStateException("持久化动作目录含重复或无效行");
            }
            DecisionPointAuthoringDescriptor.DecisionActionDescriptor expected = byKey.get(
                new ActionKey(row.actionCode(), row.versionNo()));
            if (expected == null || !actions.contains(expected) || drifted(expected, row)
                || row.actionVersionId() != descriptor.firstActionVersionId() + actions.indexOf(expected)
                || row.status() != ConfigurationStatus.ACTIVE) {
                throw new IllegalStateException("持久化动作目录与内置 Handler 不一致");
            }
        }
    }

    /** 判断持久化行的执行协议是否偏离内置 Handler。 */
    private static boolean drifted(DecisionPointAuthoringDescriptor.DecisionActionDescriptor expected,
        PersistedActionVersion row) {
        return !expected.handlerCode().equals(row.handlerCode())
            || !expected.implementationVersion().equals(row.implementationVersion())
            || !expected.implementationHash().equals(row.implementationHash())
            || expected.actionCategory() != row.actionCategory()
            || expected.terminalFlag() != row.terminalFlag()
            || expected.decisionCode() != row.decisionCode()
            || expected.mergePolicy() != row.mergePolicy()
            || expected.shadowSupportedFlag() != row.shadowSupportedFlag();
    }

    public record ActionKey(String actionCode, int versionNo) {
        public ActionKey {
            Objects.requireNonNull(actionCode, "actionCode");
        }
    }

    public record PersistedActionVersion(
        long actionVersionId,
        String actionCode,
        int versionNo,
        String handlerCode,
        String implementationVersion,
        String implementationHash,
        ActionCategory actionCategory,
        boolean terminalFlag,
        DecisionValue decisionCode,
        MergePolicy mergePolicy,
        boolean shadowSupportedFlag,
        ConfigurationStatus status
    ) { }
}
