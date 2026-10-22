package com.paytech.platform.support.workflow.domain.validation;

import java.util.Map;
import java.util.Set;

import com.paytech.platform.support.workflow.domain.action.ActionTemplateReference;
import com.paytech.platform.support.workflow.domain.form.FormDefinition;
import com.paytech.platform.support.workflow.domain.graph.WorkflowGraph;

/**
 * 发布校验的输入：草稿内容与调用方事先查好的模板、人员事实。校验本身不访问数据库。
 *
 * @param graph 解析后的流程图
 * @param graphJsonBytes graph_json 的 UTF-8 字节数
 * @param startForm 解析后的启动表单
 * @param templates 流程图引用的动作模板，按模板 ID；缺失即视为不存在
 * @param activeOperationsAccountIds 流程图引用的人员中，属于 ACTIVE Operations 账号的 ID
 * @param startFormJsonBytes start_form_json 的 UTF-8 字节数
 */
public record WorkflowPublicationInput(WorkflowGraph graph, int graphJsonBytes, FormDefinition startForm,
    Map<Long, ActionTemplateReference> templates, Set<Long> activeOperationsAccountIds, int startFormJsonBytes) {

    /** 复制集合，保持不可变。 */
    public WorkflowPublicationInput {
        templates = Map.copyOf(templates);
        activeOperationsAccountIds = Set.copyOf(activeOperationsAccountIds);
    }

    /**
     * 不关心启动表单大小的输入（字节数记为 0）。
     *
     * @param graph 解析后的流程图
     * @param graphJsonBytes graph_json 的 UTF-8 字节数
     * @param startForm 解析后的启动表单
     * @param templates 动作模板
     * @param activeOperationsAccountIds ACTIVE Operations 账号
     */
    public WorkflowPublicationInput(WorkflowGraph graph, int graphJsonBytes, FormDefinition startForm,
        Map<Long, ActionTemplateReference> templates, Set<Long> activeOperationsAccountIds) {
        this(graph, graphJsonBytes, startForm, templates, activeOperationsAccountIds, 0);
    }
}
