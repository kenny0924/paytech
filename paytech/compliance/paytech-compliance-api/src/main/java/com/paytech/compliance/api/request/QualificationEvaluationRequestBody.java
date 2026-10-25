package com.paytech.compliance.api.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 批量资格判定请求；每种角色要求的资格类型由 compliance 决定，调用方不传。 */
public class QualificationEvaluationRequestBody {

    /**
     * 要判定的主体
     * <li>1—50 个；同一角色类型与标识重复出现时只判定一次。</li>
     */
    @NotEmpty
    @Size(max = 50)
    private List<@NotNull @Valid QualificationSubjectBody> subjects;

    /** 读取要判定的主体。 */
    public List<QualificationSubjectBody> getSubjects() {
        return subjects;
    }

    /** 设置要判定的主体。 */
    public void setSubjects(List<QualificationSubjectBody> subjects) {
        this.subjects = subjects;
    }
}
