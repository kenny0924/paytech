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

/** 首期 Handler 目录；未知或漂移的持久化目录一律 fail closed。 */
public final class DecisionActionRegistry {

    private static final DecisionActionRegistry V1 = new DecisionActionRegistry();

    private final List<CheckoutPrepareAuthoringDescriptor.DecisionActionDescriptor> actions;
    private final Map<ActionKey, CheckoutPrepareAuthoringDescriptor.DecisionActionDescriptor> byKey;

    private DecisionActionRegistry() {
        actions = CheckoutPrepareAuthoringDescriptor.v1().actions();
        Map<ActionKey, CheckoutPrepareAuthoringDescriptor.DecisionActionDescriptor> index = new HashMap<>();
        actions.forEach(value -> index.put(new ActionKey(value.actionCode(), value.versionNo()), value));
        byKey = Map.copyOf(index);
    }

    public static DecisionActionRegistry checkoutPrepareV1() {
        return V1;
    }

    public List<CheckoutPrepareAuthoringDescriptor.DecisionActionDescriptor> authoringActions() {
        return actions;
    }

    public CheckoutPrepareAuthoringDescriptor.DecisionActionDescriptor require(ActionKey key) {
        CheckoutPrepareAuthoringDescriptor.DecisionActionDescriptor value = byKey.get(key);
        if (value == null) {
            throw new InvalidParameterException("不支持的决策动作版本");
        }
        return value;
    }

    public void verifyPersistedCatalog(List<PersistedActionVersion> persisted) {
        Objects.requireNonNull(persisted, "persisted");
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
            CheckoutPrepareAuthoringDescriptor.DecisionActionDescriptor expected = byKey.get(
                new ActionKey(row.actionCode(), row.versionNo()));
            if (expected == null
                || !expected.handlerCode().equals(row.handlerCode())
                || !expected.implementationVersion().equals(row.implementationVersion())
                || !expected.implementationHash().equals(row.implementationHash())
                || expected.actionCategory() != row.actionCategory()
                || expected.terminalFlag() != row.terminalFlag()
                || expected.decisionCode() != row.decisionCode()
                || expected.mergePolicy() != row.mergePolicy()
                || expected.shadowSupportedFlag() != row.shadowSupportedFlag()
                || row.actionVersionId() != 240001L + actions.indexOf(expected)
                || row.status() != ConfigurationStatus.ACTIVE) {
                throw new IllegalStateException("持久化动作目录与内置 Handler 不一致");
            }
        }
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
