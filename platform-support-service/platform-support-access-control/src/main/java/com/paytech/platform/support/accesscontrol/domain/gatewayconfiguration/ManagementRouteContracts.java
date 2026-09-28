package com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationProjection.*;

/** Platform 管理 API 已部署的最小传输合同；不包含 Target、转发地址或 Gateway 路由开关。 */
public final class ManagementRouteContracts {
    private static final Map<String, Contract> CONTRACTS = load();

    private ManagementRouteContracts() {}

    /** 固定协议要求独立于当前可编辑配置，允许移除后来额外添加的 Header/Query。 */
    public static void requireTransport(RouteRow route, List<HeaderRow> headers, List<QueryParameterRow> queries) {
        Contract contract = contract(route);
        if (contract == null) throw new BizCheckFailException("管理 API 传输合同尚未登记，不能在线修改");
        boolean completeHeaders = contract.headers().stream().allMatch(h -> headers.stream()
            .anyMatch(current -> h.headerName().equals(current.headerName()) && h.headerUse().equals(current.headerUse())));
        boolean completeQuery = contract.queryParameters().stream().allMatch(q -> queries.stream().anyMatch(current -> q.equals(current.parameterName())));
        if (!completeHeaders || !completeQuery) throw new BizCheckFailException("内置管理 Route 不能移除合同需要的 Header 或 Query");
    }

    /** Body 以部署端点的合法请求容量为下限，不将人工曾经调高的容量永久冻结。 */
    public static void requireBody(RouteRow route) {
        if (!GatewayManagementProtection.protectedRoute(route)) return;
        Contract contract = contract(route);
        if (contract == null || route.bodyLimitBytes() < contract.minimumBodyBytes())
            throw new BizCheckFailException("内置管理 Route 的 Body 容量不足以接收已部署的合法请求");
    }

    private static Contract contract(RouteRow route) { return CONTRACTS.get(route.httpMethod() + " " + route.downstreamPathTemplate()); }

    private static Map<String, Contract> load() {
        try (var input = new ClassPathResource("operations/management-route-contracts.json").getInputStream()) {
            return Arrays.stream(JsonMapper.builder().build().readValue(input, Contract[].class))
                .collect(Collectors.toUnmodifiableMap(c -> c.httpMethod() + " " + c.path(), c -> c));
        } catch (java.io.IOException failure) { throw new IllegalStateException("管理 API 传输合同加载失败", failure); }
    }

    /** 只记录端点维护时不能移除的字段和容量，不用于匹配业务请求。 */
    public record Contract(String httpMethod, String path, long minimumBodyBytes, List<Header> headers, List<String> queryParameters) {}

    public record Header(String headerName, String headerUse) {}
}
