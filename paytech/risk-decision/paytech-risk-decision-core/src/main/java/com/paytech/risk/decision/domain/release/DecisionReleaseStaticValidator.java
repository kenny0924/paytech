package com.paytech.risk.decision.domain.release;

import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry.PersistedActionVersion;
import com.paytech.risk.decision.domain.enums.StaticCheckCode;
import com.paytech.risk.decision.domain.enums.StaticCheckSeverity;
import com.paytech.risk.decision.domain.enums.StaticCheckStatus;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.DecisionPointVersionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.DecisionReleaseProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.RuleSetVersionProjection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Release 冻结闭包的固定八项纯静态检查。 */
public interface DecisionReleaseStaticValidator {

    List<StaticCheckResult> validate(DecisionReleaseClosure closure);

    record DecisionReleaseClosure(DecisionPointVersionProjection point, RuleSetVersionProjection ruleSet,
                                  DecisionReleaseProjection release, List<PersistedActionVersion> persistedActions) {
        public DecisionReleaseClosure {
            persistedActions = persistedActions == null ? null
                : Collections.unmodifiableList(new ArrayList<>(persistedActions));
        }
    }

    record StaticViolation(String errorCode, String path, String ruleCode, Long nodeId, String actionCode) { }

    record StaticCheckResult(StaticCheckCode checkCode, StaticCheckStatus checkStatus,
                             StaticCheckSeverity severity, String errorCode, String errorSummary) { }
}
