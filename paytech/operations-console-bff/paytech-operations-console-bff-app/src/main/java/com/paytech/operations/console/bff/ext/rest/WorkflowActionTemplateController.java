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
import com.paytech.operations.console.bff.service.model.WorkflowModels.CreateWorkflowActionTemplateRequestBody;
import com.paytech.operations.console.bff.service.model.WorkflowModels.UpdateWorkflowActionTemplateRequestBody;
import com.paytech.operations.console.bff.service.model.WorkflowModels.ValidateWorkflowActionTemplateRequestBody;
import com.paytech.platform.support.api.workflow.response.WorkflowActionTemplateResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowActionTemplateValidationResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowMutationResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowTemplateReferenceListResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 工作流动作目录的浏览器入口，全部简单转发 Platform 同路径
 * <li>每个路由由 Gateway 绑定 WORKFLOW_DEFINITION_READ（查询）或 WORKFLOW_ACTION_TEMPLATE_MANAGE（写），并经 PLT-I02 实时授权；Platform 再次校验。</li>
 * <li>Query 与 Body 按配置管理的严格边界解析：未知字段、重复或多余 Query、查询带 Body 均在访问 Platform 前拒绝。</li>
 */
@RestController
@AllArgsConstructor
@RequestMapping("/workflow-admin/v1/action-templates")
public class WorkflowActionTemplateController {

    private final PlatformWorkflowClient client;

    /** 动作模板分页。 */
    @GetMapping
    public PagingResponseBody<WorkflowActionTemplateResponseBody> listActionTemplates(OperatorContext context,
        @RequestParam(value = "actionType", required = false) String actionType,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return client.listActionTemplates(context, actionType, status, keyword, page, size);
    }

    /** 动作模板详情（含配置）。 */
    @GetMapping("/{templateId}")
    public WorkflowActionTemplateResponseBody getActionTemplate(OperatorContext context,
        @PathVariable("templateId") @Pattern(regexp = WorkflowModels.ID) String templateId) {
        return client.getActionTemplate(context, templateId);
    }

    /** 引用该模板的已发布流程版本：冻结版本、是否等于当前版本、运行中实例数。 */
    @GetMapping("/{templateId}/references")
    public WorkflowTemplateReferenceListResponseBody listTemplateReferences(OperatorContext context,
        @PathVariable("templateId") @Pattern(regexp = WorkflowModels.ID) String templateId) {
        return client.listTemplateReferences(context, templateId);
    }

    /** 新建动作模板（ACTIVE）。 */
    @PostMapping
    public WorkflowMutationResponseBody createActionTemplate(OperatorContext context,
        @Valid @RequestBody CreateWorkflowActionTemplateRequestBody request) {
        return client.createActionTemplate(context, request);
    }

    /** 校验动作模板配置，不保存（REQ-05）：逐项问题供页面定位。 */
    @PostMapping("/validation")
    public WorkflowActionTemplateValidationResponseBody validateActionTemplate(OperatorContext context,
        @Valid @RequestBody ValidateWorkflowActionTemplateRequestBody request) {
        return client.validateActionTemplate(context, request);
    }

    /** 修改动作模板；不影响已发布版本。 */
    @PutMapping("/{templateId}")
    public WorkflowMutationResponseBody updateActionTemplate(OperatorContext context,
        @PathVariable("templateId") @Pattern(regexp = WorkflowModels.ID) String templateId,
        @Valid @RequestBody UpdateWorkflowActionTemplateRequestBody request) {
        return client.updateActionTemplate(context, templateId, request);
    }

    /** 启用或停用动作模板。 */
    @PostMapping("/{templateId}/status")
    public WorkflowMutationResponseBody changeActionTemplateStatus(OperatorContext context,
        @PathVariable("templateId") @Pattern(regexp = WorkflowModels.ID) String templateId,
        @Valid @RequestBody ChangeWorkflowStatusRequestBody request) {
        return client.changeActionTemplateStatus(context, templateId, request);
    }
}
