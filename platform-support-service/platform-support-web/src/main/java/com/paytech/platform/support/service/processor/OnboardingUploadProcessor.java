package com.paytech.platform.support.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.api.ResourceLock;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerAuthenticationService;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientOnboardingAccessService;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileModels.*;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.*;
import com.paytech.platform.support.filedocument.domainservice.service.FileUploadService;
import com.paytech.platform.support.filedocument.integration.storage.UploadStorage;
import com.paytech.platform.support.integration.files.CctFileTargetClient;

/** 同步上传用例；CCT/存储均位于数据库事务外，IAM 校验与 Upload 同库提交；每次请求新建 Upload，请求重放由 Gateway 处理。 */
@Component
@lombok.AllArgsConstructor
@ConditionalOnProperty(name="files.onboarding.enabled",havingValue="true")
public class OnboardingUploadProcessor {
    private final Clock clock;
    private final IdGenerator ids;
    private final Environment environment;
    private final FileUploadService files;
    private final CctFileTargetClient targets;
    private final ObjectProvider<UploadStorage> adapters;
    private final ClientOnboardingAccessService access;
    private final CustomerAuthenticationService authentication;
    private final OperationsTransactionExecutor transactions;

    /** 按账号、申请与主体协调同一上传目标；账号行锁在 Lease 失效后继续串行 Tenant 解析。 */
    @ResourceLock(prefix="file:upload:start",key="#lockKey")
    public UploadResponseBody start(long accountId,long sessionId,String lockKey,Start request) {
        transactions.query(() -> authentication.context(accountId,sessionId));
        long clientId=targets.authorize(accountId,request.applicationId(),request.subjectPartyId());
        List<UploadStorage> available=adapters.orderedStream().toList();
        Upload upload=transactions.write(() -> {
                long tenantId=access.lockUploadTenant(accountId,sessionId,clientId);
                var now=clock.instant().truncatedTo(ChronoUnit.MICROS);
                String provider=environment.getProperty("files.onboarding.storage-provider", "");
                String container=environment.getProperty("files.onboarding.storage-container-ref", "");
                String policy=environment.getProperty("files.onboarding.scan-policy-code", "");
                int lifetime=environment.getProperty("files.onboarding.upload-seconds",Integer.class,900);
                if (!provider.matches("[A-Z][A-Z0-9_]{0,63}") || container.isBlank() || container.length()>255
                    || !policy.matches("[A-Z][A-Z0-9_]{0,63}") || lifetime<60 || lifetime>900
                    || available.stream().filter(a -> provider.equals(a.providerCode())).count()!=1)
                    throw new ServiceUnavailableException();
                return files.create(ids.nextId(),accountId,tenantId,request,policy,provider,container,now,now.plusSeconds(lifetime));
            });
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
