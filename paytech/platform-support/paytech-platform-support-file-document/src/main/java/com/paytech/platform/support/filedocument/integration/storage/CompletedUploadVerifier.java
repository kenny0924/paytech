package com.paytech.platform.support.filedocument.integration.storage;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;
import com.paytech.foundation.common.error.*;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Verified;
import com.paytech.platform.support.filedocument.integration.storage.CompletedUploadStorage.Opened;
/** 流式读取实际对象，内存有界；外部 I/O 由调用者安排在本地事务之外。存储按上传记录上的实例与桶定位，不受路由影响。 */
@Component
@lombok.AllArgsConstructor
public final class CompletedUploadVerifier {
    private final FileStorageRegistry registry;
    /** 首次上传：对象恰好一个版本；不接受客户端摘要或存储 ETag，超出声明长度立即拒绝。 */
    public Verified verify(Upload upload) {
        try (var storage=registry.openForRead(upload.storageProvider(),upload.containerRef());
            var value=storage.open(upload)) {
            return verified(upload,value);
        } catch (FailException | ServiceUnavailableException rejected) { throw rejected; }
        catch (Exception unavailable) { throw new ServiceUnavailableException(); }
    }
    /**
     * 替换上传：比替换基准新的版本恰好一个时读取并核对该版本
     *
     * @param upload 替换上传
     * @return 新版本数量与（恰好一个时的）核对结果
     */
    public Replacement verifyReplacement(Upload upload) {
        try (var storage=registry.openForRead(upload.storageProvider(),upload.containerRef())) {
            List<String> newer=storage.newerVersions(upload);
            if (newer.size()!=1) {
                return new Replacement(newer.size(),null);
            }
            try (var value=storage.open(upload,newer.getFirst())) {
                return new Replacement(1,verified(upload,value));
            }
        } catch (FailException | ServiceUnavailableException rejected) { throw rejected; }
        catch (Exception unavailable) { throw new ServiceUnavailableException(); }
    }
    /** 核对版本引用、服务端识别类型、实际长度与摘要。 */
    private Verified verified(Upload upload,Opened value) throws IOException, NoSuchAlgorithmException {
        if (value==null || !bounded(value.version(),128) || "null".equalsIgnoreCase(value.version())
            || !bounded(value.encryptionKeyRef(),255) || value.bytes()==null) {
            throw new ServiceUnavailableException();
        }
        if (!upload.expectedMime().equals(value.detectedMime())) {
            throw new FailException(FileErrorCode.FILE_POLICY_REJECTED);
        }
        var digest=MessageDigest.getInstance("SHA-256");
        byte[] buffer=new byte[8192];
        long count=0;
        int length;
        while ((length=value.bytes().read(buffer))!=-1) {
            if (length>upload.expectedSize()-count) {
                throw new FailException(FileErrorCode.FILE_POLICY_REJECTED);
            }
            count+=length;
            digest.update(buffer,0,length);
        }
        if (count!=upload.expectedSize()) {
            throw new FailException(FileErrorCode.FILE_POLICY_REJECTED);
        }
        return new Verified(value.version(),HexFormat.of().formatHex(digest.digest()),count,value.detectedMime(),value.encryptionKeyRef());
    }
    /** 存储引用必须能完整落库且不能含控制字符。 */
    private boolean bounded(String value,int limit) { return value!=null && !value.isBlank() && value.length()<=limit && value.codePoints().noneMatch(Character::isISOControl); }

    /**
     * 替换上传的核对结果
     *
     * @param newerVersions 比替换基准新的版本数：0 表示未检测到上传，大于 1 表示有多余写入
     * @param verified 恰好一个新版本时的核对结果；否则为空
     */
    public record Replacement(int newerVersions, Verified verified) {}
}
