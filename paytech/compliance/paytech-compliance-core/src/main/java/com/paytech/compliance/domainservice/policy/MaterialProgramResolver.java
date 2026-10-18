package com.paytech.compliance.domainservice.policy;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.compliance.core.configuration.MaterialRequirementsProperties;
import com.paytech.compliance.core.enums.MaterialQualificationType;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 按资格类型解析唯一的通用材料程序编码
 * <li>每类资格一个全局程序，不按国家区分；映射缺失、重复、未知字段或未知资格类型一律 503，不回退为无需材料。</li>
 */
@Component
public class MaterialProgramResolver {
    /** 映射配置最大字符数。 */
    static final int MAX_SOURCE_LENGTH = 65536;

    /** 每行必须且只能包含的字段。 */
    static final Set<String> ROW_FIELDS = Set.of("qualificationType", "programCode");

    private final JsonMapper json;
    private final MaterialRequirementsProperties properties;

    /** 单独构造严格解析器，拒绝重复键与尾随内容，不改变其他业务 JSON 规则。 */
    public MaterialProgramResolver(JsonMapper json, MaterialRequirementsProperties properties) {
        this.json = json.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
        this.properties = properties;
    }

    /** 每次读取当前配置并返回该资格类型的程序编码。 */
    public String resolve(MaterialQualificationType type) {
        try {
            String match = match(rows(properties.getProgramsJson()), type);
            if (match == null) {
                throw new IllegalArgumentException("未配置资格类型的材料程序");
            }
            return match;
        } catch (RuntimeException invalid) {
            throw new ServiceUnavailableException(invalid);
        }
    }

    /** 解析映射数组并校验整体规模。 */
    JsonNode rows(String source) {
        if (source == null || source.isBlank() || source.length() > MAX_SOURCE_LENGTH) {
            throw new IllegalArgumentException("材料程序映射为空或超长");
        }
        JsonNode rows = json.readTree(source);
        if (!rows.isArray() || rows.isEmpty() || rows.size() > MaterialQualificationType.values().length) {
            throw new IllegalArgumentException("材料程序映射必须为非空且有界的数组");
        }
        return rows;
    }

    /** 校验全部行后返回目标资格类型的程序编码；任一行非法都使整份映射失效。 */
    String match(JsonNode rows, MaterialQualificationType type) {
        String match = null;
        Set<String> qualifications = new HashSet<>();
        for (JsonNode row : rows) {
            requireRowShape(row);
            String qualification = row.path("qualificationType").asString();
            String programCode = row.path("programCode").asString();
            if (MaterialQualificationType.ofCode(qualification) == null || !programCode.matches("[A-Z][A-Z0-9_]{0,63}")
                || !qualifications.add(qualification)) {
                throw new IllegalArgumentException("材料程序映射行无效或重复");
            }
            if (type.getCode().equals(qualification)) {
                match = programCode;
            }
        }
        return match;
    }

    /** 行必须为只含两个字符串字段的对象。 */
    void requireRowShape(JsonNode row) {
        if (!row.isObject() || !row.propertyNames().equals(ROW_FIELDS)) {
            throw new IllegalArgumentException("材料程序映射行字段不符合合同");
        }
        for (String field : ROW_FIELDS) {
            if (!row.get(field).isString()) {
                throw new IllegalArgumentException("材料程序映射行字段必须为字符串");
            }
        }
    }
}
