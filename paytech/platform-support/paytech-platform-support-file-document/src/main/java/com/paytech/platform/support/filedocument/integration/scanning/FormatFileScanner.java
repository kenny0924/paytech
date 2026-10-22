package com.paytech.platform.support.filedocument.integration.scanning;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;

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
 * <li>text/csv 只凭开头字节识别，另在读取全文时复核全部字节是不含 NUL 的合法 UTF-8</li>
 * <li>类型一致（CSV 还须全文合法）返回 FORMAT_VALID，否则 FORMAT_INVALID；字节与登记不符或存储不可用时抛出异常，
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
            Utf8TextInspector text = StoredContentType.CSV.equals(detected) ? new Utf8TextInspector() : null;
            String hash = digest(bytes, file.verified().sizeBytes(), text);
            if (!hash.equals(file.verified().contentHash())) {
                throw new ServiceUnavailableException();
            }
            boolean formatValid = detected.equals(file.verified().mimeType()) && (text == null || text.finish());
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
     * 读满全部字节计算 SHA-256；实际长度与登记不一致视为字节不符。
     *
     * @param bytes 文件字节
     * @param expectedSize 登记大小
     * @param text 识别为文本时同步检查全文的检查器；其他类型为空
     * @return SHA-256 小写十六进制
     */
    static String digest(InputStream bytes, long expectedSize, Utf8TextInspector text) throws IOException {
        MessageDigest digest = sha256();
        byte[] buffer = new byte[8192];
        long count = 0;
        int length;
        while ((length = bytes.read(buffer)) != -1) {
            count += length;
            if (count > expectedSize) {
                throw new ServiceUnavailableException();
            }
            digest.update(buffer, 0, length);
            if (text != null) {
                text.update(buffer, 0, length);
            }
        }
        if (count != expectedSize) {
            throw new ServiceUnavailableException();
        }
        return HexFormat.of().formatHex(digest.digest());
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
