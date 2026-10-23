package com.paytech.operations.console.bff.integration.impl;

import java.util.regex.Pattern;

import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 将远端错误坐标映射到本次实际提交的页面 Body；不传播远端字段文案或未知属性。 */
final class ConfigurationValidationConverter {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern SEGMENT = Pattern.compile("([A-Za-z][A-Za-z0-9]*)(?:\\[(0|[1-9][0-9]{0,2})])?");
    private static final String MESSAGE = "请检查此处配置";

    private ConfigurationValidationConverter() { }

    /** 只保留实际 Body 内的完整路径，显式 null 可定位，越界、未知及畸形路径丢弃。 */
    static RequestBodyValidationException local(Object body, HttpRequestValidationException failure) {
        JsonNode wire = tree(body);
        var errors = failure.fieldPaths().stream().filter(path -> exists(wire, path)).distinct().sorted()
            .limit(100).map(path -> new ProblemFieldError(path, MESSAGE)).toList();
        return new RequestBodyValidationException(errors, failure);
    }

    /** 页面请求体的本地序列化树。 */
    static JsonNode tree(Object body) {
        return JSON.valueToTree(body);
    }

    /** 只遍历本地序列化树，禁止把远端字段文本当作表达式执行或把对象内容拼入响应。 */
    static boolean exists(JsonNode body, String path) {
        if (path == null || path.isBlank() || path.length() > 256) return false;
        JsonNode current = body;
        for (String part : path.split("\\.", -1)) {
            var segment = SEGMENT.matcher(part);
            if (!segment.matches() || current == null || !current.isObject()) return false;
            current = current.get(segment.group(1));
            if (current == null) return false;
            if (segment.group(2) != null) {
                if (!current.isArray()) return false;
                current = current.get(Integer.parseInt(segment.group(2)));
                if (current == null) return false;
            }
        }
        return true;
    }
}
