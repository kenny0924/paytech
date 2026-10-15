package com.paytech.risk.decision.domain.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor.InputFieldDescriptor;

/**
 * 风控点统一评估的输入合同：业务身份格式与逐字段规则，由工程登记
 * <li>只做格式层校验；字段语义（如 IP 字面量）由风控点评估器构造输入时再校验。</li>
 * <li>规则可引用的输入字段登记在决策点合同中；构造时核对每个都在本合同内且必填一致，漂移即启动失败。</li>
 *
 * @param pointCode
 *            风控点编码
 * @param contractVersion
 *            输入合同版本
 * @param businessKeyPattern
 *            业务身份格式
 * @param fields
 *            全部输入字段，按登记顺序
 */
public record EvaluationInputContract(String pointCode, int contractVersion, Pattern businessKeyPattern,
    List<Field> fields) {

    public EvaluationInputContract {
        Objects.requireNonNull(pointCode);
        Objects.requireNonNull(businessKeyPattern);
        fields = List.copyOf(fields);
        requireRuleFieldsCovered(pointCode, fields);
    }

    /**
     * 决策点合同中的每个规则输入字段都必须在评估合同内，且必填一致
     *
     * @param pointCode 风控点编码
     * @param fields 评估合同字段
     *         <li>规则字段编码为下划线形式，评估字段为驼峰形式，如 client_id 对应 clientId。</li>
     */
    static void requireRuleFieldsCovered(String pointCode, List<Field> fields) {
        for (InputFieldDescriptor ruleField : DecisionPointAuthoringDescriptor.require(pointCode).inputFields()) {
            String key = camelCase(ruleField.fieldCode());
            Field field = fields.stream().filter(value -> value.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalStateException("评估合同缺少规则输入字段: " + pointCode + "." + key));
            if (field.required() != ruleField.requiredFlag()) {
                throw new IllegalStateException("评估合同与规则输入字段必填不一致: " + pointCode + "." + key);
            }
        }
    }

    /** 下划线编码转驼峰。 */
    static String camelCase(String code) {
        StringBuilder result = new StringBuilder();
        boolean upper = false;
        for (char value : code.toCharArray()) {
            if (value == '_') {
                upper = true;
                continue;
            }
            result.append(upper ? Character.toUpperCase(value) : value);
            upper = false;
        }
        return result.toString();
    }

    /**
     * 输入字段规则
     *
     * @param key
     *            字段编码
     * @param required
     *            是否必填
     * @param pattern
     *            取值格式；为空表示只限长度
     * @param maxLength
     *            最大字符数
     */
    public record Field(String key, boolean required, Pattern pattern, int maxLength) {
    }

    /**
     * 字段级违规
     *
     * @param field
     *            字段路径，如 inputs.amount
     * @param message
     *            可外显的安全说明，不回显原值
     */
    public record Violation(String field, String message) {
    }

    /**
     * 校验业务身份与输入
     *
     * @param businessKey
     *            业务身份
     * @param inputs
     *            输入字段；值为空等同于未提供
     * @return 全部违规，按业务身份、未知字段、合同字段顺序；合法时为空
     */
    public List<Violation> violations(String businessKey, Map<String, String> inputs) {
        List<Violation> result = new ArrayList<>();
        if (businessKey == null || !businessKeyPattern.matcher(businessKey).matches()) {
            result.add(new Violation("businessKey", "业务身份格式无效"));
        }
        List<String> known = fields.stream().map(Field::key).toList();
        inputs.keySet().stream().filter(key -> !known.contains(key)).sorted()
            .forEach(key -> result.add(new Violation("inputs." + key, "未知字段")));
        for (Field field : fields) {
            String value = inputs.get(field.key());
            if (value == null) {
                if (field.required()) {
                    result.add(new Violation("inputs." + field.key(), "必填字段缺失"));
                }
                continue;
            }
            if (value.length() > field.maxLength()
                || field.pattern() != null && !field.pattern().matcher(value).matches()) {
                result.add(new Violation("inputs." + field.key(), "格式无效"));
            }
        }
        return result;
    }
}
