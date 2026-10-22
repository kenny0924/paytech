package com.paytech.operations.console.bff.service.model;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import tools.jackson.databind.JsonNode;

/**
 * 工作流页面的写请求体：与 Platform 工作流合同（paytech-platform-support-api 的 workflow.request）逐字段对应
 * <li>沿用 BFF 配置管理的 record 模型，由严格 Body 解析器拒绝未知、重复字段与类型强转；流程图与表单是结构化 JSON，
 * 其结构与语义由 Platform 严格校验。</li>
 * <li>响应体直接使用 Platform 工作流合同的响应类型（同路径转发，无聚合）。</li>
 */
public final class WorkflowModels {

    /** ID 的十进制字符串。 */
    public static final String ID = "[1-9][0-9]{0,18}";

    /** 节点、动作 key。 */
    public static final String KEY = "[A-Za-z][A-Za-z0-9_]{0,63}";

    /** 模板、流程编码。 */
    public static final String CODE = "[A-Z][A-Z0-9_]{0,63}";

    /** 只承载类型，禁止创建实例。 */
    private WorkflowModels() {}

    /** 启用或停用。
     * @param status ACTIVE 或 INACTIVE。
     * @param expectedVersion 读取时的版本。
     */
    public record ChangeWorkflowStatusRequestBody(
        @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status,
        @NotNull @Positive Integer expectedVersion) {}

    /** 新建动作模板。
     * @param templateCode 模板编码，创建后不可修改。
     * @param templateName 模板名称。
     * @param actionType FORM、FILE_UPLOAD、PAGE_TASK、MANUAL_CONFIRM、DATA_QUERY。
     * @param description 说明，可空。
     * @param config 按类型的配置对象。
     */
    public record CreateWorkflowActionTemplateRequestBody(
        @NotBlank @Pattern(regexp = CODE) String templateCode,
        @NotBlank @Size(max = 128) String templateName,
        @NotBlank @Pattern(regexp = "FORM|FILE_UPLOAD|PAGE_TASK|MANUAL_CONFIRM|DATA_QUERY") String actionType,
        @Size(max = 512) String description,
        @NotNull JsonNode config) {}

    /** 修改动作模板。
     * @param templateName 模板名称。
     * @param description 说明，可空。
     * @param config 按类型的配置对象。
     * @param expectedVersion 读取时的版本。
     */
    public record UpdateWorkflowActionTemplateRequestBody(
        @NotBlank @Size(max = 128) String templateName,
        @Size(max = 512) String description,
        @NotNull JsonNode config,
        @NotNull @Positive Integer expectedVersion) {}

    /** 新建流程定义。
     * @param definitionCode 流程编码，创建后不可修改。
     * @param definitionName 流程名称。
     * @param description 说明，可空。
     */
    public record CreateWorkflowDefinitionRequestBody(
        @NotBlank @Pattern(regexp = CODE) String definitionCode,
        @NotBlank @Size(max = 128) String definitionName,
        @Size(max = 512) String description) {}

    /** 保存草稿。
     * @param definitionName 流程名称。
     * @param description 说明，可空。
     * @param graph 流程图。
     * @param startForm 启动表单。
     * @param expectedVersion 读取时流程定义的版本。
     */
    public record SaveWorkflowDraftRequestBody(
        @NotBlank @Size(max = 128) String definitionName,
        @Size(max = 512) String description,
        @NotNull JsonNode graph,
        @NotNull JsonNode startForm,
        @NotNull @Positive Integer expectedVersion) {}

    /** 发布草稿。
     * @param expectedVersion 读取时流程定义的版本。
     */
    public record PublishWorkflowDefinitionRequestBody(@NotNull @Positive Integer expectedVersion) {}

    /** 发起实例。
     * @param definitionId 流程定义 ID。
     * @param versionNo 页面读取时的当前发布版本号。
     * @param title 实例标题。
     * @param businessKey 业务关联键，可空。
     * @param startValues 启动表单提交值。
     * @param approvers 对默认审批人的修改，可空。
     * @param executors 对默认执行人的修改，可空。
     */
    public record StartWorkflowInstanceRequestBody(
        @NotBlank @Pattern(regexp = ID) String definitionId,
        @NotNull @Positive Integer versionNo,
        @NotBlank @Size(max = 128) String title,
        @Size(max = 128) String businessKey,
        @NotNull JsonNode startValues,
        @Size(max = 100) List<@Valid NodeApprovers> approvers,
        @Size(max = 2000) List<@Valid ActionExecutor> executors) {}

    /** 某节点审批人的修改。
     * @param nodeKey 节点 key。
     * @param approverIds 审批人账号 ID。
     */
    public record NodeApprovers(
        @NotBlank @Pattern(regexp = KEY) String nodeKey,
        @NotEmpty @Size(max = 50) List<@NotNull @Pattern(regexp = ID) String> approverIds) {}

    /** 某动作执行人的修改。
     * @param actionKey 动作 key。
     * @param executorId 执行人账号 ID。
     */
    public record ActionExecutor(
        @NotBlank @Pattern(regexp = KEY) String actionKey,
        @NotBlank @Pattern(regexp = ID) String executorId) {}

    /** 提交动作。
     * @param output 提交内容。
     */
    public record SubmitWorkflowActionRequestBody(@NotNull JsonNode output) {}

    /** 终止实例。
     * @param reason 终止原因。
     */
    public record TerminateWorkflowInstanceRequestBody(@NotBlank @Size(max = 512) String reason) {}

    /** 转交动作或审批。
     * @param toUserId 新处理人账号 ID。
     * @param reason 转交说明，可空。
     */
    public record TransferWorkflowTaskRequestBody(
        @NotBlank @Pattern(regexp = ID) String toUserId,
        @Size(max = 512) String reason) {}

    /** 审批决定。
     * @param decision APPROVE、RETURN、REJECT。
     * @param comment 意见；退回修改与驳回必填。
     * @param rejectTargetNodeKey 汇合后驳回的目标节点，可空。
     */
    public record DecideWorkflowApprovalRequestBody(
        @NotBlank @Pattern(regexp = "APPROVE|RETURN|REJECT") String decision,
        @Size(max = 512) String comment,
        @Pattern(regexp = KEY) String rejectTargetNodeKey) {}

    /** 开始上传附件；来源、原因与上传人由 Platform 确定。
     * @param filename 文件名，最多 255 个字符。
     * @param mimeType 声明的规范 MIME，小写 type/subtype。
     * @param sizeBytes 声明字节数，正整数十进制字符串。
     */
    public record StartWorkflowAttachmentUploadRequestBody(
        @NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 128) @Pattern(regexp = "[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*") String mimeType,
        @NotBlank @Pattern(regexp = ID) String sizeBytes) {

        /** 不记录可能包含个人信息的文件名。 */
        @Override
        public String toString() {
            return "StartWorkflowAttachmentUploadRequestBody[REDACTED]";
        }
    }
}
