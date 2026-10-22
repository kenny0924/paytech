package com.paytech.channel.connector.protocol.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 外部 API 配置 JSON 列的规范化编解码与变更摘要；纯内存，不访问数据库。 */
@Component
@AllArgsConstructor
public class ExternalApiConfigurationCodec {

    private final ObjectMapper json;

    /** 出站地址按保存顺序序列化为 [{endpointCode, baseUrl, sandbox}]；sandbox 只在为 true 时写出。 */
    public String endpointsJson(List<EndpointDefinition> endpoints) {
        var rows = new ArrayList<LinkedHashMap<String, Object>>();
        for (var endpoint : endpoints) {
            var row = new LinkedHashMap<String, Object>();
            row.put("endpointCode", endpoint.getEndpointCode());
            row.put("baseUrl", endpoint.getBaseUrl());
            if (endpoint.isSandbox()) {
                row.put("sandbox", true);
            }
            rows.add(row);
        }
        return json.writeValueAsString(rows);
    }

    /** 解析已保存的出站地址；未配置（null）为空列表，结构不符视为数据损坏。 */
    public List<EndpointDefinition> endpoints(String value) {
        if (value == null) {
            return List.of();
        }
        JsonNode root = json.readTree(value);
        if (root == null || !root.isArray()) {
            throw new ErrorException("渠道出站地址数据损坏");
        }
        var endpoints = new ArrayList<EndpointDefinition>();
        for (JsonNode node : root) {
            JsonNode code = node.get("endpointCode");
            JsonNode url = node.get("baseUrl");
            JsonNode sandbox = node.get("sandbox");
            if (code == null || url == null || !code.isString() || !url.isString()
                || sandbox != null && !sandbox.isBoolean()) {
                throw new ErrorException("渠道出站地址数据损坏");
            }
            endpoints.add(new EndpointDefinition(code.asString(), url.asString(), sandbox != null && sandbox.booleanValue()));
        }
        return endpoints;
    }

    /** 脚本参数声明序列化为 [{name, dataType, required}]。 */
    public String parametersJson(List<ParameterDeclaration> parameters) {
        var rows = new ArrayList<LinkedHashMap<String, Object>>();
        for (var parameter : parameters) {
            var row = new LinkedHashMap<String, Object>();
            row.put("name", parameter.getName());
            row.put("dataType", parameter.getDataType());
            row.put("required", parameter.isRequired());
            rows.add(row);
        }
        return json.writeValueAsString(rows);
    }

    /** 解析已保存的脚本参数声明；结构不符视为数据损坏。 */
    public List<ParameterDeclaration> parameters(String value) {
        JsonNode root = json.readTree(value);
        if (root == null || !root.isArray()) {
            throw new ErrorException("公共脚本参数声明数据损坏");
        }
        var parameters = new ArrayList<ParameterDeclaration>();
        for (JsonNode node : root) {
            if (!node.path("name").isString() || !node.path("dataType").isString() || !node.path("required").isBoolean()) {
                throw new ErrorException("公共脚本参数声明数据损坏");
            }
            parameters.add(new ParameterDeclaration(node.get("name").asString(), node.get("dataType").asString(),
                node.get("required").asBoolean()));
        }
        return parameters;
    }

    /** 已保存的原子 API 定义中按顺序引用的脚本编码（去重）。 */
    public java.util.Set<String> scriptCodes(String definitionJson) {
        var codes = new java.util.LinkedHashSet<String>();
        for (JsonNode node : tree(definitionJson).path("scripts")) {
            codes.add(node.path("scriptCode").asString(""));
        }
        return codes;
    }

    /** 已保存的流程中引用某原子 API 的节点标识，按出现顺序。 */
    public List<String> nodesUsing(String flowJson, String apiCode) {
        var nodes = new ArrayList<String>();
        for (JsonNode node : tree(flowJson).path("nodes")) {
            if (apiCode.equals(node.path("integrationApi").asString(""))) {
                nodes.add(node.path("id").asString(""));
            }
        }
        return nodes;
    }

    /** 从已保存的原子 API 定义提取流程依赖的合同；定义在保存时已通过结构校验。 */
    public ApiContract apiContract(String code, String name, List<FieldDefinition> requestFields,
        String definitionJson) {
        JsonNode definition = tree(definitionJson);
        var fixedKeys = new java.util.LinkedHashSet<String>();
        for (String key : definition.path("fixedValues").propertyNames()) {
            fixedKeys.add(key.startsWith("HEADER:") ? "HEADER:" + key.substring(7).toLowerCase(java.util.Locale.ROOT)
                : key);
        }
        var outputs = new java.util.LinkedHashMap<String, List<ParameterDeclaration>>();
        for (String branch : List.of("SUCCESS", "FAIL")) {
            outputs.put(branch, declarations(definition.path("outputs").path(branch)));
        }
        var cardKeys = new java.util.LinkedHashSet<String>();
        definition.path("cardFields").forEach(key -> cardKeys.add(key.asString("")));
        var credentialKeys = new java.util.LinkedHashSet<String>();
        definition.path("credentialFields").forEach(key -> credentialKeys.add(key.asString("")));
        return new ApiContract(code, name, requestFields, fixedKeys, declarations(definition.path("params")), outputs,
            cardKeys, credentialKeys);
    }

    /** 读取 [{name, dataType, required}] 形状的声明数组。 */
    List<ParameterDeclaration> declarations(JsonNode array) {
        var result = new ArrayList<ParameterDeclaration>();
        for (JsonNode node : array) {
            result.add(new ParameterDeclaration(node.path("name").asString(""), node.path("dataType").asString(""),
                node.path("required").asBoolean(false)));
        }
        return result;
    }

    /** 解析已保存的 JSON 列。 */
    public JsonNode tree(String value) {
        JsonNode root = json.readTree(value);
        if (root == null) {
            throw new ErrorException("外部 API 配置 JSON 数据损坏");
        }
        return root;
    }

    /** 字段组按顺序的规范化文本摘要，供变更记录写入摘要而不写入完整定义。 */
    public String fieldsDigest(List<FieldDefinition> fields) {
        var rows = new ArrayList<List<Object>>();
        for (var field : fields) {
            rows.add(java.util.Arrays.asList(field.getLocation().getCode(), field.getPath(), field.getName(),
                field.getDataType().getCode(), field.isRequired(), field.getDescription()));
        }
        return sha256(json.writeValueAsString(rows));
    }

    /** 任意规范化文本的 SHA-256 小写十六进制摘要。 */
    public String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new ErrorException("运行环境缺少 SHA-256", failure);
        }
    }
}
