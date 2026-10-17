package com.paytech.foundation.web.request;

import java.util.regex.Pattern;

import com.paytech.foundation.common.error.InvalidParameterException;

/**
 * Operations 入口的严格元数据解析实现；由 {@code WebAutoConfiguration} 注册，宿主可替换。
 *
 * @author Claude
 */
public final class OperationRequestContextParserImpl implements OperationRequestContextParser {

    private static final Pattern POSITIVE_ID = Pattern.compile("[1-9][0-9]{0,18}");

    private static final Pattern UPPER_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    private static final Pattern SAFE_PARAMETER_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,63}");

    private static final int MAX_SESSION_ID_LENGTH = 128;

    /**
     * 解析 Actor 与 Session；任一不合法即拒绝。
     */
    @Override
    public OperationRequestContext parse(String actorAccountId, String sessionId) {
        long actorId = parsePositiveId("actorAccountId", actorAccountId);
        if (!isSessionId(sessionId)) {
            throw invalid("sessionId");
        }
        return new OperationRequestContext(actorId, sessionId);
    }

    /**
     * 只接受无前导零、不超过 long 的正十进制整数。
     */
    @Override
    public long parsePositiveId(String parameterName, String rawValue) {
        if (rawValue == null || !POSITIVE_ID.matcher(rawValue).matches()) {
            throw invalid(parameterName);
        }
        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException exception) {
            throw invalid(parameterName);
        }
    }

    /**
     * 只接受大写字母开头、由大写字母数字下划线组成、最长 64 的 Code。
     */
    @Override
    public String parseRequiredUpperCode(String parameterName, String rawValue) {
        if (rawValue == null || !UPPER_CODE.matcher(rawValue).matches()) {
            throw invalid(parameterName);
        }
        return rawValue;
    }

    /**
     * null 原样返回，否则按必填 Code 校验。
     */
    @Override
    public String parseOptionalUpperCode(String parameterName, String rawValue) {
        return rawValue == null ? null : parseRequiredUpperCode(parameterName, rawValue);
    }

    /**
     * Session：1..128 个可打印 ASCII，首尾不能是空格。
     */
    private static boolean isSessionId(String sessionId) {
        if (sessionId == null || sessionId.isEmpty() || sessionId.length() > MAX_SESSION_ID_LENGTH
            || sessionId.charAt(0) == ' ' || sessionId.charAt(sessionId.length() - 1) == ' ') {
            return false;
        }
        for (int index = 0; index < sessionId.length(); index++) {
            char value = sessionId.charAt(index);
            if (value < 0x20 || value > 0x7e) {
                return false;
            }
        }
        return true;
    }

    /**
     * 参数名不合法时只报告通用名，避免把调用方输入回显到错误详情。
     */
    private static InvalidParameterException invalid(String parameterName) {
        String safeName = parameterName != null && SAFE_PARAMETER_NAME.matcher(parameterName).matches()
            ? parameterName
            : "parameter";
        return new InvalidParameterException(safeName + " 格式无效");
    }
}
