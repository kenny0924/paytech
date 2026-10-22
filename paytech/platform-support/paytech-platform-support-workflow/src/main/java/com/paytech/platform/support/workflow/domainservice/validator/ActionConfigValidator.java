package com.paytech.platform.support.workflow.domainservice.validator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.paytech.platform.support.workflow.core.common.WorkflowValueFormats;
import com.paytech.platform.support.workflow.core.enums.WorkflowProblemCode;
import com.paytech.platform.support.workflow.domain.action.ActionConfig;
import com.paytech.platform.support.workflow.domain.action.DataQueryActionConfig;
import com.paytech.platform.support.workflow.domain.action.FileUploadActionConfig;
import com.paytech.platform.support.workflow.domain.action.FormActionConfig;
import com.paytech.platform.support.workflow.domain.action.ManualConfirmActionConfig;
import com.paytech.platform.support.workflow.domain.action.PageParameter;
import com.paytech.platform.support.workflow.domain.action.PageTaskActionConfig;
import com.paytech.platform.support.workflow.domain.validation.WorkflowGraphProblem;

/**
 * 动作模板配置校验（技术方案 3.3）：新建与修改模板时按类型校验 config，一次返回全部问题。
 */
public final class ActionConfigValidator {

    /**
     * 校验一份动作模板配置。
     *
     * @param config 解析后的配置
     * @return 问题列表；没有问题时为空列表
     */
    public List<WorkflowGraphProblem> validate(ActionConfig config) {
        ProblemCollector problems = new ProblemCollector();
        switch (config) {
            case FormActionConfig form -> checkForm(form, problems);
            case FileUploadActionConfig upload -> checkFileUpload(upload, problems);
            case PageTaskActionConfig page -> checkPageTask(page, problems);
            case ManualConfirmActionConfig confirm -> {
            }
            case DataQueryActionConfig query -> {
            }
        }
        return problems.problems();
    }

    /**
     * 表单动作至少一个字段，字段规则同启动表单。
     *
     * @param form 表单动作配置
     * @param problems 问题收集
     */
    private void checkForm(FormActionConfig form, ProblemCollector problems) {
        if (form.form().fields().isEmpty()) {
            problems.graph(WorkflowProblemCode.ACTION_CONFIG_INVALID, "表单动作至少需要一个字段");
        }
        new FormDefinitionRules(problems).check(form.form(), "表单动作");
    }

    /**
     * 上传文件动作的数量与 MIME 类型。
     *
     * @param upload 上传文件动作配置
     * @param problems 问题收集
     */
    private void checkFileUpload(FileUploadActionConfig upload, ProblemCollector problems) {
        String invalid = FormDefinitionRules.fileCountProblem(upload.minFiles(), upload.maxFiles(),
            upload.mimeTypes().stream().anyMatch(mime -> !WorkflowValueFormats.isMimeType(mime)));
        if (invalid != null) {
            problems.graph(WorkflowProblemCode.ACTION_CONFIG_INVALID, "上传文件动作配置不合法：" + invalid);
        }
    }

    /**
     * 页面任务只允许控制台内部路由，参数 key 合法且不重复。
     *
     * @param page 页面任务配置
     * @param problems 问题收集
     */
    private void checkPageTask(PageTaskActionConfig page, ProblemCollector problems) {
        String route = page.pageRoute();
        boolean internal = route != null && route.startsWith("/") && !route.startsWith("//") && !route.contains("://")
            && !route.contains("\\");
        if (!internal) {
            problems.graph(WorkflowProblemCode.ACTION_CONFIG_INVALID, "pageRoute 必须是以 / 开头的控制台内部路由：" + route);
        }
        Set<String> keys = new HashSet<>();
        for (PageParameter parameter : page.parameters()) {
            if (!WorkflowValueFormats.isKey(parameter.key()) || !keys.add(parameter.key())) {
                problems.item(WorkflowProblemCode.ACTION_CONFIG_INVALID, null, parameter.key(),
                    "页面参数 key 不合法或重复：" + parameter.key());
            }
        }
    }
}
