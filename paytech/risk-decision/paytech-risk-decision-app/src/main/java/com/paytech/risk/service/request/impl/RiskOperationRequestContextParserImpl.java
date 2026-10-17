package com.paytech.risk.service.request.impl;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.service.request.RiskOperationRequestContext;
import com.paytech.risk.service.request.RiskOperationRequestContextParser;

/** Operations 风控配置入口的严格元数据解析实现。 */
@Component
public final class RiskOperationRequestContextParserImpl implements RiskOperationRequestContextParser {

    private static final Pattern POSITIVE_ID = Pattern.compile("[1-9][0-9]{0,18}");

    private static final Pattern UPPER_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    private static final Pattern SAFE_PARAMETER_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,63}");

    @Override
    public RiskOperationRequestContext parse(String actorAccountId, String sessionId) {
        long actorId = parsePositiveId("actorAccountId", actorAccountId);
        if (!isSessionId(sessionId)) {
            throw invalid("sessionId");
        }
        return new RiskOperationRequestContext(actorId, sessionId);
    }

    @Override
    public long parsePositiveId(String parameterName, String rawValue) {
        if (rawValue == null || !POSITIVE_ID.matcher(rawValue).matches()) {
            throw invalid(parameterName);
        }
        try {
            long value = Long.parseLong(rawValue);
            if (value <= 0) {
                throw invalid(parameterName);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw invalid(parameterName);
        }
    }

    @Override
    public String parseRequiredUpperCode(String parameterName, String rawValue) {
        if (rawValue == null || !UPPER_CODE.matcher(rawValue).matches()) {
            throw invalid(parameterName);
        }
        return rawValue;
    }

    @Override
    public String parseOptionalUpperCode(String parameterName, String rawValue) {
        return rawValue == null ? null : parseRequiredUpperCode(parameterName, rawValue);
    }

    private static boolean isSessionId(String sessionId) {
        if (sessionId == null || sessionId.isEmpty() || sessionId.length() > 128
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

    private static InvalidParameterException invalid(String parameterName) {
        String safeName = parameterName != null && SAFE_PARAMETER_NAME.matcher(parameterName).matches()
            ? parameterName
            : "parameter";
        return new InvalidParameterException(safeName + " 格式无效");
    }
}
