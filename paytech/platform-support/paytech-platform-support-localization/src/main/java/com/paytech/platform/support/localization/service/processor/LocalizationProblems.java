package com.paytech.platform.support.localization.service.processor;

import java.util.List;

import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.platform.support.api.enums.LocalizationErrorCode;
import com.paytech.platform.support.localization.domain.LocalizationProblem;

/** 把校验问题投影为带 errors[] 的 Problem；每次最多列出 200 条。 */
final class LocalizationProblems {

    /** 单次响应列出的问题上限（技术方案 3.5、3.3）。 */
    static final int LIMIT = 200;

    private LocalizationProblems() {
    }

    /**
     * 有问题时以公共 VALIDATION_ERROR（422）拒绝。
     *
     * @param problems 问题
     */
    static void rejectIfAny(List<LocalizationProblem> problems) {
        if (!problems.isEmpty()) {
            throw reject(CommonErrorCode.VALIDATION_ERROR, problems, null);
        }
    }

    /**
     * ICU 问题以 I18N_MESSAGE_INVALID（422）拒绝。
     *
     * @param problems ICU 问题
     */
    static void rejectInvalidMessages(List<LocalizationProblem> problems) {
        if (!problems.isEmpty()) {
            throw reject(LocalizationErrorCode.I18N_MESSAGE_INVALID, problems, null);
        }
    }

    /**
     * 以给定错误码拒绝；超过上限时只列前 200 条，并追加一条 field 为 totalField 的总数说明。
     *
     * @param code 错误码
     * @param problems 问题
     * @param totalField 总数说明的字段名；为 null 时不追加
     * @return 待抛出的异常
     */
    static RequestBodyValidationException reject(CodeMessageEnum code, List<LocalizationProblem> problems,
        String totalField) {
        List<ProblemFieldError> errors = new java.util.ArrayList<>(problems.stream().limit(LIMIT)
            .map(problem -> new ProblemFieldError(problem.field(), problem.message())).toList());
        if (totalField != null) {
            errors.add(new ProblemFieldError(totalField, "共 " + problems.size() + " 条" + (problems.size() > LIMIT
                ? "，仅列出前 " + LIMIT + " 条" : "")));
        }
        return new RequestBodyValidationException(code, errors, null);
    }
}
