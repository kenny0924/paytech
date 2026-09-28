package com.paytech.platform.support.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.api.ResourceLock;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerAuthenticationService;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientOnboardingAccessService;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileModels.*;
import com.paytech.platform.support.core.reliability.service.ClientFileCommandService;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.*;
import com.paytech.platform.support.filedocument.domainservice.service.FileUploadService;
import com.paytech.platform.support.filedocument.integration.storage.UploadStorage;
import com.paytech.platform.support.integration.files.CctFileTargetClient;

/** 同步上传用例；CCT/HMAC/存储均位于数据库事务外，IAM 与 Upload/回执同库提交。 */
@Component
@lombok.AllArgsConstructor
@ConditionalOnProperty(name="files.onboarding.enabled",havingValue="true")
public class OnboardingUploadProcessor {
    private final Clock clock;
    private final IdGenerator ids;
    private final Environment environment;
    private final FileUploadService files;
    private final CctFileTargetClient targets;
    private final ClientFileCommandService commands;
    private final ObjectProvider<UploadStorage> adapters;
    private final ClientOnboardingAccessService access;
    private final CustomerAuthenticationService authentication;
    private final OperationsTransactionExecutor transactions;

    /** 新资源以原命令身份协调；唯一键和账号行锁在 Lease 失效后仍保护原回执。 */
    @ResourceLock(prefix="file:upload:start",key="#lockKey")
    public UploadResponseBody start(long accountId,long sessionId,String key,String lockKey,Start request) {
        transactions.query(() -> authentication.context(accountId,sessionId));
        long clientId=targets.authorize(accountId,request.applicationId(),request.subjectPartyId());
        var command=commands.prepare(accountId,key,request);
        List<UploadStorage> available=adapters.orderedStream().toList();
        Upload upload;
        try {
            upload=transactions.write(() -> {
                long tenantId=access.lockUploadTenant(accountId,sessionId,clientId);
                var now=clock.instant().truncatedTo(ChronoUnit.MICROS);
                Long previous=commands.previous(command);
                if (previous!=null) return files.resume(previous,accountId,tenantId,request,now);
                String provider=environment.getProperty("files.onboarding.storage-provider", "");
                String container=environment.getProperty("files.onboarding.storage-container-ref", "");
                String policy=environment.getProperty("files.onboarding.scan-policy-code", "");
                int lifetime=environment.getProperty("files.onboarding.upload-seconds",Integer.class,900);
                if (!provider.matches("[A-Z][A-Z0-9_]{0,63}") || container.isBlank() || container.length()>255
                    || !policy.matches("[A-Z][A-Z0-9_]{0,63}") || lifetime<60 || lifetime>900
                    || available.stream().filter(a -> provider.equals(a.providerCode())).count()!=1)
                    throw new ServiceUnavailableException();
                var created=files.create(ids.nextId(),accountId,tenantId,request,policy,provider,container,now,now.plusSeconds(lifetime));
                commands.complete(command,created.uploadId(),now);
                return created;
            });
        } catch (DuplicateKeyException conflict) {
            // 只让调用者以原键重放已提交结果；当前竞争事务已经全部回滚。
            throw new ConcurrentRequestException("上传命令正在处理，请使用原命令重试");
        }
        var matching=available.stream().filter(a -> upload.storageProvider().equals(a.providerCode())).toList();
        if (matching.size()!=1) throw new ServiceUnavailableException();
        Grant grant;
        try { grant=matching.getFirst().authorize(upload); }
        catch (RuntimeException unavailable) { throw new ServiceUnavailableException(); }
        if (grant==null || grant.expiresAt()==null || !clock.instant().isBefore(grant.expiresAt())
            || grant.expiresAt().isAfter(upload.expiresAt()) || grant.url()==null || !grant.url().startsWith("https://"))
            throw new ServiceUnavailableException();
        return new UploadResponseBody(Long.toString(upload.uploadId()),
            new UploadAuthorization("PUT",grant.url(),grant.headers(),grant.expiresAt()),upload.expiresAt());
    }
}
