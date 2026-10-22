package com.paytech.platform.support.workflow.service.processor.request;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

import tools.jackson.databind.JsonNode;

/** 发起实例的内部请求。 */
@Getter
@AllArgsConstructor
public class StartInstanceRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话。 */
    @NotBlank
    private final String session;

    /** 定义 ID。 */
    @NotNull
    private final Long definitionId;

    /** 页面读取时的当前发布版本号。 */
    @NotNull
    private final Integer versionNo;

    /** 标题。 */
    @NotBlank
    private final String title;

    /** 业务关联键；可空。 */
    private final String businessKey;

    /** 启动值。 */
    @NotNull
    private final JsonNode startValues;

    /** 节点审批人修改；可为空映射。 */
    @NotNull
    private final Map<String, List<Long>> approvers;

    /** 动作执行人修改；可为空映射。 */
    @NotNull
    private final Map<String, Long> executors;
}
