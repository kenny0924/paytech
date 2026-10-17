package com.paytech.migration.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 基于 JDK {@link MessageDigest} 的 SHA-256 实现。
 *
 * @author Claude
 */
public class ChecksumCalculatorImpl implements ChecksumCalculator {

    private static final char BYTE_ORDER_MARK = '﻿';

    @Override
    public String checksum(String content) {
        String normalized = content.isEmpty() || content.charAt(0) != BYTE_ORDER_MARK ? content : content.substring(1);
        normalized = normalized.replace("\r\n", "\n").replace('\r', '\n');
        return HexFormat.of().formatHex(sha256().digest(normalized.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 新建 SHA-256 摘要器；该算法是 JDK 必备算法。
     *
     * @return 摘要器
     */
    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }
}
