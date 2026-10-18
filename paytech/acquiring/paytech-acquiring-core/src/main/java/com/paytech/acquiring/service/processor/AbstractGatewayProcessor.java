package com.paytech.acquiring.service.processor;

import java.util.Objects;

/**
 * 从 Gateway 进入 Commerce 的命令处理模板。
 *
 * <p>
 * HTTP 校验、异常响应和请求日志由现有 Web 基础设施负责；本模板只固定应用层处理顺序。
 *
 * @param <Request>
 *            应用请求
 * @param <Response>
 *            应用结果
 */
public abstract class AbstractGatewayProcessor<Request, Response> {

    /**
     * 校验并处理 Gateway 命令。
     *
     * @param request
     *            应用请求
     * @return 应用处理结果
     */
    public final Response process(Request request) {
        validateBasic(request);
        validate(request);
        return processRequest(request);
    }

    /** 校验所有 Gateway 命令共享的必要输入。 */
    protected void validateBasic(Request request) {
        Objects.requireNonNull(request, "request");
    }

    /** 执行具体命令的附加无副作用校验。 */
    protected void validate(Request request) {}

    /** 执行具体命令。 */
    protected abstract Response processRequest(Request request);
}
