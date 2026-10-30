package com.paytech.platform.support.dataquery.domainservice.validator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.platform.support.dataquery.core.configuration.DataQueryJointProperties;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyValueType;
import com.paytech.platform.support.dataquery.domain.JointQueryGraph;
import com.paytech.platform.support.dataquery.domain.JointQueryKey;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeDetail;
import com.paytech.platform.support.dataquery.domain.QueryKeyValues;

/**
 * 联合查询一步的输入校验与规范化（technique 8.2 J3），全部问题一次返回 422，field 形如 keys[0].values[3]。
 * <li>depth = 0：恰好 1 个入口键，ACTIVE 且允许作为入口，值不超过 maxEntryValues，逐个按值类型与入口正则整串匹配。</li>
 * <li>depth > 0：键类型不重复且 ACTIVE，每键值不超过 maxValuesPerKey；不做正则校验（值来自上一层结果）。</li>
 * <li>已加载对象（technique 8.7）：未知或停用的键类型忽略，重复键类型合并；值按值类型规范化，不做正则校验。</li>
 * <li>纯内存校验；键类型取自本次请求现读的图。</li>
 */
@Component
public class JointQueryStepValidator {

    private final DataQueryJointProperties properties;

    /**
     * 构造。
     *
     * @param properties 联合查询护栏
     */
    public JointQueryStepValidator(DataQueryJointProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验并规范化本层键值。
     *
     * @param depth 层号
     * @param keys 请求键值
     * @param graph 本次请求的图
     * @return 规范化并去重后的键值
     * @throws RequestBodyValidationException 任一字段不合法
     */
    public List<JointQueryKey> validate(int depth, List<JointQueryKey> keys, JointQueryGraph graph) {
        List<ProblemFieldError> errors = new ArrayList<>();
        if (depth > properties.getMaxDepth() - 1) {
            errors.add(new ProblemFieldError("depth", "层号不能超过 " + (properties.getMaxDepth() - 1)));
        }
        if (depth == 0 && keys.size() != 1) {
            errors.add(new ProblemFieldError("keys", "入口只能有 1 个键类型"));
        }
        List<JointQueryKey> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < keys.size(); index++) {
            JointQueryKey key = keys.get(index);
            String field = "keys[" + index + "]";
            if (!seen.add(key.getKeyType())) {
                errors.add(new ProblemFieldError(field + ".keyType", "键类型重复: " + key.getKeyType()));
                continue;
            }
            JointQueryKey checked = checkKey(depth, key, graph.keyType(key.getKeyType()), field, errors);
            if (checked != null) {
                normalized.add(checked);
            }
        }
        if (!errors.isEmpty()) {
            throw new RequestBodyValidationException(errors, null);
        }
        return List.copyOf(normalized);
    }

    /**
     * 校验并规范化已加载对象。
     *
     * @param loaded 请求中的已加载对象；为 null 表示没有
     * @param graph 本次请求的图
     * @return 键类型 → 规范值；没有时为空映射
     * @throws RequestBodyValidationException 任一值不能按键类型规范化
     */
    public Map<String, Set<String>> validateLoaded(List<JointQueryKey> loaded, JointQueryGraph graph) {
        if (loaded == null || loaded.isEmpty()) {
            return Map.of();
        }
        List<ProblemFieldError> errors = new ArrayList<>();
        Map<String, Set<String>> subjects = new LinkedHashMap<>();
        for (int index = 0; index < loaded.size(); index++) {
            JointQueryKey key = loaded.get(index);
            QueryKeyTypeDetail keyType = graph.keyType(key.getKeyType());
            if (keyType == null || !keyType.active()) {
                continue;
            }
            Set<String> values = subjects.computeIfAbsent(key.getKeyType(), code -> new LinkedHashSet<>());
            for (int position = 0; position < key.getValues().size(); position++) {
                String normalized = QueryKeyValues.normalize(keyType.getValueType(), key.getValues().get(position));
                if (normalized == null) {
                    errors.add(new ProblemFieldError("loadedSubjects[" + index + "].values[" + position + "]",
                        message(keyType, null)));
                    continue;
                }
                values.add(normalized);
            }
        }
        if (!errors.isEmpty()) {
            throw new RequestBodyValidationException(errors, null);
        }
        Map<String, Set<String>> result = new LinkedHashMap<>();
        subjects.forEach((code, values) -> result.put(code, Set.copyOf(values)));
        return Map.copyOf(result);
    }

    /**
     * 一个键：键类型状态、值个数，再逐值规范化。
     *
     * @return 规范化后的键；键类型不可用时为 null
     */
    JointQueryKey checkKey(int depth, JointQueryKey key, QueryKeyTypeDetail keyType, String field,
        List<ProblemFieldError> errors) {
        boolean entry = depth == 0;
        if (keyType == null || !keyType.active() || entry && !keyType.isEntryAllowed()) {
            errors.add(new ProblemFieldError(field + ".keyType", (entry ? "键类型不存在、未启用或不能作为入口: "
                : "键类型不存在或未启用: ") + key.getKeyType()));
            return null;
        }
        int maximum = entry ? properties.getMaxEntryValues() : properties.getMaxValuesPerKey();
        if (key.getValues().size() > maximum) {
            errors.add(new ProblemFieldError(field + ".values", "值不能超过 " + maximum + " 个"));
            return null;
        }
        Pattern pattern = entry && keyType.getValuePattern() != null ? Pattern.compile(keyType.getValuePattern())
            : null;
        Set<String> values = new LinkedHashSet<>();
        for (int index = 0; index < key.getValues().size(); index++) {
            String value = key.getValues().get(index);
            String normalized = QueryKeyValues.normalize(keyType.getValueType(), value);
            if (normalized == null || pattern != null && !pattern.matcher(value).matches()) {
                errors.add(new ProblemFieldError(field + ".values[" + index + "]", message(keyType, normalized)));
                continue;
            }
            values.add(normalized);
        }
        return new JointQueryKey(key.getKeyType(), List.copyOf(values));
    }

    /** 值不合法的说明：优先给出键类型的输入提示。 */
    static String message(QueryKeyTypeDetail keyType, String normalized) {
        if (normalized == null) {
            return keyType.getValueType() == QueryKeyValueType.INTEGER ? keyType.getKeyTypeName()
                + " 必须为 0 到 9223372036854775807 的整数" : keyType.getKeyTypeName() + " 不能为空";
        }
        return keyType.getKeyTypeName() + " 格式不正确" + (keyType.getInputHint() == null ? ""
            : "：" + keyType.getInputHint());
    }
}
