package com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import com.paytech.foundation.common.error.InvalidParameterException;

/** 配置保存的领域命令，不包含会话或传输 DTO。 */
public final class GatewayConfigurationCommands {
    private GatewayConfigurationCommands() {}

    /** expectedVersion 为零仅表示创建，channel 只在创建时传入。 */
    public record TargetWrite(String code, String channel, String discoveryServiceName,
        int expectedVersion, String reason) {
        public TargetWrite {
            code = GatewayConfigurationCommands.code(code);
            reason = GatewayConfigurationCommands.reason(reason);
            discoveryServiceName = text(discoveryServiceName, 255, "discoveryServiceName");
        }
    }

    /** 全量 Route 配置，已规范化子集合可直接用于命令 Hash。 */
    public record RouteWrite(String code, String channel, RouteConfiguration configuration,
        int expectedVersion, String reason) {
        public RouteWrite {
            code = GatewayConfigurationCommands.code(code);
            reason = GatewayConfigurationCommands.reason(reason);
            if (configuration == null) throw new InvalidParameterException("Route 配置不能为空");
        }
    }

    /** 集合为全量替换；规范化排序不能掩盖重复输入。 */
    public record RouteConfiguration(String httpMethod, String externalPathTemplate, String downstreamPathTemplate,
        String targetServiceCode, String securityProfileCode, String authorizationMode, Long permissionId,
        String requiredScopeCode, String requestCookieMode, String responseCookieMode, long bodyLimitBytes,
        int bodyReceiveTimeoutMillis, int responseTimeoutMillis, List<Header> headers,
        List<String> queryParameterNames, List<Guard> guardRules) {
        public RouteConfiguration {
            requiredScopeCode = requiredScopeCode == null || requiredScopeCode.isBlank() ? null : requiredScopeCode;
            if (headers == null || queryParameterNames == null || guardRules == null
                || headers.stream().anyMatch(java.util.Objects::isNull) || queryParameterNames.stream().anyMatch(java.util.Objects::isNull) || guardRules.stream().anyMatch(java.util.Objects::isNull)) {
                throw new InvalidParameterException("Route 子集合必填且不能包含 null");
            }
            headers = headers.stream().sorted(Comparator.comparing(Header::headerUse)
                .thenComparing(Header::headerName)).toList();
            queryParameterNames = queryParameterNames.stream().sorted().toList();
            guardRules = guardRules.stream().sorted(Comparator.comparing(Guard::ruleType)
                .thenComparing(Guard::dimension)).toList();
            if (headers.stream().distinct().count() != headers.size()
                || queryParameterNames.stream().distinct().count() != queryParameterNames.size()
                || guardRules.stream().map(g -> g.ruleType() + ":" + g.dimension()).distinct().count() != guardRules.size()) {
                throw new InvalidParameterException("Route 子集合不能重复");
            }
        }
    }

    /** Header 名称按 HTTP 语义规范化，不读取任何 Header 值。 */
    public record Header(String headerName, String headerUse) {
        public Header {
            headerName = text(headerName, 255, "headerName").toLowerCase(Locale.ROOT);
            headerUse = text(headerUse, 64, "headerUse");
        }
    }

    /** Guard 的数值形状、组合及上限由唯一编译器验证。 */
    public record Guard(String ruleType, String dimension, Long capacity, Long refillTokens,
        Long refillPeriodMillis, Long concurrencyLimit) {
        public Guard {
            ruleType = text(ruleType, 64, "ruleType");
            dimension = text(dimension, 64, "dimension");
        }
    }

    /** 稳定业务编码不允许静默变更大小写。 */
    private static String code(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9-]{2,127}")) {
            throw new InvalidParameterException("配置编码必须为 3 至 128 位小写 kebab-case");
        }
        return value;
    }

    /** 仅对人类说明去除首尾空白。 */
    private static String reason(String value) {
        return text(value == null ? null : value.strip(), 500, "reason");
    }

    /** 必填文本只验证，不改变路径等大小写敏感身份。 */
    private static String text(String value, int limit, String field) {
        if (value == null || value.isBlank() || value.length() > limit) {
            throw new InvalidParameterException(field + " 不能为空或超过长度限制");
        }
        return value;
    }
}
