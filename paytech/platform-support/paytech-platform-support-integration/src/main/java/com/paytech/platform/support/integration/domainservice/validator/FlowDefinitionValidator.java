package com.paytech.platform.support.integration.domainservice.validator;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.platform.support.api.enums.ApiFieldLocation;
import com.paytech.platform.support.integration.domain.ApiContract;
import com.paytech.platform.support.integration.domain.FieldDefinition;
import com.paytech.platform.support.integration.domain.FlowGraph;
import com.paytech.platform.support.integration.domain.ParameterDeclaration;
import com.paytech.platform.support.integration.domain.RegisteredFunction;
import com.paytech.platform.support.integration.domain.RegisteredFunctionCatalog;
import com.paytech.platform.support.integration.domain.ValidationIssue;

import lombok.AllArgsConstructor;
import lombok.Getter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 渠道标准 API 实现 flow_definition_json 的静态校验（流程结构第 1～7 节、材料交接第 5 节）。
 *
 * <p>纯内存校验，不执行函数、表达式或外部请求；依赖调用方读取的标准 API 字段与本接入方全部原子 API 合同。返回全部问题。
 */
@Component
@AllArgsConstructor
public class FlowDefinitionValidator {

    public static final int MAX_BYTES = 65_536;
    public static final int MAX_TIMEOUT = 60_000;
    public static final int MAX_INITIALIZERS = 8;
    public static final int MAX_NODES = 16;
    public static final int MAX_RETURNS = 16;
    public static final int MAX_EDGES = 64;
    public static final int MAX_REQUEST_MAPPINGS = 128;
    static final String PROTECTED_HANDLE = "PROTECTED_HANDLE";

    private static final Pattern ID = Pattern.compile("[a-z][A-Za-z0-9]{0,63}");
    private static final Pattern INDEX = Pattern.compile("\\[[^\\]]*]");

    private final ObjectMapper json;
    private final ValueBindingValidator bindings;

    /**
     * 校验一份流程定义。
     *
     * @param flow 请求体中的 flowDefinition 对象
     * @param requestFields 标准 API 请求字段
     * @param responseFields 标准 API 响应字段
     * @param apis 本接入方全部原子 API 合同，按编码索引
     * @return 问题列表；无问题时附带摘要
     */
    public Result validate(JsonNode flow, List<FieldDefinition> requestFields, List<FieldDefinition> responseFields,
        Map<String, ApiContract> apis) {
        var state = new State(requestFields, responseFields, apis);
        if (flow == null || !flow.isObject()) {
            state.issue("FLOW_INVALID", "flowDefinition", "flowDefinition 必须是对象");
            return new Result(state.issues, null);
        }
        state.keys(flow, "flowDefinition", Set.of("timeoutMs", "initializers", "entry", "nodes", "returns", "edges"),
            Set.of("timeoutMs", "initializers", "entry", "nodes", "returns", "edges"));
        String normalized = json.writeValueAsString(flow);
        if (normalized.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            state.issue("FLOW_TOO_LARGE", "flowDefinition", "流程序列化后不能超过 65536 字节");
        }
        timeout(flow.get("timeoutMs"), state);
        collectIds(flow, state);
        initializers(flow.get("initializers"), state);
        nodes(flow.get("nodes"), state);
        returns(flow.get("returns"), state);
        String entry = flow.path("entry").asString("");
        if (!state.nodeApis.containsKey(entry)) {
            state.issue("ENTRY_INVALID", "flowDefinition.entry", "entry 必须是节点标识");
        }
        var edges = edges(flow.get("edges"), state);
        graph(entry, edges, state);
        if (!state.issues.isEmpty()) {
            return new Result(state.issues, null);
        }
        return new Result(state.issues, new Summary(state.nodeApis.size(), state.returnIds.size(),
            List.copyOf(state.initializerFunctions.keySet()), flow.get("timeoutMs").asInt(), normalized,
            Set.copyOf(state.nodeApis.values())));
    }

    /** 总预算为 1～60000 毫秒的整数。 */
    void timeout(JsonNode node, State state) {
        if (node == null || !node.isIntegralNumber() || node.asLong() < 1 || node.asLong() > MAX_TIMEOUT) {
            state.issue("TIMEOUT_INVALID", "flowDefinition.timeoutMs", "timeoutMs 必须是 1～60000 的整数");
        }
    }

    /** 先收集三类标识，保证全局唯一，后续引用检查可以前向查找。 */
    void collectIds(JsonNode flow, State state) {
        var seen = new HashSet<String>();
        for (String section : List.of("initializers", "nodes", "returns")) {
            JsonNode array = flow.get(section);
            if (array == null || !array.isArray()) {
                continue;
            }
            for (int index = 0; index < array.size(); index++) {
                String id = array.get(index).path("id").asString("");
                String location = "flowDefinition." + section + "[" + index + "].id";
                if (!ID.matcher(id).matches()) {
                    state.issue("ID_INVALID", location, "标识必须匹配 [a-z][A-Za-z0-9]{0,63}");
                } else if (!seen.add(id)) {
                    state.issue("ID_DUPLICATED", location, "标识在初始化项、节点与出口中必须全局唯一");
                }
                switch (section) {
                    case "initializers" -> state.initializerFunctions.put(id, array.get(index).path("function").asString(""));
                    case "nodes" -> state.nodeApis.put(id, array.get(index).path("integrationApi").asString(""));
                    default -> state.returnIds.add(id);
                }
            }
        }
    }

    /** 初始化项：已注册的初始化函数，参数完整，依赖只能指向其他初始化项且不成环。 */
    void initializers(JsonNode node, State state) {
        if (!state.array(node, "flowDefinition.initializers", 0, MAX_INITIALIZERS)) {
            return;
        }
        var dependencies = new LinkedHashMap<String, Set<String>>();
        for (int index = 0; index < node.size(); index++) {
            String location = "flowDefinition.initializers[" + index + "]";
            JsonNode item = node.get(index);
            if (!state.object(item, location)) {
                continue;
            }
            state.keys(item, location, Set.of("id", "function", "args", "required"),
                Set.of("id", "function", "args", "required"));
            String id = item.path("id").asString("");
            var function = RegisteredFunctionCatalog.find(item.path("function").asString(""));
            if (function.isEmpty() || !RegisteredFunctionCatalog.INITIALIZER.equals(function.get().getKind())) {
                state.issue("INITIALIZER_FUNCTION_INVALID", location + ".function", "函数未注册或不是初始化函数");
            } else {
                dependencies.put(id, initializerArgs(item.get("args"), location + ".args", id, function.get(), state));
            }
            if (!item.path("required").isBoolean()) {
                state.issue("REQUIRED_INVALID", location + ".required", "required 必须是布尔值");
            }
        }
        var graphEdges = new ArrayList<String[]>();
        dependencies.forEach((id, uses) -> uses.forEach(use -> graphEdges.add(new String[] {use, id})));
        if (new FlowGraph("", dependencies.keySet(), graphEdges).hasCycle()) {
            state.issue("INITIALIZER_CYCLE", "flowDefinition.initializers", "初始化项之间的依赖不能形成环");
        }
    }

    /** 初始化参数只能读取标准 API 输入或其他初始化项的非凭据结果，返回依赖的初始化项。 */
    Set<String> initializerArgs(JsonNode args, String location, String id, RegisteredFunction function, State state) {
        var uses = new LinkedHashSet<String>();
        if (!state.object(args, location)) {
            return uses;
        }
        for (String name : args.propertyNames()) {
            if (function.getArgs().stream().noneMatch(parameter -> parameter.getName().equals(name))) {
                state.issue("FUNCTION_ARG_UNKNOWN", location + "." + name, "函数没有参数 " + name);
                continue;
            }
            bindings.validate(args.get(name), location + "." + name, false, path -> {
                if (path.startsWith("steps")) {
                    return "初始化项在节点之前执行，不能读取 steps";
                }
                String problem = state.contextReference(path, false);
                if (problem == null && path.startsWith("resources.")) {
                    String target = segment(path, 1);
                    uses.add(target);
                    return target.equals(id) ? "初始化项不能引用自身结果" : null;
                }
                return problem;
            }, state.issues);
        }
        function.getArgs().stream().filter(parameter -> parameter.isRequired() && !args.has(parameter.getName()))
            .forEach(parameter -> state.issue("FUNCTION_ARG_MISSING", location + "." + parameter.getName(),
                "缺少必填函数参数"));
        return uses;
    }

    /** 节点：同接入方原子 API，请求与参数绑定合法并覆盖必填项。 */
    void nodes(JsonNode node, State state) {
        if (!state.array(node, "flowDefinition.nodes", 1, MAX_NODES)) {
            return;
        }
        for (int index = 0; index < node.size(); index++) {
            String location = "flowDefinition.nodes[" + index + "]";
            JsonNode item = node.get(index);
            if (!state.object(item, location)) {
                continue;
            }
            state.keys(item, location, Set.of("id", "integrationApi", "request", "params"),
                Set.of("id", "integrationApi", "request"));
            String id = item.path("id").asString("");
            ApiContract api = state.apis.get(item.path("integrationApi").asString(""));
            if (api == null) {
                state.issue("INTEGRATION_API_NOT_FOUND", location + ".integrationApi", "同一接入方没有该原子 API");
                continue;
            }
            nodeRequest(item.get("request"), location + ".request", id, api, state);
            nodeParams(item.get("params"), location + ".params", id, api, state);
        }
    }

    /** 请求键必须是原子 API 请求字段且不与固定值冲突；与固定值合起来覆盖必填字段。 */
    void nodeRequest(JsonNode request, String location, String nodeId, ApiContract api, State state) {
        if (!state.object(request, location)) {
            return;
        }
        if (request.size() > MAX_REQUEST_MAPPINGS) {
            state.issue("REQUEST_MAPPING_LIMIT", location, "单节点请求映射最多 " + MAX_REQUEST_MAPPINGS + " 项");
        }
        var fieldKeys = new HashMap<String, FieldDefinition>();
        api.getRequestFields().forEach(field -> fieldKeys.put(key(field.getLocation().getCode(), field.getPath()), field));
        var assigned = new LinkedHashSet<String>();
        for (String raw : request.propertyNames()) {
            String key = normalizeKey(raw);
            if (!fieldKeys.containsKey(key)) {
                state.issue("REQUEST_FIELD_UNKNOWN", location + "." + raw, "不是该原子 API 的请求字段");
            } else if (api.getFixedValueKeys().contains(key)) {
                state.issue("REQUEST_FIELD_FIXED", location + "." + raw, "该字段已有固定值，不能再由节点赋值");
            }
            assigned.add(key);
            if (api.getCardFieldKeys().contains(key)) {
                cardBinding(request.get(raw), location + "." + raw, state);
                continue;
            }
            if (api.getCredentialFieldKeys().contains(key)) {
                credentialBinding(request.get(raw), location + "." + raw, state);
                continue;
            }
            bindings.validate(request.get(raw), location + "." + raw, true,
                path -> state.nodeReference(path, nodeId, location + "." + raw), state.issues);
        }
        assigned.addAll(api.getFixedValueKeys());
        for (var field : api.getRequestFields()) {
            if (field.isRequired() && !field.getPath().contains("[]")
                && !covered(field.getLocation().getCode(), field.getPath(), assigned)) {
                state.issue("REQUEST_FIELD_UNCOVERED", location,
                    "必填请求字段未赋值：" + field.getLocation().getCode() + ":" + field.getPath());
            }
        }
    }

    /** 调用参数：已声明、必填齐全；受控句柄只能以 PATH 绑定初始化项结果中的凭据。 */
    void nodeParams(JsonNode params, String location, String nodeId, ApiContract api, State state) {
        JsonNode values = params == null ? json.createObjectNode() : params;
        if (!state.object(values, location)) {
            return;
        }
        for (String name : values.propertyNames()) {
            var declared = api.getParams().stream().filter(item -> item.getName().equals(name)).findFirst();
            if (declared.isEmpty()) {
                state.issue("PARAM_UNKNOWN", location + "." + name, "原子 API 没有声明该调用参数");
                continue;
            }
            JsonNode binding = values.get(name);
            if (PROTECTED_HANDLE.equals(declared.get().getDataType())) {
                handleBinding(binding, location + "." + name, state);
                continue;
            }
            bindings.validate(binding, location + "." + name, true,
                path -> state.nodeReference(path, nodeId, location + "." + name), state.issues);
        }
        api.getParams().stream().filter(item -> item.isRequired() && !values.has(item.getName()))
            .forEach(item -> state.issue("PARAM_MISSING", location + "." + item.getName(), "缺少必填调用参数"));
    }

    /** 受控句柄参数：只能是指向已声明初始化项 credentials 的 PATH。 */
    void handleBinding(JsonNode binding, String location, State state) {
        if (!credentialPath(binding, state)) {
            state.issue("HANDLE_BINDING_INVALID", location,
                "受控句柄参数只能以 PATH 绑定 resources.<初始化项>.credentials.<名称>");
        }
    }

    /** 凭据字段（Toss Pay 接入技术方案 T1）：绑定规则同受控句柄参数。 */
    void credentialBinding(JsonNode binding, String location, State state) {
        if (!credentialPath(binding, state)) {
            state.issue("CREDENTIAL_BINDING_INVALID", location,
                "凭据字段只能以 PATH 绑定 resources.<初始化项>.credentials.<名称>");
        }
    }

    /** 绑定是指向已声明初始化项 credentials 的 PATH。 */
    static boolean credentialPath(JsonNode binding, State state) {
        String path = binding == null ? "" : binding.path("path").asString("");
        boolean pathKind = binding != null && binding.isObject() && "PATH".equals(binding.path("kind").asString(""))
            && binding.size() == 2;
        return pathKind && path.startsWith("resources.") && "credentials".equals(segment(path, 2))
            && path.split("\\.").length == 4 && state.initializerFunctions.containsKey(segment(path, 1));
    }

    /** 卡字段（PayerMax 卡直付对接技术方案第 3.2 节）：只能以 PATH 绑定初始化项返回的卡值句柄 resources.&lt;id&gt;.&lt;卡值&gt;。 */
    void cardBinding(JsonNode binding, String location, State state) {
        String path = binding == null ? "" : binding.path("path").asString("");
        boolean pathKind = binding != null && binding.isObject() && "PATH".equals(binding.path("kind").asString(""))
            && binding.size() == 2;
        var function = RegisteredFunctionCatalog.find(state.initializerFunctions.getOrDefault(segment(path, 1), ""));
        if (!pathKind || !path.startsWith("resources.") || path.split("\\.").length != 3 || function.isEmpty()
            || !RegisteredFunctionCatalog.CARD.equals(function.get().getReturnRoots().get(segment(path, 2)))) {
            state.issue("CARD_BINDING_INVALID", location, "卡字段只能以 PATH 绑定 resources.<初始化项>.<卡值>");
        }
    }

    /** 出口：SUCCESS/FAIL 映射标准 API 响应字段并覆盖必填项，ERROR 不写 outputs。 */
    void returns(JsonNode node, State state) {
        if (!state.array(node, "flowDefinition.returns", 1, MAX_RETURNS)) {
            return;
        }
        for (int index = 0; index < node.size(); index++) {
            String location = "flowDefinition.returns[" + index + "]";
            JsonNode item = node.get(index);
            if (!state.object(item, location)) {
                continue;
            }
            state.keys(item, location, Set.of("id", "executeStatus", "outputs"), Set.of("id", "executeStatus"));
            String status = item.path("executeStatus").asString("");
            if (!Set.of("SUCCESS", "FAIL", "ERROR").contains(status)) {
                state.issue("EXECUTE_STATUS_INVALID", location + ".executeStatus", "出口状态只允许 SUCCESS、FAIL、ERROR");
            } else if ("ERROR".equals(status)) {
                if (item.has("outputs")) {
                    state.issue("ERROR_OUTPUTS_NOT_ALLOWED", location + ".outputs", "ERROR 出口不能写 outputs");
                }
            } else {
                returnOutputs(item.get("outputs"), location + ".outputs", item.path("id").asString(""), state);
            }
        }
    }

    /** 出口输出键映射标准 API 响应字段，并覆盖全部必填响应字段。 */
    void returnOutputs(JsonNode outputs, String location, String returnId, State state) {
        if (!state.object(outputs, location)) {
            return;
        }
        var assigned = new LinkedHashSet<String>();
        for (String name : outputs.propertyNames()) {
            String path = state.responsePath(name);
            if (path == null) {
                state.issue("OUTPUT_FIELD_UNKNOWN", location + "." + name, "不是标准 API 的响应字段");
            } else {
                assigned.add(key("BODY", path));
            }
            bindings.validate(outputs.get(name), location + "." + name, true,
                path2 -> state.returnReference(path2, returnId, location + "." + name), state.issues);
        }
        for (var field : state.responseFields) {
            if (field.isRequired() && !field.getPath().contains("[]") && !covered("BODY", field.getPath(), assigned)) {
                state.issue("OUTPUT_FIELD_UNCOVERED", location, "必填响应字段未赋值：" + field.getPath());
            }
        }
    }

    /** 连线：起点为节点、终点为节点或出口；每个节点恰好一条兜底，其余带条件与唯一优先级。 */
    List<String[]> edges(JsonNode node, State state) {
        var result = new ArrayList<String[]>();
        if (!state.array(node, "flowDefinition.edges", 0, MAX_EDGES)) {
            return result;
        }
        var defaults = new HashMap<String, Integer>();
        var priorities = new HashMap<String, Set<Long>>();
        for (int index = 0; index < node.size(); index++) {
            String location = "flowDefinition.edges[" + index + "]";
            JsonNode edge = node.get(index);
            if (!state.object(edge, location)) {
                continue;
            }
            state.keys(edge, location, Set.of("from", "to", "condition", "priority", "default"), Set.of("from", "to"));
            String from = edge.path("from").asString("");
            String to = edge.path("to").asString("");
            if (!state.nodeApis.containsKey(from)) {
                state.issue("EDGE_FROM_INVALID", location + ".from", "连线起点必须是节点");
            }
            if (!state.nodeApis.containsKey(to) && !state.returnIds.contains(to)) {
                state.issue("EDGE_TO_INVALID", location + ".to", "连线终点必须是节点或出口");
            }
            if (edge.path("default").asBoolean(false)) {
                if (edge.has("condition") || edge.has("priority")) {
                    state.issue("DEFAULT_EDGE_INVALID", location, "兜底连线不能带 condition 或 priority");
                }
                defaults.merge(from, 1, Integer::sum);
            } else {
                conditionalEdge(edge, location, from, priorities, state);
            }
            result.add(new String[] {from, to});
        }
        for (String nodeId : state.nodeApis.keySet()) {
            if (defaults.getOrDefault(nodeId, 0) != 1) {
                state.issue("DEFAULT_EDGE_COUNT", "flowDefinition.edges", "节点 " + nodeId + " 必须恰好有一条兜底连线");
            }
        }
        return result;
    }

    /** 条件连线：条件表达式只读输入、非凭据资源与已执行节点结果；优先级为同一起点内唯一的正整数。 */
    void conditionalEdge(JsonNode edge, String location, String from, Map<String, Set<Long>> priorities,
        State state) {
        JsonNode priority = edge.get("priority");
        if (priority == null || !priority.isIntegralNumber() || priority.asLong() < 1) {
            state.issue("PRIORITY_INVALID", location + ".priority", "条件连线必须带正整数 priority");
        } else if (!priorities.computeIfAbsent(from, key -> new HashSet<>()).add(priority.asLong())) {
            state.issue("PRIORITY_DUPLICATED", location + ".priority", "同一起点的 priority 不能重复");
        }
        String condition = bindings.expressionText(edge.get("condition"), location + ".condition", state.issues);
        if (condition == null) {
            return;
        }
        for (String path : bindings.references(condition)) {
            String problem = state.edgeReference(path, from, location + ".condition");
            if (problem != null) {
                state.issue("REFERENCE_NOT_ALLOWED", location + ".condition", problem + "：" + path);
            }
        }
    }

    /** 图结构：全部可达、无环，再按支配节点检查 steps 引用。 */
    void graph(String entry, List<String[]> edges, State state) {
        var vertices = new LinkedHashSet<String>(state.nodeApis.keySet());
        vertices.addAll(state.returnIds);
        var graph = new FlowGraph(entry, vertices, edges);
        if (graph.hasCycle()) {
            state.issue("FLOW_CYCLE", "flowDefinition.edges", "流程不能有环");
            return;
        }
        var reachable = graph.reachable();
        vertices.stream().filter(vertex -> !reachable.contains(vertex)).forEach(vertex -> state.issue("UNREACHABLE",
            "flowDefinition", "从 entry 无法到达：" + vertex));
        var dominators = graph.dominators();
        for (StepUse use : state.stepUses) {
            var dominating = dominators.getOrDefault(use.vertex(), Set.of());
            boolean executed = dominating.contains(use.step()) && (use.inclusive() || !use.step().equals(use.vertex()));
            if (!executed) {
                state.issue("STEP_NOT_ON_ALL_PATHS", use.location(),
                    "节点 " + use.step() + " 不是该位置的必经节点，不能读取 steps." + use.step());
            }
        }
    }

    /** 某个键或其祖先已赋值即视为覆盖。 */
    static boolean covered(String location, String path, Set<String> assigned) {
        String current = path;
        while (true) {
            if (assigned.contains(key(location, current))) {
                return true;
            }
            int dot = current.lastIndexOf('.');
            if (dot < 0) {
                return false;
            }
            current = current.substring(0, dot);
        }
    }

    /** 请求字段键，HEADER 名称小写。 */
    static String key(String location, String path) {
        return normalizeKey(location + ":" + path);
    }

    /** HEADER 键按小写比较，其他位置保持原样。 */
    static String normalizeKey(String key) {
        return key.startsWith("HEADER:") ? "HEADER:" + key.substring(7).toLowerCase(Locale.ROOT) : key;
    }

    /** 点分路径的第 index 段（0 起），不存在返回空串。 */
    static String segment(String path, int index) {
        String[] parts = path.split("\\.");
        return index < parts.length ? INDEX.matcher(parts[index]).replaceAll("") : "";
    }

    /** 一次 steps 引用：在哪个顶点、引用哪个节点，以及该节点自身是否已执行。 */
    record StepUse(String vertex, String step, boolean inclusive, String location) {
    }

    /** 单次校验的累积状态与上下文引用规则。 */
    final class State {
        final List<ValidationIssue> issues = new ArrayList<>();
        final Map<String, String> initializerFunctions = new LinkedHashMap<>();
        final Map<String, String> nodeApis = new LinkedHashMap<>();
        final Set<String> returnIds = new LinkedHashSet<>();
        final List<StepUse> stepUses = new ArrayList<>();
        final List<FieldDefinition> requestFields;
        final List<FieldDefinition> responseFields;
        final Map<String, ApiContract> apis;

        State(List<FieldDefinition> requestFields, List<FieldDefinition> responseFields, Map<String, ApiContract> apis) {
            this.requestFields = requestFields;
            this.responseFields = responseFields;
            this.apis = apis;
        }

        void issue(String code, String location, String message) {
            issues.add(new ValidationIssue(code, location, message));
        }

        /** 节点请求与参数可读输入、非凭据资源及必经节点（不含自身）。 */
        String nodeReference(String path, String nodeId, String location) {
            return path.startsWith("steps") ? step(path, nodeId, false, location) : contextReference(path, false);
        }

        /** 连线条件可读输入、非凭据资源及起点（含起点自身）的必经节点。 */
        String edgeReference(String path, String from, String location) {
            return path.startsWith("steps") ? step(path, from, true, location) : contextReference(path, false);
        }

        /** 出口输出可读输入、非凭据资源及必经节点。 */
        String returnReference(String path, String returnId, String location) {
            return path.startsWith("steps") ? step(path, returnId, false, location) : contextReference(path, false);
        }

        /** input 必须是标准 API 请求字段；resources 必须是已声明初始化项的非凭据返回属性。 */
        String contextReference(String path, boolean allowCredentials) {
            if (path.startsWith("input.")) {
                return inputPath(path.substring(6)) ? null : "不是标准 API 的请求字段";
            }
            if (path.startsWith("resources.")) {
                String id = segment(path, 1);
                var function = RegisteredFunctionCatalog.find(initializerFunctions.getOrDefault(id, ""));
                if (function.isEmpty()) {
                    return "没有该初始化项";
                }
                String root = segment(path, 2);
                if (!function.get().getReturnRoots().containsKey(root)) {
                    return "初始化函数没有返回属性 " + root;
                }
                if (PROTECTED_HANDLE.equals(function.get().getReturnRoots().get(root)) && !allowCredentials) {
                    return "凭据只能以受控句柄传给原子 API 的 PROTECTED_HANDLE 参数";
                }
                if (RegisteredFunctionCatalog.CARD.equals(function.get().getReturnRoots().get(root))) {
                    return "卡值只能以 PATH 赋给原子 API 的卡字段";
                }
                return null;
            }
            return "只能引用 input、resources 或 steps";
        }

        /** steps.&lt;id&gt; 必须是节点，路径只能到 executeStatus 或已声明输出；必经检查延后到图计算之后。 */
        String step(String path, String vertex, boolean inclusive, String location) {
            String id = segment(path, 1);
            String api = nodeApis.get(id);
            if (api == null) {
                return "没有该节点";
            }
            String next = segment(path, 2);
            if (!next.isEmpty() && !next.equals("executeStatus") && !next.equals("outputs")) {
                return "steps 只能读取 executeStatus 或 outputs";
            }
            String output = segment(path, 3);
            ApiContract contract = apis.get(api);
            if (next.equals("outputs") && !output.isEmpty() && contract != null && contract.getOutputs().values().stream()
                .flatMap(List::stream).map(ParameterDeclaration::getName).noneMatch(output::equals)) {
                return "原子 API " + api + " 没有声明输出 " + output;
            }
            stepUses.add(new StepUse(vertex, id, inclusive, location));
            return null;
        }

        /** 路径（下标视为 []）等于或是某个请求字段的祖先。 */
        boolean inputPath(String path) {
            String normalized = INDEX.matcher(path).replaceAll("[]");
            return requestFields.stream().map(FieldDefinition::getPath).anyMatch(field -> field.equals(normalized)
                || field.startsWith(normalized + ".") || field.startsWith(normalized + "[]"));
        }

        /** 出口输出键对应的响应字段路径，键可省略 outputs. 前缀；不存在返回 null。 */
        String responsePath(String name) {
            for (var field : responseFields) {
                if (field.getLocation() == ApiFieldLocation.BODY
                    && (field.getPath().equals(name) || field.getPath().equals("outputs." + name))) {
                    return field.getPath();
                }
            }
            return null;
        }

        /** 数组且数量在范围内。 */
        boolean array(JsonNode node, String location, int minimum, int maximum) {
            if (node == null || !node.isArray()) {
                issue("ARRAY_REQUIRED", location, "必须是数组");
                return false;
            }
            if (node.size() < minimum || node.size() > maximum) {
                issue("ARRAY_SIZE_INVALID", location, "数量必须为 " + minimum + "～" + maximum);
                return false;
            }
            return true;
        }

        /** 必须是对象。 */
        boolean object(JsonNode node, String location) {
            if (node == null || !node.isObject()) {
                issue("OBJECT_REQUIRED", location, "必须是对象");
                return false;
            }
            return true;
        }

        /** 拒绝未知属性并要求必填属性存在。 */
        void keys(JsonNode node, String location, Set<String> allowed, Set<String> required) {
            node.propertyNames().stream().filter(name -> !allowed.contains(name))
                .forEach(name -> issue("PROPERTY_UNKNOWN", location + "." + name, "不支持的属性"));
            required.stream().filter(name -> !node.has(name)).sorted()
                .forEach(name -> issue("PROPERTY_MISSING", location + "." + name, "缺少必填属性"));
        }
    }

    /** 校验结果。 */
    @Getter
    @AllArgsConstructor
    public static class Result {

        /** 全部问题。 */
        private final List<ValidationIssue> issues;

        /** 无问题时的摘要，否则为 null。 */
        private final Summary summary;
    }

    /** 已通过校验的流程摘要。 */
    @Getter
    @AllArgsConstructor
    public static class Summary {

        /** 节点数。 */
        private final int nodeCount;

        /** 出口数。 */
        private final int returnCount;

        /** 初始化项标识，按声明顺序。 */
        private final List<String> initializerIds;

        /** 总预算毫秒数。 */
        private final int timeoutMs;

        /** 规范化 JSON。 */
        private final String normalizedJson;

        /** 引用的原子 API 编码。 */
        private final Set<String> integrationApis;
    }
}
