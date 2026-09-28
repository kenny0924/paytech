package com.paytech.risk.configuration;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.api.decision.request.SaveFlowRequestBody;
import com.paytech.risk.decision.domain.variable.VariableOperandJson;
import com.paytech.risk.decision.domainservice.service.RiskVariableException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 只限制流程协议：有界 Body、精确键/标量和无 Body 查询，不替代图业务校验。 */
@Configuration(proxyBeanMethods = false)
public class DecisionFlowRequestConfiguration implements WebMvcConfigurer {
    private final JsonMapper mapper;

    public DecisionFlowRequestConfiguration(JsonMapper mapper) {
        this.mapper = mapper.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build();
    }

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(list -> list.addFirst(new FlowRequestConverter(mapper)));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
                String path = request.getRequestURI();
                Set<String> keys = Set.of();
                if (request.getMethod().equals("GET")) {
                    if (path.endsWith("/authoring-context")) keys = Set.of("pointCode");
                    else if (path.endsWith("/flows")) keys = Set.of("pointCode", "flowCode", "keyword", "page", "size");
                    else if (path.endsWith("/versions")) keys = Set.of("page", "size", "maxVersionNo");
                    else if (path.endsWith("/rule-set-reference")) keys = Set.of("sourceType", "sourceVersionId", "nodeKey");
                } else if (request.getMethod().equals("DELETE")) keys = Set.of("expectedVersion");
                for (var parameter : request.getParameterMap().entrySet()) {
                    if (!keys.contains(parameter.getKey()) || parameter.getValue().length != 1 || parameter.getValue()[0].isEmpty())
                        throw new InvalidParameterException("Query 参数未知、重复或为空");
                    if (Set.of("page", "size", "maxVersionNo").contains(parameter.getKey())
                        && !parameter.getValue()[0].matches("[1-9][0-9]{0,9}"))
                        throw new InvalidParameterException("Query 数值格式无效");
                }
                if ((request.getMethod().equals("GET") || request.getMethod().equals("DELETE"))
                    && request.getInputStream().read() != -1) throw new InvalidParameterException("此请求不接受 Body");
                return true;
            }
        }).addPathPatterns("/decision-flows/v1/**");
    }

    private static final class FlowRequestConverter extends JacksonJsonHttpMessageConverter {
        private FlowRequestConverter(JsonMapper mapper) { super(mapper); }

        @Override
        public boolean canRead(ResolvableType type, MediaType mediaType) {
            return type.resolve() == SaveFlowRequestBody.class && super.canRead(type, mediaType);
        }

        @Override
        public Object read(ResolvableType type, HttpInputMessage input, Map<String, Object> hints) throws IOException {
            byte[] bytes = input.getBody().readNBytes(65537);
            if (bytes.length > 65536) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
            try (var parser = getMapper().createParser(bytes)) {
                JsonNode tree = getMapper().readTree(parser);
                if (parser.nextToken() != null) throw new IllegalArgumentException();
                shape(tree);
                return getMapper().treeToValue(tree, SaveFlowRequestBody.class);
            } catch (JacksonException | IllegalArgumentException | RiskVariableException failure) {
                throw new HttpMessageNotReadableException("流程请求不符合 JSON 字段或类型合同", input);
            }
        }

        @Override
        protected Object readInternal(Class<?> type, HttpInputMessage input) throws IOException {
            return read(ResolvableType.forClass(type), input, Map.of());
        }

        @Override
        public boolean canWrite(ResolvableType type, Class<?> valueClass, MediaType mediaType) { return false; }

        private static void shape(JsonNode value) {
            keys(value, "pointCode", "flowCode", "flowName", "baseFlowVersionId", "expectedVersion", "changeReason", "graph");
            for (String field : ListFields.SAVE_TEXT) text(value.get(field), false);
            text(value.get("baseFlowVersionId"), true); text(value.get("expectedVersion"), true);
            JsonNode graph = value.get("graph"); keys(graph, "schemaVersion", "nodes", "edges"); integer(graph.get("schemaVersion"));
            array(graph.get("nodes")); array(graph.get("edges"));
            for (JsonNode node : graph.get("nodes")) {
                keys(node, "nodeKey", "type", "name", "position", "ruleSetVersionId", "decisionCode", "reasonCode", "reasonMessage");
                for (String field : Set.of("nodeKey", "type", "name")) text(node.get(field), false);
                for (String field : Set.of("ruleSetVersionId", "decisionCode", "reasonCode", "reasonMessage")) text(node.get(field), true);
                keys(node.get("position"), "x", "y");
                text(node.get("position").get("x"), false); text(node.get("position").get("y"), false);
            }
            for (JsonNode edge : graph.get("edges")) {
                keys(edge, "edgeKey", "sourceNodeKey", "targetNodeKey", "type", "priority", "name", "condition");
                for (String field : Set.of("edgeKey", "sourceNodeKey", "targetNodeKey", "type", "name")) text(edge.get(field), false);
                integer(edge.get("priority"));
                if (!edge.get("condition").isNull()) {
                    keys(edge.get("condition"), "schemaVersion", "root"); integer(edge.get("condition").get("schemaVersion"));
                    int version = edge.get("condition").get("schemaVersion").intValue();
                    if (version != 1 && version != 2) throw new IllegalArgumentException();
                    expression(edge.get("condition").get("root"), 1, new int[]{0}, version);
                }
            }
        }

        private static void expression(JsonNode node, int depth, int[] count, int version) {
            // 传输保护略宽于业务 8/32 上限，使可解析的超限草稿仍能得到静态问题报告。
            if (node == null || !node.isObject() || depth > 16 || ++count[0] > 256) throw new IllegalArgumentException();
            text(node.get("kind"), false);
            if (node.get("kind").stringValue().equals("GROUP")) {
                keys(node, "kind", "operator", "children"); text(node.get("operator"), false); array(node.get("children"));
                for (JsonNode child : node.get("children")) expression(child, depth + 1, count, version);
            } else if (node.get("kind").stringValue().equals("PREDICATE")) {
                if (version == 2) {
                    keys(node, "kind", "operator", "operands"); text(node.get("operator"), false); array(node.get("operands"));
                    for (JsonNode operand : node.get("operands")) VariableOperandJson.read(operand);
                    return;
                }
                keys(node, "kind", "source", "operator", "values"); keys(node.get("source"), "type", "fieldCode");
                text(node.get("source").get("type"), false); text(node.get("source").get("fieldCode"), false);
                text(node.get("operator"), false); array(node.get("values"));
                for (JsonNode value : node.get("values")) if (!value.isString() && !value.isBoolean()) throw new IllegalArgumentException();
            } else throw new IllegalArgumentException();
        }

        private static void keys(JsonNode node, String... keys) {
            if (node == null || !node.isObject() || !node.propertyNames().equals(Set.of(keys))) throw new IllegalArgumentException();
        }

        private static void array(JsonNode node) { if (node == null || !node.isArray()) throw new IllegalArgumentException(); }

        private static void integer(JsonNode node) {
            if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) throw new IllegalArgumentException();
        }

        private static void text(JsonNode node, boolean nullable) {
            if (node == null || !(node.isString() || nullable && node.isNull())) throw new IllegalArgumentException();
        }
    }

    private static final class ListFields {
        private static final Set<String> SAVE_TEXT = Set.of("pointCode", "flowCode", "flowName", "changeReason");
        private ListFields() { }
    }
}
