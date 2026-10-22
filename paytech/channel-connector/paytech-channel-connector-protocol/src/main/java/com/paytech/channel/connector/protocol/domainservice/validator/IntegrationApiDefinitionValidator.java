package com.paytech.channel.connector.protocol.domainservice.validator;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.enums.ApiFieldLocation;
import com.paytech.channel.connector.api.enums.ScriptExecutionStage;
import com.paytech.channel.connector.protocol.domain.FieldDefinition;
import com.paytech.channel.connector.protocol.domain.IntegrationApiDefinition;
import com.paytech.channel.connector.protocol.domain.ParameterDeclaration;
import com.paytech.channel.connector.protocol.domain.ScriptSignature;
import com.paytech.channel.connector.protocol.domain.ScriptUse;
import com.paytech.channel.connector.protocol.domain.ValidationIssue;

import lombok.AllArgsConstructor;
import lombok.Getter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 原子 API definition_json 的静态校验（原子 API 结构第 2～8 节、物理设计 P7）。
 *
 * <p>纯内存校验，依赖调用方事先读取的请求字段、渠道地址代码与同渠道脚本签名；返回全部问题。
 */
@Component
@AllArgsConstructor
public class IntegrationApiDefinitionValidator {

    /** 序列化后的最大字节数。 */
    public static final int MAX_BYTES = 65_536;

    /** 调用参数上限。 */
    public static final int MAX_PARAMS = 32;

    /** 脚本引用上限。 */
    public static final int MAX_SCRIPTS = 16;

    /** 状态规则上限。 */
    public static final int MAX_RULES = 32;

    /** 每个输出分支的输出上限。 */
    public static final int MAX_OUTPUTS = 64;

    static final Set<String> TOP_LEVEL = Set.of("params", "fixedValues", "transport", "requestEncoding",
        "responseDecoding", "scripts", "statusRules", "outputs");
    /** 可选顶层属性：decimalEncoding 缺省为 STRING；cardFields、credentialFields 缺省为没有卡字段、凭据字段。 */
    static final Set<String> TOP_LEVEL_ALLOWED = Set.of("params", "fixedValues", "transport", "requestEncoding",
        "responseDecoding", "decimalEncoding", "cardFields", "credentialFields", "scripts", "statusRules", "outputs");

    /** 卡字段上限。 */
    public static final int MAX_CARD_FIELDS = 8;

    /** 凭据字段上限。 */
    public static final int MAX_CREDENTIAL_FIELDS = 4;
    public static final Set<String> DECIMAL_ENCODINGS = Set.of("STRING", "NUMBER");
    static final Set<String> FIELD_TYPES = Set.of("STRING", "BOOLEAN", "INTEGER", "DECIMAL", "OBJECT", "ARRAY");
    public static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    static final Set<String> HTTP_ROOTS = Set.of("http.responseStatus", "http.responseHeaders", "http.responseBody");
    static final String PROTECTED_HANDLE = "PROTECTED_HANDLE";

    private static final Pattern NAME = Pattern.compile("[a-z][A-Za-z0-9]{0,63}");
    private static final Pattern OUTPUT_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private static final Pattern VARIABLE = Pattern.compile("\\{([^{}]*)}");
    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private static final Pattern PATH_LITERAL = Pattern.compile("[A-Za-z0-9\\-._~!$&'()*+,;=:@%]*");

    private final ObjectMapper json;
    private final ValueBindingValidator bindings;

    /**
     * 校验一份原子 API 定义。
     *
     * @param definition 请求体中的 definition 对象
     * @param requestFields 本 API 的请求字段
     * @param endpointCodes 所属渠道的出站地址代码
     * @param scripts 同渠道全部脚本签名，按编码索引
     * @return 问题列表；无问题时附带解析出的定义摘要
     */
    public Result validate(JsonNode definition, List<FieldDefinition> requestFields, Set<String> endpointCodes,
        Map<String, ScriptSignature> scripts) {
        var issues = new ArrayList<ValidationIssue>();
        if (definition == null || !definition.isObject()) {
            issues.add(new ValidationIssue("DEFINITION_INVALID", "definition", "definition 必须是对象"));
            return new Result(issues, null);
        }
        requireKeys(definition, "definition", TOP_LEVEL_ALLOWED, TOP_LEVEL, issues);
        String normalized = json.writeValueAsString(definition);
        if (normalized.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            issues.add(new ValidationIssue("DEFINITION_TOO_LARGE", "definition", "定义序列化后不能超过 65536 字节"));
        }
        var params = params(definition.get("params"), issues);
        var fixedKeys = fixedValues(definition.get("fixedValues"), requestFields, issues);
        var transport = transport(definition.get("transport"), requestFields, endpointCodes, issues);
        encodings(definition, requestFields, issues);
        cardFields(definition, requestFields, fixedKeys, issues);
        credentialFields(definition, requestFields, fixedKeys, issues);
        var scriptUses = scripts(definition.get("scripts"), params, scripts, issues);
        statusRules(definition.get("statusRules"), params, issues);
        var outputs = outputs(definition.get("outputs"), params, issues);
        if (!issues.isEmpty()) {
            return new Result(issues, null);
        }
        return new Result(issues, new IntegrationApiDefinition(params, fixedKeys, transport.get("method"),
            transport.get("endpointCode"), transport.get("pathTemplate"), scriptUses, outputs, normalized));
    }

    /** 调用参数：名称唯一、类型受支持，PROTECTED_HANDLE 只能交给脚本。 */
    List<ParameterDeclaration> params(JsonNode node, List<ValidationIssue> issues) {
        var result = new ArrayList<ParameterDeclaration>();
        if (!array(node, "definition.params", MAX_PARAMS, issues)) {
            return result;
        }
        var names = new HashSet<String>();
        for (int index = 0; index < node.size(); index++) {
            String location = "definition.params[" + index + "]";
            JsonNode item = node.get(index);
            var declaration = declaration(item, location, Set.of("name", "dataType", "required", "description"),
                NAME, withHandle(), issues);
            if (declaration == null) {
                continue;
            }
            if (!names.add(declaration.getName())) {
                issues.add(new ValidationIssue("PARAM_DUPLICATED", location + ".name", "调用参数名称重复"));
            }
            result.add(declaration);
        }
        return result;
    }

    /** 固定值：键必须是本 API 请求字段，值只能是常量表达式。 */
    Set<String> fixedValues(JsonNode node, List<FieldDefinition> requestFields, List<ValidationIssue> issues) {
        var keys = new LinkedHashSet<String>();
        if (node == null || !node.isObject()) {
            issues.add(new ValidationIssue("FIXED_VALUES_INVALID", "definition.fixedValues", "fixedValues 必须是对象"));
            return keys;
        }
        var fieldKeys = requestFields.stream().map(this::fieldKey).collect(java.util.stream.Collectors.toSet());
        for (String key : node.propertyNames()) {
            String location = "definition.fixedValues." + key;
            if (!fieldKeys.contains(normalizeKey(key))) {
                issues.add(new ValidationIssue("FIXED_VALUE_FIELD_UNKNOWN", location, "固定值的键必须是本 API 的请求字段"));
            }
            JsonNode value = node.get(key);
            if (!"EXPRESSION".equals(value.path("kind").asString(""))) {
                issues.add(new ValidationIssue("FIXED_VALUE_NOT_CONSTANT", location, "固定值只能是常量表达式"));
                continue;
            }
            bindings.validate(value, location, false, path -> "固定值不能引用上下文", issues);
            keys.add(normalizeKey(key));
        }
        return keys;
    }

    /** 传输：HTTP、方法、受控地址代码、安全路径模板与超时范围。 */
    Map<String, String> transport(JsonNode node, List<FieldDefinition> requestFields, Set<String> endpointCodes,
        List<ValidationIssue> issues) {
        var result = new LinkedHashMap<String, String>();
        String location = "definition.transport";
        if (node == null || !node.isObject()) {
            issues.add(new ValidationIssue("TRANSPORT_INVALID", location, "transport 必须是对象"));
            return result;
        }
        requireKeys(node, location, Set.of("protocol", "method", "endpointCode", "pathTemplate", "connectTimeoutMs",
            "responseTimeoutMs"), Set.of("protocol", "method", "endpointCode", "pathTemplate"), issues);
        if (!"HTTP".equals(node.path("protocol").asString(""))) {
            issues.add(new ValidationIssue("TRANSPORT_PROTOCOL_INVALID", location + ".protocol", "本期只支持 HTTP"));
        }
        String method = node.path("method").asString("");
        if (!METHODS.contains(method)) {
            issues.add(new ValidationIssue("TRANSPORT_METHOD_INVALID", location + ".method",
                "method 只允许 GET、POST、PUT、PATCH、DELETE"));
        }
        String endpoint = node.path("endpointCode").asString("");
        if (!endpointCodes.contains(endpoint)) {
            issues.add(new ValidationIssue("ENDPOINT_NOT_FOUND", location + ".endpointCode", "渠道没有该出站地址代码"));
        }
        String template = node.path("pathTemplate").asString("");
        pathTemplate(template, requestFields, location + ".pathTemplate", issues);
        timeout(node.get("connectTimeoutMs"), location + ".connectTimeoutMs", 100, 10_000, issues);
        timeout(node.get("responseTimeoutMs"), location + ".responseTimeoutMs", 100, 30_000, issues);
        result.put("method", method);
        result.put("endpointCode", endpoint);
        result.put("pathTemplate", template);
        return result;
    }

    /** 路径模板：单个 / 开头的相对路径，变量与 PATH 字段一一对应。 */
    void pathTemplate(String template, List<FieldDefinition> requestFields, String location,
        List<ValidationIssue> issues) {
        if (!template.startsWith("/") || template.startsWith("//") || template.contains("\\")) {
            issues.add(new ValidationIssue("PATH_TEMPLATE_INVALID", location, "路径模板必须以单个 / 开头，不能含反斜线"));
            return;
        }
        var variables = new LinkedHashSet<String>();
        Matcher matcher = VARIABLE.matcher(template);
        while (matcher.find()) {
            if (!VARIABLE_NAME.matcher(matcher.group(1)).matches() || !variables.add(matcher.group(1))) {
                issues.add(new ValidationIssue("PATH_VARIABLE_INVALID", location, "路径变量名非法或重复：" + matcher.group(1)));
            }
        }
        for (String segment : template.substring(1).split("/", -1)) {
            String literal = VARIABLE.matcher(segment).replaceAll("");
            if (segment.equals(".") || segment.equals("..") || !PATH_LITERAL.matcher(literal).matches()) {
                issues.add(new ValidationIssue("PATH_TEMPLATE_INVALID", location,
                    "路径模板不能包含 scheme、host、查询串、片段或 . / .. 段"));
                return;
            }
        }
        var pathFields = requestFields.stream().filter(field -> field.getLocation() == ApiFieldLocation.PATH)
            .map(FieldDefinition::getPath).collect(java.util.stream.Collectors.toSet());
        if (!pathFields.equals(variables)) {
            issues.add(new ValidationIssue("PATH_VARIABLE_MISMATCH", location,
                "路径变量必须与 PATH 请求字段一一对应：变量 " + variables + "，字段 " + pathFields));
        }
    }

    /** 可选超时：存在时必须是范围内整数。 */
    void timeout(JsonNode node, String location, int minimum, int maximum, List<ValidationIssue> issues) {
        if (node == null) {
            return;
        }
        if (!node.isIntegralNumber() || node.asLong() < minimum || node.asLong() > maximum) {
            issues.add(new ValidationIssue("TIMEOUT_INVALID", location, "超时必须是 " + minimum + "～" + maximum + " 毫秒的整数"));
        }
    }

    /** 编解码取值；NONE 请求编码不允许 BODY 字段；可选 decimalEncoding 决定渠道报文中 DECIMAL 的 JSON 形式。 */
    void encodings(JsonNode definition, List<FieldDefinition> requestFields, List<ValidationIssue> issues) {
        String request = definition.path("requestEncoding").asString("");
        if (!Set.of("JSON", "FORM_URLENCODED", "NONE").contains(request)) {
            issues.add(new ValidationIssue("ENCODING_INVALID", "definition.requestEncoding",
                "requestEncoding 只允许 JSON、FORM_URLENCODED、NONE"));
        } else if ("NONE".equals(request)
            && requestFields.stream().anyMatch(field -> field.getLocation() == ApiFieldLocation.BODY)) {
            issues.add(new ValidationIssue("ENCODING_BODY_CONFLICT", "definition.requestEncoding",
                "requestEncoding 为 NONE 时不能定义 BODY 请求字段"));
        }
        if (!Set.of("JSON", "NONE").contains(definition.path("responseDecoding").asString(""))) {
            issues.add(new ValidationIssue("DECODING_INVALID", "definition.responseDecoding",
                "responseDecoding 只允许 JSON、NONE"));
        }
        if (definition.has("decimalEncoding")
            && !DECIMAL_ENCODINGS.contains(definition.path("decimalEncoding").asString(""))) {
            issues.add(new ValidationIssue("DECIMAL_ENCODING_INVALID", "definition.decimalEncoding",
                "decimalEncoding 只允许 STRING、NUMBER"));
        }
    }

    /**
     * 卡字段（PayerMax 卡直付对接技术方案第 3.2 节）：只能是 JSON 编码下的 BODY 字符串字段（不含数组段），不能有固定值；
     * 这些字段只接受卡值句柄，其他字段不能接收卡值。
     */
    void cardFields(JsonNode definition, List<FieldDefinition> requestFields, Set<String> fixedKeys,
        List<ValidationIssue> issues) {
        JsonNode node = definition.get("cardFields");
        if (node == null || !array(node, "definition.cardFields", MAX_CARD_FIELDS, issues)) {
            return;
        }
        if (!"JSON".equals(definition.path("requestEncoding").asString("")) && !node.isEmpty()) {
            issues.add(new ValidationIssue("CARD_FIELD_ENCODING", "definition.cardFields", "卡字段只能用于 JSON 请求编码"));
        }
        var seen = new HashSet<String>();
        for (int index = 0; index < node.size(); index++) {
            String location = "definition.cardFields[" + index + "]";
            String key = node.get(index).asString("");
            if (!bodyStringField(requestFields, key)) {
                issues.add(new ValidationIssue("CARD_FIELD_INVALID", location, "卡字段必须是本 API 的 BODY 字符串请求字段（不含数组段）"));
            } else if (fixedKeys.contains(key)) {
                issues.add(new ValidationIssue("CARD_FIELD_FIXED", location, "卡字段不能有固定值"));
            } else if (!seen.add(key)) {
                issues.add(new ValidationIssue("CARD_FIELD_DUPLICATED", location, "卡字段重复"));
            }
        }
    }

    /**
     * 凭据字段（Toss Pay 接入技术方案 T1）：规则同卡字段，且不能同时是卡字段；这些字段只接受受控句柄，其他请求体字段不能接收受控句柄。
     */
    void credentialFields(JsonNode definition, List<FieldDefinition> requestFields, Set<String> fixedKeys,
        List<ValidationIssue> issues) {
        JsonNode node = definition.get("credentialFields");
        if (node == null || !array(node, "definition.credentialFields", MAX_CREDENTIAL_FIELDS, issues)) {
            return;
        }
        if (!"JSON".equals(definition.path("requestEncoding").asString("")) && !node.isEmpty()) {
            issues.add(new ValidationIssue("CREDENTIAL_FIELD_ENCODING", "definition.credentialFields", "凭据字段只能用于 JSON 请求编码"));
        }
        var cardKeys = new HashSet<String>();
        definition.path("cardFields").forEach(key -> cardKeys.add(key.asString("")));
        var seen = new HashSet<String>();
        for (int index = 0; index < node.size(); index++) {
            String location = "definition.credentialFields[" + index + "]";
            String key = node.get(index).asString("");
            if (!bodyStringField(requestFields, key)) {
                issues.add(new ValidationIssue("CREDENTIAL_FIELD_INVALID", location, "凭据字段必须是本 API 的 BODY 字符串请求字段（不含数组段）"));
            } else if (fixedKeys.contains(key)) {
                issues.add(new ValidationIssue("CREDENTIAL_FIELD_FIXED", location, "凭据字段不能有固定值"));
            } else if (cardKeys.contains(key)) {
                issues.add(new ValidationIssue("CREDENTIAL_FIELD_CONFLICT", location, "凭据字段不能同时是卡字段"));
            } else if (!seen.add(key)) {
                issues.add(new ValidationIssue("CREDENTIAL_FIELD_DUPLICATED", location, "凭据字段重复"));
            }
        }
    }

    /** 键是本 API 的 BODY 字符串请求字段，路径不含数组段。 */
    boolean bodyStringField(List<FieldDefinition> requestFields, String key) {
        var field = requestFields.stream().filter(item -> fieldKey(item).equals(key)).findFirst();
        return field.isPresent() && field.get().getLocation() == ApiFieldLocation.BODY
            && "STRING".equals(field.get().getDataType().getCode()) && !field.get().getPath().contains("[]");
    }

    /** 脚本引用：同渠道脚本、阶段一致、参数只引用调用参数或常量。 */
    List<ScriptUse> scripts(JsonNode node, List<ParameterDeclaration> params, Map<String, ScriptSignature> scripts,
        List<ValidationIssue> issues) {
        var result = new ArrayList<ScriptUse>();
        if (!array(node, "definition.scripts", MAX_SCRIPTS, issues)) {
            return result;
        }
        var ids = new HashSet<String>();
        for (int index = 0; index < node.size(); index++) {
            String location = "definition.scripts[" + index + "]";
            JsonNode item = node.get(index);
            if (item == null || !item.isObject()) {
                issues.add(new ValidationIssue("SCRIPT_USE_INVALID", location, "脚本引用必须是对象"));
                continue;
            }
            requireKeys(item, location, Set.of("id", "scriptCode", "stage", "params"), Set.of("id", "scriptCode", "stage"),
                issues);
            String id = item.path("id").asString("");
            if (!NAME.matcher(id).matches() || !ids.add(id)) {
                issues.add(new ValidationIssue("SCRIPT_USE_ID_INVALID", location + ".id", "脚本引用标识非法或重复"));
            }
            var stage = ScriptExecutionStage.fromCode(item.path("stage").asString(""));
            ScriptSignature signature = scripts.get(item.path("scriptCode").asString(""));
            if (signature == null) {
                issues.add(new ValidationIssue("SCRIPT_NOT_FOUND", location + ".scriptCode", "同一渠道没有该脚本"));
            } else if (stage != signature.getStage()) {
                issues.add(new ValidationIssue("SCRIPT_STAGE_MISMATCH", location + ".stage", "引用阶段与脚本的执行阶段不一致"));
            } else {
                scriptParams(item.get("params"), location + ".params", signature, params, issues);
            }
            if (stage != null && signature != null) {
                result.add(new ScriptUse(id, signature.getCode(), stage));
            }
        }
        return result;
    }

    /** 脚本参数绑定：声明过的参数、必填齐全，受控句柄只能原样传给受控句柄参数。 */
    void scriptParams(JsonNode node, String location, ScriptSignature signature, List<ParameterDeclaration> params,
        List<ValidationIssue> issues) {
        JsonNode values = node == null ? json.createObjectNode() : node;
        if (!values.isObject()) {
            issues.add(new ValidationIssue("SCRIPT_PARAMS_INVALID", location, "params 必须是对象"));
            return;
        }
        for (String name : values.propertyNames()) {
            var declared = signature.getParameters().stream().filter(item -> item.getName().equals(name)).findFirst();
            if (declared.isEmpty()) {
                issues.add(new ValidationIssue("SCRIPT_PARAM_UNKNOWN", location + "." + name, "脚本没有该参数"));
                continue;
            }
            boolean handle = PROTECTED_HANDLE.equals(declared.get().getDataType());
            JsonNode binding = values.get(name);
            if (handle && !"PATH".equals(binding.path("kind").asString(""))) {
                issues.add(new ValidationIssue("HANDLE_BINDING_INVALID", location + "." + name,
                    "受控句柄参数只能以 PATH 绑定受控句柄调用参数"));
                continue;
            }
            bindings.validate(binding, location + "." + name, false, path -> scriptReference(path, params, handle),
                issues);
        }
        signature.getParameters().stream().filter(item -> item.isRequired() && !values.has(item.getName()))
            .forEach(item -> issues.add(new ValidationIssue("SCRIPT_PARAM_MISSING", location + "." + item.getName(),
                "缺少必填脚本参数")));
    }

    /** 脚本参数只能读取声明过的调用参数；受控句柄与普通参数不能混用。 */
    String scriptReference(String path, List<ParameterDeclaration> params, boolean handle) {
        var declared = param(path, params);
        if (declared == null) {
            return "脚本参数只能引用已声明的 params.<name> 或常量";
        }
        if (PROTECTED_HANDLE.equals(declared.getDataType()) != handle) {
            return handle ? "受控句柄参数必须绑定受控句柄调用参数" : "受控句柄只能传给受控句柄脚本参数";
        }
        return null;
    }

    /** 状态规则：至少一条，第一条为真决定 SUCCESS 或 FAIL，条件只读响应与普通调用参数。 */
    void statusRules(JsonNode node, List<ParameterDeclaration> params, List<ValidationIssue> issues) {
        if (!array(node, "definition.statusRules", MAX_RULES, issues)) {
            return;
        }
        if (node.isEmpty()) {
            issues.add(new ValidationIssue("STATUS_RULES_EMPTY", "definition.statusRules", "至少需要一条状态规则"));
        }
        for (int index = 0; index < node.size(); index++) {
            String location = "definition.statusRules[" + index + "]";
            JsonNode rule = node.get(index);
            if (rule == null || !rule.isObject()) {
                issues.add(new ValidationIssue("STATUS_RULE_INVALID", location, "状态规则必须是对象"));
                continue;
            }
            requireKeys(rule, location, Set.of("condition", "executeStatus"), Set.of("condition", "executeStatus"),
                issues);
            if (!Set.of("SUCCESS", "FAIL").contains(rule.path("executeStatus").asString(""))) {
                issues.add(new ValidationIssue("EXECUTE_STATUS_INVALID", location + ".executeStatus",
                    "executeStatus 只允许 SUCCESS、FAIL；全部不命中固定为 ERROR"));
            }
            String condition = bindings.expressionText(rule.get("condition"), location + ".condition", issues);
            if (condition != null) {
                for (String path : bindings.references(condition)) {
                    String problem = responseReference(path, params);
                    if (problem != null) {
                        issues.add(new ValidationIssue("REFERENCE_NOT_ALLOWED", location + ".condition", problem + "：" + path));
                    }
                }
            }
        }
    }

    /** 输出：只允许 SUCCESS、FAIL 两个分支，名称唯一，取值只读响应与普通调用参数。 */
    Map<String, List<ParameterDeclaration>> outputs(JsonNode node, List<ParameterDeclaration> params,
        List<ValidationIssue> issues) {
        var result = new LinkedHashMap<String, List<ParameterDeclaration>>();
        if (node == null || !node.isObject()) {
            issues.add(new ValidationIssue("OUTPUTS_INVALID", "definition.outputs", "outputs 必须是对象"));
            return result;
        }
        requireKeys(node, "definition.outputs", Set.of("SUCCESS", "FAIL"), Set.of("SUCCESS", "FAIL"), issues);
        for (String branch : List.of("SUCCESS", "FAIL")) {
            result.put(branch, branchOutputs(node.get(branch), "definition.outputs." + branch, params, issues));
        }
        return result;
    }

    /** 单个分支的输出声明与取值。 */
    List<ParameterDeclaration> branchOutputs(JsonNode node, String location, List<ParameterDeclaration> params,
        List<ValidationIssue> issues) {
        var result = new ArrayList<ParameterDeclaration>();
        if (!array(node, location, MAX_OUTPUTS, issues)) {
            return result;
        }
        var names = new HashSet<String>();
        for (int index = 0; index < node.size(); index++) {
            String itemLocation = location + "[" + index + "]";
            JsonNode item = node.get(index);
            var declaration = declaration(item, itemLocation, Set.of("name", "dataType", "required", "value"),
                OUTPUT_NAME, FIELD_TYPES, issues);
            if (declaration == null) {
                continue;
            }
            if (!names.add(declaration.getName())) {
                issues.add(new ValidationIssue("OUTPUT_DUPLICATED", itemLocation + ".name", "同一分支的输出名称重复"));
            }
            bindings.validate(item.get("value"), itemLocation + ".value", true, path -> responseReference(path, params),
                issues);
            result.add(declaration);
        }
        return result;
    }

    /** 状态规则与输出可读 http.responseStatus/Headers/Body 与非受控句柄的调用参数。 */
    String responseReference(String path, List<ParameterDeclaration> params) {
        if (path.startsWith("result")) {
            return null;
        }
        if (path.startsWith("http")) {
            return HTTP_ROOTS.stream().anyMatch(root -> path.equals(root) || path.startsWith(root + ".")
                || path.startsWith(root + "[")) ? null : "只能读取 http.responseStatus、responseHeaders、responseBody";
        }
        var declared = param(path, params);
        if (declared == null) {
            return "只能引用响应或已声明的调用参数";
        }
        return PROTECTED_HANDLE.equals(declared.getDataType()) ? "受控句柄只能传给脚本" : null;
    }

    /** 解析 params.<name> 对应的调用参数声明，路径不是单层参数时返回 null。 */
    ParameterDeclaration param(String path, List<ParameterDeclaration> params) {
        if (!path.startsWith("params.") || path.indexOf('.', 7) >= 0 || path.indexOf('[') >= 0) {
            return null;
        }
        String name = path.substring(7);
        return params.stream().filter(item -> item.getName().equals(name)).findFirst().orElse(null);
    }

    /** 名称、类型、必填组成的声明。 */
    ParameterDeclaration declaration(JsonNode item, String location, Set<String> allowed, Pattern namePattern,
        Set<String> types, List<ValidationIssue> issues) {
        if (item == null || !item.isObject()) {
            issues.add(new ValidationIssue("DECLARATION_INVALID", location, "声明必须是对象"));
            return null;
        }
        var required = new HashSet<>(Set.of("name", "dataType", "required"));
        if (allowed.contains("value")) {
            required.add("value");
        }
        requireKeys(item, location, allowed, required, issues);
        String name = item.path("name").asString("");
        String type = item.path("dataType").asString("");
        boolean valid = true;
        if (!namePattern.matcher(name).matches()) {
            issues.add(new ValidationIssue("DECLARATION_NAME_INVALID", location + ".name", "名称格式非法"));
            valid = false;
        }
        if (!types.contains(type)) {
            issues.add(new ValidationIssue("DECLARATION_TYPE_INVALID", location + ".dataType", "数据类型不受支持：" + type));
            valid = false;
        }
        if (!item.path("required").isBoolean()) {
            issues.add(new ValidationIssue("DECLARATION_REQUIRED_INVALID", location + ".required", "required 必须是布尔值"));
            valid = false;
        }
        if (item.has("description") && !item.get("description").isNull()
            && (!item.get("description").isString() || item.get("description").asString().length() > 512)) {
            issues.add(new ValidationIssue("DECLARATION_DESCRIPTION_INVALID", location + ".description",
                "说明必须是不超过 512 字符的字符串"));
        }
        return valid ? new ParameterDeclaration(name, type, item.get("required").asBoolean()) : null;
    }

    /** 数组且不超过上限。 */
    boolean array(JsonNode node, String location, int maximum, List<ValidationIssue> issues) {
        if (node == null || !node.isArray()) {
            issues.add(new ValidationIssue("ARRAY_REQUIRED", location, "必须是数组"));
            return false;
        }
        if (node.size() > maximum) {
            issues.add(new ValidationIssue("ARRAY_TOO_LARGE", location, "最多 " + maximum + " 项"));
            return false;
        }
        return true;
    }

    /** 拒绝未知属性并要求必填属性存在。 */
    void requireKeys(JsonNode node, String location, Set<String> allowed, Set<String> required,
        List<ValidationIssue> issues) {
        node.propertyNames().stream().filter(name -> !allowed.contains(name)).forEach(name -> issues
            .add(new ValidationIssue("PROPERTY_UNKNOWN", location + "." + name, "不支持的属性")));
        required.stream().filter(name -> !node.has(name)).sorted().forEach(name -> issues
            .add(new ValidationIssue("PROPERTY_MISSING", location + "." + name, "缺少必填属性")));
    }

    /** 参数类型含受控句柄。 */
    Set<String> withHandle() {
        var types = new HashSet<>(FIELD_TYPES);
        types.add(PROTECTED_HANDLE);
        return types;
    }

    /** 请求字段键 &lt;LOCATION&gt;:&lt;path&gt;，HEADER 名称不区分大小写。 */
    String fieldKey(FieldDefinition field) {
        return normalizeKey(field.getLocation().getCode() + ":" + field.getPath());
    }

    /** HEADER 键按小写比较，其他位置保持原样。 */
    String normalizeKey(String key) {
        return key.startsWith("HEADER:") ? "HEADER:" + key.substring(7).toLowerCase(Locale.ROOT) : key;
    }

    /** 校验结果。 */
    @Getter
    @AllArgsConstructor
    public static class Result {

        /** 全部问题。 */
        private final List<ValidationIssue> issues;

        /** 无问题时的定义摘要，否则为 null。 */
        private final IntegrationApiDefinition definition;
    }
}
