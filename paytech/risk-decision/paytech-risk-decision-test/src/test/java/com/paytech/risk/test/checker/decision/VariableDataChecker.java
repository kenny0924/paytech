package com.paytech.risk.test.checker.decision;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.function.Executable;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.VariableUsage;
import com.paytech.risk.test.builder.decision.VariableBuilder;
import com.paytech.risk.test.builder.decision.VariableBuilder.MemberSpec;
import com.paytech.risk.test.mapper.decision.VariableTestMapper;
import com.paytech.risk.test.mocker.decision.VariableMocker.MockedVariable;

/**
 * 统一变量根与集合成员的数据库 Checker
 * <li>逐列核对变量根，常量值只出现在与类型一致的唯一槽位；成员逐行核对规范键、类型槽位、展示名称与顺序。</li>
 * <li>拒绝的写请求以原夹具规格核对“未改变”，包括版本、审计字段与全部成员。</li>
 *
 * @author Kenny
 */
@TestComponent
public class VariableDataChecker {

    private final VariableTestMapper mapper;

    public VariableDataChecker(VariableTestMapper mapper) {
        this.mapper = mapper;
    }

    /** 编码从未写入数据库。 */
    public void assertAbsent(String code) {
        assertTrue(mapper.selectVariable(code).isEmpty(), "不应写入变量：" + code);
        assertTrue(mapper.selectMembers(code).isEmpty(), "不应写入成员：" + code);
    }

    /** 夹具保持原样：根的全部列及成员均未改变。 */
    public void assertUnchanged(MockedVariable variable) {
        assertStored(variable.code(), variable.spec());
        assertMembers(variable.code(), variable.spec(), variable.spec().updateTime());
    }

    /**
     * 根的全部列与期望一致
     *
     * @param code 变量编码
     * @param expected 期望规格；更新时间为 null 时只要求为合同精度的时间，由调用方另行比对
     * @return 库中的更新时间，供响应时间比对
     */
    public Instant assertStored(String code, VariableBuilder expected) {
        List<Map<String, Object>> rows = mapper.selectVariable(code);
        assertEquals(1, rows.size(), "变量根行数：" + code);
        Map<String, Object> row = rows.getFirst();
        Instant updateTime = instant(row.get("update_time"));
        List<Executable> checks = new ArrayList<>();
        checks.add(() -> assertEquals(expected.variableName(), row.get("variable_name"), "variable_name"));
        checks.add(() -> assertEquals(expected.description(), row.get("description"), "description"));
        checks.add(() -> assertEquals(expected.sourceType().getCode(), row.get("source_type"), "source_type"));
        checks.add(() -> assertEquals(expected.valueType().getCode(), row.get("value_type"), "value_type"));
        checks.add(() -> assertEquals(expected.pointCode(), row.get("point_code"), "point_code"));
        checks.add(() -> assertEquals(expected.sourceContractCode(), row.get("source_contract_code"), "source_contract_code"));
        checks.add(() -> assertEquals(expected.sourceContractVersion(), row.get("source_contract_version"),
            "source_contract_version"));
        checks.add(() -> assertEquals(expected.sourceField(), row.get("source_field"), "source_field"));
        checks.add(() -> assertEquals(expected.usages().contains(VariableUsage.RULE_CONDITION), flag(row, "rule_condition_flag"),
            "rule_condition_flag"));
        checks.add(() -> assertEquals(expected.usages().contains(VariableUsage.FLOW_CONDITION), flag(row, "flow_condition_flag"),
            "flow_condition_flag"));
        checks.add(() -> assertEquals(expected.usages().contains(VariableUsage.ACTION_PARAMETER),
            flag(row, "action_parameter_flag"), "action_parameter_flag"));
        checks.add(() -> assertEquals(expected.systemManaged(), flag(row, "system_managed_flag"), "system_managed_flag"));
        checks.add(() -> assertEquals(expected.formatCode().getCode(), row.get("format_code"), "format_code"));
        checks.add(() -> assertEquals(expected.numericUnit() == null ? null : expected.numericUnit().getCode(),
            row.get("numeric_unit"), "numeric_unit"));
        checks.add(() -> assertEquals(expected.currencyCode(), row.get("currency_code"), "currency_code"));
        checks.add(() -> assertEquals(expected.currencyVariableCode(), row.get("currency_variable_code"),
            "currency_variable_code"));
        checks.add(() -> assertEquals(expected.allowedValuesVariableCode(), row.get("allowed_values_variable_code"),
            "allowed_values_variable_code"));
        checks.add(() -> assertSlots(expected.valueType(), expected.currentValue(), row, "当前值"));
        checks.add(() -> assertEquals(expected.status().getCode(), row.get("status"), "status"));
        checks.add(() -> assertEquals(expected.version(), ((Number) row.get("version")).longValue(), "version"));
        checks.add(() -> assertEquals(expected.createBy(), ((Number) row.get("create_by")).longValue(), "create_by"));
        checks.add(() -> assertEquals(expected.updateBy(), ((Number) row.get("update_by")).longValue(), "update_by"));
        checks.add(() -> assertEquals(expected.createTime() == null ? updateTime : expected.createTime(),
            instant(row.get("create_time")), "create_time"));
        if (expected.updateTime() != null) {
            checks.add(() -> assertEquals(expected.updateTime(), updateTime, "update_time"));
        }
        assertAll("变量根 " + code, checks);
        return updateTime;
    }

    /** 成员行与期望完全一致：数量、顺序号从 1 连续、规范键、类型槽位、展示名称与写入时间。 */
    public void assertMembers(String code, VariableBuilder expected, Instant createTime) {
        List<Map<String, Object>> rows = mapper.selectMembers(code);
        List<MemberSpec> members = expected.members();
        assertEquals(members.size(), rows.size(), "成员数量：" + code);
        for (int index = 0; index < members.size(); index++) {
            MemberSpec member = members.get(index);
            Map<String, Object> row = rows.get(index);
            int sortOrder = index + 1;
            assertAll("成员 " + sortOrder, () -> assertEquals(sortOrder, ((Number) row.get("sort_order")).intValue()),
                () -> assertEquals(member.valueKey(), row.get("value_key"), "value_key"),
                () -> assertSlots(expected.valueType(), member.value(), row, "成员值"),
                () -> assertEquals(member.displayName(), row.get("display_name"), "display_name"),
                () -> assertEquals(createTime, instant(row.get("create_time")), "create_time"));
        }
    }

    /** 期望值只出现在类型对应的槽位，其余四个槽位为空。 */
    static void assertSlots(DecisionValueType type, Object value, Map<String, Object> row, String label) {
        Object string = row.get("string_value");
        Object number = row.get("number_value");
        Object bool = row.get("boolean_value");
        Object datetime = row.get("datetime_value");
        Object duration = row.get("duration_value");
        assertAll(label,
            () -> assertEquals(value != null && type == DecisionValueType.STRING ? value : null, string, "string_value"),
            () -> assertEquals(value != null && type == DecisionValueType.NUMBER ? new BigDecimal((String) value) : null,
                number, "number_value"),
            () -> assertEquals(value != null && type == DecisionValueType.BOOLEAN ? value : null,
                bool == null ? null : flag(bool), "boolean_value"),
            () -> assertEquals(value != null && type == DecisionValueType.DATETIME ? Instant.parse((String) value) : null,
                datetime == null ? null : instant(datetime), "datetime_value"),
            () -> assertEquals(value != null && type == DecisionValueType.DURATION ? Long.valueOf((String) value) : null,
                duration == null ? null : ((Number) duration).longValue(), "duration_value"));
    }

    private static boolean flag(Map<String, Object> row, String column) {
        return flag(row.get(column));
    }

    private static boolean flag(Object value) {
        assertFalse(value == null, "标志列不能为空");
        return value instanceof Boolean bool ? bool : ((Number) value).intValue() == 1;
    }

    /** DATETIME(6) 列按 UTC 会话时区读取。 */
    static Instant instant(Object value) {
        if (value instanceof java.time.LocalDateTime local) {
            return local.toInstant(java.time.ZoneOffset.UTC);
        }
        return ((Timestamp) value).toInstant();
    }
}
