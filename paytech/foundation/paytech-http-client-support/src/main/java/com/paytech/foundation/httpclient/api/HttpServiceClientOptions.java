package com.paytech.foundation.httpclient.api;

import java.util.Objects;
import java.util.function.Function;

import org.springframework.http.client.ClientHttpRequestInterceptor;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.httpclient.core.transport.InternalTokenExchangeInterceptor;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;

/** Immutable wire-contract mode for a typed HTTP service client. @author Codex */
public final class HttpServiceClientOptions {

    private static final DependencyFailureMapper DEFAULT_DEPENDENCY_FAILURE_MAPPER =
        (kind, cause) -> new ServiceUnavailableException(cause);
    private static final HttpServiceClientOptions STRICT = new HttpServiceClientOptions(false,
        DEFAULT_DEPENDENCY_FAILURE_MAPPER, 0, false, null, null, false);
    private static final HttpServiceClientOptions MIGRATION_WITH_LEGACY_ERROR_RESPONSE =
        new HttpServiceClientOptions(true, DEFAULT_DEPENDENCY_FAILURE_MAPPER, 0, false, null, null, false);

    private final boolean legacyErrorResponseEnabled;
    private final DependencyFailureMapper dependencyFailureMapper;
    private final int strictJsonResponseBytes;
    private final boolean requestValidationFailuresEnabled;
    private final Function<String, ClientHttpRequestInterceptor> internalTokenInterceptor;
    private final String internalTokenTarget;
    private final boolean ownerProblemDetailEnabled;

    private HttpServiceClientOptions(boolean legacyErrorResponseEnabled, DependencyFailureMapper dependencyFailureMapper,
        int strictJsonResponseBytes, boolean requestValidationFailuresEnabled,
        Function<String, ClientHttpRequestInterceptor> internalTokenInterceptor, String internalTokenTarget,
        boolean ownerProblemDetailEnabled) {
        this.legacyErrorResponseEnabled = legacyErrorResponseEnabled;
        this.dependencyFailureMapper = Objects.requireNonNull(dependencyFailureMapper,
            "dependencyFailureMapper must not be null");
        this.strictJsonResponseBytes = strictJsonResponseBytes;
        this.requestValidationFailuresEnabled = requestValidationFailuresEnabled;
        this.internalTokenInterceptor = internalTokenInterceptor;
        this.internalTokenTarget = internalTokenTarget;
        this.ownerProblemDetailEnabled = ownerProblemDetailEnabled;
    }

    public static HttpServiceClientOptions strict() {
        return STRICT;
    }

    public static HttpServiceClientOptions migrationWithLegacyErrorResponse() {
        return MIGRATION_WITH_LEGACY_ERROR_RESPONSE;
    }

    /**
     * Returns an immutable option set whose target maps validated dependency failures.
     * The mapper must preserve the supplied cause in the returned local exception.
     */
    public HttpServiceClientOptions withDependencyFailureMapper(DependencyFailureMapper mapper) {
        return new HttpServiceClientOptions(legacyErrorResponseEnabled, mapper, strictJsonResponseBytes,
            requestValidationFailuresEnabled, internalTokenInterceptor, internalTokenTarget, ownerProblemDetailEnabled);
    }

    /**
     * 对要求闭合 JSON 的接口启用标准 Jackson 严格绑定，并限制成功与错误响应的字节数。
     * 不接受缺失构造参数、null 原始类型、重复/未知字段、隐式类型转换和尾随 JSON。
     * nullable 引用字段仍允许显式 null；字段业务含义继续由调用方验证。
     */
    public HttpServiceClientOptions withStrictJsonResponses(int maximumBytes) {
        if (maximumBytes <= 0 || maximumBytes == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maximumBytes must be between 1 and Integer.MAX_VALUE - 1");
        }
        return new HttpServiceClientOptions(legacyErrorResponseEnabled, dependencyFailureMapper, maximumBytes,
            requestValidationFailuresEnabled, internalTokenInterceptor, internalTokenTarget, ownerProblemDetailEnabled);
    }

    /**
     * 显式接收合规的 422 字段校验失败；调用方必须按本地请求合同投影字段，不能直接外显上游路径。
     * 默认关闭，其他客户端及旧四字段错误 Reader 保持原有分类。
     */
    public HttpServiceClientOptions withRequestValidationFailures() {
        return new HttpServiceClientOptions(legacyErrorResponseEnabled, dependencyFailureMapper,
            strictJsonResponseBytes, true, internalTokenInterceptor, internalTokenTarget, ownerProblemDetailEnabled);
    }

    /**
     * 显式外显 Owner 的业务拒绝说明：严格 Problem 的 BUSINESS_CHECK_FAILED、CONCURRENT_REQUEST、NOT_FOUND 与已登记合同码
     * 以远端已校验的 detail 作为本地异常消息，宿主 MVC 边界据此输出 Owner 原因。detail 空白、超过 512 字符或含控制字符时
     * 回退公共默认消息；INVALID_REQUEST、字段校验失败、5xx 与旧四字段格式保持原有分类和消息。
     * 默认关闭；只应对已承诺 detail 为可安全外显提示的第一方 Owner 开启。
     */
    public HttpServiceClientOptions withOwnerProblemDetail() {
        return new HttpServiceClientOptions(legacyErrorResponseEnabled, dependencyFailureMapper, strictJsonResponseBytes,
            requestValidationFailuresEnabled, internalTokenInterceptor, internalTokenTarget, true);
    }

    /**
     * 为 {@link HttpServiceClientFactory#configureGroup} 创建的客户端注入内部调用 Token（D26）；目标服务名取 Group 名。
     * 需要宿主依赖 {@code paytech-internal-auth}；Purpose 由接口方法上的 {@code @InternalPurpose} 覆盖。
     *
     * @param supplier Token 来源
     * @return 新选项
     */
    public HttpServiceClientOptions withInternalToken(InternalTokenSupplier supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        return new HttpServiceClientOptions(legacyErrorResponseEnabled, dependencyFailureMapper,
            strictJsonResponseBytes, requestValidationFailuresEnabled,
            target -> new InternalTokenExchangeInterceptor(supplier, target), null, ownerProblemDetailEnabled);
    }

    /**
     * 为 {@link HttpServiceClientFactory#create} 创建的客户端注入内部调用 Token，显式给出目标服务发现名。
     *
     * @param supplier Token 来源
     * @param targetService 目标服务名（Token {@code aud}）
     * @return 新选项
     */
    public HttpServiceClientOptions withInternalToken(InternalTokenSupplier supplier, String targetService) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        Objects.requireNonNull(targetService, "targetService must not be null");
        return new HttpServiceClientOptions(legacyErrorResponseEnabled, dependencyFailureMapper,
            strictJsonResponseBytes, requestValidationFailuresEnabled,
            target -> new InternalTokenExchangeInterceptor(supplier, target), targetService,
            ownerProblemDetailEnabled);
    }

    /**
     * 按目标服务名创建 Token 拦截器；未启用时返回 null。
     *
     * @param groupName HTTP Service Group 名，{@code create} 路径为 null
     * @return 拦截器或 null
     * @throws IllegalArgumentException 启用了 Token 但无法确定目标服务名
     */
    ClientHttpRequestInterceptor internalTokenInterceptor(String groupName) {
        if (internalTokenInterceptor == null) {
            return null;
        }
        String target = internalTokenTarget != null ? internalTokenTarget : groupName;
        if (target == null) {
            throw new IllegalArgumentException("withInternalToken(supplier) requires configureGroup; "
                + "use withInternalToken(supplier, targetService) with create()");
        }
        return internalTokenInterceptor.apply(target);
    }

    boolean ownerProblemDetailEnabled() {
        return ownerProblemDetailEnabled;
    }

    boolean requestValidationFailuresEnabled() {
        return requestValidationFailuresEnabled;
    }

    int strictJsonResponseBytes() {
        return strictJsonResponseBytes;
    }

    boolean legacyErrorResponseEnabled() {
        return legacyErrorResponseEnabled;
    }

    DependencyFailureMapper dependencyFailureMapper() {
        return dependencyFailureMapper;
    }

    /** Stable, Foundation-generic categories for dependency failures. */
    public enum DependencyFailureKind {
        TIMEOUT,

        UNAVAILABLE
    }

    /** Maps one validated dependency failure to the target's safe local exception. */
    @FunctionalInterface
    public interface DependencyFailureMapper {

        RuntimeException map(DependencyFailureKind kind, Throwable cause);
    }
}
