package com.paytech.platform.support.workflow.domainservice.convert;

import java.io.Serial;

import com.paytech.foundation.common.error.InvalidParameterException;

import lombok.Getter;

/**
 * 工作流配置 JSON 的结构错误：携带出错位置的 JSON 路径与不含提交原值的原因。
 *
 * <p>请求入口可把它投影为请求 Body 字段错误（422 VALIDATION_ERROR）；未投影时仍按参数错误 400 处理，消息为“路径 原因”。</p>
 */
@Getter
public class WorkflowJsonFieldException extends InvalidParameterException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 出错位置的 JSON 路径，根为 graph_json、start_form_json、config_json 等。 */
    private final String path;

    /** 可外显的原因，不含提交的原值。 */
    private final String reason;

    /**
     * 构造结构错误。
     *
     * @param path JSON 路径
     * @param reason 原因
     */
    public WorkflowJsonFieldException(String path, String reason) {
        this(path, reason, null);
    }

    /**
     * 构造带原因异常的结构错误。
     *
     * @param path JSON 路径
     * @param reason 原因
     * @param cause 诊断用原因，不外显
     */
    public WorkflowJsonFieldException(String path, String reason, Throwable cause) {
        super(path + " " + reason, cause);
        this.path = path;
        this.reason = reason;
    }
}
