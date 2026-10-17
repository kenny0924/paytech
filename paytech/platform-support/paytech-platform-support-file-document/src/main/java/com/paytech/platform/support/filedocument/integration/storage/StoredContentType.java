package com.paytech.platform.support.filedocument.integration.storage;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/** 按文件头识别入驻材料的媒体类型；只认可 PDF、PNG、JPEG，其他内容返回通用二进制类型。 */
public final class StoredContentType {
    /** 无法识别的内容，不会出现在任何允许名单中。 */
    public static final String UNKNOWN = "application/octet-stream";
    /** 识别所需的最长文件头字节数。 */
    static final int HEADER_BYTES = 8;
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private StoredContentType() {
    }

    /**
     * 读取文件头识别类型后复位流，调用方继续从首字节读取完整内容。
     *
     * @param stream 支持 mark/reset 的字节流
     * @return 识别出的 MIME
     */
    public static String detect(BufferedInputStream stream) throws IOException {
        stream.mark(HEADER_BYTES);
        byte[] header = stream.readNBytes(HEADER_BYTES);
        stream.reset();
        return detect(header);
    }

    /** 以缓冲流包装原始流，保证可以先识别再完整读取。 */
    public static BufferedInputStream buffered(InputStream stream) {
        return stream instanceof BufferedInputStream buffered ? buffered : new BufferedInputStream(stream);
    }

    /** 纯文件头比较，不读取浏览器声明或扩展名。 */
    static String detect(byte[] header) {
        if (startsWith(header, PDF)) {
            return "application/pdf";
        }
        if (startsWith(header, PNG)) {
            return "image/png";
        }
        if (startsWith(header, JPEG)) {
            return "image/jpeg";
        }
        return UNKNOWN;
    }

    /** 文件头长度不足时视为不匹配。 */
    private static boolean startsWith(byte[] header, byte[] signature) {
        if (header.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (header[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
