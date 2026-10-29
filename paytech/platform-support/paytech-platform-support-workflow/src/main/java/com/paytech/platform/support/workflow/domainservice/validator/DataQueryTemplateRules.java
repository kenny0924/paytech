package com.paytech.platform.support.workflow.domainservice.validator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.paytech.platform.support.workflow.core.enums.WorkflowProblemCode;
import com.paytech.platform.support.workflow.domain.action.DataQueryActionConfig;
import com.paytech.platform.support.workflow.domain.validation.DataQueryTemplateFact;
import com.paytech.platform.support.workflow.domain.validation.WorkflowGraphProblem;

/**
 * DATA_QUERY 配置与 data-query 模板事实的一致性（technique 8.4）：动作模板新建 / 修改与流程发布校验共用。纯内存计算。
 * <li>模板须存在且模板与数据源都是 ACTIVE；参数列表与模板声明一致（不多列、不漏必填）。</li>
 * <li>模板声明了出参列时，satisfied、missing 与每个 out_ 列都须在声明中；这些列都不能配置屏蔽规则。</li>
 */
public final class DataQueryTemplateRules {

    /** 判定列名。 */
    public static final String SATISFIED_COLUMN = "satisfied";

    /** 原因码列名。 */
    public static final String MISSING_COLUMN = "missing";

    /**
     * 校验一份 DATA_QUERY 配置。
     *
     * @param config 数据查询配置
     * @param fact 模板事实；模板不存在时为 null
     * @param nodeKey 问题所在节点；动作模板校验时为 null
     * @param itemKey 问题定位条目（节点动作 key）；动作模板校验时为 null，此时参数问题定位到参数 key、
     *        列问题定位到输出名（satisfied / missing 为列名），模板问题为 null
     * @return 问题列表；没有问题时为空列表
     */
    public List<WorkflowGraphProblem> check(DataQueryActionConfig config, DataQueryTemplateFact fact, String nodeKey,
        String itemKey) {
        List<WorkflowGraphProblem> problems = new ArrayList<>();
        String code = config.queryCode();
        if (fact == null) {
            problems.add(problem(WorkflowProblemCode.DATA_QUERY_TEMPLATE_NOT_FOUND, nodeKey, itemKey,
                "查询模板不存在：" + code));
            return problems;
        }
        if (!fact.active()) {
            problems.add(problem(WorkflowProblemCode.DATA_QUERY_TEMPLATE_INACTIVE, nodeKey, itemKey,
                "查询模板或其数据源已停用：" + code));
        }
        checkParameters(config, fact, nodeKey, itemKey, problems);
        checkColumns(config, fact, nodeKey, itemKey, problems);
        return problems;
    }

    /**
     * 参数列表与模板声明一致：列出的参数须已声明，必填参数须列出。
     *
     * @param config 数据查询配置
     * @param fact 模板事实
     * @param nodeKey 节点
     * @param itemKey 条目
     * @param problems 问题列表
     */
    void checkParameters(DataQueryActionConfig config, DataQueryTemplateFact fact, String nodeKey, String itemKey,
        List<WorkflowGraphProblem> problems) {
        for (String key : config.parameters()) {
            if (!fact.parameterRequired().containsKey(key)) {
                problems.add(problem(WorkflowProblemCode.DATA_QUERY_PARAMETER_UNDECLARED, nodeKey, item(itemKey, key),
                    "查询模板 " + config.queryCode() + " 未声明参数 " + key));
            }
        }
        for (Map.Entry<String, Boolean> entry : fact.parameterRequired().entrySet()) {
            if (entry.getValue() && !config.parameters().contains(entry.getKey())) {
                problems.add(problem(WorkflowProblemCode.DATA_QUERY_PARAMETER_MISSING, nodeKey,
                    item(itemKey, entry.getKey()),
                    "未列出查询模板 " + config.queryCode() + " 的必填参数 " + entry.getKey()));
            }
        }
    }

    /**
     * 判定、原因码与输出列须已声明（模板声明了出参列时）且未配置屏蔽。
     *
     * @param config 数据查询配置
     * @param fact 模板事实
     * @param nodeKey 节点
     * @param itemKey 条目
     * @param problems 问题列表
     */
    void checkColumns(DataQueryActionConfig config, DataQueryTemplateFact fact, String nodeKey, String itemKey,
        List<WorkflowGraphProblem> problems) {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put(SATISFIED_COLUMN, SATISFIED_COLUMN);
        columns.put(MISSING_COLUMN, MISSING_COLUMN);
        config.outputs().forEach(output -> columns.put(output.column(), output.name()));
        columns.forEach((column, detail) -> {
            String name = column.toLowerCase(Locale.ROOT);
            if (!fact.declaredColumns().isEmpty() && !fact.declaredColumns().contains(name)) {
                problems.add(problem(WorkflowProblemCode.DATA_QUERY_OUTPUT_UNDECLARED, nodeKey, item(itemKey, detail),
                    "查询模板 " + config.queryCode() + " 没有返回列 " + column));
            }
            if (fact.maskedColumns().contains(name)) {
                problems.add(problem(WorkflowProblemCode.DATA_QUERY_OUTPUT_MASKED, nodeKey, item(itemKey, detail),
                    "查询模板 " + config.queryCode() + " 的列 " + column + " 已屏蔽，不能用于判断"));
            }
        });
    }

    /**
     * 问题定位条目：流程发布时为节点动作 key，动作模板校验时为参数 key 或输出名。
     *
     * @param actionKey 节点动作 key；动作模板校验时为 null
     * @param detail 参数 key 或输出名
     * @return 条目
     */
    static String item(String actionKey, String detail) {
        return actionKey != null ? actionKey : detail;
    }

    /**
     * 构造一条问题。
     *
     * @param code 问题码
     * @param nodeKey 节点
     * @param itemKey 条目
     * @param message 中文说明
     * @return 问题
     */
    static WorkflowGraphProblem problem(WorkflowProblemCode code, String nodeKey, String itemKey, String message) {
        return new WorkflowGraphProblem(code, nodeKey, null, itemKey, message);
    }
}
