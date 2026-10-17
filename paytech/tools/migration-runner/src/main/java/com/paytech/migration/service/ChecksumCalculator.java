package com.paytech.migration.service;

/**
 * 迁移脚本与初始化数据的校验和。
 *
 * @author Claude
 */
public interface ChecksumCalculator {

    /**
     * 计算校验和：去掉 UTF-8 BOM，把 CRLF 与单独的 CR 归一化为 LF，再对 UTF-8 字节取 SHA-256。
     *
     * @param content 文件全文
     * @return 64 位小写十六进制
     */
    String checksum(String content);
}
