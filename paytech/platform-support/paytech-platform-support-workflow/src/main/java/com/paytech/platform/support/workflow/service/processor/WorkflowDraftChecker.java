package com.paytech.platform.support.workflow.service.processor;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.platform.support.workflow.core.common.WorkflowLimits;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionTemplate;
import com.paytech.platform.support.workflow.domain.action.ActionTemplateReference;
import com.paytech.platform.support.workflow.domain.form.FormDefinition;
import com.paytech.platform.support.workflow.domain.graph.WorkflowGraph;
import com.paytech.platform.support.workflow.domain.validation.WorkflowGraphProblem;
import com.paytech.platform.support.workflow.domain.validation.WorkflowPublicationInput;
import com.paytech.platform.support.workflow.domainservice.convert.ActionConfigJsonReader;
import com.paytech.platform.support.workflow.domainservice.convert.FormDefinitionJsonReader;
import com.paytech.platform.support.workflow.domainservice.convert.WorkflowGraphJsonReader;
import com.paytech.platform.support.workflow.domainservice.convert.WorkflowJsonFieldException;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowActionTemplateRepository;
import com.paytech.platform.support.workflow.domainservice.validator.WorkflowGraphValidator;

import lombok.RequiredArgsConstructor;

/**
 * 草稿保存与发布共用的解析与发布校验：读取被引用的模板当前事实与人员是否 ACTIVE，再执行 B1 校验。只读本库。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class WorkflowDraftChecker {

    /** 流程图读取器的错误路径根。 */
    private static final String GRAPH_ROOT = "graph_json";

    /** 启动表单读取器的错误路径根。 */
    private static final String FORM_ROOT = "start_form_json";

    private final WorkflowProcessorSupport support;
    private final WorkflowActionTemplateRepository templates;
    private final WorkflowGraphValidator validator = new WorkflowGraphValidator();
    private final ActionConfigJsonReader configReader = new ActionConfigJsonReader();
    private final WorkflowGraphJsonReader graphReader = new WorkflowGraphJsonReader();
    private final FormDefinitionJsonReader formReader = new FormDefinitionJsonReader();

    /**
     * 解析并校验一份草稿。
     *
     * @param graphJson 流程图文本
     * @param startFormJson 启动表单文本
     * @return 校验结果
     * @throws InvalidParameterException 结构不合法或超过硬上限
     */
    public Checked check(String graphJson, String startFormJson) {
        int bytes = graphJson.getBytes(StandardCharsets.UTF_8).length;
        int formBytes = startFormJson.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > WorkflowLimits.MAX_GRAPH_BYTES) {
            throw new InvalidParameterException("流程图大小 " + bytes + " 字节，超过上限 " + WorkflowLimits.MAX_GRAPH_BYTES
                + " 字节（48 KiB），不能保存");
        }
        if (formBytes > WorkflowLimits.MAX_START_FORM_BYTES) {
            throw new InvalidParameterException("启动表单大小 " + formBytes + " 字节，超过上限 "
                + WorkflowLimits.MAX_START_FORM_BYTES + " 字节（12 KiB），不能保存");
        }
        WorkflowGraph graph = graphReader.read(graphJson);
        FormDefinition form = formReader.read(startFormJson);
        Map<Long, WorkflowActionTemplate> rows = new LinkedHashMap<>();
        templates.findByIds(graph.referencedTemplateIds()).forEach(row -> rows.put(row.getActionTemplateId(), row));
        Map<Long, ActionTemplateReference> references = new LinkedHashMap<>();
        rows.values().forEach(row -> references.put(row.getActionTemplateId(), reference(row)));
        List<WorkflowGraphProblem> problems = validator.validate(new WorkflowPublicationInput(graph, bytes, form,
            references, support.activeAmong(graph.referencedAccountIds()), formBytes));
        return new Checked(graph, form, rows, problems);
    }

    /**
     * 解析并校验请求提交的草稿（保存草稿、发布）：结构错误按请求体字段返回 422 VALIDATION_ERROR，
     * 字段路径以 graph、startForm 为根，如 graph.nodes[2].type、startForm.fields[0].type。
     *
     * @param graphJson 流程图文本
     * @param startFormJson 启动表单文本
     * @return 校验结果
     */
    public Checked checkSubmitted(String graphJson, String startFormJson) {
        try {
            return check(graphJson, startFormJson);
        } catch (WorkflowJsonFieldException failure) {
            throw new RequestBodyValidationException(List.of(new ProblemFieldError(bodyPath(failure.getPath()),
                failure.getReason())), failure);
        }
    }

    /**
     * 读取器路径换成请求体字段路径：graph_json → graph，start_form_json → startForm。
     *
     * @param path 读取器路径
     * @return 请求体字段路径
     */
    static String bodyPath(String path) {
        for (Map.Entry<String, String> root : Map.of(GRAPH_ROOT, "graph", FORM_ROOT, "startForm").entrySet()) {
            if (path.equals(root.getKey()) || path.startsWith(root.getKey() + ".")
                || path.startsWith(root.getKey() + "[")) {
                return root.getValue() + path.substring(root.getKey().length());
            }
        }
        return path;
    }

    /**
     * 模板行转为校验所需的模板事实。
     *
     * @param row 模板行
     * @return 模板事实
     */
    ActionTemplateReference reference(WorkflowActionTemplate row) {
        return new ActionTemplateReference(row.getActionTemplateId(), row.getVersion(), row.getActionType(),
            row.getStatus(), configReader.read(row.getActionType(), row.getConfigJson()));
    }

    /**
     * 校验结果。
     *
     * @param graph 流程图
     * @param startForm 启动表单
     * @param templates 被引用且存在的模板行
     * @param problems 问题
     */
    public record Checked(WorkflowGraph graph, FormDefinition startForm, Map<Long, WorkflowActionTemplate> templates,
        List<WorkflowGraphProblem> problems) {
    }
}
