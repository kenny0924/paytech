package com.paytech.risk.decision.domain.release;

import com.paytech.risk.decision.domain.enums.StaticCheckCode;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator.StaticCheckResult;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator.StaticViolation;
import java.util.List;

/**
 * 只向纯单元测试开放违规五元组排序和摘要边界。
 */
public final class DecisionReleaseStaticValidatorTestAccess {

    private DecisionReleaseStaticValidatorTestAccess() {
    }

    public static StaticCheckResult summarize(StaticCheckCode code, List<StaticViolation> violations) {
        return DecisionReleaseStaticValidatorImpl.summarize(code, violations);
    }
}
