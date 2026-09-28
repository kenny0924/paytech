package com.paytech.platform.support.service.processor;

import java.net.URI;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.paytech.foundation.common.error.*;
import com.paytech.platform.support.api.enums.*;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileModels.*;
import com.paytech.platform.support.accesscontrol.domainservice.service.*;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Owned;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import com.paytech.platform.support.filedocument.integration.storage.FileDownloadStorage;
import com.paytech.platform.support.integration.files.CctFileTargetClient;

/** 查询编排无写入；CCT 和存储调用均在本库事务外，私网核验不回调 CCT。 */
@Component @lombok.AllArgsConstructor
@ConditionalOnProperty(name="files.onboarding.enabled",havingValue="true")
public class OnboardingFileReadProcessor {
    private final Clock clock;
    private final FileLifecycleService files;
    private final CustomerAuthenticationService authentication;
    private final ClientOnboardingAccessService access;
    private final CctFileTargetClient targets;
    private final OperationsTransactionExecutor transactions;
    private final ObjectProvider<FileDownloadStorage> adapters;

    /** 当前元数据不包含摘要、存储坐标、临时 URL 或其他账号的存在性信息。 */
    public FileResponseBody read(long accountId,long sessionId,long fileId) {
        var owned=authorized(accountId,sessionId,fileId);var file=owned.file();
        return new FileResponseBody(Long.toString(fileId),owned.upload().displayName(),Long.toString(file.verified().sizeBytes()),
            file.verified().mimeType(),file.scanStatus().getCode(),
            file.objectStatus().getCode(),available(owned)?List.of(OnboardingFileAction.DOWNLOAD.getCode()):List.of());
    }

    /** 下载最多有效 60 秒，网络等待后重新检查会话、资源归属及文件当前状态。 */
    public DownloadResponseBody download(long accountId,long sessionId,long fileId) {
        var owned=authorized(accountId,sessionId,fileId);requireAvailable(owned);
        var matching=adapters.orderedStream().filter(a -> owned.file().provider().equals(a.providerCode())).toList();
        if (matching.size()!=1) throw new ServiceUnavailableException();
        var expires=clock.instant().truncatedTo(ChronoUnit.MICROS).plusSeconds(60);
        FileDownloadStorage.Download grant;
        try { grant=matching.getFirst().authorize(owned.file(),expires); }
        catch (RuntimeException unavailable) { throw new ServiceUnavailableException(); }
        var current=authorized(accountId,sessionId,fileId);requireAvailable(current);
        if (!owned.file().equals(current.file())) throw new FailException(OnboardingFileErrorCode.FILE_STATE_CONFLICT);
        if (grant==null || grant.expiresAt()==null || !clock.instant().isBefore(grant.expiresAt()) || grant.expiresAt().isAfter(expires))
            throw new ServiceUnavailableException();
        try {
            if (grant.url()==null || grant.url().length()>8192) throw new IllegalArgumentException();
            var uri=URI.create(grant.url());
            if (!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getFragment()!=null)
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) { throw new ServiceUnavailableException(); }
        return new DownloadResponseBody(grant.url(),grant.expiresAt());
    }

    /** CCT 已核验原申请与主体；本方法在同一只读快照验证原账号、原 Tenant 和当前文件事实。 */
    public VerificationResponseBody verify(long accountId,long fileId,long applicationId,long subjectPartyId,long clientId) {
        return transactions.query(() -> {
            var owned=files.read(fileId,accountId);
            if (owned.applicationId()!=applicationId || owned.subjectPartyId()!=subjectPartyId)
                throw new FailException(OnboardingFileErrorCode.FILE_NOT_FOUND);
            access.readFile(accountId,clientId,owned.upload().tenantId());
            var file=owned.file();
            return new VerificationResponseBody(Long.toString(fileId),Long.toString(owned.upload().uploadId()),file.verified().contentHash(),
                Long.toString(file.verified().sizeBytes()),file.verified().mimeType(),file.scanStatus().getCode(),
                file.objectStatus().getCode(),clock.instant().truncatedTo(ChronoUnit.MICROS));
        });
    }

    /** 先验证真实会话，再问原 Owner；网络等待后在新的只读快照中核对完整 IAM。 */
    private Owned authorized(long accountId,long sessionId,long fileId) {
        var original=transactions.query(() -> { authentication.context(accountId,sessionId);return files.read(fileId,accountId); });
        var target=targets.read(accountId,original.applicationId(),original.subjectPartyId());
        return transactions.query(() -> {
            authentication.context(accountId,sessionId);
            var current=files.read(fileId,accountId);
            if (current.applicationId()!=original.applicationId() || current.subjectPartyId()!=original.subjectPartyId()
                || current.upload().uploadId()!=original.upload().uploadId() || current.upload().tenantId()!=original.upload().tenantId())
                throw new FailException(OnboardingFileErrorCode.FILE_NOT_FOUND);
            access.readFile(accountId,target.clientId(),current.upload().tenantId());return current;
        });
    }

    /** 扫描通过与对象有效必须同时成立。 */
    private boolean available(Owned owned) { return owned.file().scanStatus()==FileScanStatus.AVAILABLE && owned.file().objectStatus()==FileObjectStatus.ACTIVE; }
    /** 当前不可下载不会触发重扫或恢复存储。 */
    private void requireAvailable(Owned owned) { if (!available(owned)) throw new FailException(OnboardingFileErrorCode.FILE_STATE_CONFLICT); }
}
