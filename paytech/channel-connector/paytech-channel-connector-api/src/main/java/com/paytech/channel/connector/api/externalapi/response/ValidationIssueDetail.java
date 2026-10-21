package com.paytech.channel.connector.api.externalapi.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 一个静态校验问题。 */
public class ValidationIssueDetail {

    /**
     * 严重级别
     * <li>ERROR 阻止保存，WARNING 只提示。</li>
     */
    private final String severity;

    /** 问题代码 */
    private final String code;

    /**
     * 请求体路径
     * <li>如 definition.statusRules[0].condition。</li>
     */
    private final String location;

    /** 说明 */
    private final String message;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ValidationIssueDetail(
        @JsonProperty("severity") String severity,
        @JsonProperty("code") String code,
        @JsonProperty("location") String location,
        @JsonProperty("message") String message) {
        this.severity = severity;
        this.code = code;
        this.location = location;
        this.message = message;
    }

    /** 严重级别 */
    public String getSeverity() {
        return severity;
    }

    /** 问题代码 */
    public String getCode() {
        return code;
    }

    /** 请求体路径 */
    public String getLocation() {
        return location;
    }

    /** 说明 */
    public String getMessage() {
        return message;
    }
}
