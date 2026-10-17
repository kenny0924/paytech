package com.paytech.risk.configuration;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.http.*;
import org.springframework.http.converter.*;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.api.decision.request.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;

/** 仅对新增合同及规则集查询验证重复/未知 Query、无 GET Body 及有界精确字典 JSON。 */
@Configuration(proxyBeanMethods = false)
public class DecisionDictionaryRequestConfiguration implements WebMvcConfigurer {
    private static final String RULE_SET_BASE = "/decision-rule-sets/v1";

    private final JsonMapper mapper;
    /** 复制 JSON 配置以严格检测重复字段，并避免解析异常包含原始内容。 */
    public DecisionDictionaryRequestConfiguration(JsonMapper mapper) {
        this.mapper = mapper.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build();
    }
    /** 仅为新增字典路径及规则集查询注册精确查询参数校验。 */
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            /** 拒绝未知、重复或空 Query 以及 GET Body，阻止其进入业务入口。 */
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
                String path = request.getRequestURI();
                Set<String> keys = path.contains(RULE_SET_BASE) ? ruleSetQueryKeys(request.getMethod(), path)
                    : dictionaryQueryKeys(request.getMethod(), path);
                if (keys == null) {
                    return true;
                }
                for (var parameter : request.getParameterMap().entrySet()) {
                    if (!keys.contains(parameter.getKey()) || parameter.getValue().length != 1 || parameter.getValue()[0].isEmpty())
                        throw new InvalidParameterException("Query 参数未知、重复或为空");
                }
                if (request.getMethod().equals("GET") && request.getInputStream().read() != -1)
                    throw new InvalidParameterException("查询请求不接受 Body");
                return true;
            }
        }).addPathPatterns("/decision-dictionaries/v1/**", "/decision-rule-sets/v1/authoring-context/*",
            "/decision-rule-sets/v1/rule-sets", "/decision-rule-sets/v1/rule-sets/*",
            "/decision-rule-sets/v1/rule-sets/*/versions", "/decision-rule-sets/v1/rule-sets/*/release-references");
    }

    /** 字典列表与引用查询的准确 Query 名单；详情和写操作不接受 Query。 */
    static Set<String> dictionaryQueryKeys(String method, String path) {
        if (path.endsWith("/references")) {
            return Set.of("page", "size", "scope");
        }
        if ("GET".equals(method) && (path.endsWith("/rejection-reasons") || path.endsWith("/subject-tags"))) {
            return Set.of("page", "size", "keyword", "status");
        }
        return Set.of();
    }

    /**
     * 按规则集查询的准确路径形态返回 Query 名单，名单来自对应 risk-api 接口声明。
     * <li>能力目录与版本详情不接受 Query；路径段只按位置判断，编码或 ID 恰为 versions 等字面值时不混淆。</li>
     * <li>创建、冻结和删除等写操作不属于本查询合同，返回 null 保持原有处理。</li>
     */
    static Set<String> ruleSetQueryKeys(String method, String path) {
        if (!"GET".equals(method)) {
            return null;
        }
        String route = path.substring(path.indexOf(RULE_SET_BASE) + RULE_SET_BASE.length());
        if (route.matches("/rule-sets/[^/]+/versions")) {
            return Set.of("page", "size", "maxVersionNo");
        }
        if (route.matches("/rule-sets/[^/]+/release-references")) {
            return Set.of("page", "size", "environmentCode", "status", "ruleSetVersionId");
        }
        if (route.equals("/rule-sets")) {
            return Set.of("page", "size", "pointCode", "ruleSetCode", "status");
        }
        return Set.of();
    }
    /** 优先注册仅处理字典写请求的有界 JSON 转换器。 */
    @Override public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(list -> list.addFirst(new DictionaryRequestConverter(mapper)));
    }
    private static final class DictionaryRequestConverter extends JacksonJsonHttpMessageConverter {
        private static final Map<Class<?>, Set<String>> KEYS = Map.of(
            CreateRejectionReasonRequestBody.class, Set.of("reasonCode", "name", "description", "defaultMessage"),
            UpdateRejectionReasonRequestBody.class, Set.of("expectedVersion", "name", "description", "defaultMessage"),
            CreateSubjectTagRequestBody.class, Set.of("tagCode", "name", "description"),
            UpdateSubjectTagRequestBody.class, Set.of("expectedVersion", "name", "description"),
            ChangeDictionaryStatusRequestBody.class, Set.of("expectedVersion", "status"));
        /** 仅为明确声明的字典请求复用严格 JSON 解析器。 */
        private DictionaryRequestConverter(JsonMapper mapper) { super(mapper); }
        /** 仅接管本轮明确声明的字典请求 DTO，不改变旧接口解析。 */
        @Override public boolean canRead(ResolvableType type, MediaType mediaType) {
            return KEYS.containsKey(type.resolve()) && super.canRead(type, mediaType);
        }
        /** 最多读取 16 KiB 字典 JSON，要求精确字段和类型；超限返回 413，形状错误返回 400。 */
        @Override public Object read(ResolvableType type, HttpInputMessage input, Map<String, Object> hints) throws IOException {
            byte[] body = input.getBody().readNBytes(16385);
            if (body.length > 16384) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
            try (var parser = getMapper().createParser(body)) {
                var tree = getMapper().readTree(parser);
                if (tree == null || !tree.isObject() || !tree.propertyNames().equals(KEYS.get(type.resolve())) || parser.nextToken() != null)
                    throw new IllegalArgumentException();
                for (var property : tree.properties()) {
                    var value = property.getValue();
                    if (!value.isString() && !(property.getKey().equals("description") && value.isNull()))
                        throw new IllegalArgumentException();
                }
                return getMapper().treeToValue(tree, type.resolve());
            } catch (tools.jackson.core.JacksonException | IllegalArgumentException failure) {
                throw new HttpMessageNotReadableException("字典请求不符合 JSON 协议", input);
            }
        }
        /** 将普通类型解析入口委托给同一有界 JSON 校验。 */
        @Override protected Object readInternal(Class<?> type, HttpInputMessage input) throws IOException {
            return read(ResolvableType.forClass(type), input, Map.of());
        }
        /** 禁止本请求转换器接管响应序列化。 */
        @Override public boolean canWrite(ResolvableType type, Class<?> valueClass, MediaType mediaType) { return false; }
    }
}
