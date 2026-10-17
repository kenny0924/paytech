package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.api.enums.RiskVariableErrorCode.VARIABLE_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.function.Executable;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.decision.domain.enums.VariableSource;
import com.paytech.risk.decision.domain.enums.VariableUsage;
import com.paytech.risk.test.builder.decision.VariableBuilder;
import com.paytech.risk.test.builder.decision.VariableBuilder.MemberSpec;
import com.paytech.risk.test.mocker.decision.VariableMocker.MockedVariable;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 变量详情（读取、创建、更新）的 HTTP 响应 Checker
 * <li>逐字段核对详情与期望规格，并要求响应字段集合准确，不允许多出或缺少字段。</li>
 * <li>集合预览为前三个成员；可选值预览最多一百个成员，计数为集合全部成员数。</li>
 *
 * @author Kenny
 */
public final class VariableDetailResponseChecker extends AbstractHttpResponseChecker<VariableDetailResponseChecker> {

    /** 合同时间格式：UTC、固定六位小数。 */
    static final DateTimeFormatter TIME =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

    /** 目录项字段。 */
    static final Set<String> SUMMARY_FIELDS = Set.of("variableCode", "variableName", "sourceType", "valueType", "pointCode",
        "usages", "systemManaged", "status", "version", "numericUnit", "currencyCode", "currencyVariableCode", "formatCode",
        "allowedValuesVariableCode", "currentValue", "memberCount", "previewMembers", "updateTime");

    /** 详情在目录项之外追加的字段。 */
    static final Set<String> DETAIL_FIELDS = Set.of("description", "sourceContractCode", "sourceContractVersion",
        "sourceField", "createBy", "updateBy", "createTime", "valuesAsOf", "allowedValues");

    public VariableDetailResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected VariableDetailResponseChecker self() {
        return this;
    }

    /** 读取或更新成功（200），逐字段核对；options 为关联的可选值集合，没有时为 null。 */
    public VariableDetailResponseChecker assertDetail(String code, VariableBuilder expected, MockedVariable options) {
        assertStatus(200);
        return assertBody(code, expected, options);
    }

    /** 创建成功（201），逐字段核对。 */
    public VariableDetailResponseChecker assertCreated(String code, VariableBuilder expected, MockedVariable options) {
        assertStatus(201);
        return assertBody(code, expected, options);
    }

    /** 读取时刻不早于给定时刻且不晚于当前时间。 */
    public VariableDetailResponseChecker assertValuesAsOfBetween(Instant from) {
        assertBetween("valuesAsOf", body().path("valuesAsOf").asString(), from);
        return this;
    }

    /** 写入结果：创建/更新时间与读取时刻均为本次命令时间，且与库中更新时间一致。 */
    public VariableDetailResponseChecker assertWrittenAt(Instant storedUpdateTime, boolean created) {
        JsonNode body = body();
        String time = time(storedUpdateTime);
        assertAll(() -> assertEquals(time, body.path("updateTime").asString(), "updateTime"),
            () -> assertEquals(time, body.path("valuesAsOf").asString(), "valuesAsOf"),
            () -> {
                if (created) {
                    assertEquals(time, body.path("createTime").asString(), "createTime");
                }
            });
        return this;
    }

    /** 变量不存在。 */
    public VariableDetailResponseChecker assertNotFound() {
        return assertProblem(404, VARIABLE_NOT_FOUND.getCode());
    }

    private VariableDetailResponseChecker assertBody(String code, VariableBuilder expected, MockedVariable options) {
        JsonNode body = body();
        Set<String> fields = new TreeSet<>(SUMMARY_FIELDS);
        fields.addAll(DETAIL_FIELDS);
        List<Executable> checks = new ArrayList<>(summaryChecks(body, code, expected, fields));
        checks.add(() -> assertEquals(expected.description(), text(body, "description"), "description"));
        checks.add(() -> assertEquals(expected.sourceContractCode(), text(body, "sourceContractCode"), "sourceContractCode"));
        checks.add(() -> assertEquals(expected.sourceContractVersion(),
            body.path("sourceContractVersion").isNull() ? null : body.path("sourceContractVersion").asInt(),
            "sourceContractVersion"));
        checks.add(() -> assertEquals(expected.sourceField(), text(body, "sourceField"), "sourceField"));
        checks.add(() -> assertEquals(Long.toString(expected.createBy()), text(body, "createBy"), "createBy"));
        checks.add(() -> assertEquals(Long.toString(expected.updateBy()), text(body, "updateBy"), "updateBy"));
        checks.add(() -> assertTrue(body.path("valuesAsOf").isString(), "valuesAsOf"));
        if (expected.createTime() != null) {
            checks.add(() -> assertEquals(time(expected.createTime()), text(body, "createTime"), "createTime"));
        }
        checks.add(() -> assertAllowedValues(body.path("allowedValues"), options));
        assertAll("变量详情 " + code, checks);
        return this;
    }

    /** 目录项与详情共用的字段断言；期望更新时间为 null 时由写入断言单独核对。 */
    static List<Executable> summaryChecks(JsonNode actual, String code, VariableBuilder e, Set<String> fields) {
        List<Executable> checks = new ArrayList<>();
        checks.add(() -> assertEquals(fields, names(actual), "响应字段集合"));
        checks.add(() -> assertEquals(code, text(actual, "variableCode"), "variableCode"));
        checks.add(() -> assertEquals(e.variableName(), text(actual, "variableName"), "variableName"));
        checks.add(() -> assertEquals(e.sourceType().getCode(), text(actual, "sourceType"), "sourceType"));
        checks.add(() -> assertEquals(e.valueType().getCode(), text(actual, "valueType"), "valueType"));
        checks.add(() -> assertEquals(e.pointCode(), text(actual, "pointCode"), "pointCode"));
        checks.add(() -> assertEquals(e.usages().stream().map(VariableUsage::getCode).sorted().toList(),
            actual.path("usages").valueStream().map(JsonNode::asString).toList(), "usages 按 Code 升序"));
        checks.add(() -> assertEquals(e.systemManaged(), actual.path("systemManaged").asBoolean(), "systemManaged"));
        checks.add(() -> assertTrue(actual.path("systemManaged").isBoolean(), "systemManaged 类型"));
        checks.add(() -> assertEquals(e.status().getCode(), text(actual, "status"), "status"));
        checks.add(() -> assertEquals(Long.toString(e.version()), text(actual, "version"), "version 为字符串"));
        checks.add(() -> assertEquals(e.numericUnit() == null ? null : e.numericUnit().getCode(), text(actual, "numericUnit"),
            "numericUnit"));
        checks.add(() -> assertEquals(e.currencyCode(), text(actual, "currencyCode"), "currencyCode"));
        checks.add(() -> assertEquals(e.currencyVariableCode(), text(actual, "currencyVariableCode"), "currencyVariableCode"));
        checks.add(() -> assertEquals(e.formatCode().getCode(), text(actual, "formatCode"), "formatCode"));
        checks.add(() -> assertEquals(e.allowedValuesVariableCode(), text(actual, "allowedValuesVariableCode"),
            "allowedValuesVariableCode"));
        checks.add(() -> assertValue(e.currentValue(), actual.path("currentValue"), "currentValue"));
        boolean collection = e.sourceType() == VariableSource.COLLECTION;
        checks.add(() -> assertEquals(collection ? Long.valueOf(e.members().size()) : null,
            actual.path("memberCount").isNull() ? null : actual.path("memberCount").asLong(), "memberCount"));
        checks.add(() -> assertMembers(e.members().stream().limit(3).toList(), actual.path("previewMembers"),
            "previewMembers"));
        if (e.updateTime() != null) {
            checks.add(() -> assertEquals(time(e.updateTime()), text(actual, "updateTime"), "updateTime"));
        }
        return checks;
    }

    /** 可选值：编码、名称、版本、全部成员数及最多一百项预览。 */
    static void assertAllowedValues(JsonNode actual, MockedVariable options) {
        if (options == null) {
            assertTrue(actual.isNull(), "allowedValues 应为 null");
            return;
        }
        VariableBuilder spec = options.spec();
        assertAll("allowedValues",
            () -> assertEquals(Set.of("variableCode", "variableName", "version", "memberCount", "previewMembers"),
                names(actual)),
            () -> assertEquals(options.code(), text(actual, "variableCode")),
            () -> assertEquals(spec.variableName(), text(actual, "variableName")),
            () -> assertEquals(Long.toString(spec.version()), text(actual, "version")),
            () -> assertEquals(spec.members().size(), actual.path("memberCount").asLong()),
            () -> assertMembers(spec.members().stream().limit(100).toList(), actual.path("previewMembers"),
                "allowedValues.previewMembers"));
    }

    /** 预览成员只含值与显示名称，按成员顺序。 */
    static void assertMembers(List<MemberSpec> expected, JsonNode actual, String label) {
        assertTrue(actual.isArray(), label + " 应为数组");
        assertEquals(expected.size(), actual.size(), label + " 数量");
        for (int index = 0; index < expected.size(); index++) {
            MemberSpec member = expected.get(index);
            JsonNode item = actual.get(index);
            String position = label + "[" + index + "]";
            assertAll(position, () -> assertEquals(Set.of("value", "displayName"), names(item)),
                () -> assertValue(member.value(), item.path("value"), position + ".value"),
                () -> assertEquals(member.displayName(), text(item, "displayName"), position + ".displayName"));
        }
    }

    /** 当前值只可能为规范字符串、Boolean 或 null，且 JSON 类型必须一致。 */
    static void assertValue(Object expected, JsonNode actual, String label) {
        if (expected == null) {
            assertTrue(actual.isNull(), label + " 应为 null，实际：" + actual);
        } else if (expected instanceof Boolean value) {
            assertTrue(actual.isBoolean(), label + " 应为 Boolean，实际：" + actual);
            assertEquals(value, actual.asBoolean(), label);
        } else {
            assertTrue(actual.isString(), label + " 应为字符串，实际：" + actual);
            assertEquals(expected, actual.asString(), label);
        }
    }

    /** 读取可空字符串字段；字段必须存在。 */
    static String text(JsonNode node, String field) {
        assertTrue(node.has(field), "缺少字段：" + field);
        JsonNode value = node.get(field);
        assertFalse(value.isMissingNode(), "缺少字段：" + field);
        return value.isNull() ? null : value.asString();
    }

    static Set<String> names(JsonNode node) {
        return new TreeSet<>(node.propertyNames());
    }

    static String time(Instant value) {
        return value == null ? null : TIME.format(value);
    }

    /** 时间字符串为合同格式，且位于 [from, 当前] 区间。 */
    static void assertBetween(String label, String value, Instant from) {
        assertTrue(value != null && value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{6}Z"), label + " 格式");
        Instant actual = Instant.parse(value);
        assertFalse(actual.isBefore(from), label + " 早于请求时刻");
        assertFalse(actual.isAfter(Instant.now()), label + " 晚于当前时刻");
    }
}
