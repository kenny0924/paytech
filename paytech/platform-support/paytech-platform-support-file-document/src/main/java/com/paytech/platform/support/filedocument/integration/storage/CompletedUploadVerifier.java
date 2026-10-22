package com.paytech.platform.support.filedocument.integration.storage;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import com.paytech.foundation.common.error.*;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Verified;
/** 流式读取实际对象，内存有界；外部 I/O 由调用者安排在本地事务之外。存储按上传记录上的实例与桶定位，不受路由影响。 */
@Component
@lombok.AllArgsConstructor
public final class CompletedUploadVerifier {
    private final FileStorageRegistry registry;
    /** 不接受客户端摘要或存储 ETag；超出声明长度立即拒绝。 */
    public Verified verify(Upload upload) {
        try (var storage=registry.openForRead(upload.storageProvider(),upload.containerRef());
            var value=storage.open(upload)) {
            if (value==null || !bounded(value.version(),128) || "null".equalsIgnoreCase(value.version())
                || !bounded(value.encryptionKeyRef(),255) || value.bytes()==null) throw new ServiceUnavailableException();
            if (!upload.expectedMime().equals(value.detectedMime())) throw new FailException(OnboardingFileErrorCode.FILE_POLICY_REJECTED);
            var digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[8192];long count=0;
            int length;
            while ((length=value.bytes().read(buffer))!=-1) {
                if (length>upload.expectedSize()-count) throw new FailException(OnboardingFileErrorCode.FILE_POLICY_REJECTED);
                count+=length;digest.update(buffer,0,length);
            }
            if (count!=upload.expectedSize()) throw new FailException(OnboardingFileErrorCode.FILE_POLICY_REJECTED);
            return new Verified(value.version(),HexFormat.of().formatHex(digest.digest()),count,value.detectedMime(),value.encryptionKeyRef());
        } catch (FailException | ServiceUnavailableException rejected) { throw rejected; }
        catch (Exception unavailable) { throw new ServiceUnavailableException(); }
    }
    /** 存储引用必须能完整落库且不能含控制字符。 */
    private boolean bounded(String value,int limit) { return value!=null && !value.isBlank() && value.length()<=limit && value.codePoints().noneMatch(Character::isISOControl); }
}
