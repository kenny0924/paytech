package com.paytech.platform.support.dataquery.template;

import java.util.List;

import com.paytech.platform.support.dataquery.core.enums.TemplateProblemLevel;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 保存校验结果：按发现顺序排列、已去重的问题列表。 */
@Getter
@AllArgsConstructor
public final class TemplateValidationResult {

    /** 问题；只有 ERROR 阻止保存。 */
    private final List<TemplateProblem> problems;

    /**
     * 是否存在阻止保存的错误。
     *
     * @return true 表示不能保存
     */
    public boolean hasErrors() {
        return problems.stream().anyMatch(problem -> problem.getLevel() == TemplateProblemLevel.ERROR);
    }
}
