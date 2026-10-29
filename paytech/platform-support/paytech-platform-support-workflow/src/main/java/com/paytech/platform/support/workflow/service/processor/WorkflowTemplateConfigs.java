package com.paytech.platform.support.workflow.service.processor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.dataquery.domain.QueryTemplateColumn;
import com.paytech.platform.support.dataquery.domain.QueryTemplateParameter;
import com.paytech.platform.support.dataquery.domain.QueryTemplateSignature;
import com.paytech.platform.support.dataquery.domainservice.service.QueryTemplateService;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.domain.action.ActionConfig;
import com.paytech.platform.support.workflow.domain.action.DataQueryActionConfig;
import com.paytech.platform.support.workflow.domain.validation.DataQueryTemplateFact;
import com.paytech.platform.support.workflow.domain.validation.WorkflowGraphProblem;
import com.paytech.platform.support.workflow.domainservice.convert.ActionConfigJsonReader;
import com.paytech.platform.support.workflow.domainservice.convert.WorkflowJsonFieldException;
import com.paytech.platform.support.workflow.domainservice.validator.ActionConfigValidator;
import com.paytech.platform.support.workflow.domainservice.validator.DataQueryTemplateRules;

import lombok.RequiredArgsConstructor;

/**
 * 动作模板配置的解析与校验（新建、修改共用），以及 DATA_QUERY 校验所需的 data-query 模板事实读取
 * <li>结构错误（类型不符、未知属性或编码）按请求体字段返回 422 VALIDATION_ERROR，字段路径如 config.fields[2].type。</li>
 * <li>语义问题合并为一条中文拒绝原因；DATA_QUERY 另按当前 data-query 模板事实校验（technique 8.4，只读本库）。</li>
 */
@Component
@RequiredArgsConstructor
public class WorkflowTemplateConfigs {

    /** 读取器错误路径的根，对应请求体的 config 属性。 */
    private static final String JSON_ROOT = "config_json";

    private static final String BODY_ROOT = "config";

    private final QueryTemplateService queryTemplates;
    private final ActionConfigJsonReader reader = new ActionConfigJsonReader();
    private final ActionConfigValidator validator = new ActionConfigValidator();
    private final DataQueryTemplateRules dataQueryRules = new DataQueryTemplateRules();

    /**
     * 要求配置符合类型要求；DATA_QUERY 同时要求与 data-query 模板事实一致。
     *
     * @param type 动作类型
     * @param configJson 配置文本
     */
    public void requireValid(WorkflowActionType type, String configJson) {
        List<WorkflowGraphProblem> problems = problems(type, configJson);
        if (!problems.isEmpty()) {
            throw new BizCheckFailException("动作模板配置不合法：" + problems.stream().map(WorkflowGraphProblem::message)
                .collect(Collectors.joining("；")));
        }
    }

    /**
     * 配置的语义问题：先按类型校验格式与上限，通过后 DATA_QUERY 再按当前 data-query 模板事实校验
     * （itemKey：参数问题为参数 key，列问题为输出名，模板问题为 null）。结构错误仍抛 422。
     *
     * @param type 动作类型
     * @param configJson 配置文本
     * @return 问题列表；没有问题时为空列表
     */
    public List<WorkflowGraphProblem> problems(WorkflowActionType type, String configJson) {
        ActionConfig config = read(type, configJson);
        List<WorkflowGraphProblem> problems = new ArrayList<>(validator.validate(config));
        if (problems.isEmpty() && config instanceof DataQueryActionConfig query) {
            problems.addAll(dataQueryRules.check(query, fact(query.queryCode()), null, null));
        }
        return problems;
    }

    /**
     * 按编码读取 data-query 模板事实；只读本库，可在事务内调用。
     *
     * @param queryCodes 模板编码
     * @return 编码到事实；不存在的模板不出现
     */
    public Map<String, DataQueryTemplateFact> facts(Collection<String> queryCodes) {
        Map<String, DataQueryTemplateFact> facts = new LinkedHashMap<>();
        queryCodes.stream().distinct().forEach(code -> {
            DataQueryTemplateFact fact = fact(code);
            if (fact != null) {
                facts.put(code, fact);
            }
        });
        return facts;
    }

    /**
     * 一个 data-query 模板的事实：签名转为校验输入，列名统一小写。
     *
     * @param queryCode 模板编码
     * @return 事实；模板不存在时为 null
     */
    DataQueryTemplateFact fact(String queryCode) {
        QueryTemplateSignature signature = queryTemplates.findTemplateSignature(queryCode);
        if (signature == null) {
            return null;
        }
        Map<String, Boolean> parameters = signature.getParameters().stream()
            .collect(Collectors.toMap(QueryTemplateParameter::getName, QueryTemplateParameter::isRequired,
                (first, second) -> first, LinkedHashMap::new));
        return new DataQueryTemplateFact(signature.getTemplateCode(),
            signature.getStatus() == QueryTemplateStatus.ACTIVE && signature.isDataSourceActive(), parameters,
            signature.getColumns().stream().map(column -> lower(column.getName())).collect(Collectors.toSet()),
            signature.getColumns().stream().filter(QueryTemplateColumn::isMasked).map(column -> lower(column.getName()))
                .collect(Collectors.toSet()));
    }

    /** 解析配置；结构错误投影为请求体字段错误。 */
    ActionConfig read(WorkflowActionType type, String configJson) {
        try {
            return reader.read(type, configJson);
        } catch (WorkflowJsonFieldException failure) {
            String field = failure.getPath().startsWith(JSON_ROOT)
                ? BODY_ROOT + failure.getPath().substring(JSON_ROOT.length()) : BODY_ROOT;
            throw new RequestBodyValidationException(List.of(new ProblemFieldError(field, failure.getReason())),
                failure);
        }
    }

    /**
     * 列名小写。
     *
     * @param name 列名
     * @return 小写列名
     */
    static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
