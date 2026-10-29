package com.paytech.risk.control.domain.underwriting;

import java.io.Serial;
import java.util.List;

import com.paytech.foundation.common.error.BizCheckFailException;

/** 承保字段组合或提交必填校验失败；Web 转为 422 VALIDATION_ERROR 并按 errors[] 定位到请求 Body 字段。 */
public final class UnderwritingValidationException extends BizCheckFailException {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 字段问题：外部 JSON 路径与可外显的中文说明。 */
    public record Issue(String field, String message) {
    }

    private final List<Issue> issues;

    /**
     * 构造校验失败
     *
     * @param issues 至少一个字段问题
     */
    public UnderwritingValidationException(List<Issue> issues) {
        super("承保字段校验未通过：" + issues.getFirst().field());
        this.issues = List.copyOf(issues);
    }

    /** 字段问题。 */
    public List<Issue> issues() {
        return issues;
    }
}
