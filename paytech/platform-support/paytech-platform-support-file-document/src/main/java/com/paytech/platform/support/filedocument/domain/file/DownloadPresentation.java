package com.paytech.platform.support.filedocument.domain.file;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import com.paytech.platform.support.api.enums.FileDisposition;
import com.paytech.platform.support.api.enums.FileScanStatus;

/**
 * 下载地址要求存储返回的展示方式：实际生效的下载方式、Content-Disposition 与 INLINE 时的 Content-Type
 * <li>INLINE 只对扫描 AVAILABLE 且登记 MIME 在允许名单内的文件生效，否则静默降为 ATTACHMENT；未指定按 ATTACHMENT。</li>
 * <li>两种方式都带原文件名：filename 为 ASCII 回退（非 ASCII、引号、反斜杠与控制字符替换为 _），filename* 为 RFC 5987 UTF-8 编码。</li>
 * <li>纯计算，不访问数据库或存储。</li>
 */
public final class DownloadPresentation {

    /** 允许浏览器内打开的登记 MIME。 */
    static final Set<String> INLINE_MIME_TYPES = Set.of("application/pdf", "image/png", "image/jpeg");

    /** RFC 5987 attr-char 中除字母数字外允许原样输出的字符。 */
    private static final String ATTR_CHAR_SYMBOLS = "!#$&+-.^_`|~";

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    /** 实际生效的下载方式。 */
    private final FileDisposition disposition;

    /** Content-Disposition 完整值。 */
    private final String contentDisposition;

    /** INLINE 时为登记的 MIME，ATTACHMENT 时为空。 */
    private final String contentType;

    /** 只经工厂方法创建。 */
    private DownloadPresentation(FileDisposition disposition, String contentDisposition, String contentType) {
        this.disposition = disposition;
        this.contentDisposition = contentDisposition;
        this.contentType = contentType;
    }

    /**
     * 按请求方式与文件事实确定展示方式
     *
     * @param requested 调用方请求的方式，可空（按 ATTACHMENT）
     * @param filename 原文件名；为空时使用 file-{fileId}
     * @param fileId 文件 ID
     * @param mimeType 登记的 MIME
     * @param scanStatus 扫描状态
     * @return 展示方式
     */
    public static DownloadPresentation of(FileDisposition requested, String filename, long fileId, String mimeType,
        FileScanStatus scanStatus) {
        String name = filename == null || filename.isBlank() ? "file-" + fileId : filename;
        boolean inline = requested == FileDisposition.INLINE && scanStatus == FileScanStatus.AVAILABLE
            && INLINE_MIME_TYPES.contains(mimeType);
        FileDisposition effective = inline ? FileDisposition.INLINE : FileDisposition.ATTACHMENT;
        String type = inline ? "inline" : "attachment";
        String header = type + "; filename=\"" + asciiFallback(name) + "\"; filename*=UTF-8''" + encode(name);
        return new DownloadPresentation(effective, header, inline ? mimeType : null);
    }

    /** 可打印 ASCII 原样保留，其余字符与引号、反斜杠替换为 _；代理对按一个字符替换。 */
    static String asciiFallback(String name) {
        StringBuilder fallback = new StringBuilder(name.length());
        name.codePoints().forEach(point -> fallback.append(
            point >= 0x20 && point <= 0x7E && point != '"' && point != '\\' ? (char) point : '_'));
        return fallback.toString();
    }

    /** RFC 5987 ext-value 的值部分：attr-char 原样输出，其余 UTF-8 字节百分号编码。 */
    static String encode(String name) {
        StringBuilder encoded = new StringBuilder();
        for (byte value : name.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = value & 0xFF;
            if (unsigned < 0x80 && (Character.isLetterOrDigit(unsigned) || ATTR_CHAR_SYMBOLS.indexOf(unsigned) >= 0)) {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%').append(HEX[unsigned >> 4]).append(HEX[unsigned & 0x0F]);
            }
        }
        return encoded.toString();
    }

    /** 实际生效的下载方式。 */
    public FileDisposition disposition() {
        return disposition;
    }

    /** Content-Disposition 完整值。 */
    public String contentDisposition() {
        return contentDisposition;
    }

    /** INLINE 时为登记的 MIME，ATTACHMENT 时为空。 */
    public String contentType() {
        return contentType;
    }

    /** 文件名可能含个人信息。 */
    @Override
    public String toString() {
        return "DownloadPresentation[" + disposition + "]";
    }
}
