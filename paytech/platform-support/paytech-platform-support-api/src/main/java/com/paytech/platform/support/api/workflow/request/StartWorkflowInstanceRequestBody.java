package com.paytech.platform.support.api.workflow.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import tools.jackson.databind.JsonNode;

/**
 * 发起流程实例
 * <li>definitionCode 与 definitionId + versionNo 二选一：给出 definitionCode 时另两项必须为空，按编码取当前发布版本；
 * 否则 definitionId 与 versionNo 都必填。违反返回 400 INVALID_REQUEST。</li>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StartWorkflowInstanceRequestBody {

    /**
     * 流程定义 ID
     * <li>按 ID 发起时必填；按 definitionCode 发起时必须为空。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "ID 必须是十进制字符串")
    private String definitionId;

    /**
     * 页面读取时的当前发布版本号
     * <li>按 ID 发起时必填，发布中途换版本时拒绝，防止按旧表单发起；按 definitionCode 发起时必须为空。</li>
     */
    @Positive
    private Integer versionNo;

    /**
     * 实例标题
     * <li>最长 128。</li>
     */
    @NotBlank
    @Size(max = 128)
    private String title;

    /**
     * 可选业务关联键
     * <li>最长 128，按原文精确匹配（区分大小写）；给出时同一流程定义下只允许一个 RUNNING 实例，重复发起返回已有实例
     * （created=false）。</li>
     */
    @Size(max = 128)
    private String businessKey;

    /**
     * 启动表单提交值
     * <li>{字段 key: 值}；发起后不可修改。FILE 字段为文件地址数组，如 ["/files/v1/123"]，格式不合法返回 422（字段路径 startValues.{key}[i]）。</li>
     */
    @NotNull
    private JsonNode startValues;

    /**
     * 对版本默认审批人的修改
     * <li>可空；只列需要修改的节点，审批人不能为空。</li>
     */
    @Size(max = 100)
    private List<@Valid NodeApprovers> approvers;

    /**
     * 对版本默认执行人的修改
     * <li>可空；只列需要修改的动作。</li>
     */
    @Size(max = 2000)
    private List<@Valid ActionExecutor> executors;

    /**
     * 流程定义编码
     * <li>与 definitionId + versionNo 二选一；给出时取该定义的当前发布版本。</li>
     */
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "definitionCode 格式不合法")
    private String definitionCode;

    /** 某节点审批人的修改。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeApprovers {

        /** 节点 key。 */
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "nodeKey 格式不合法")
        private String nodeKey;

        /** 审批人账号 ID<li>非空、不重复，都须是 ACTIVE 的 Operations 账号。</li> */
        @NotEmpty
        @Size(max = 50)
        private List<@NotNull @Pattern(regexp = "[1-9][0-9]{0,18}", message = "ID 必须是十进制字符串") String> approverIds;
    }

    /** 某动作执行人的修改。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionExecutor {

        /** 动作 key。 */
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "actionKey 格式不合法")
        private String actionKey;

        /** 执行人账号 ID<li>须是 ACTIVE 的 Operations 账号。</li> */
        @NotBlank
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "ID 必须是十进制字符串")
        private String executorId;
    }
}
