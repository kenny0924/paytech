package com.paytech.foundation.httpproblem.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;

/**
 * 字段错误异常的两种入口：公共 VALIDATION_ERROR 与 Owner 业务码。
 *
 * @see RequestBodyValidationException#RequestBodyValidationException(CodeMessageEnum, List, Throwable)
 */
class RequestBodyValidationExceptionAutoTest {

    /** 公共入口固定 VALIDATION_ERROR 与其默认消息。 */
    @Test
    void test_commonEntryUsesValidationError() {
        var failure = new RequestBodyValidationException(List.of(new ProblemFieldError("name", "必填")), null);

        assertSame(CommonErrorCode.VALIDATION_ERROR, failure.getErrorCode());
        assertEquals(CommonErrorCode.VALIDATION_ERROR.getMessage(), failure.getMessage());
    }

    /** Owner 业务码保留码与默认消息，字段清单不可变且与调用方集合隔离。 */
    @Test
    void test_ownerCodeKeepsCodeMessageAndImmutableErrors() {
        var source = new ArrayList<>(List.of(new ProblemFieldError("contact.phone", "手机号须带国家码")));
        var failure = new RequestBodyValidationException(OwnerCode.INVALID_FORM, source, null);
        source.clear();

        assertSame(OwnerCode.INVALID_FORM, failure.getErrorCode());
        assertEquals(OwnerCode.INVALID_FORM.getMessage(), failure.getMessage());
        assertEquals(List.of(new ProblemFieldError("contact.phone", "手机号须带国家码")), failure.errors());
        assertThrows(UnsupportedOperationException.class, () -> failure.errors().add(new ProblemFieldError("a", "b")));
    }

    /** 空白字段或文案、缺少错误码均拒绝构造。 */
    @Test
    void test_rejectsBlankEntriesAndMissingCode() {
        assertThrows(IllegalArgumentException.class, () -> new RequestBodyValidationException(OwnerCode.INVALID_FORM,
            List.of(new ProblemFieldError(" ", "说明")), null));
        assertThrows(NullPointerException.class, () -> new RequestBodyValidationException(OwnerCode.INVALID_FORM,
            List.of(new ProblemFieldError("name", null)), null));
        assertThrows(NullPointerException.class, () -> new RequestBodyValidationException(null,
            List.of(new ProblemFieldError("name", "说明")), null));
    }

    /** 测试用 Owner 业务码。 */
    enum OwnerCode implements CodeMessageEnum {
        INVALID_FORM;

        @Override
        public String getCode() {
            return "INVALID_FORM";
        }

        @Override
        public String getMessage() {
            return "表单字段不符合约定";
        }
    }
}
