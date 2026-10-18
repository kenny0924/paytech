package com.paytech.risk.decision.domainservice.policy;

import static com.paytech.risk.decision.domainservice.service.RiskVariableException.Reason.*;
import com.paytech.risk.decision.domain.variable.*;
import com.paytech.risk.decision.domain.variable.RiskVariable.*;
import com.paytech.risk.decision.domain.enums.*;
import com.paytech.risk.decision.domainservice.service.RiskVariableException;
import java.util.*;

/** 变量管理的纯值规范，不访问数据库或外部服务。 */
public final class VariableManagementPolicy {
    /** 仅提供无状态规范。 */
    private VariableManagementPolicy() { }

    /** 金额币种和可选集合是仅有的变量依赖。 */
    public static Set<String> dependencies(Definition definition) {
        Set<String> codes = new TreeSet<>();
        if (definition.currencyVariableCode() != null) { codes.add(definition.currencyVariableCode()); }
        if (definition.allowedValuesVariableCode() != null) { codes.add(definition.allowedValuesVariableCode()); }
        return codes;
    }

    /** 只有常量拥有当前标量；其他来源不得混入值。 */
    public static Object currentValue(Definition definition, Object value) {
        if (definition.sourceType() == VariableSource.CONSTANT) {
            return VariableValueNormalizer.normalize(definition.valueType(), definition.formatCode(), value);
        }
        if (value != null) { throw invalid("非 CONSTANT 的 currentValue 必须为空"); }
        return null;
    }

    /** 单个错误或重复值均拒绝整批，不在服务器静默丢弃条目。 */
    public static List<Member> normalizeMembers(Definition definition, List<Member> members) {
        if (members == null || members.size() > 1000) { throw invalid("集合最多 1000 项"); }
        Set<String> keys = new HashSet<>();
        List<Member> result = new ArrayList<>();
        for (Member member : members) {
            if (member == null) { throw invalid("成员不可为空"); }
            Object value = VariableValueNormalizer.normalize(definition.valueType(), definition.formatCode(), member.value());
            if (!keys.add(VariableValueNormalizer.key(definition.valueType(), value))) { throw invalid("成员包含重复规范值"); }
            result.add(new Member(value, text(member.displayName(), 255, true), result.size() + 1));
        }
        return List.copyOf(result);
    }

    /** 展示文案去首尾空白；不清洗成员业务值。 */
    public static String text(String value, int max, boolean nullable) {
        if (value == null && nullable) { return null; }
        if (value == null || value.strip().isEmpty() || value.strip().codePointCount(0, value.strip().length()) > max
            || value.codePoints().anyMatch(Character::isISOControl)) { throw invalid("名称或说明长度、字符无效"); }
        return value.strip();
    }

    /** 构造有明确消费者的变量业务结果。 */
    public static RiskVariableException failure(RiskVariableException.Reason reason, String message) { return new RiskVariableException(reason, message); }

    /** 定义错误保留在用户可编辑的请求边界。 */
    public static RiskVariableException invalid(String message) { return failure(INVALID_VARIABLE_DEFINITION, message); }
}
