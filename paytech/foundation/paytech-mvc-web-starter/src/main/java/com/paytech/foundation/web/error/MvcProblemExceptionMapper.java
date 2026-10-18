package com.paytech.foundation.web.error;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.foundation.web.api.ApiErrorStatusResolver;
import com.paytech.foundation.web.api.RetryAfterProvider;
import com.paytech.foundation.web.logging.HttpRequestResponseLoggingFilter;
import com.paytech.foundation.web.request.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 对 MVC Advice 与前置 Filter 共用的异常分类器。
 *
 * @author Codex
 */
public final class MvcProblemExceptionMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(MvcProblemExceptionMapper.class);
    private final ApiErrorStatusResolver statusResolver;
    private final RequestBodyFieldErrorProjector fieldErrorProjector;

    public MvcProblemExceptionMapper(ApiErrorStatusResolver statusResolver,
        RequestBodyFieldErrorProjector fieldErrorProjector) {
        this.statusResolver = Objects.requireNonNull(statusResolver, "statusResolver must not be null");
        this.fieldErrorProjector = Objects.requireNonNull(fieldErrorProjector,
            "fieldErrorProjector must not be null");
    }

    /**
     * 将异常分类为唯一的安全 Problem 描述。
     *
     * @param exception
     *            MVC 或过滤器异常
     * @param request
     *            当前请求
     * @return 安全映射结果
     */
    public MappedProblem map(Exception exception, HttpServletRequest request) {
        Objects.requireNonNull(exception, "exception must not be null");
        Objects.requireNonNull(request, "request must not be null");
        if (exception instanceof ServiceUnavailableException unavailableException) {
            logSystemFailure("服务暂时不可用", unavailableException, request);
            return withRetryAfter(problem(HttpStatus.SERVICE_UNAVAILABLE, unavailableException.getErrorCode(),
                unavailableException.getErrorCode().getMessage()), unavailableException);
        }
        if (exception instanceof RequestBodyValidationException validationException) {
            return problem(HttpStatus.UNPROCESSABLE_CONTENT, CommonErrorCode.VALIDATION_ERROR,
                CommonErrorCode.VALIDATION_ERROR.getMessage(), validationException.errors(), HttpHeaders.EMPTY);
        }
        if (exception instanceof FailException failException) {
            return mapFailException(failException);
        }
        if (exception instanceof ErrorException errorException) {
            logSystemFailure("系统处理结果未知", errorException, request);
            return mapErrorException(errorException);
        }
        if (exception instanceof MethodArgumentNotValidException validationException) {
            return mapMethodArgumentValidation(validationException, request);
        }
        if (exception instanceof HandlerMethodValidationException validationException) {
            return mapHandlerMethodValidation(validationException, request);
        }
        if (exception instanceof NoHandlerFoundException || exception instanceof NoResourceFoundException) {
            logInvalidRequest(exception, request);
            return problem(HttpStatus.NOT_FOUND, CommonErrorCode.NOT_FOUND,
                CommonErrorCode.NOT_FOUND.getMessage());
        }
        if (exception instanceof HttpRequestMethodNotSupportedException methodException) {
            logInvalidRequest(exception, request);
            return problem(HttpStatus.METHOD_NOT_ALLOWED, CommonErrorCode.METHOD_NOT_ALLOWED,
                CommonErrorCode.METHOD_NOT_ALLOWED.getMessage(), List.of(),
                approvedHeaders(methodException.getHeaders(), HttpHeaders.ALLOW));
        }
        if (exception instanceof HttpMediaTypeNotSupportedException mediaException) {
            logInvalidRequest(exception, request);
            return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, CommonErrorCode.UNSUPPORTED_MEDIA_TYPE,
                CommonErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage(), List.of(),
                approvedHeaders(mediaException.getHeaders(), HttpHeaders.ACCEPT, HttpHeaders.ACCEPT_PATCH));
        }
        if (exception instanceof ErrorResponse errorResponse) {
            return mapStandardErrorResponse(exception, errorResponse, request);
        }
        if (isMalformedRequest(exception)) {
            logInvalidRequest(exception, request);
            return problem(HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_REQUEST,
                CommonErrorCode.INVALID_REQUEST.getMessage());
        }
        logUnexpectedFailure(exception, request);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR,
            CommonErrorCode.INTERNAL_ERROR.getMessage());
    }

    private MappedProblem mapFailException(FailException exception) {
        if (CommonErrorCode.CONCURRENT_REQUEST.getCode().equals(exception.getCode())) {
            return withRetryAfter(problem(HttpStatus.CONFLICT, exception.getErrorCode(), exception.getMessage()),
                exception);
        }
        if (CommonErrorCode.INTERNAL_TOKEN_INVALID.getCode().equals(exception.getCode())) {
            // 内部调用凭证的拒绝原因不外显，避免成为探测签名与声明的反馈。
            return problem(HttpStatus.UNAUTHORIZED, CommonErrorCode.INTERNAL_TOKEN_INVALID,
                CommonErrorCode.INTERNAL_TOKEN_INVALID.getMessage());
        }
        HttpStatus status = resolveOwnerStatus(exception.getErrorCode(), HttpStatus.BAD_REQUEST, true);
        return withRetryAfter(problem(status, exception.getErrorCode(), exception.getMessage()), exception);
    }

    private MappedProblem mapErrorException(ErrorException exception) {
        if (CommonErrorCode.UPSTREAM_INVALID_RESPONSE.getCode().equals(exception.getCode())) {
            return withRetryAfter(problem(HttpStatus.BAD_GATEWAY, exception.getErrorCode(),
                exception.getErrorCode().getMessage()), exception);
        }
        HttpStatus status = resolveOwnerStatus(exception.getErrorCode(), HttpStatus.INTERNAL_SERVER_ERROR, false);
        return withRetryAfter(problem(status, exception.getErrorCode(), exception.getErrorCode().getMessage()),
            exception);
    }

    private MappedProblem withRetryAfter(MappedProblem problem, Exception exception) {
        if (!(exception instanceof RetryAfterProvider provider)) {
            return problem;
        }
        long seconds;
        try {
            seconds = provider.retryAfterSeconds();
        } catch (RuntimeException ignored) {
            return problem;
        }
        if (seconds < RetryAfterProvider.MIN_DELAY_SECONDS || seconds > RetryAfterProvider.MAX_DELAY_SECONDS) {
            return problem;
        }
        HttpHeaders headers = HttpHeaders.copyOf(problem.headers());
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        return new MappedProblem(problem.status(), problem.errorCode(), problem.detail(), problem.errors(), headers);
    }

    private MappedProblem mapMethodArgumentValidation(MethodArgumentNotValidException exception,
        HttpServletRequest request) {
        logInvalidRequest(exception, request);
        if (!exception.getParameter().hasParameterAnnotation(RequestBody.class)) {
            return invalidRequest();
        }
        List<ProblemFieldError> fieldErrors =
            fieldErrorProjector.project(exception.getParameter(), exception.getBindingResult());
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, CommonErrorCode.VALIDATION_ERROR,
            CommonErrorCode.VALIDATION_ERROR.getMessage(), fieldErrors, HttpHeaders.EMPTY);
    }

    private MappedProblem mapHandlerMethodValidation(HandlerMethodValidationException exception,
        HttpServletRequest request) {
        if (exception.isForReturnValue()) {
            logUnexpectedFailure(exception, request);
            return problem(HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR,
                CommonErrorCode.INTERNAL_ERROR.getMessage());
        }
        logInvalidRequest(exception, request);
        BodyValidationVisitor visitor = new BodyValidationVisitor(fieldErrorProjector);
        try {
            exception.visitResults(visitor);
        } catch (RuntimeException ignored) {
            return invalidRequest();
        }
        if (!exception.getCrossParameterValidationResults().isEmpty() || !visitor.isBodyOnly()) {
            return invalidRequest();
        }
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, CommonErrorCode.VALIDATION_ERROR,
            CommonErrorCode.VALIDATION_ERROR.getMessage(), visitor.fieldErrors(), HttpHeaders.EMPTY);
    }

    private MappedProblem mapStandardErrorResponse(Exception exception, ErrorResponse errorResponse,
        HttpServletRequest request) {
        HttpStatus status = standardStatus(errorResponse.getStatusCode());
        if (status == null || (!status.is4xxClientError() && !status.is5xxServerError())) {
            logUnexpectedFailure(exception, request);
            return problem(HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR,
                CommonErrorCode.INTERNAL_ERROR.getMessage());
        }
        if (status.is4xxClientError()) {
            logInvalidRequest(exception, request);
            return problem(status, CommonErrorCode.INVALID_REQUEST, CommonErrorCode.INVALID_REQUEST.getMessage());
        }
        logUnexpectedFailure(exception, request);
        return problem(status, CommonErrorCode.INTERNAL_ERROR, CommonErrorCode.INTERNAL_ERROR.getMessage());
    }

    private boolean isMalformedRequest(Exception exception) {
        return exception instanceof BindException
            || exception instanceof HttpMessageNotReadableException
            || exception instanceof ServletRequestBindingException
            || exception instanceof MethodArgumentTypeMismatchException;
    }

    private HttpStatus resolveOwnerStatus(CodeMessageEnum errorCode, HttpStatus defaultStatus, boolean clientError) {
        try {
            HttpStatus resolved = statusResolver.resolve(errorCode, defaultStatus);
            if (resolved != null && (clientError ? resolved.is4xxClientError() : resolved.is5xxServerError())) {
                return resolved;
            }
        } catch (RuntimeException ignored) {
            // Owner resolver is an extension boundary; invalid implementations fail closed.
        }
        return defaultStatus;
    }

    private HttpStatus standardStatus(HttpStatusCode statusCode) {
        return statusCode == null ? null : HttpStatus.resolve(statusCode.value());
    }

    private HttpHeaders approvedHeaders(HttpHeaders source, String... names) {
        if (source == null || source.isEmpty()) {
            return HttpHeaders.EMPTY;
        }
        HttpHeaders approved = new HttpHeaders();
        for (String name : names) {
            for (String value : source.getOrEmpty(name)) {
                approved.add(name, value);
            }
        }
        return approved;
    }

    private MappedProblem invalidRequest() {
        return problem(HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_REQUEST,
            CommonErrorCode.INVALID_REQUEST.getMessage());
    }

    private MappedProblem problem(HttpStatus status, CodeMessageEnum errorCode, String detail) {
        return problem(status, errorCode, detail, List.of(), HttpHeaders.EMPTY);
    }

    private MappedProblem problem(HttpStatus status, CodeMessageEnum errorCode, String detail,
        List<ProblemFieldError> errors, HttpHeaders headers) {
        return new MappedProblem(status, errorCode, detail, errors, headers);
    }

    private void logInvalidRequest(Exception exception, HttpServletRequest request) {
        LOGGER.warn("请求参数不正确, requestId={}, exceptionType={}", requestId(request),
            exception.getClass().getName());
    }

    private void logSystemFailure(String message, ErrorException exception, HttpServletRequest request) {
        LOGGER.error("{}, requestId={}, errorCode={}, exceptionType={}", message, requestId(request),
            exception.getCode(), exception.getClass().getName());
    }

    private void logUnexpectedFailure(Exception exception, HttpServletRequest request) {
        if (Boolean.TRUE.equals(
            request.getAttribute(HttpRequestResponseLoggingFilter.METADATA_ONLY_REQUEST_ATTRIBUTE))) {
            LOGGER.error("敏感请求发生未处理系统异常, requestId={}", requestId(request));
            return;
        }
        LOGGER.error("未处理系统异常, requestId={}, exceptionType={}", requestId(request),
            exception.getClass().getName());
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
        return requestId instanceof String value ? value : null;
    }

    private static final class BodyValidationVisitor implements HandlerMethodValidationException.Visitor {

        private final RequestBodyFieldErrorProjector projector;
        private final List<ProblemFieldError> fieldErrors = new ArrayList<>();
        private boolean sawResult;
        private boolean nonBodyResult;

        private BodyValidationVisitor(RequestBodyFieldErrorProjector projector) {
            this.projector = projector;
        }

        @Override
        public void requestBody(RequestBody requestBody, ParameterErrors errors) {
            sawResult = true;
            fieldErrors.addAll(projector.project(errors.getMethodParameter(), errors));
        }

        @Override
        public void requestBodyValidationResult(RequestBody requestBody, ParameterValidationResult result) {
            sawResult = true;
        }

        @Override
        public void cookieValue(CookieValue cookieValue, ParameterValidationResult result) {
            rejectNonBody();
        }

        @Override
        public void matrixVariable(MatrixVariable matrixVariable, ParameterValidationResult result) {
            rejectNonBody();
        }

        @Override
        public void modelAttribute(ModelAttribute modelAttribute, ParameterErrors errors) {
            rejectNonBody();
        }

        @Override
        public void pathVariable(PathVariable pathVariable, ParameterValidationResult result) {
            rejectNonBody();
        }

        @Override
        public void requestHeader(RequestHeader requestHeader, ParameterValidationResult result) {
            rejectNonBody();
        }

        @Override
        public void requestParam(RequestParam requestParam, ParameterValidationResult result) {
            rejectNonBody();
        }

        @Override
        public void requestPart(RequestPart requestPart, ParameterErrors errors) {
            rejectNonBody();
        }

        @Override
        public void other(ParameterValidationResult result) {
            rejectNonBody();
        }

        private void rejectNonBody() {
            sawResult = true;
            nonBodyResult = true;
        }

        private boolean isBodyOnly() {
            return sawResult && !nonBodyResult;
        }

        private List<ProblemFieldError> fieldErrors() {
            return fieldErrors.stream().distinct()
                .sorted(java.util.Comparator.comparing(ProblemFieldError::field)
                    .thenComparing(ProblemFieldError::message))
                .toList();
        }
    }
}
