package com.paytech.foundation.common.auto.web;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PagingRequestBodyTest {

    @Test
    void test_missingParametersUseDefaultsIndependently() {
        Assertions.assertEquals(new PagingRequestBody(1, 10), PagingRequestBody.of(null, null));
        Assertions.assertEquals(new PagingRequestBody(2, 10), PagingRequestBody.of(2, null));
        Assertions.assertEquals(new PagingRequestBody(1, 50), PagingRequestBody.of(null, 50));
        Assertions.assertEquals(0, PagingRequestBody.of(null, null).offset());
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 20, 50, 100, 200})
    void test_supportedSizesHaveOneBasedOffsets(int size) {
        Assertions.assertEquals(size, new PagingRequestBody(2, size).offset());
        Assertions.assertEquals(99_999 * size, new PagingRequestBody(100_000, size).offset());
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 100_001, Integer.MAX_VALUE})
    void test_invalidPageIsRejectedBeforeOffsetCalculation(int page) {
        InvalidParameterException exception = Assertions.assertThrows(InvalidParameterException.class,
            () -> PagingRequestBody.of(page, 200));

        Assertions.assertEquals("INVALID_REQUEST", exception.getCode());
        Assertions.assertEquals("页码必须在 1 到 100000 之间", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 11, 30, 199, 201, Integer.MAX_VALUE})
    void test_unsupportedSizeIsRejectedInsteadOfRounded(int size) {
        InvalidParameterException exception = Assertions.assertThrows(InvalidParameterException.class,
            () -> new PagingRequestBody(1, size));

        Assertions.assertEquals("INVALID_REQUEST", exception.getCode());
        Assertions.assertEquals("每页条数必须为 10、20、50、100 或 200", exception.getMessage());
    }
}
