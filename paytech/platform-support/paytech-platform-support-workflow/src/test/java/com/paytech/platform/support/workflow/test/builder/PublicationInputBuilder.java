package com.paytech.platform.support.workflow.test.builder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.paytech.platform.support.workflow.domain.action.ActionTemplateReference;
import com.paytech.platform.support.workflow.domain.form.FormDefinition;
import com.paytech.platform.support.workflow.domain.graph.WorkflowGraph;
import com.paytech.platform.support.workflow.domain.validation.WorkflowPublicationInput;

/**
 * 发布校验输入构造器：默认使用全部测试模板、全部 ACTIVE 人员与启动表单基线，图大小 1 KiB。
 */
public final class PublicationInputBuilder {

    private final WorkflowGraph graph;

    private int graphJsonBytes = 1024;

    private int startFormJsonBytes = 512;

    private FormDefinition startForm = WorkflowFixtures.startForm();

    private final Map<Long, ActionTemplateReference> templates = new HashMap<>(WorkflowFixtures.templates());

    private final Set<Long> activeAccounts = new HashSet<>(WorkflowFixtures.activeAccounts());

    /**
     * 以流程图开始构造。
     *
     * @param graph 流程图
     */
    private PublicationInputBuilder(WorkflowGraph graph) {
        this.graph = graph;
    }

    /**
     * 以流程图开始构造。
     *
     * @param graph 流程图
     * @return 构造器
     */
    public static PublicationInputBuilder of(WorkflowGraph graph) {
        return new PublicationInputBuilder(graph);
    }

    /**
     * 设置 start_form_json 字节数。
     *
     * @param bytes 字节数
     * @return 构造器
     */
    public PublicationInputBuilder startFormJsonBytes(int bytes) {
        this.startFormJsonBytes = bytes;
        return this;
    }

    /**
     * 设置 graph_json 字节数。
     *
     * @param bytes 字节数
     * @return 构造器
     */
    public PublicationInputBuilder graphJsonBytes(int bytes) {
        this.graphJsonBytes = bytes;
        return this;
    }

    /**
     * 设置启动表单。
     *
     * @param form 启动表单
     * @return 构造器
     */
    public PublicationInputBuilder startForm(FormDefinition form) {
        this.startForm = form;
        return this;
    }

    /**
     * 去掉一个模板，模拟模板不存在。
     *
     * @param templateId 模板 ID
     * @return 构造器
     */
    public PublicationInputBuilder withoutTemplate(long templateId) {
        templates.remove(templateId);
        return this;
    }

    /**
     * 构造输入。
     *
     * @return 发布校验输入
     */
    public WorkflowPublicationInput build() {
        return new WorkflowPublicationInput(graph, graphJsonBytes, startForm, templates, activeAccounts,
            startFormJsonBytes);
    }
}
