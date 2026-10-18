package com.paytech.platform.support.integration.domainservice.validator;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.platform.support.integration.core.configuration.ExternalApiExecutionProperties;

import lombok.AllArgsConstructor;

import com.paytech.platform.support.integration.domain.EndpointDefinition;
import com.paytech.platform.support.integration.domain.ValidationIssue;

/** 接入方出站地址的静态规则（物理设计第 4 节 P5）；纯内存校验，允许的协议来自执行配置（默认只有 https）。 */
@Component
@AllArgsConstructor
public class IntegrationEndpointValidator {

    /** 每个接入方最多出站地址数。 */
    public static final int MAX_ENDPOINTS = 16;

    final ExternalApiExecutionProperties properties;

    /**
     * 校验接入方的全部出站地址。
     *
     * @param endpoints 保存顺序的地址列表
     * @return 发现的问题；为空表示通过
     */
    public List<ValidationIssue> validate(List<EndpointDefinition> endpoints) {
        var issues = new ArrayList<ValidationIssue>();
        if (endpoints.isEmpty() || endpoints.size() > MAX_ENDPOINTS) {
            issues.add(new ValidationIssue("ENDPOINT_COUNT_INVALID", "endpoints", "出站地址必须为 1～16 项"));
            return issues;
        }
        var codes = new HashSet<String>();
        for (int index = 0; index < endpoints.size(); index++) {
            var endpoint = endpoints.get(index);
            if (!codes.add(endpoint.getEndpointCode())) {
                issues.add(new ValidationIssue("ENDPOINT_DUPLICATED", "endpoints[" + index + "].endpointCode",
                    "地址代码重复"));
            }
            String problem = baseUrlProblem(endpoint.getBaseUrl());
            if (problem != null) {
                issues.add(new ValidationIssue("ENDPOINT_URL_INVALID", "endpoints[" + index + "].baseUrl", problem));
            }
        }
        return issues;
    }

    /** 只允许配置协议（默认 https）且带主机名的地址，可带路径前缀，不允许用户信息、查询串和片段；返回 null 表示合法。 */
    String baseUrlProblem(String value) {
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException failure) {
            return "基础地址不是合法 URL";
        }
        if (uri.getScheme() == null || !properties.getAllowedSchemes().contains(uri.getScheme())) {
            return "基础地址只允许 " + String.join("、", properties.getAllowedSchemes());
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return "基础地址必须包含主机名";
        }
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            return "基础地址不能包含用户信息、查询串或片段";
        }
        return null;
    }
}
