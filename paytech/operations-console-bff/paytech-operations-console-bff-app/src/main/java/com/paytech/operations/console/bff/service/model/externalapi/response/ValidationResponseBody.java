package com.paytech.operations.console.bff.service.model.externalapi.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 只校验不保存的结果。 */
public class ValidationResponseBody {

    /**
     * 是否可以保存
     * <li>没有 ERROR 级问题时为 true。</li>
     */
    private final boolean valid;

    /** 全部问题 */
    private final List<ValidationIssueDetail> issues;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ValidationResponseBody(
        @JsonProperty("valid") boolean valid,
        @JsonProperty("issues") List<ValidationIssueDetail> issues) {
        this.valid = valid;
        this.issues = issues == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(issues));
    }

    /** 是否可以保存 */
    public boolean isValid() {
        return valid;
    }

    /** 全部问题 */
    public List<ValidationIssueDetail> getIssues() {
        return issues;
    }
}
