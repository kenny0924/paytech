package com.paytech.foundation.web.error;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpMethod;

import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.web.request.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 将安全映射结果写成唯一 MVC Problem Profile。
 *
 * @author Codex
 */
public final class ProblemResponseWriter {

    private final ApiProblemResponseFactory responseFactory;
    private final ObjectMapper objectMapper;

    public ProblemResponseWriter(ApiProblemResponseFactory responseFactory, ObjectMapper objectMapper) {
        this.responseFactory = Objects.requireNonNull(responseFactory, "responseFactory must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 替换未提交响应，只保留映射器明确批准的标准 Header。
     *
     * @param request
     *            当前请求
     * @param response
     *            当前响应
     * @param mappedProblem
     *            已安全分类的 Problem
     * @throws IOException
     *             写入失败
     */
    public void write(HttpServletRequest request, HttpServletResponse response, MappedProblem mappedProblem)
        throws IOException {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(mappedProblem, "mappedProblem must not be null");
        if (response.isCommitted()) {
            throw new IllegalStateException("Cannot replace a committed response");
        }
        String requestId = requestId(request);
        response.reset();
        response.setStatus(mappedProblem.status().value());
        mappedProblem.headers().forEach((name, values) -> setHeader(response, name, values));
        response.setHeader(ApiProblemProfile.REQUEST_ID_HEADER, requestId);
        response.setContentType(ApiProblemProfile.CONTENT_TYPE);
        if (HttpMethod.HEAD.matches(request.getMethod())) {
            return;
        }
        ApiProblemResponse problem = responseFactory.create(mappedProblem, requestId);
        byte[] body = serialize(problem);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
        if (value instanceof String requestId && ApiProblemProfile.isValidRequestId(requestId)) {
            return requestId;
        }
        String generatedRequestId = UUID.randomUUID().toString();
        if (!ApiProblemProfile.isValidRequestId(generatedRequestId)) {
            throw new IllegalStateException("Generated request ID does not match the Problem Profile");
        }
        request.setAttribute(RequestIdFilter.REQUEST_ATTRIBUTE, generatedRequestId);
        return generatedRequestId;
    }

    private void setHeader(HttpServletResponse response, String name, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        response.setHeader(name, values.getFirst());
        for (int index = 1; index < values.size(); index++) {
            response.addHeader(name, values.get(index));
        }
    }

    private byte[] serialize(ApiProblemResponse problem) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", problem.type());
        root.put("title", problem.title());
        root.put("detail", problem.detail());
        root.put("code", problem.code());
        root.put("requestId", problem.requestId());
        root.put("timestamp", problem.timestamp());
        if (problem.errors() != null && !problem.errors().isEmpty()) {
            ArrayNode errors = root.putArray("errors");
            for (ProblemFieldError fieldError : problem.errors()) {
                ObjectNode error = errors.addObject();
                error.put("field", fieldError.field());
                error.put("message", fieldError.message());
            }
        }
        return objectMapper.writeValueAsBytes(root);
    }
}
