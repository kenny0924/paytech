package com.paytech.platform.support.auto.api;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.platform.support.api.files.FileAddress;

/**
 * 文件地址值类型：格式化与解析互逆，只接受 {@code /files/v1/} 加无前导零的正整数文件 ID。纯 Java 合同，不启动应用。
 *
 * @see FileAddress#of(long)
 * @see FileAddress#parse(String)
 */
class FileAddressTest {

    /** 由 ID 格式化、由文本解析得到同一地址；最大 long 可往返。 */
    @Test
    void test_normalFlow() {
        FileAddress formatted = FileAddress.of(880000000123L);
        FileAddress parsed = FileAddress.parse("/files/v1/880000000123");
        FileAddress maximum = FileAddress.parse("/files/v1/" + Long.MAX_VALUE);

        assertAll(() -> assertEquals("/files/v1/880000000123", formatted.value()),
            () -> assertEquals(880000000123L, parsed.fileId()),
            () -> assertEquals(formatted, parsed),
            () -> assertEquals(formatted.hashCode(), parsed.hashCode()),
            () -> assertEquals(formatted.value(), formatted.toString()),
            () -> assertEquals("/files/v1/1", FileAddress.of(1).value()),
            () -> assertEquals(Long.MAX_VALUE, maximum.fileId()),
            () -> assertNotEquals(formatted, FileAddress.of(880000000124L)));
    }

    /** 非正文件 ID 不能格式化。 */
    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void test_invalidFileId(long fileId) {
        assertThrows(IllegalArgumentException.class, () -> FileAddress.of(fileId));
    }

    /** 前缀、前导零、零、空白、尾部斜杠、查询串、大小写、非数字与超出 long 范围均拒绝。 */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "/files/v1/", "/files/v1/0", "/files/v1/0123", "/files/v1/-1", "files/v1/1",
        "/files/v2/1", "/FILES/v1/1", "/files/v1/1/", "/files/v1/1?x=1", " /files/v1/1", "/files/v1/1 ",
        "/files/v1/1a", "/files/v1/9223372036854775808", "/files/v1/99999999999999999999", "/platform/files/v1/1",
        "https://example.com/files/v1/1"})
    void test_invalidParameter(String value) {
        assertThrows(IllegalArgumentException.class, () -> FileAddress.parse(value));
    }
}
