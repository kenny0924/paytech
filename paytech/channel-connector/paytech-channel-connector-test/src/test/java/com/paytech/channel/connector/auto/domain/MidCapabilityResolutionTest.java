package com.paytech.payment.auto.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import com.paytech.payment.channel.core.configuration.CapabilityDefinition;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.payment.channel.core.dal.dataobject.MidCapability;
import com.paytech.payment.channel.core.dal.dataobject.MidCapabilityDetail;
import com.paytech.payment.channel.core.enums.CapabilityStatus;
import com.paytech.payment.channel.core.enums.CapabilityValueType;
import com.paytech.payment.channel.domain.MidCapabilityContext;
import com.paytech.payment.channel.service.processor.request.MidCriteria;

/**
 * 能力覆盖、歧义与完整交易维度的纯规则测试。
 * @see MidCapabilityContext#resolve(MidCriteria, Map)
 */
class MidCapabilityResolutionTest {
    /** 精确配置的 false 覆盖通配配置的 true。 */
    @Test
    void exactFalseOverridesWildcardTrue() {
        var result = new MidCapabilityContext(List.of(scope(1L, "*", "*"), scope(2L, "VISA", "AE")),
            List.of(value(1L, true), value(2L, false))).resolve(criteria(), catalog());
        assertEquals(1, result.size());
        assertEquals(Boolean.FALSE, result.getFirst().getBooleanValue());
    }

    /** 同等精确度即使值相同也不能随机选取。 */
    @Test
    void ambiguousScopesFailClosed() {
        var context = new MidCapabilityContext(List.of(scope(1L, "VISA", "*"), scope(2L, "*", "AE")),
            List.of(value(1L, true), value(2L, true)));
        assertThrows(ErrorException.class, () -> context.resolve(criteria(), catalog()));
    }

    /** 未知支付体系只能匹配通配规则。 */
    @Test
    void absentSchemeDoesNotMatchConcreteSchemes() {
        var criteria = criteria();
        criteria.setSchemeCode(null);
        assertTrue(new MidCapabilityContext(List.of(scope(1L, "VISA", "AE")),
            List.of(value(1L, true))).resolve(criteria, catalog()).isEmpty());
    }

    /** 每一行必须满足完整条件，不能把不同条件拼成一个许可。 */
    @Test
    void differentMccDoesNotMatch() {
        var scope = scope(1L, "VISA", "AE");
        scope.setMccCode("5999");
        assertTrue(new MidCapabilityContext(List.of(scope), List.of(value(1L, true)))
            .resolve(criteria(), catalog()).isEmpty());
    }

    /** 更精确的唯一结果可以覆盖低层歧义，与数据库行顺序无关。 */
    @Test
    void uniqueMoreSpecificScopeWinsOverLowerTie() {
        var result = new MidCapabilityContext(List.of(scope(1L, "VISA", "*"), scope(2L, "*", "AE"),
            scope(3L, "VISA", "AE")), List.of(value(1L, true), value(2L, true), value(3L, false)))
            .resolve(criteria(), catalog());
        assertEquals(Boolean.FALSE, result.getFirst().getBooleanValue());
    }

    /** 精确组关闭时完全不参与解析，不能把 DISABLED 当作 false。 */
    @Test
    void disabledScopeDoesNotOverrideFallback() {
        var disabled = scope(2L, "VISA", "AE");
        disabled.setCapabilityStatus(CapabilityStatus.DISABLED);
        var result = new MidCapabilityContext(List.of(scope(1L, "*", "*"), disabled),
            List.of(value(1L, true), value(2L, false))).resolve(criteria(), catalog());
        assertEquals(Boolean.TRUE, result.getFirst().getBooleanValue());
    }

    /** 不再接受旧 MCC 通配符，必须先完成数据迁移。 */
    @Test
    void rejectsLegacyMccWildcard() {
        var scope = scope(1L, "*", "*");
        scope.setMccCode("*");
        assertThrows(ErrorException.class, () -> new MidCapabilityContext(List.of(scope), List.of(value(1L, true)))
            .resolve(criteria(), catalog()));
    }

    /** 精确金额区间保留八位精度，颠倒或负数直接拒绝。 */
    @Test
    void validatesDecimalRangeWithoutRounding() {
        var min = value(1L, true);
        min.setCapabilityCode("AMOUNT.MIN");
        min.setValueType(CapabilityValueType.DECIMAL);
        min.setBooleanValue(null);
        min.setDecimalValue(new java.math.BigDecimal("1.00000001"));
        var max = value(1L, true);
        max.setCapabilityCode("AMOUNT.MAX");
        max.setValueType(CapabilityValueType.DECIMAL);
        max.setBooleanValue(null);
        max.setDecimalValue(new java.math.BigDecimal("1.00000002"));
        var definition = new CapabilityDefinition();
        definition.setValueType(CapabilityValueType.DECIMAL);
        var catalog = Map.of("AMOUNT.MIN", definition, "AMOUNT.MAX", definition);
        var context = new MidCapabilityContext(List.of(scope(1L, "*", "*")), List.of(min, max));
        assertEquals(2, context.resolve(criteria(), catalog).size());
        max.setDecimalValue(new java.math.BigDecimal("1.00000000"));
        assertThrows(ErrorException.class, () -> context.resolve(criteria(), catalog));
        min.setDecimalValue(new java.math.BigDecimal("-0.00000001"));
        assertThrows(ErrorException.class, () -> context.resolve(criteria(), catalog));
    }

    /** CODE 类型必须匹配目录值域，非法 Boolean 不得隐式转 true。 */
    @Test
    void validatesCodeAndBooleanValues() {
        var detail = value(1L, true);
        var context = new MidCapabilityContext(List.of(scope(1L, "*", "*")), List.of(detail));
        detail.setBooleanValue(2);
        assertThrows(ErrorException.class, () -> context.resolve(criteria(), catalog()));
        detail.setValueType(CapabilityValueType.CODE);
        detail.setBooleanValue(null);
        detail.setCodeValue("SUPPORTED");
        var definition = new CapabilityDefinition();
        definition.setValueType(CapabilityValueType.CODE);
        definition.setAllowedCodes(java.util.Set.of("SUPPORTED"));
        var catalog = Map.of("ACTION.PAY", definition);
        assertEquals("SUPPORTED", context.resolve(criteria(), catalog).getFirst().getCodeValue());
        detail.setCodeValue("INVALID");
        assertThrows(ErrorException.class, () -> context.resolve(criteria(), catalog));
    }

    /** 注册本例实际使用的能力类型。 */
    Map<String, CapabilityDefinition> catalog() {
        var definition = new CapabilityDefinition();
        definition.setValueType(CapabilityValueType.BOOLEAN);
        return Map.of("ACTION.PAY", definition);
    }

    /** 构造条件组，保持本例其他交易维度相同。 */
    MidCapability scope(long id, String scheme, String country) {
        var scope = new MidCapability();
        scope.setMidCapabilityId(id);
        scope.setMidId(30L);
        scope.setPaymentMethodCode("CARD");
        scope.setSchemeCode(scheme);
        scope.setCountryCode(country);
        scope.setCurrency("AED");
        scope.setMccCode("5734");
        scope.setCapabilityStatus(CapabilityStatus.ENABLED);
        return scope;
    }

    /** 构造代码目录中明确注册的布尔能力。 */
    MidCapabilityDetail value(long scope, boolean enabled) {
        var value = new MidCapabilityDetail();
        value.setMidCapabilityDetailId(scope);
        value.setMidCapabilityId(scope);
        value.setCapabilityCode("ACTION.PAY");
        value.setValueType(CapabilityValueType.BOOLEAN);
        value.setBooleanValue(enabled ? 1 : 0);
        return value;
    }

    /** 构造消费者国家与交易主单位币种条件。 */
    MidCriteria criteria() {
        var criteria = new MidCriteria();
        criteria.setPaymentMethodCode("CARD");
        criteria.setSchemeCode("VISA");
        criteria.setCountryCode("AE");
        criteria.setCurrency("AED");
        criteria.setMccCode("5734");
        return criteria;
    }
}
