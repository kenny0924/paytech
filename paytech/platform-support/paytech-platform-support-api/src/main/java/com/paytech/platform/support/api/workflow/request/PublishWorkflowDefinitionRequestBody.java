package com.paytech.platform.support.api.workflow.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 发布当前草稿。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PublishWorkflowDefinitionRequestBody {

    /** 读取时流程定义的版本；不符返回 409。 */
    @NotNull
    @Positive
    private Integer expectedVersion;
}
