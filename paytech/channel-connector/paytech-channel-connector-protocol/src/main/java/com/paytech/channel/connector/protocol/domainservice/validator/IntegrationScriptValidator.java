package com.paytech.channel.connector.protocol.domainservice.validator;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.protocol.domain.ParameterDeclaration;
import com.paytech.channel.connector.protocol.domain.ValidationIssue;
import com.paytech.channel.connector.protocol.domainservice.expression.ExpressionEngine;
import com.paytech.channel.connector.protocol.domainservice.expression.ExpressionInvalidException;
import com.paytech.channel.connector.protocol.domainservice.expression.ExpressionProfile;

import lombok.AllArgsConstructor;

/**
 * 公共脚本的静态规则（物理设计第 6 节）：参数声明最多 32 项、名称唯一、类型受支持，源码非空、不超过 32 KiB
 * 且能按脚本剖面编译（研发计划第 3 步第 4 节）；执行阶段由入口按允许值校验。
 */
@Component
@AllArgsConstructor
public class IntegrationScriptValidator {

    /** 参数声明上限。 */
    public static final int MAX_PARAMETERS = 32;

    /** 源码 UTF-8 字节上限。 */
    public static final int MAX_SOURCE_BYTES = 32 * 1024;

    static final Set<String> TYPES = Set.of("STRING", "BOOLEAN", "INTEGER", "DECIMAL", "OBJECT", "ARRAY",
        "PROTECTED_HANDLE");

    private static final Pattern NAME = Pattern.compile("[a-z][A-Za-z0-9]{0,63}");

    final ExpressionEngine expressionEngine;

    /**
     * 校验脚本参数与源码。
     *
     * @param parameters 参数声明，顺序即编辑器展示顺序
     * @param sourceCode 脚本源码
     * @return 发现的问题；为空表示通过
     */
    public List<ValidationIssue> validate(List<ParameterDeclaration> parameters, String sourceCode) {
        var issues = new ArrayList<ValidationIssue>();
        if (parameters.size() > MAX_PARAMETERS) {
            issues.add(new ValidationIssue("PARAMETER_LIMIT_EXCEEDED", "parameterDefinition", "参数声明最多 32 项"));
        }
        var names = new HashSet<String>();
        for (int index = 0; index < parameters.size(); index++) {
            var parameter = parameters.get(index);
            String location = "parameterDefinition[" + index + "]";
            if (!NAME.matcher(parameter.getName()).matches()) {
                issues.add(new ValidationIssue("PARAMETER_NAME_INVALID", location + ".name",
                    "参数名以小写字母开头，只能包含字母和数字，最多 64 字符"));
            } else if (!names.add(parameter.getName())) {
                issues.add(new ValidationIssue("PARAMETER_DUPLICATED", location + ".name", "参数名称重复"));
            }
            if (!TYPES.contains(parameter.getDataType())) {
                issues.add(new ValidationIssue("PARAMETER_TYPE_INVALID", location + ".dataType", "参数类型不受支持"));
            }
        }
        if (sourceCode == null || sourceCode.isBlank()) {
            issues.add(new ValidationIssue("SOURCE_EMPTY", "sourceCode", "脚本源码不能为空"));
        } else if (sourceCode.getBytes(StandardCharsets.UTF_8).length > MAX_SOURCE_BYTES) {
            issues.add(new ValidationIssue("SOURCE_TOO_LARGE", "sourceCode", "脚本源码不能超过 32 KiB"));
        } else {
            compile(sourceCode, issues);
        }
        return issues;
    }

    /** 按脚本剖面编译源码，失败记为 EXPRESSION_INVALID。 */
    void compile(String sourceCode, List<ValidationIssue> issues) {
        try {
            expressionEngine.compile(ExpressionProfile.SCRIPT, sourceCode);
        } catch (ExpressionInvalidException exception) {
            issues.add(new ValidationIssue("EXPRESSION_INVALID", "sourceCode", exception.getMessage()));
        }
    }
}
