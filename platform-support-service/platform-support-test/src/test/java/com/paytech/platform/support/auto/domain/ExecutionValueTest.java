package com.paytech.platform.support.auto.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.platform.support.api.enums.ApiFieldDataType;
import com.paytech.platform.support.api.enums.ApiFieldLocation;
import com.paytech.platform.support.integration.domain.FieldDefinition;
import com.paytech.platform.support.integration.domain.execution.ExecutionErrorException;
import com.paytech.platform.support.integration.domain.execution.FieldSchema;
import com.paytech.platform.support.integration.domain.execution.FieldSchemaViolation;
import com.paytech.platform.support.integration.domainservice.execution.ValueResolver;
import com.paytech.platform.support.integration.domainservice.expression.ExpressionEngine;
import com.paytech.platform.support.integration.domainservice.function.MoneyMinorUnitsFunction;
import com.paytech.platform.support.test.builder.ExternalApiSamples;

/**
 * 执行取值规则（流程结构第 5 节）：标准 API 输入按字段结构校验并转换，取值绑定的缺失、null、类型与精度规则，
 * 以及 MONEY_MINOR_UNITS 的无损换算。直接调用公开的结构、取值器与函数，不经过 HTTP。
 *
 * @see FieldSchema#fromJson(tools.jackson.databind.JsonNode, String)
 * @see ValueResolver#resolve(tools.jackson.databind.JsonNode, Map, String, boolean, String)
 */
class ExecutionValueTest {

    private static final String STRING = ApiFieldDataType.STRING.getCode();
    private static final String INTEGER = ApiFieldDataType.INTEGER.getCode();
    private static final String DECIMAL = ApiFieldDataType.DECIMAL.getCode();

    private final ValueResolver resolver = new ValueResolver(new ExpressionEngine(),
        List.of(new MoneyMinorUnitsFunction()));

    /** 输入结构：DECIMAL 字符串转为十进制，INTEGER 为 64 位整数，未声明字段、缺少必填、null 与格式不符都被拒绝。 */
    @Test
    void test_inputSchema() {
        var schema = FieldSchema.of(List.of(field("order", ApiFieldDataType.OBJECT, true),
            field("order.amount", ApiFieldDataType.DECIMAL, true), field("order.count", ApiFieldDataType.INTEGER, false),
            field("order.items", ApiFieldDataType.ARRAY, false), field("order.items[].sku", ApiFieldDataType.STRING, true),
            field("extra", ApiFieldDataType.OBJECT, false)));

        var value = schema.fromJson(ExternalApiSamples.object("""
            {"order":{"amount":"10.50","count":2,"items":[{"sku":"A"}]},"extra":{"any":[1,"x"]}}"""), "");

        assertEquals(Map.of("order", Map.of("amount", new BigDecimal("10.50"), "count", 2L,
            "items", List.of(Map.of("sku", "A"))), "extra", Map.of("any", List.of(1L, "x"))), value);
        assertViolation(schema, "{\"order\":{\"amount\":\"1\",\"unknown\":1}}", "order.unknown");
        assertViolation(schema, "{\"order\":{}}", "order.amount");
        assertViolation(schema, "{\"order\":{\"amount\":null}}", "order.amount");
        assertViolation(schema, "{\"order\":{\"amount\":\"1e3\"}}", "order.amount");
        assertViolation(schema, "{\"order\":{\"amount\":10.5}}", "order.amount");
        assertViolation(schema, "{\"order\":{\"amount\":\"1\",\"count\":\"2\"}}", "order.count");
        assertViolation(schema, "{\"order\":{\"amount\":\"1\",\"items\":[{}]}}", "order.items[0].sku");
    }

    /** PATH：缺失或 null 时非必填省略、必填 ERROR；中途经过非对象同样视为缺失。 */
    @Test
    void test_pathMissingAndNull() {
        var input = new HashMap<String, Object>();
        input.put("present", "v");
        input.put("empty", null);
        Map<String, Object> environment = Map.of("input", input);

        assertEquals("v", resolve("{\"kind\":\"PATH\",\"path\":\"input.present\"}", environment, STRING, true));
        assertNull(resolve("{\"kind\":\"PATH\",\"path\":\"input.missing\"}", environment, STRING, false));
        assertNull(resolve("{\"kind\":\"PATH\",\"path\":\"input.empty\"}", environment, STRING, false));
        assertNull(resolve("{\"kind\":\"PATH\",\"path\":\"input.present.deeper\"}", environment, STRING, false));
        assertThrows(ExecutionErrorException.class,
            () -> resolve("{\"kind\":\"PATH\",\"path\":\"input.empty\"}", environment, STRING, true));
    }

    /** EXPRESSION：类型严格匹配、十进制运算精确，求值异常与 nil 按规则处理。 */
    @Test
    void test_expressionTypes() {
        Map<String, Object> environment = Map.of("input", Map.of("amount", new BigDecimal("0.1"), "count", 3L));

        assertEquals(new BigDecimal("0.3"),
            resolve("{\"kind\":\"EXPRESSION\",\"expression\":\"input.amount + 0.2\"}", environment, DECIMAL, true));
        assertEquals(4L, resolve("{\"kind\":\"EXPRESSION\",\"expression\":\"input.count + 1\"}", environment, INTEGER, true));
        assertThrows(ExecutionErrorException.class,
            () -> resolve("{\"kind\":\"EXPRESSION\",\"expression\":\"input.count\"}", environment, STRING, true));
        assertThrows(ExecutionErrorException.class,
            () -> resolve("{\"kind\":\"EXPRESSION\",\"expression\":\"input.amount\"}", environment, INTEGER, true));
        assertThrows(ExecutionErrorException.class,
            () -> resolve("{\"kind\":\"EXPRESSION\",\"expression\":\"input.count / 0\"}", environment, INTEGER, true));
        assertNull(resolve("{\"kind\":\"EXPRESSION\",\"expression\":\"nil\"}", environment, STRING, false));
    }

    /** MONEY_MINOR_UNITS：按币种精度无损换算，多余的非零小数、未知币种与非法金额一律 ERROR。 */
    @Test
    void test_moneyMinorUnits() {
        assertEquals(1050L, minorUnits("10.50", "AED"));
        assertEquals(1050L, minorUnits("10.500", "AED"));
        assertEquals(1000L, minorUnits("1000", "JPY"));
        assertEquals(10500L, minorUnits("10.5", "KWD"));
        for (String[] invalid : List.of(new String[] {"10.505", "AED"}, new String[] {"10.5", "JPY"},
            new String[] {"1e3", "AED"}, new String[] {"-1", "AED"}, new String[] {"10", "XYZ"},
            new String[] {"10", "aed"}, new String[] {"99999999999999999999", "AED"})) {
            assertThrows(ExecutionErrorException.class, () -> minorUnits(invalid[0], invalid[1]),
                () -> String.join(" ", invalid));
        }
    }

    private Object minorUnits(String amount, String currency) {
        Map<String, Object> environment = Map.of("input", Map.of("amount", amount, "currency", currency));
        return resolve("""
            {"kind":"FUNCTION","function":"%s","args":{
              "amount":{"kind":"PATH","path":"input.amount"},"currency":{"kind":"PATH","path":"input.currency"}},
             "result":"result"}""".formatted(MoneyMinorUnitsFunction.CODE), environment, INTEGER, true);
    }

    private Object resolve(String binding, Map<String, Object> environment, String dataType, boolean required) {
        return resolver.resolve(ExternalApiSamples.object(binding), environment, dataType, required, "value");
    }

    private static void assertViolation(FieldSchema schema, String json, String path) {
        var violation = assertThrows(FieldSchemaViolation.class, () -> schema.fromJson(ExternalApiSamples.object(json), ""));
        assertEquals(path, violation.getPath());
    }

    private static FieldDefinition field(String path, ApiFieldDataType type, boolean required) {
        return new FieldDefinition(ApiFieldLocation.BODY, path, path, type, required, null);
    }
}
