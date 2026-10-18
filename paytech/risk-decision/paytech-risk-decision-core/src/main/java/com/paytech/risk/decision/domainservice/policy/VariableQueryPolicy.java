package com.paytech.risk.decision.domainservice.policy;

import java.util.*;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.VariableSource;
import com.paytech.risk.decision.domain.enums.VariableStatus;
import com.paytech.risk.decision.domain.enums.VariableUsage;
import com.paytech.risk.decision.domain.variable.DecisionSourceContracts;
import com.paytech.risk.decision.domain.variable.RiskVariable;

/** 目录互斥模式、用途上下文和字面搜索的 Owner 规则。 */
public final class VariableQueryPolicy {
    /** 仅提供纯查询规则。 */
    private VariableQueryPolicy() { }
    /** 列表请求互斥及用途上下文在入站边界一次校验。 */
    public static RiskVariable.Query query(String keyword, String source, String type, String status, String point, String usage,
        String nodeType, String codes, Integer page, Integer size) {
        List<String> selectedCodes = null;
        if (codes != null) {
            if (Arrays.asList(keyword, source, type, status, point, usage, nodeType, page, size).stream().anyMatch(Objects::nonNull)) {
                throw new InvalidParameterException("codes 批取与筛选或分页互斥");
            }
            selectedCodes = Arrays.stream(codes.split(",", -1)).map(VariableQueryPolicy::code).toList();
            if (selectedCodes.size() > 100 || new HashSet<>(selectedCodes).size() != selectedCodes.size()) {
                throw new InvalidParameterException("批取最多 100 个不重复变量编码");
            }
        }
        validateUsage(point, usage, nodeType);
        return new RiskVariable.Query(pattern(keyword), parse(VariableSource.class, source), parse(DecisionValueType.class, type),
            parse(VariableStatus.class, status), point, parse(VariableUsage.class, usage), nodeType, selectedCodes,
            number(page, 1, 10000), number(size, 20, 100));
    }

    /** 条件用途必须携带受支持点，节点输出仅通过准确节点类型筛选。 */
    static void validateUsage(String point, String usage, String nodeType) {
        if (point != null && !DecisionSourceContracts.supports(code(point))) { throw new InvalidParameterException("决策点尚未支持"); }
        if (usage != null && point == null) { throw new InvalidParameterException("条件用途须指定 pointCode"); }
        if (nodeType != null && (!"FLOW_CONDITION".equals(usage) || !Set.of("START", "RULE_SET").contains(nodeType))) {
            throw new InvalidParameterException("nodeType 仅用于流程出线条件");
        }
    }

    /** 字面包含搜索转义 SQL 通配符，不接受控制字符。 */
    public static String pattern(String keyword) {
        if (keyword == null) { return null; }
        if (keyword.codePointCount(0, keyword.length()) > 128 || keyword.codePoints().anyMatch(Character::isISOControl)) {
            throw new InvalidParameterException("搜索关键字无效");
        }
        return "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
    /** 本合同页大小为 1–100，查询范围只补缺省，不截断用户输入。 */
    public static int number(Integer value, int defaultValue, int maximum) {
        int result = value == null ? defaultValue : value;
        if (result < 1 || result > maximum) { throw new InvalidParameterException("分页参数超出允许范围"); }
        return result;
    }
    /** 规范编码在进入资源锁方法之前完成验证。 */
    static String code(String value) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,63}")) throw new InvalidParameterException("variableCode 格式无效");
        return value;
    }

    /** 精确工程枚举解析，不允许未知代码静默退为默认。 */
    static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null) return null;
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException failure) { throw new InvalidParameterException("变量代码值无效：" + type.getSimpleName()); }
    }
}
