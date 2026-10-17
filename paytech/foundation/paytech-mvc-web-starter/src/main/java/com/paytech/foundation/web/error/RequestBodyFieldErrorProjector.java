package com.paytech.foundation.web.error;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.MethodParameter;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;

import com.paytech.foundation.httpproblem.api.ProblemFieldError;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.introspect.BeanPropertyDefinition;

/**
 * 把 Bean Validation 的内部路径安全投影为外部 JSON 属性路径。
 *
 * @author Codex
 */
public final class RequestBodyFieldErrorProjector {

    private static final int MAX_PATH_LENGTH = 256;
    private static final Pattern INTERNAL_SEGMENT = Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*)(?:\\[(\\d+)])?");
    private static final Pattern SAFE_EXTERNAL_PROPERTY = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]{0,127}");
    private static final String GENERIC_MESSAGE = "is invalid";
    private static final Map<String, String> SAFE_MESSAGES = safeMessages();
    private static final Comparator<ProblemFieldError> FIELD_ERROR_ORDER =
        Comparator.comparing(ProblemFieldError::field).thenComparing(ProblemFieldError::message);

    private final ObjectMapper objectMapper;

    public RequestBodyFieldErrorProjector(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 只投影可由 Jackson 反序列化元数据唯一解析的字段错误。
     *
     * @param parameter
     *            {@code @RequestBody} 参数
     * @param errors
     *            Bean Validation 结果
     * @return 去重并稳定排序后的安全字段错误
     */
    public List<ProblemFieldError> project(MethodParameter parameter, Errors errors) {
        Objects.requireNonNull(parameter, "parameter must not be null");
        Objects.requireNonNull(errors, "errors must not be null");
        JavaType rootType = objectMapper.constructType(parameter.getGenericParameterType());
        List<ProblemFieldError> projected = new ArrayList<>();
        for (FieldError fieldError : errors.getFieldErrors()) {
            String externalPath = externalPath(rootType, fieldError.getField());
            if (externalPath != null) {
                projected.add(new ProblemFieldError(externalPath, safeMessage(fieldError)));
            }
        }
        return projected.stream().distinct().sorted(FIELD_ERROR_ORDER).toList();
    }

    private String externalPath(JavaType rootType, String rawPath) {
        if (rawPath == null || rawPath.isBlank() || rawPath.length() > MAX_PATH_LENGTH) {
            return null;
        }
        String[] segments = rawPath.split("\\.", -1);
        JavaType currentType = rootType;
        StringBuilder externalPath = new StringBuilder();
        for (int index = 0; index < segments.length; index++) {
            Matcher matcher = INTERNAL_SEGMENT.matcher(segments[index]);
            if (!matcher.matches() || currentType == null || currentType.isMapLikeType()
                || currentType.isCollectionLikeType() || currentType.isArrayType()) {
                return null;
            }
            BeanPropertyDefinition property = uniqueProperty(currentType, matcher.group(1));
            if (property == null || !SAFE_EXTERNAL_PROPERTY.matcher(property.getName()).matches()) {
                return null;
            }
            if (externalPath.length() > 0) {
                externalPath.append('.');
            }
            externalPath.append(property.getName());
            currentType = property.getPrimaryType();
            if (currentType != null && currentType.isMapLikeType()) {
                return null;
            }
            String collectionIndex = matcher.group(2);
            if (collectionIndex != null) {
                if (currentType == null || currentType.isMapLikeType()
                    || (!currentType.isCollectionLikeType() && !currentType.isArrayType())) {
                    return null;
                }
                currentType = currentType.getContentType();
                if (currentType == null || currentType.isJavaLangObject()) {
                    return null;
                }
                externalPath.append('[').append(collectionIndex).append(']');
            }
            if (index < segments.length - 1 && (currentType == null || currentType.isMapLikeType()
                || currentType.isCollectionLikeType() || currentType.isArrayType())) {
                return null;
            }
        }
        return externalPath.toString();
    }

    private BeanPropertyDefinition uniqueProperty(JavaType type, String internalName) {
        var config = objectMapper.deserializationConfig();
        var introspector = config.classIntrospectorInstance().forOperation(config);
        var annotatedClass = introspector.introspectClassAnnotations(type);
        BeanDescription description = introspector.introspectForDeserialization(type, annotatedClass);
        List<BeanPropertyDefinition> properties = description.findProperties();
        BeanPropertyDefinition match = null;
        for (BeanPropertyDefinition property : properties) {
            if (!property.couldDeserialize() || !internalName.equals(property.getInternalName())) {
                continue;
            }
            if (match != null) {
                return null;
            }
            match = property;
        }
        if (match == null || hasMultipleConstructorParameters(match)) {
            return null;
        }
        String externalName = match.getName();
        long externalMatches = properties.stream()
            .filter(BeanPropertyDefinition::couldDeserialize)
            .filter(property -> externalName.equals(property.getName()))
            .count();
        if (externalMatches != 1) {
            return null;
        }
        return match;
    }

    private boolean hasMultipleConstructorParameters(BeanPropertyDefinition property) {
        var parameters = property.getConstructorParameters();
        if (!parameters.hasNext()) {
            return false;
        }
        parameters.next();
        return parameters.hasNext();
    }

    private String safeMessage(FieldError fieldError) {
        String code = fieldError.getCode();
        return code == null ? GENERIC_MESSAGE : SAFE_MESSAGES.getOrDefault(code, GENERIC_MESSAGE);
    }

    private static Map<String, String> safeMessages() {
        Map<String, String> messages = new HashMap<>();
        messages.put("NotNull", "must not be null");
        messages.put("NotBlank", "must not be blank");
        messages.put("NotEmpty", "must not be empty");
        messages.put("Size", "size is outside the allowed range");
        messages.put("Min", "must be greater than or equal to the minimum");
        messages.put("DecimalMin", "must be greater than or equal to the minimum");
        messages.put("Max", "must be less than or equal to the maximum");
        messages.put("DecimalMax", "must be less than or equal to the maximum");
        messages.put("Positive", "must be greater than zero");
        messages.put("PositiveOrZero", "must be greater than or equal to zero");
        messages.put("Negative", "must be less than zero");
        messages.put("NegativeOrZero", "must be less than or equal to zero");
        messages.put("Pattern", "has an invalid format");
        messages.put("Email", "must be a well-formed email address");
        messages.put("Past", "must be in the past");
        messages.put("PastOrPresent", "must be in the past or present");
        messages.put("Future", "must be in the future");
        messages.put("FutureOrPresent", "must be in the present or future");
        return Map.copyOf(messages);
    }
}
