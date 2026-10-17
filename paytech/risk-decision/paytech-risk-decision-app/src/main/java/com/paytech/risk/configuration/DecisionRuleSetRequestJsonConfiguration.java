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
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.paytech.risk.api.decision.request.CreateRuleSetVersionRequestBody;
import com.paytech.risk.api.decision.request.DeleteRuleSetRequestBody;
import com.paytech.risk.decision.domain.variable.VariableOperandJson;
import com.paytech.risk.decision.domainservice.service.RiskVariableException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 删除的 BIGINT 版本必须来自 JSON 字符串，禁止 Reader 将数值隐式转换为文本。 */
@Configuration(proxyBeanMethods = false)
public class DecisionRuleSetRequestJsonConfiguration implements WebMvcConfigurer {
    private final JsonMapper mapper;

    /** 保留应用 JSON 配置，仅为删除 Reader 开启重复键检查和安全错误。 */
    public DecisionRuleSetRequestJsonConfiguration(JsonMapper mapper) {
        this.mapper = mapper.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build();
    }

    /** 仅为已明确字符串长整数合同的删除请求安装独立 Reader。 */
    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(converters -> converters.addFirst(new DeleteRequestConverter(mapper)));
    }

    private static final class DeleteRequestConverter extends JacksonJsonHttpMessageConverter {
        /** 绑定当前应用 JSON Mapper 的严格读取副本。 */
        private DeleteRequestConverter(JsonMapper mapper) { super(mapper); }

        /** 其他请求继续使用原有 Reader。 */
        @Override
        public boolean canRead(ResolvableType type, MediaType mediaType) {
            return (type.resolve() == DeleteRuleSetRequestBody.class || type.resolve() == CreateRuleSetVersionRequestBody.class) && super.canRead(type, mediaType);
        }

        /** 在 Bean Validation 前验证唯一对象和准确字符串字段。 */
        @Override
        public Object read(ResolvableType type, HttpInputMessage input, Map<String, Object> hints) throws IOException {
            byte[] bytes = input.getBody().readNBytes(65537);
            if (bytes.length > 65536) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
            try (var parser = getMapper().createParser(bytes)) {
                var tree = getMapper().readTree(parser);
                if (parser.nextToken() != null || tree == null || !tree.isObject()) throw new IllegalArgumentException();
                if (type.resolve() == CreateRuleSetVersionRequestBody.class) {
                    if (tree.hasNonNull("operandFormat")) variableShape(tree);
                    return getMapper().treeToValue(tree, CreateRuleSetVersionRequestBody.class);
                }
                if (tree.size() != 1
                    || !tree.has("expectedVersion") || !tree.get("expectedVersion").isString()) {
                    throw new IllegalArgumentException("删除请求必须包含字符串 expectedVersion");
                }
                return getMapper().treeToValue(tree, DeleteRuleSetRequestBody.class);
            } catch (JacksonException | IllegalArgumentException | RiskVariableException failure) {
                throw new HttpMessageNotReadableException("规则集请求不符合 JSON 字段或类型协议", input);
            }
        }

        /** 新操作数必须具备全部十个键，缺失与显式 null 不能混淆。 */
        private static void variableShape(JsonNode root) {
            if (!root.propertyNames().equals(Set.of("pointCode", "ruleSetCode", "ruleSetName", "baseRuleSetVersionId",
                "baseContentHash", "changeReason", "rules", "operandFormat")) || !root.get("operandFormat").isString()) throw new IllegalArgumentException();
            if (!root.get("rules").isArray()) throw new IllegalArgumentException();
            for (JsonNode rule : root.get("rules")) {
                if (!rule.isObject() || !rule.has("conditionNodes") || !rule.get("conditionNodes").isArray()
                    || !rule.has("additionalActions") || !rule.get("additionalActions").isArray()) throw new IllegalArgumentException();
                requireKeys(rule, "ruleCode", "ruleName", "priorityNo", "enabledFlag", "reasonCode", "conditionNodes", "action");
                if (!rule.get("action").isNull()) requireKeys(rule.get("action"), "actionCode", "actionVersionNo");
                for (JsonNode node : rule.get("conditionNodes")) {
                    requireKeys(node, "nodeKey", "parentNodeKey", "sequenceNo", "nodeType", "groupOperator", "comparisonOperator", "operands");
                    if (!node.isObject() || !node.has("operands") || !node.get("operands").isArray()) throw new IllegalArgumentException();
                    for (JsonNode operand : node.get("operands")) VariableOperandJson.read(operand);
                }
                for (JsonNode action : rule.get("additionalActions")) {
                    requireKeys(action, "sequenceNo", "actionCode", "actionVersionNo", "targetBindingCode", "parameters");
                    if (!action.get("parameters").isArray()) throw new IllegalArgumentException();
                    for (JsonNode parameter : action.get("parameters"))
                        requireKeys(parameter, "parameterCode", "valueSequence", "sourceType", "fieldCode", "valueType", "literalValue");
                    if (!action.isObject() || !action.has("subjectVariableCode") || !action.get("subjectVariableCode").isString())
                        throw new IllegalArgumentException();
                }
            }
        }

        /** 普通 class 仍区分必填键缺失与业务允许的显式 null。 */
        private static void requireKeys(JsonNode value, String... fields) {
            if (value == null || !value.isObject() || !value.propertyNames().containsAll(Set.of(fields)))
                throw new IllegalArgumentException("规则配置缺少合同字段");
        }

        /** 类路径读取复用相同验证，避免另一路径隐式转换。 */
        @Override
        protected Object readInternal(Class<?> type, HttpInputMessage input) throws IOException {
            return read(ResolvableType.forClass(type), input, Map.of());
        }

        /** 本 Reader 不接管任何响应写出。 */
        @Override
        public boolean canWrite(ResolvableType type, Class<?> valueClass, MediaType mediaType) { return false; }
    }
}
