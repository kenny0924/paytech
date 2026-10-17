package com.paytech.foundation.common.auto.error;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.*;

class BusinessExceptionTest {

    @Test
    void preservesExplicitBusinessContext() {
        BizCheckFailException exception = new BizCheckFailException("Client不存在: clientId=10001");

        assertInstanceOf(FailException.class, exception);
        assertSame(CommonErrorCode.BUSINESS_CHECK_FAILED, exception.errorCode());
        assertEquals("BUSINESS_CHECK_FAILED", exception.getCode());
        assertEquals("Client不存在: clientId=10001", exception.getMessage());
    }

    @Test
    void usesStableDefaultWhenMessageIsBlank() {
        BusinessException exception = new BusinessException(CommonErrorCode.INVALID_REQUEST, "  ");

        assertEquals(CommonErrorCode.INVALID_REQUEST.getMessage(), exception.getMessage());
    }

    @Test
    void retainsOriginalCause() {
        IllegalStateException cause = new IllegalStateException("provider failure");

        BusinessException exception = new BusinessException(CommonErrorCode.INTERNAL_ERROR, "系统处理失败", cause);

        assertSame(cause, exception.getCause());
    }

    @Test
    void classifiesUnknownErrorsAsRetryableCategory() {
        IllegalStateException cause = new IllegalStateException("provider result is unknown");

        ErrorException exception = new ErrorException(cause);

        assertSame(CommonErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        assertEquals(CommonErrorCode.INTERNAL_ERROR.getCode(), exception.getCode());
        assertEquals(CommonErrorCode.INTERNAL_ERROR.getMessage(), exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void classifiesUnavailableDependencyForHttpAdapters() {
        IllegalStateException cause = new IllegalStateException("database unavailable");
        ServiceUnavailableException exception =
            new ServiceUnavailableException(CommonErrorCode.INTERNAL_ERROR, cause);

        assertInstanceOf(ErrorException.class, exception);
        assertSame(CommonErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        assertEquals(CommonErrorCode.INTERNAL_ERROR.getMessage(), exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void test_invalidParameterUsesPublicCategoryAndRetainsSafeContext() {
        IllegalArgumentException cause = new IllegalArgumentException("internal parser diagnostic");
        InvalidParameterException exception = new InvalidParameterException("page 必须大于零", cause);

        assertInstanceOf(FailException.class, exception);
        assertSame(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
        assertEquals("INVALID_REQUEST", exception.getCode());
        assertEquals("page 必须大于零", exception.getMessage());
        assertSame(cause, exception.getCause());
        assertEquals(CommonErrorCode.INVALID_REQUEST.getMessage(), new InvalidParameterException().getMessage());
        assertEquals(CommonErrorCode.INVALID_REQUEST.getMessage(), new InvalidParameterException(" ").getMessage());
    }

    @Test
    void test_concurrentRequestUsesPublicCategoryAndRetainsSafeContext() {
        IllegalStateException cause = new IllegalStateException("internal version diagnostic");
        ConcurrentRequestException exception = new ConcurrentRequestException("该记录已被更新，请重新加载", cause);

        assertInstanceOf(FailException.class, exception);
        assertSame(CommonErrorCode.CONCURRENT_REQUEST, exception.getErrorCode());
        assertEquals("CONCURRENT_REQUEST", exception.getCode());
        assertEquals("该记录已被更新，请重新加载", exception.getMessage());
        assertSame(cause, exception.getCause());
        assertEquals(CommonErrorCode.CONCURRENT_REQUEST.getMessage(), new ConcurrentRequestException().getMessage());
        assertEquals(CommonErrorCode.CONCURRENT_REQUEST.getMessage(), new ConcurrentRequestException(" ").getMessage());
    }

    @Test
    void test_unavailableDependencyNeedsNoBusinessErrorCode() {
        IllegalStateException cause = new IllegalStateException("internal connection diagnostic");
        ServiceUnavailableException exception = new ServiceUnavailableException(cause);

        assertInstanceOf(ErrorException.class, exception);
        assertSame(CommonErrorCode.SERVICE_UNAVAILABLE, exception.getErrorCode());
        assertEquals("SERVICE_UNAVAILABLE", exception.getCode());
        assertEquals(CommonErrorCode.SERVICE_UNAVAILABLE.getMessage(), exception.getMessage());
        assertSame(cause, exception.getCause());
        assertEquals(CommonErrorCode.SERVICE_UNAVAILABLE.getMessage(), new ServiceUnavailableException().getMessage());
    }

    @Test
    void test_businessCheckRetainsCauseWithoutChangingPublicCategory() {
        IllegalStateException cause = new IllegalStateException("internal business diagnostic");
        BizCheckFailException exception = new BizCheckFailException("Client 尚未启用", cause);

        assertSame(CommonErrorCode.BUSINESS_CHECK_FAILED, exception.getErrorCode());
        assertEquals("Client 尚未启用", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void rejectsMissingErrorCode() {
        assertThrows(NullPointerException.class, () -> new BusinessException(null, "无效异常"));
        assertThrows(NullPointerException.class, () -> new ErrorException(null, "无效异常"));
    }
}
