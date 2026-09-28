package com.paytech.foundation.common.auto.enums;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.common.error.CommonErrorCode;

class CodeEnumTest {

    @Test
    void returnsStableCodeWithoutUsingEnumName() {
        assertEquals("INVALID_REQUEST", CommonErrorCode.INVALID_REQUEST.getCode());
        assertEquals("VALIDATION_ERROR", CommonErrorCode.VALIDATION_ERROR.getCode());
        assertEquals("NOT_FOUND", CommonErrorCode.NOT_FOUND.getCode());
        assertEquals("METHOD_NOT_ALLOWED", CommonErrorCode.METHOD_NOT_ALLOWED.getCode());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", CommonErrorCode.UNSUPPORTED_MEDIA_TYPE.getCode());
        assertEquals(CommonErrorCode.INVALID_REQUEST.getCode(), CodeEnum.defaultCode(CommonErrorCode.INVALID_REQUEST));
        assertTrue(CommonErrorCode.INVALID_REQUEST instanceof CodeMessageEnum);
    }

    @Test
    void supportsNullAndDefaultValues() {
        assertNull(CodeEnum.defaultCode(null));
        assertEquals(CommonErrorCode.INTERNAL_ERROR.getCode(),
            CodeEnum.defaultCode(null, CommonErrorCode.INTERNAL_ERROR));
    }

    @Test
    void everyCommonErrorCodeIsNonBlankAndUnique() {
        Set<String> codes = new HashSet<>();
        for (CommonErrorCode errorCode : CommonErrorCode.values()) {
            assertFalse(errorCode.getCode().isBlank());
            assertTrue(codes.add(errorCode.getCode()), () -> "错误码重复: " + errorCode.getCode());
        }
    }
}
