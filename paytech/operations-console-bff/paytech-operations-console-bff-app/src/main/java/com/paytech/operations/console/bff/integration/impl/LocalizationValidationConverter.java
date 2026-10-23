package com.paytech.operations.console.bff.integration.impl;

import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;

/**
 * 多语言写接口的 Platform 422 VALIDATION_ERROR 字段投影
 * <li>请求体内的字段（如 translations[i].text）按本次 Body 投影，沿用配置写操作的做法。</li>
 * <li>翻译文件导入的请求体只有文件 ID，Platform 按合同返回 namespaces.{命名空间}.{key}：只保留符合该形态的路径（最多 200 条），
 * 不传播远端文案。</li>
 *
 * @author Claude
 */
final class LocalizationValidationConverter {
    private static final Pattern TRANSLATION_FILE_PATH = Pattern.compile(
        "namespaces\\.[a-z][a-z0-9-]{0,63}\\.[A-Za-z0-9_.-]{1,191}");
    private static final int MAX_ERRORS = 200;
    private static final String TRANSLATION_FILE_MESSAGE = "该条翻译未通过校验";

    private LocalizationValidationConverter() { }

    /** 调用 Platform 写接口；字段校验失败按本次 Body 投影。 */
    static <T> T write(Object request, Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }

    /** 调用翻译文件导入；字段校验失败只保留合同定义的文件内路径。 */
    static <T> T importFile(Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpRequestValidationException failure) {
            throw translationFile(failure);
        }
    }

    /** 文件内路径投影：形态不符、超长与重复的路径丢弃。 */
    static RequestBodyValidationException translationFile(HttpRequestValidationException failure) {
        var errors = failure.fieldPaths().stream()
            .filter(path -> path != null && path.length() <= 270 && TRANSLATION_FILE_PATH.matcher(path).matches())
            .distinct().limit(MAX_ERRORS).map(path -> new ProblemFieldError(path, TRANSLATION_FILE_MESSAGE)).toList();
        return new RequestBodyValidationException(errors, failure);
    }
}
