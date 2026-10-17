package com.paytech.risk.configuration;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.api.decision.request.CreateVariableRequestBody;
import com.paytech.risk.api.decision.request.UpdateVariableRequestBody;
import com.paytech.risk.api.decision.request.ReplaceMembersRequestBody;
import com.paytech.risk.api.decision.RiskVariableModels.MemberRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.http.*;
import org.springframework.http.converter.*;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 仅对变量能力接管有界严格 JSON 与查询参数，不改变历史接口读取。 */
@Configuration(proxyBeanMethods = false)
public class RiskVariableRequestConfiguration implements WebMvcConfigurer {
    private final JsonMapper mapper;

    /** 复制解析器防止重复键及原始请求内容进入错误诊断。 */
    public RiskVariableRequestConfiguration(JsonMapper mapper) {
        this.mapper = mapper.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build();
    }

    /** 按固定操作验证准确 Query，GET/DELETE 不接受任何请求 Body。 */
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            /** 拒绝未知、重复、空 Query 和查询携带 Body。 */
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
                Set<String> keys = keys(request.getMethod(), request.getRequestURI());
                for (var entry : request.getParameterMap().entrySet()) {
                    if (!keys.contains(entry.getKey()) || entry.getValue().length != 1 || entry.getValue()[0].isEmpty()) {
                        throw new InvalidParameterException("变量 Query 参数未知、重复或为空");
                    }
                }
                if (Set.of("GET", "DELETE").contains(request.getMethod()) && request.getInputStream().read() != -1) {
                    throw new InvalidParameterException("变量查询和删除不接受 Body");
                }
                return true;
            }
        }).addPathPatterns("/decision-variables/v1/**");
    }

    /** 使用三个 RequestBody 的真实字段定义校验全部键，避免另维护结构名单。 */
    @Override public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(list -> list.addFirst(new VariableRequestConverter(mapper)));
    }

    /** 查询名单是 HTTP 合同，不是业务变量目录。 */
    static Set<String> keys(String method, String path) {
        if ("DELETE".equals(method)) { return Set.of("expectedVersion"); }
        if (!"GET".equals(method)) { return Set.of(); }
        if (path.endsWith("/authoring-context")) { return Set.of("pointCode"); }
        if (path.endsWith("/variables")) { return Set.of("keyword", "sourceType", "valueType", "status", "pointCode", "usage", "nodeType", "codes", "page", "size"); }
        if (path.endsWith("/members")) { return Set.of("keyword", "page", "size", "expectedVersion"); }
        if (path.endsWith("/references")) { return Set.of("scope", "cursor", "size"); }
        return Set.of();
    }

    /** 有界请求转换器只处理本能力三个明确的写 DTO。 */
    static final class VariableRequestConverter extends JacksonJsonHttpMessageConverter {
        private static final Set<Class<?>> REQUESTS = Set.of(CreateVariableRequestBody.class, UpdateVariableRequestBody.class, ReplaceMembersRequestBody.class);

        /** 复用严格重复键解析器。 */
        VariableRequestConverter(JsonMapper mapper) { super(mapper); }
        /** 只有本能力的 RequestBody 才由该转换器处理。 */
        @Override public boolean canRead(ResolvableType type, MediaType mediaType) {
            return REQUESTS.contains(type.resolve()) && super.canRead(type, mediaType);
        }
        /** 定义最多 16 KiB，成员最多 64 KiB；显式 null 与字段缺失分别处理。 */
        @Override public Object read(ResolvableType type, HttpInputMessage input, Map<String, Object> hints) throws IOException {
            int limit = type.resolve() == ReplaceMembersRequestBody.class ? 65536 : 16384;
            byte[] bytes = input.getBody().readNBytes(limit + 1);
            if (bytes.length > limit) { throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE); }
            try (var parser = getMapper().createParser(bytes)) {
                JsonNode tree = getMapper().readTree(parser);
                validateRecord(tree, type.resolve());
                if (parser.nextToken() != null) { throw new IllegalArgumentException(); }
                return getMapper().treeToValue(tree, type.resolve());
            } catch (JacksonException | IllegalArgumentException failure) {
                throw new HttpMessageNotReadableException("变量请求不符合 JSON 字段或类型合同", input);
            }
        }
        /** 兼容普通读取入口，仍执行同一字节上限和字段检查。 */
        @Override protected Object readInternal(Class<?> type, HttpInputMessage input) throws IOException {
            return read(ResolvableType.forClass(type), input, Map.of());
        }
        /** 禁止该写请求转换器接管响应。 */
        @Override public boolean canWrite(ResolvableType type, Class<?> valueClass, MediaType mediaType) { return false; }

        /** 完整键由请求 class 的实例字段提供，标量不接受数字/字符串的隐式互转。 */
        static void validateRecord(JsonNode node, Class<?> type) {
            List<Field> fields = fields(type);
            Set<String> keys = fields.stream().map(Field::getName).collect(Collectors.toSet());
            if (node == null || !node.isObject() || !node.propertyNames().equals(keys)) {
                throw new IllegalArgumentException();
            }
            for (Field field : fields) {
                validateValue(field, node.get(field.getName()));
            }
        }

        /** 按字段声明类型检查单个 JSON 值，显式 null 由 Bean Validation 与业务规则判断。 */
        static void validateValue(Field field, JsonNode value) {
            if (value.isNull()) {
                return;
            }
            Class<?> type = field.getType();
            if (type == String.class && !value.isString()) {
                throw new IllegalArgumentException();
            }
            if (type == Integer.class && (!value.isIntegralNumber() || !value.canConvertToInt())) {
                throw new IllegalArgumentException();
            }
            if (type == Object.class && !value.isString() && !value.isBoolean()) {
                throw new IllegalArgumentException();
            }
            if (type == List.class) {
                validateList(field.getName(), value);
            }
        }

        /** 只取请求合同的实例字段，静态常量不属于 JSON 键。 */
        static List<Field> fields(Class<?> type) {
            return Arrays.stream(type.getDeclaredFields()).filter(field -> !Modifier.isStatic(field.getModifiers())).toList();
        }

        /** 成员也必须有准确字段；用途数组仅接受字符串。 */
        static void validateList(String name, JsonNode node) {
            if (!node.isArray()) {
                throw new IllegalArgumentException();
            }
            for (JsonNode child : node) {
                if ("members".equals(name)) {
                    validateRecord(child, MemberRequest.class);
                } else if (!child.isString()) {
                    throw new IllegalArgumentException();
                }
            }
        }
    }
}
