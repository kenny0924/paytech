package com.paytech.operations.console.bff.integration.impl;

import java.util.Set;

import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;

import tools.jackson.databind.json.JsonMapper;

/**
 * 外部 API 配置的 422 字段路径投影：保留以本次请求体顶层属性开头的完整路径，说明统一为安全文案。
 *
 * <p>与通用配置投影不同，流程与原子 API 定义的路径段包含 {@code BODY:amount}、{@code HEADER:Content-Type}
 * 这类以冒号、连字符组成的键，无法逐段在请求体中定位；这里只核对根属性、长度与可见字符，不执行或拼接远端文本。
 * 具体问题说明由各校验接口以 200 结果返回。
 */
final class ExternalApiValidationConverter {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String MESSAGE = "请检查此处配置";
    private static final int MAX_PATH = 512;
    private static final int MAX_ERRORS = 100;

    private ExternalApiValidationConverter() {
    }

    /** 只保留属于本次请求体的路径，去重排序后最多 100 条。 */
    static RequestBodyValidationException local(Object body, HttpRequestValidationException failure) {
        Set<String> roots = Set.copyOf(JSON.valueToTree(body).propertyNames());
        var errors = failure.fieldPaths().stream().filter(path -> belongs(path, roots)).distinct().sorted()
            .limit(MAX_ERRORS).map(path -> new ProblemFieldError(path, MESSAGE)).toList();
        return new RequestBodyValidationException(errors, failure);
    }

    /** 路径非空、长度受限、只含可见 ASCII，且首段是请求体的顶层属性。 */
    static boolean belongs(String path, Set<String> roots) {
        if (path == null || path.isEmpty() || path.length() > MAX_PATH
            || !path.chars().allMatch(character -> character > 0x20 && character < 0x7f)) {
            return false;
        }
        int end = path.length();
        int dot = path.indexOf('.');
        int bracket = path.indexOf('[');
        if (dot >= 0) {
            end = dot;
        }
        if (bracket >= 0 && bracket < end) {
            end = bracket;
        }
        return roots.contains(path.substring(0, end));
    }
}
