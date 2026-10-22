package com.paytech.platform.support.dataquery.template;

import java.util.List;
import java.util.Map;

import com.paytech.platform.support.dataquery.core.enums.QueryParameterType;

import lombok.Builder;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

/**
 * 一个模板参数的声明（parameters_json 的一项），也用于描述 LIST 元素与 OBJECT 字段。
 *
 * <p>约束是否适用于该类型由保存校验检查；min、max 以文本保存，按类型解析。</p>
 */
@Getter
@Builder(toBuilder = true)
public final class QueryParameterDeclaration {

    /** 参数名，不能以 __ 开头。 */
    private final String name;

    /** 类型。 */
    private final QueryParameterType type;

    /** LIST 的元素类型；其他类型为 null。 */
    private final QueryParameterType elementType;

    /** 是否必填；缺失且无默认值时报错。 */
    private final boolean required;

    /** 默认值，JSON 形态与调用方传入相同；没有时为 null。 */
    private final JsonNode defaultValue;

    /** STRING 的最大长度；没有时为 null。 */
    private final Integer maxLength;

    /** INTEGER、DECIMAL、DATE、DATETIME 的下限（含）；没有时为 null。 */
    private final String min;

    /** INTEGER、DECIMAL、DATE、DATETIME 的上限（含）；没有时为 null。 */
    private final String max;

    /** STRING 或 ENUM 的取值范围；ENUM 必填；没有时为 null。 */
    private final List<String> allowedValues;

    /** LIST 的最大元素数，不超过系统上限；没有时取系统上限。 */
    private final Integer maxSize;

    /** FRAGMENT 的“取值 → 固定片段原文”，保持声明顺序；其他类型为 null。 */
    private final Map<String, String> choices;

    /** LIST(OBJECT) 元素的字段；其他类型为 null。 */
    private final List<QueryParameterDeclaration> fields;

    /**
     * LIST 元素的描述：类型取 elementType，沿用本声明的值约束与字段。
     *
     * @return 元素声明
     */
    public QueryParameterDeclaration elementDeclaration() {
        return toBuilder().type(elementType).elementType(null).required(true).defaultValue(null).maxSize(null)
            .build();
    }

    /**
     * 按名称查找 OBJECT 字段。
     *
     * @param fieldName 字段名
     * @return 字段声明；不存在时为 null
     */
    public QueryParameterDeclaration field(String fieldName) {
        if (fields == null) {
            return null;
        }
        return fields.stream().filter(field -> field.getName().equals(fieldName)).findFirst().orElse(null);
    }
}
