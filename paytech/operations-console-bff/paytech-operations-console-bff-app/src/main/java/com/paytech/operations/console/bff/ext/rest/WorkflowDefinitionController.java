package com.paytech.operations.console.bff.ext.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformWorkflowClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.WorkflowModels;
import com.paytech.operations.console.bff.service.model.WorkflowModels.ChangeWorkflowStatusRequestBody;
import com.paytech.operations.console.bff.service.model.WorkflowModels.CreateWorkflowDefinitionRequestBody;
import com.paytech.operations.console.bff.service.model.WorkflowModels.PublishWorkflowDefinitionRequestBody;
import com.paytech.operations.console.bff.service.model.WorkflowModels.SaveWorkflowDraftRequestBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDefinitionDetailResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDefinitionSummaryBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDraftResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowMutationResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowVersionBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;

/**
 * 工作流流程定义的浏览器入口，全部简单转发 Platform 同路径
 * <li>每个路由由 Gateway 绑定 WORKFLOW_DEFINITION_READ（查询）或 WORKFLOW_DEFINITION_MANAGE（写），并经 PLT-I02 实时授权；Platform 再次校验。</li>
 * <li>发布校验不通过时保留 Platform 400 的业务原因；完整问题列表从定义详情读取。</li>
 */
@RestController
@AllArgsConstructor
@RequestMapping("/workflow-admin/v1/definitions")
public class WorkflowDefinitionController {

    private final PlatformWorkflowClient client;

    /** 流程定义分页。 */
    @GetMapping
    public PagingResponseBody<WorkflowDefinitionSummaryBody> listDefinitions(OperatorContext context,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return client.listDefinitions(context, status, keyword, page, size);
    }

    /** 定义、草稿（含校验问题）与版本列表。 */
    @GetMapping("/{definitionId}")
    public WorkflowDefinitionDetailResponseBody getDefinition(OperatorContext context,
        @PathVariable("definitionId") @Pattern(regexp = WorkflowModels.ID) String definitionId) {
        return client.getDefinition(context, definitionId);
    }

    /** 新建定义及草稿 v1。 */
    @PostMapping
    public WorkflowMutationResponseBody createDefinition(OperatorContext context,
        @Valid @RequestBody CreateWorkflowDefinitionRequestBody request) {
        return client.createDefinition(context, request);
    }

    /** 保存草稿，返回校验问题。 */
    @PutMapping("/{definitionId}/draft")
    public WorkflowDraftResponseBody saveDraft(OperatorContext context,
        @PathVariable("definitionId") @Pattern(regexp = WorkflowModels.ID) String definitionId,
        @Valid @RequestBody SaveWorkflowDraftRequestBody request) {
        return client.saveDraft(context, definitionId, request);
    }

    /** 发布草稿。 */
    @PostMapping("/{definitionId}/publication")
    public WorkflowMutationResponseBody publishDefinition(OperatorContext context,
        @PathVariable("definitionId") @Pattern(regexp = WorkflowModels.ID) String definitionId,
        @Valid @RequestBody PublishWorkflowDefinitionRequestBody request) {
        return client.publishDefinition(context, definitionId, request);
    }

    /** 启用或停用流程定义。 */
    @PostMapping("/{definitionId}/status")
    public WorkflowMutationResponseBody changeDefinitionStatus(OperatorContext context,
        @PathVariable("definitionId") @Pattern(regexp = WorkflowModels.ID) String definitionId,
        @Valid @RequestBody ChangeWorkflowStatusRequestBody request) {
        return client.changeDefinitionStatus(context, definitionId, request);
    }

    /** 查看某个版本。 */
    @GetMapping("/{definitionId}/versions/{versionNo}")
    public WorkflowVersionBody getVersion(OperatorContext context,
        @PathVariable("definitionId") @Pattern(regexp = WorkflowModels.ID) String definitionId,
        @PathVariable("versionNo") @Positive Integer versionNo) {
        return client.getVersion(context, definitionId, versionNo);
    }
}
