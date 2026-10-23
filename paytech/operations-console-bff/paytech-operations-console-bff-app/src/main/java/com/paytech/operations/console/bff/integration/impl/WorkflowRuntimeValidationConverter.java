package com.paytech.operations.console.bff.integration.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;

import tools.jackson.databind.JsonNode;

/**
 * 工作流运行时（完成动作 #19、发起实例）的 Platform 422 字段错误投影。
 * <li>与配置类接口不同，提交值的键由流程版本的表单定义决定、页面可能完全没有带上（缺必填），因此提交值根下（
 * {@code output.*}、{@code startValues.*}）语法合法的路径即使不在本次请求体内也保留；其余路径仍按
 * {@link ConfigurationValidationConverter} 只保留请求体内存在的路径。</li>
 * <li>字段文案只外显本类登记的 Platform 固定校验文案（逐字相等，或「超过 N 个字符」），它们不含用户数据；
 * 其余远端文案（含文件地址、字段名等数据）一律替换为本地通用提示，不透传。</li>
 */
final class WorkflowRuntimeValidationConverter {
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}(?:\\[(?:0|[1-9][0-9]{0,2})])?");
    private static final Pattern LENGTH_LIMIT = Pattern.compile("超过 [1-9][0-9]{0,5} 个字符");
    private static final String FALLBACK = "请检查此处填写";

    /** Platform 工作流运行时的固定校验文案（ActionOutputChecker、FormValueValidator、AttachmentRules）。 */
    private static final Set<String> SAFE_MESSAGES = Set.of("必填", "至少上传一个文件",
        "文件未通过安全检查，请删除后重新上传", "必须是 JSON 对象", "未定义的字段", "必须是文本", "必须是十进制数字字符串",
        "必须是布尔值", "不是可选值", "必须是 YYYY-MM-DD 日期", "必须是文件地址数组", "文件不能重复",
        "文件地址格式不合法，应为 /files/v1/{fileId}");

    private WorkflowRuntimeValidationConverter() {
    }

    /**
     * 投影 Platform 422：提交值根下的路径全部保留，登记文案透传，其余文案替换为通用提示。
     *
     * @param body 本次页面请求体
     * @param root 提交值在请求体中的属性名（output / startValues）
     * @param failure Platform 422
     * @return 本地字段校验异常
     */
    static RequestBodyValidationException local(Object body, String root, HttpRequestValidationException failure) {
        Map<String, String> messages = new LinkedHashMap<>();
        failure.remoteFieldErrors().forEach(error -> messages.putIfAbsent(error.field(), error.message()));
        failure.fieldPaths().forEach(path -> messages.putIfAbsent(path, null));
        JsonNode wire = ConfigurationValidationConverter.tree(body);
        var errors = messages.keySet().stream()
            .filter(path -> underRoot(path, root) || ConfigurationValidationConverter.exists(wire, path)).sorted()
            .limit(100)
            .map(path -> new ProblemFieldError(path, safeMessage(messages.get(path)))).toList();
        return new RequestBodyValidationException(errors, failure);
    }

    /** 提交值根下、每段都是合法键（可带数组下标）的路径；根本身也算。 */
    static boolean underRoot(String path, String root) {
        if (path == null || path.length() > 256 || !path.startsWith(root)) {
            return false;
        }
        if (path.equals(root)) {
            return true;
        }
        if (path.charAt(root.length()) != '.') {
            return false;
        }
        for (String part : path.substring(root.length() + 1).split("\\.", -1)) {
            if (!SEGMENT.matcher(part).matches()) {
                return false;
            }
        }
        return true;
    }

    /** 登记文案原样返回，否则通用提示。 */
    static String safeMessage(String remote) {
        if (remote != null && (SAFE_MESSAGES.contains(remote) || LENGTH_LIMIT.matcher(remote).matches())) {
            return remote;
        }
        return FALLBACK;
    }
}
