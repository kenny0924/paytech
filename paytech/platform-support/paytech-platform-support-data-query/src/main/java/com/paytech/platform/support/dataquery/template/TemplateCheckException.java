package com.paytech.platform.support.dataquery.template;

import java.io.Serial;

import com.paytech.foundation.common.error.BizCheckFailException;

import lombok.Getter;

/**
 * 模板解析或渲染失败；携带定位到正文行列的问题，执行路径直接作为 400 返回，保存校验转为问题列表。
 */
@Getter
public class TemplateCheckException extends BizCheckFailException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 问题，含行、列与原因。 */
    private final transient TemplateProblem problem;

    /**
     * 以正文位置与原因构造。
     *
     * @param position 位置
     * @param message 原因
     */
    public TemplateCheckException(SourcePosition position, String message) {
        this(TemplateProblem.error(position, message), null);
    }

    /**
     * 以问题与原因异常构造。
     *
     * @param problem 问题
     * @param cause 原因异常；没有时为 null
     */
    public TemplateCheckException(TemplateProblem problem, Throwable cause) {
        super(problem.describe(), cause);
        this.problem = problem;
    }
}
