package com.paytech.operations.console.bff.configuration;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.core.ResolvableType;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.SmartHttpMessageConverter;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.json.JsonMapper;

/** 只读取已发布的 Risk 成功根；错误由 Foundation 解码，请求由独立 JSON Writer 序列化。 */
public final class RiskOwnerResponseHttpMessageConverter implements SmartHttpMessageConverter<Object> {
    private final RiskOwnerResponseShapeValidator validator = new RiskOwnerResponseShapeValidator();
    private final JsonMapper mapper = JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build();

    @Override
    public boolean canRead(ResolvableType type, MediaType mediaType) {
        return validator.supports(type) && (mediaType == null || MediaType.APPLICATION_JSON.isCompatibleWith(mediaType));
    }

    @Override
    public Object read(ResolvableType type, HttpInputMessage input, Map<String, Object> hints) throws IOException {
        try (var body = input.getBody(); var parser = mapper.createParser(boundedBody(body, type))) {
            var tree = mapper.readTree(parser);
            if (parser.nextToken() != null) throw new IllegalArgumentException("Risk 成功响应必须只有一个 JSON 根");
            validator.validate(type, tree);
            return mapper.treeToValue(tree, mapper.constructType(type.getType()));
        } catch (JacksonIOException failure) {
            // 传输 I/O 必须交给 Foundation 分类；只保留底层原因，避免携带 Jackson 的 JSON 诊断。
            throw new ResourceAccessException("Risk 成功响应读取失败", failure.getCause());
        } catch (IOException failure) {
            throw new ResourceAccessException("Risk 成功响应读取失败", failure);
        } catch (JacksonException | IllegalArgumentException failure) {
            // Jackson 原始诊断可能含机密键和值；对外仅保留安全的解码故障类别。
            throw new HttpMessageNotReadableException("Risk 成功响应不符合协议", input);
        }
    }

    private static boolean flowResponse(ResolvableType type) {
        Class<?> resolved = type.resolve();
        if (resolved != null && resolved.getEnclosingClass() == com.paytech.risk.decision.api.DecisionFlowModels.class) return true;
        if (resolved == com.paytech.risk.decision.api.response.FlowAuthoringContextResponseBody.class
            || resolved == com.paytech.risk.decision.api.response.FlowListItemResponseBody.class
            || resolved == com.paytech.risk.decision.api.response.FlowDetailResponseBody.class
            || resolved == com.paytech.risk.decision.api.response.FlowHistoryItemResponseBody.class
            || resolved == com.paytech.risk.decision.api.response.RuleSetReferenceResponseBody.class
            || resolved == com.paytech.risk.decision.api.response.LegacyPointResponseBody.class) return true;
        if (resolved == com.paytech.risk.decision.api.response.SaveFlowResponseBody.class
            || resolved == com.paytech.risk.decision.api.response.DeleteFlowResponseBody.class
            || resolved == com.paytech.risk.decision.api.response.ValidateFlowResponseBody.class) return true;
        return java.util.Arrays.stream(type.getGenerics()).anyMatch(RiskOwnerResponseHttpMessageConverter::flowResponse);
    }

    private static java.io.InputStream boundedBody(java.io.InputStream body, ResolvableType type) throws IOException {
        boolean variable = RiskVariableResponseShape.supports(type);
        boolean detail = type.resolve() == com.paytech.risk.decision.api.response.RuleSetDetailResponseBody.class;
        if (!variable && !detail && !flowResponse(type)) return body;
        boolean variablePage = type.resolve() == com.paytech.risk.decision.api.response.VariableListResponseBody.class
            || type.resolve() == com.paytech.risk.decision.api.response.VariableMembersResponseBody.class;
        int limit = variablePage ? 512 * 1024 : 1024 * 1024;
        byte[] bytes = body.readNBytes(limit + 1);
        if (bytes.length > limit) throw new IllegalArgumentException("Risk 响应超过协议上限");
        return new java.io.ByteArrayInputStream(bytes);
    }

    @Override
    public boolean canWrite(ResolvableType type, Class<?> valueClass, MediaType mediaType) {
        return false;
    }

    @Override
    public List<MediaType> getSupportedMediaTypes() {
        return List.of(MediaType.APPLICATION_JSON);
    }

    @Override
    public void write(Object value, ResolvableType type, MediaType contentType, HttpOutputMessage output,
        Map<String, Object> hints) {
        throw new HttpMessageNotWritableException("Risk 成功响应读取器不支持写入");
    }
}
