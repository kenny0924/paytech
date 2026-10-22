package com.paytech.card.integration;

import java.io.IOException;
import java.io.InputStream;

import com.paytech.card.domain.binlibrary.BinLibrarySourceFile;

/**
 * Platform 内部文件接口端口（BIN 库导入）：查询文件元数据、签发短时下载地址并流式读取原版本字节。
 * <li>访问外部服务，只能在数据库事务外由 Processor 或补偿 Handler 调用。</li>
 */
public interface PlatformFileClient {
    /**
     * 查询文件元数据
     *
     * @param fileId 文件 ID
     * @return 元数据；Platform 返回文件不存在时为 null
     */
    BinLibrarySourceFile getFile(long fileId);

    /**
     * 签发下载地址（最长 60 秒有效）后立即建立连接，把原版本字节流交给读取方
     *
     * @param fileId 文件 ID
     * @param reader 读取方；流在其返回后关闭
     * @param <T> 读取结果类型
     * @return 读取方的结果
     *         <li>下载地址返回非 2xx、连接或读取中断时抛出运行时异常，调用方按可重试处理。</li>
     */
    <T> T download(long fileId, ContentReader<T> reader);

    /**
     * 文件字节读取方
     *
     * @param <T> 读取结果类型
     */
    @FunctionalInterface
    interface ContentReader<T> {
        /**
         * 读取文件字节
         *
         * @param content 原版本字节流
         * @return 读取结果
         * @throws IOException 读取失败
         */
        T read(InputStream content) throws IOException;
    }
}
