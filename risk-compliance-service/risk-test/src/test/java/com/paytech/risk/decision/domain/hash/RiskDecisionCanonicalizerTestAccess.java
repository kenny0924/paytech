package com.paytech.risk.decision.domain.hash;

import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.CanonicalHash;
import java.util.List;

/**
 * 只向纯单元测试开放内部 JCS Writer 的协议判别向量。
 */
public final class RiskDecisionCanonicalizerTestAccess {

    private RiskDecisionCanonicalizerTestAccess() {
    }

    public static CanonicalHash canonicalizeKeyValueObject(List<KeyValue> values) {
        return RiskDecisionCanonicalizerImpl.canonicalizeTestObject(values.stream()
            .map(value -> new RiskDecisionCanonicalizerImpl.TestProperty(value.key(), value.value())).toList());
    }

    public record KeyValue(String key, String value) {
    }
}
