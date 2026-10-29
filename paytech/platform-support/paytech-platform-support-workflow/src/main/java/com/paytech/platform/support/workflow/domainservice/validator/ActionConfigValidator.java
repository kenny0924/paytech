package com.paytech.platform.support.workflow.domainservice.validator;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.paytech.platform.support.workflow.core.common.WorkflowValueFormats;
import com.paytech.platform.support.workflow.core.enums.FormFieldType;
import com.paytech.platform.support.workflow.core.enums.WorkflowProblemCode;
import com.paytech.platform.support.workflow.domain.action.ActionConfig;
import com.paytech.platform.support.workflow.domain.action.DataQueryActionConfig;
import com.paytech.platform.support.workflow.domain.action.DataQueryOutput;
import com.paytech.platform.support.workflow.domain.action.FileUploadActionConfig;
import com.paytech.platform.support.workflow.domain.action.FormActionConfig;
import com.paytech.platform.support.workflow.domain.action.ManualConfirmActionConfig;
import com.paytech.platform.support.workflow.domain.action.PageParameter;
import com.paytech.platform.support.workflow.domain.action.PageTaskActionConfig;
import com.paytech.platform.support.workflow.domain.validation.WorkflowGraphProblem;

/**
 * 动作模板配置校验（技术方案 3.3）：新建与修改模板时按类型校验 config，一次返回全部问题。
 * <li>2026-10-22 起上传文件动作只有「至少一个」开关，存量 minFiles / maxFiles / mimeTypes 只按结构接受、不再校验；
 * 人工确认的 noteRequired 忽略。</li>
 * <li>DATA_QUERY（REQ-05）只校验本配置自身的格式与上限；查询模板是否存在、参数与列是否一致由调用方读取 data-query
 * 事实后另行校验。</li>
 */
public final class ActionConfigValidator {

    /** DATA_QUERY 参数与输出各自的上限。 */
    static final int MAX_DATA_QUERY_ITEMS = 20;

    /** 完成标准说明的平台长度上限。 */
    static final int INSTRUCTION_LIMIT = 2000;

    private static final Pattern QUERY_CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");

    private static final Pattern DESCRIPTION_KEY = Pattern.compile("[a-z][A-Za-z0-9_.-]{0,127}");

    private static final Set<FormFieldType> OUTPUT_TYPES = EnumSet.of(FormFieldType.TEXT, FormFieldType.NUMBER,
        FormFieldType.BOOLEAN, FormFieldType.ENUM, FormFieldType.DATE);

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
            case FileUploadActionConfig upload -> {
            }
            case PageTaskActionConfig page -> checkPageTask(page, problems);
            case ManualConfirmActionConfig confirm -> {
            }
            case DataQueryActionConfig query -> checkDataQuery(query, problems);
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

    /**
     * 数据查询动作：模板编码格式、参数 key 与输出声明合法且不重复、文案键与说明不越界。
     *
     * @param query 数据查询配置
     * @param problems 问题收集
     */
    private void checkDataQuery(DataQueryActionConfig query, ProblemCollector problems) {
        if (query.queryCode() == null || !QUERY_CODE.matcher(query.queryCode()).matches()) {
            problems.graph(WorkflowProblemCode.ACTION_CONFIG_INVALID,
                "queryCode 必须是大写字母开头、只含大写字母、数字或下划线的 2–64 位编码：" + query.queryCode());
        }
        checkDataQueryParameters(query.parameters(), problems);
        checkDataQueryOutputs(query.outputs(), problems);
        if (query.descriptionKey() != null && !DESCRIPTION_KEY.matcher(query.descriptionKey()).matches()) {
            problems.graph(WorkflowProblemCode.ACTION_CONFIG_INVALID, "descriptionKey 格式不合法：" + query.descriptionKey());
        }
        if (query.instruction() != null
            && query.instruction().codePointCount(0, query.instruction().length()) > INSTRUCTION_LIMIT) {
            problems.graph(WorkflowProblemCode.ACTION_CONFIG_INVALID, "instruction 超过 " + INSTRUCTION_LIMIT + " 个字符");
        }
    }

    /**
     * 查询参数 key 合法、不重复且不超过上限。
     *
     * @param parameters 参数 key
     * @param problems 问题收集
     */
    private void checkDataQueryParameters(List<String> parameters, ProblemCollector problems) {
        if (parameters.size() > MAX_DATA_QUERY_ITEMS) {
            problems.graph(WorkflowProblemCode.ACTION_CONFIG_INVALID, "查询参数超过 " + MAX_DATA_QUERY_ITEMS + " 个");
        }
        Set<String> keys = new HashSet<>();
        for (String key : parameters) {
            if (!WorkflowValueFormats.isKey(key) || !keys.add(key)) {
                problems.item(WorkflowProblemCode.ACTION_CONFIG_INVALID, null, key, "查询参数 key 不合法或重复：" + key);
            }
        }
    }

    /**
     * 输出声明：名称合法且不区分大小写不重复，类型受限，ENUM 带不重复的可选值，其他类型不带可选值。
     *
     * @param outputs 输出声明
     * @param problems 问题收集
     */
    private void checkDataQueryOutputs(List<DataQueryOutput> outputs, ProblemCollector problems) {
        if (outputs.size() > MAX_DATA_QUERY_ITEMS) {
            problems.graph(WorkflowProblemCode.ACTION_CONFIG_INVALID, "输出超过 " + MAX_DATA_QUERY_ITEMS + " 个");
        }
        Set<String> names = new HashSet<>();
        for (DataQueryOutput output : outputs) {
            if (!WorkflowValueFormats.isKey(output.name()) || !names.add(output.name().toLowerCase(Locale.ROOT))) {
                problems.item(WorkflowProblemCode.ACTION_CONFIG_INVALID, null, output.name(),
                    "输出名不合法或重复（不区分大小写）：" + output.name());
            }
            if (output.type() == null || !OUTPUT_TYPES.contains(output.type())) {
                problems.item(WorkflowProblemCode.ACTION_CONFIG_INVALID, null, output.name(),
                    "输出 " + output.name() + " 的类型只能是 TEXT、NUMBER、BOOLEAN、ENUM 或 DATE");
            }
            boolean isEnum = output.type() == FormFieldType.ENUM;
            boolean distinct = new HashSet<>(output.enumValues()).size() == output.enumValues().size();
            if (isEnum && (output.enumValues().isEmpty() || !distinct
                || output.enumValues().stream().anyMatch(WorkflowValueFormats::isBlank))) {
                problems.item(WorkflowProblemCode.ACTION_CONFIG_INVALID, null, output.name(),
                    "ENUM 输出 " + output.name() + " 至少需要一个不重复的可选值");
            }
            if (!isEnum && !output.enumValues().isEmpty()) {
                problems.item(WorkflowProblemCode.ACTION_CONFIG_INVALID, null, output.name(),
                    "只有 ENUM 输出可以配置可选值：" + output.name());
            }
        }
    }
}
