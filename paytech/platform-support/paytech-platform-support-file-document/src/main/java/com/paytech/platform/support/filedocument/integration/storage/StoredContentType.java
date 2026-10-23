package com.paytech.platform.support.filedocument.integration.storage;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 按文件内容识别媒体类型；认可 PDF、PNG、JPEG 文件头与 UTF-8 文本（CSV），其他内容返回通用二进制类型。
 * <li>文本只看开头 {@link #SNIFF_BYTES} 字节：没有上述文件头、不含 NUL、是合法 UTF-8（容忍末尾被截断的多字节字符）即为
 * text/csv；全文是否合法由格式检查 {@code FormatFileScanner} 复核。</li>
 */
public final class StoredContentType {
    /** PDF 文件头识别结果。 */
    public static final String PDF_TYPE = "application/pdf";
    /** PNG 文件头识别结果。 */
    public static final String PNG_TYPE = "image/png";
    /** JPEG 文件头识别结果。 */
    public static final String JPEG_TYPE = "image/jpeg";
    /** 无法识别的内容，不会出现在任何允许名单中。 */
    public static final String UNKNOWN = "application/octet-stream";
    /** UTF-8 文本内容，供卡 BIN 库等 CSV 上传使用。 */
    public static final String CSV = "text/csv";
    /** {@link #detect} 能返回的全部规范 MIME（不含 {@link #UNKNOWN}），按 PDF、PNG、JPEG、CSV 顺序；检查策略只能从中选择。 */
    public static final List<String> SUPPORTED = List.of(PDF_TYPE, PNG_TYPE, JPEG_TYPE, CSV);
    /** 识别所需的最长读取字节数：文件头与文本抽样共用。 */
    static final int SNIFF_BYTES = 8192;
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private StoredContentType() {
    }

    /**
     * 读取开头字节识别类型后复位流，调用方继续从首字节读取完整内容。
     *
     * @param stream 支持 mark/reset 的字节流
     * @return 识别出的 MIME
     */
    public static String detect(BufferedInputStream stream) throws IOException {
        stream.mark(SNIFF_BYTES);
        byte[] head = stream.readNBytes(SNIFF_BYTES);
        stream.reset();
        return detect(head, head.length == SNIFF_BYTES);
    }

    /** 以缓冲流包装原始流，保证可以先识别再完整读取。 */
    public static BufferedInputStream buffered(InputStream stream) {
        return stream instanceof BufferedInputStream buffered ? buffered : new BufferedInputStream(stream, SNIFF_BYTES);
    }

    /**
     * 纯内容判断，不读取浏览器声明或扩展名。
     *
     * @param head 开头字节
     * @param truncated 内容是否长于 head；为真时末尾不完整的多字节字符不算非法
     * @return 识别出的 MIME
     */
    static String detect(byte[] head, boolean truncated) {
        if (startsWith(head, PDF)) {
            return PDF_TYPE;
        }
        if (startsWith(head, PNG)) {
            return PNG_TYPE;
        }
        if (startsWith(head, JPEG)) {
            return JPEG_TYPE;
        }
        if (head.length > 0 && Utf8TextInspector.isText(head, truncated)) {
            return CSV;
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
