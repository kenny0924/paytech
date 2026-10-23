package com.paytech.platform.support.filedocument.integration.scanning;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.filedocument.domain.enums.FileScanType;
import com.paytech.platform.support.filedocument.domain.file.FileModels.File;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Requirement;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Result;
import com.paytech.platform.support.filedocument.integration.storage.StoredContentType;
import com.paytech.platform.support.filedocument.integration.storage.Utf8TextInspector;
import com.paytech.platform.support.filedocument.integration.storage.FileObjectStorage;
import com.paytech.platform.support.filedocument.integration.storage.FileStorageRegistry;

/**
 * Platform 内置格式检查（FORMAT）。
 * <li>按登记版本重新读取字节，复核 SHA-256 与大小，并以文件头复核媒体类型等于登记类型</li>
 * <li>text/csv 与 application/json 只凭开头字节识别，另在读取全文时复核全部字节是不含 NUL 的合法 UTF-8</li>
 * <li>application/json 还须全文恰好是一个 JSON 对象（根为对象、其后只有空白）；带 BOM 的内容不会被识别为 JSON</li>
 * <li>类型一致（文本还须全文合法）返回 FORMAT_VALID，否则 FORMAT_INVALID；字节与登记不符或存储不可用时抛出异常，
 * 由补偿重试，不伪造结果</li>
 * <li>不是病毒扫描；生产启用前须另配 ANTI_MALWARE 要求与真实扫描器</li>
 */
@Component
@ConditionalOnProperty(name = "files.enabled", havingValue = "true")
public final class FormatFileScanner implements FileScanner {
    /** 扫描策略中引用的检查器代码。 */
    public static final String SCANNER_CODE = "PLATFORM_FORMAT";
    /** 当前检查规则版本，整数递增。 */
    static final int RUNTIME_VERSION = 1;
    /** 新建检查策略修订时写入的最低运行版本。 */
    public static final String MINIMUM_RUNTIME_VERSION = "1";
    /** 本检查器能识别的规范 MIME；检查策略的允许类型只能从中选择。 */
    public static final List<String> SUPPORTED_MIME_TYPES = StoredContentType.SUPPORTED;
    /** 类型一致。 */
    public static final String VALID = "FORMAT_VALID";
    /** 类型不一致。 */
    public static final String INVALID = "FORMAT_INVALID";
    private final Clock clock;
    private final FileStorageRegistry registry;

    /** 注入时钟与按记录坐标构建存储适配器的注册表。 */
    public FormatFileScanner(Clock clock, FileStorageRegistry registry) {
        this.clock = clock;
        this.registry = registry;
    }

    /** 固定为 PLATFORM_FORMAT。 */
    @Override
    public String scannerCode() {
        return SCANNER_CODE;
    }

    /**
     * 只处理 FORMAT 要求；同一文件与版本的结果是确定的，重复调用即恢复原检查。
     *
     * @param file 已登记文件
     * @param requirement 本检查器负责的要求
     * @param attemptNo 稳定尝试号
     * @return 终局检查结果
     */
    @Override
    public Result recoverOrScan(File file, Requirement requirement, int attemptNo) {
        if (requirement.type() != FileScanType.FORMAT) {
            throw new ServiceUnavailableException();
        }
        try (FileObjectStorage storage = registry.openForRead(file.provider(), file.container());
            InputStream raw = storage.open(file)) {
            BufferedInputStream bytes = StoredContentType.buffered(raw);
            String detected = StoredContentType.detect(bytes);
            Utf8TextInspector text = StoredContentType.isText(detected) ? new Utf8TextInspector() : null;
            VerifyingStream verifying = new VerifyingStream(bytes, file.verified().sizeBytes(), text);
            boolean structureValid = !StoredContentType.JSON.equals(detected) || JsonObjectInspector.isObject(verifying);
            String hash = verifying.finish();
            if (!hash.equals(file.verified().contentHash())) {
                throw new ServiceUnavailableException();
            }
            boolean formatValid = detected.equals(file.verified().mimeType()) && (text == null || text.finish())
                && structureValid;
            String code = formatValid ? VALID : INVALID;
            return new Result(file.fileId(), requirement.id(), attemptNo, file.verified().objectVersion(), hash,
                Integer.toString(RUNTIME_VERSION), code, clock.instant());
        } catch (IOException exception) {
            throw new ServiceUnavailableException(exception);
        }
    }

    /** 版本按整数比较；无法解析的版本不被接受。 */
    @Override
    public boolean acceptsRuntime(String minimum, String actual) {
        try {
            return Integer.parseInt(actual) >= Integer.parseInt(minimum);
        } catch (NumberFormatException | NullPointerException unknown) {
            return false;
        }
    }

    /**
     * 读满全部字节计算 SHA-256；实际长度与登记不一致视为字节不符
     *
     * @param bytes 文件字节
     * @param expectedSize 登记大小
     * @param text 识别为文本时同步检查全文的检查器；其他类型为空
     * @return SHA-256 小写十六进制
     */
    static String digest(InputStream bytes, long expectedSize, Utf8TextInspector text) throws IOException {
        return new VerifyingStream(bytes, expectedSize, text).finish();
    }

    /**
     * 边读边计算摘要、计数并检查文本的输入流；JSON 解析器与摘要共用同一次读取，内存有界
     * <li>读到的字节数超过登记大小立即抛出不可用；{@link #finish()} 读完剩余字节并核对总长度。</li>
     */
    static final class VerifyingStream extends FilterInputStream {

        private final MessageDigest digest = sha256();
        private final long expectedSize;
        private final Utf8TextInspector text;
        private long count;

        /**
         * 包装原始字节流。
         *
         * @param bytes 文件字节
         * @param expectedSize 登记大小
         * @param text 文本检查器，可空
         */
        VerifyingStream(InputStream bytes, long expectedSize, Utf8TextInspector text) {
            super(bytes);
            this.expectedSize = expectedSize;
            this.text = text;
        }

        /** 单字节读取同样计入摘要。 */
        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int length = read(one, 0, 1);
            return length == -1 ? -1 : one[0] & 0xFF;
        }

        /** 读取一段字节并计入摘要、计数与文本检查。 */
        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                count += read;
                if (count > expectedSize) {
                    throw new ServiceUnavailableException();
                }
                digest.update(buffer, offset, read);
                if (text != null) {
                    text.update(buffer, offset, read);
                }
            }
            return read;
        }

        /** 跳过的字节也必须计入摘要，因此按读取处理。 */
        @Override
        public long skip(long length) throws IOException {
            byte[] buffer = new byte[(int) Math.min(8192, Math.max(0, length))];
            int read = buffer.length == 0 ? 0 : read(buffer, 0, buffer.length);
            return Math.max(read, 0);
        }

        /** 不支持标记，避免重复计入摘要。 */
        @Override
        public boolean markSupported() {
            return false;
        }

        /** 由调用方的 try-with-resources 关闭原始流；解析器不得提前关闭。 */
        @Override
        public void close() {
        }

        /**
         * 读完剩余字节并核对总长度。
         *
         * @return SHA-256 小写十六进制
         */
        String finish() throws IOException {
            byte[] buffer = new byte[8192];
            while (read(buffer, 0, buffer.length) != -1) {
                // 剩余字节只计入摘要与文本检查
            }
            if (count != expectedSize) {
                throw new ServiceUnavailableException();
            }
            return HexFormat.of().formatHex(digest.digest());
        }
    }

    /** JDK 必备算法。 */
    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
