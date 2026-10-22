package com.paytech.platform.support.service.processor;

import java.net.URI;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Owned;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import com.paytech.platform.support.filedocument.integration.storage.FileDownloadStorage;
import com.paytech.platform.support.filedocument.integration.storage.FileObjectStorage;
import com.paytech.platform.support.filedocument.integration.storage.FileStorageRegistry;
import com.paytech.platform.support.service.processor.request.ReviewFileDownloadRequest;
import com.paytech.platform.support.service.processor.response.ReviewFileDownloadResponse;

import lombok.AllArgsConstructor;

/**
 * 运营审核下载入驻材料。
 * <li>只读编排：同一只读快照内重检运营会话与权限并读取文件，随后在事务外签名</li>
 * <li>签名只在本地计算、没有网络等待，因此不再开第二个事务重检；审计要求一个请求只观察一个事务</li>
 * <li>签发成功后登记 SUCCEEDED 审计事实，事实只含文件标识，不含下载地址</li>
 * <li>文件与审核任务的对应关系由 Operations BFF 依据 Risk 任务事实组合；本处只负责运营权限和文件状态</li>
 * <li>不写业务数据、不加锁；签名与审计登记均在只读快照之外</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnExpression("${files.onboarding.enabled:false} && ${operations.authentication.enabled:false}")
public class ReviewFileDownloadProcessor extends AbstractProcessor<ReviewFileDownloadRequest, ReviewFileDownloadResponse> {
    /** 客户入驻审核查看权限。 */
    static final String REVIEW_READ = "CLIENT_ONBOARDING_REVIEW_READ";
    /** 客户入驻材料核验权限。 */
    static final String EVIDENCE_REVIEW = "CLIENT_ONBOARDING_EVIDENCE_REVIEW";
    /** 下载授权最长有效秒数。 */
    static final long DOWNLOAD_SECONDS = 60;
    /** 成功审计原因代码。 */
    static final String AUDIT_REASON = "REVIEW_FILE_DOWNLOAD_AUTHORIZED";
    private final Clock clock;
    private final FileLifecycleService files;
    private final OperationsAuditService audit;
    private final OperationsTransactionExecutor transactions;
    private final FileStorageRegistry registry;
    private final OperationsManagementPolicyService policy;

    @Override
    protected ReviewFileDownloadResponse processRequest(ReviewFileDownloadRequest request) {
        String actorId = request.getActorId();
        long fileId = request.getFileId();
        Authorized authorized = authorized(actorId, request.getSessionId(), fileId);
        Owned owned = authorized.owned();
        var expires = clock.instant().truncatedTo(ChronoUnit.MICROS).plusSeconds(DOWNLOAD_SECONDS);
        FileDownloadStorage.Download grant = sign(owned, expires);
        requireGrant(grant, expires);
        long tenantId = authorized.actor().member().tenantId();
        audit.record(() -> OperationsAuditFact.Outcome.succeeded(actorId, tenantId, new Target("FILE", Long.toString(fileId)),
            AUDIT_REASON, clock.instant()));
        var file = owned.file();
        return new ReviewFileDownloadResponse(fileId, owned.upload().displayName(), file.verified().mimeType(),
            file.verified().sizeBytes(), grant.url(), grant.expiresAt());
    }

    /** 同一只读快照内确认权限与可用文件。 */
    Authorized authorized(String actorId, String sessionId, long fileId) {
        Authorized authorized = transactions.query(() -> new Authorized(
            policy.requirePermission(actorId, sessionId, REVIEW_READ, EVIDENCE_REVIEW), files.readForReview(fileId)));
        Owned owned = authorized.owned();
        if (owned.file().scanStatus() != FileScanStatus.AVAILABLE || owned.file().objectStatus() != FileObjectStatus.ACTIVE) {
            throw new FailException(OnboardingFileErrorCode.FILE_STATE_CONFLICT);
        }
        return authorized;
    }

    /** 已确认权限的运营身份与对应文件。 */
    record Authorized(SessionContext actor, Owned owned) {
    }

    /** 事务外按原文件记录的实例与桶签名；实例或桶不可读、适配器失败为依赖不可用。 */
    FileDownloadStorage.Download sign(Owned owned, java.time.Instant expires) {
        try (FileObjectStorage storage = registry.openForRead(owned.file().provider(), owned.file().container())) {
            return storage.authorize(owned.file(), expires);
        } catch (ServiceUnavailableException unavailable) {
            throw unavailable;
        } catch (RuntimeException unavailable) {
            throw new ServiceUnavailableException(unavailable);
        }
    }

    /** 授权必须为未过期、不晚于期限的 HTTPS 地址。 */
    void requireGrant(FileDownloadStorage.Download grant, java.time.Instant expires) {
        if (grant == null || grant.expiresAt() == null || !clock.instant().isBefore(grant.expiresAt())
            || grant.expiresAt().isAfter(expires) || grant.url() == null || grant.url().length() > 8192) {
            throw new ServiceUnavailableException();
        }
        URI uri = URI.create(grant.url());
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new ServiceUnavailableException();
        }
    }
}
