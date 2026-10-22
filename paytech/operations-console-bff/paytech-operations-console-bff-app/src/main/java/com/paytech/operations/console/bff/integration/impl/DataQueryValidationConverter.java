package com.paytech.operations.console.bff.integration.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.web.client.RestClientResponseException;

import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 数据查询 422 字段错误投影到本次页面 Body，保留 Platform 的字段文案
 * <li>模板正文的“第 N 行第 M 列：”定位、参数声明与执行参数的原因由 Platform 生成且承诺可外显，编辑器与试运行表单依赖它定位；
 * 因此与其他配置写入不同，文案不替换为统一提示。</li>
 * <li>路径必须落在本次 Body 内：完整路径存在，或父对象存在而末段字段缺失（如缺少必填参数 params.startDate）。
 * 越界下标、未知顶层字段与畸形路径丢弃；同一路径的多条问题全部保留，最多 100 条，输出顺序由公共 MVC 边界决定。</li>
 * <li>文案为空、超过 512 字符或含控制字符时使用统一提示。</li>
 *
 * @author Claude
 */
final class DataQueryValidationConverter {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern SEGMENT = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)(?:\\[(0|[1-9][0-9]{0,4})])?");
    private static final String FALLBACK_MESSAGE = "请检查此处配置";
    private static final int MAX_ERRORS = 100;
    private static final int MAX_PATH_LENGTH = 256;
    private static final int MAX_MESSAGE_LENGTH = 512;

    /** 只承载转换，禁止创建实例。 */
    private DataQueryValidationConverter() {
    }

    /**
     * 按本次页面 Body 投影 Platform 字段错误
     *
     * @param body 本次提交给 Platform 的页面请求
     * @param failure Platform 合规 422
     * @return 带字段清单的本地校验失败
     */
    static RequestBodyValidationException local(Object body, HttpRequestValidationException failure) {
        JsonNode wire = JSON.valueToTree(body);
        Set<String> paths = Set.copyOf(failure.fieldPaths());
        var errors = new ArrayList<ProblemFieldError>();
        for (ProblemFieldError error : remoteErrors(failure)) {
            if (errors.size() == MAX_ERRORS) {
                break;
            }
            if (paths.contains(error.field()) && locatable(wire, error.field())) {
                errors.add(new ProblemFieldError(error.field(), safeMessage(error.message())));
            }
        }
        return new RequestBodyValidationException(errors, failure);
    }

    /** 读取 Foundation 已校验过形态的远端 errors[]；取不到时只保留路径并使用统一提示。 */
    private static List<ProblemFieldError> remoteErrors(HttpRequestValidationException failure) {
        if (failure.getCause() instanceof RestClientResponseException response) {
            try {
                JsonNode errors = JSON.readTree(response.getResponseBodyAsString()).path("errors");
                var result = new LinkedHashSet<ProblemFieldError>();
                errors.forEach(error -> result.add(new ProblemFieldError(error.path("field").asString(),
                    error.path("message").asString())));
                return List.copyOf(result);
            } catch (JacksonException unreadable) {
                // 已由 Foundation 校验为严格 Problem，读不到时退回只投影路径。
            }
        }
        return failure.fieldPaths().stream().map(path -> new ProblemFieldError(path, FALLBACK_MESSAGE)).toList();
    }

    /** 完整路径存在，或父对象存在而末段字段缺失；只遍历本地序列化树。 */
    private static boolean locatable(JsonNode body, String path) {
        if (path == null || path.isBlank() || path.length() > MAX_PATH_LENGTH) {
            return false;
        }
        String[] parts = path.split("\\.", -1);
        JsonNode current = body;
        for (int i = 0; i < parts.length; i++) {
            var segment = SEGMENT.matcher(parts[i]);
            if (!segment.matches() || current == null || !current.isObject()) {
                return false;
            }
            JsonNode field = current.get(segment.group(1));
            boolean last = i == parts.length - 1;
            if (field == null) {
                return last && segment.group(2) == null && i > 0;
            }
            current = field;
            if (segment.group(2) != null) {
                if (!current.isArray()) {
                    return false;
                }
                current = current.get(Integer.parseInt(segment.group(2)));
                if (current == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 可外显的远端文案；不合格时使用统一提示。 */
    private static String safeMessage(String message) {
        if (message == null || message.isBlank() || message.codePointCount(0, message.length()) > MAX_MESSAGE_LENGTH
            || message.codePoints().anyMatch(Character::isISOControl)) {
            return FALLBACK_MESSAGE;
        }
        return message;
    }
}
