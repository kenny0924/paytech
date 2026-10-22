package com.paytech.platform.support.workflow.service.processor;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.domain.action.ActionConfig;
import com.paytech.platform.support.workflow.domain.validation.WorkflowGraphProblem;
import com.paytech.platform.support.workflow.domainservice.convert.ActionConfigJsonReader;
import com.paytech.platform.support.workflow.domainservice.convert.WorkflowJsonFieldException;
import com.paytech.platform.support.workflow.domainservice.validator.ActionConfigValidator;

/**
 * 动作模板配置的解析与校验（新建、修改共用）
 * <li>结构错误（类型不符、未知属性或编码）按请求体字段返回 422 VALIDATION_ERROR，字段路径如 config.fields[2].type。</li>
 * <li>语义问题合并为一条中文拒绝原因。</li>
 */
@Component
public class WorkflowTemplateConfigs {

    /** 读取器错误路径的根，对应请求体的 config 属性。 */
    private static final String JSON_ROOT = "config_json";

    private static final String BODY_ROOT = "config";

    private final ActionConfigJsonReader reader = new ActionConfigJsonReader();
    private final ActionConfigValidator validator = new ActionConfigValidator();

    /**
     * 要求配置符合类型要求。
     *
     * @param type 动作类型
     * @param configJson 配置文本
     */
    public void requireValid(WorkflowActionType type, String configJson) {
        List<WorkflowGraphProblem> problems = validator.validate(read(type, configJson));
        if (!problems.isEmpty()) {
            throw new BizCheckFailException("动作模板配置不合法：" + problems.stream().map(WorkflowGraphProblem::message)
                .collect(Collectors.joining("；")));
        }
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
}
