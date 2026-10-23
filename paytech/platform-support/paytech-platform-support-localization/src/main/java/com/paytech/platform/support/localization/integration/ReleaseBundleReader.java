package com.paytech.platform.support.localization.integration;

import com.paytech.platform.support.api.files.FileAddress;

/**
 * 语言包读取端口：两次发布的差异按发布的文件内容对比（技术方案 3.2）；在事务外调用。
 * <li>实现 {@code FileDocumentReleaseBundleReader}：经 file-document 按地址读取原版本字节并核对登记摘要。</li>
 */
public interface ReleaseBundleReader {

    /**
     * 读取语言包字节。
     *
     * @param fileAddress 文件服务地址
     * @return 语言包字节
     */
    byte[] read(FileAddress fileAddress);
}
