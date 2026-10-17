package com.paytech.migration.auto;

import com.paytech.migration.service.ChecksumCalculator;
import com.paytech.migration.service.ChecksumCalculatorImpl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 校验和按 LF 归一化：CRLF、CR、BOM 不影响结果，内容变化影响结果。
 *
 * @see ChecksumCalculator#checksum(String)
 * @author Claude
 */
class ChecksumCalculatorAutoTest {

    /**
     * {@code printf 'SELECT 1;\n' | shasum -a 256}。
     */
    private static final String SELECT_ONE_LF = "b4e0497804e46e0a0b0b8c31975b062152d551bac49c3c2e80932567b4085dcd";

    private final ChecksumCalculator calculator = new ChecksumCalculatorImpl();

    @Test
    void test_lineEndingsNormalized() {
        Assertions.assertEquals(SELECT_ONE_LF, calculator.checksum("SELECT 1;\n"));
        Assertions.assertEquals(SELECT_ONE_LF, calculator.checksum("SELECT 1;\r\n"));
        Assertions.assertEquals(SELECT_ONE_LF, calculator.checksum("SELECT 1;\r"));
        Assertions.assertEquals(SELECT_ONE_LF, calculator.checksum("﻿SELECT 1;\r\n"));
        Assertions.assertEquals(calculator.checksum("A;\nB;\n"), calculator.checksum("A;\r\nB;\r\n"));
    }

    @Test
    void test_contentChange() {
        Assertions.assertNotEquals(SELECT_ONE_LF, calculator.checksum("SELECT 1;"));
        Assertions.assertNotEquals(SELECT_ONE_LF, calculator.checksum("SELECT 2;\n"));
        Assertions.assertEquals(64, calculator.checksum("").length());
    }
}
