package com.paytech.platform.support.service.processor.files;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

import org.springframework.beans.factory.ObjectProvider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Attribute;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Event;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Result;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditWriter;
import com.paytech.platform.support.filedocument.domain.file.DownloadPresentation;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Downloadable;
import com.paytech.platform.support.filedocument.domain.file.FileModels.File;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import com.paytech.platform.support.filedocument.integration.storage.FileDownloadStorage.Download;
import com.paytech.platform.support.filedocument.integration.storage.FileObjectStorage;
import com.paytech.platform.support.filedocument.integration.storage.FileStorageRegistry;
import com.paytech.platform.support.service.processor.files.request.AuthorizeFileDownloadRequest;
import com.paytech.platform.support.service.processor.files.response.AuthorizeFileDownloadResponse;

import lombok.AllArgsConstructor;

/**
 * 签发文件版本的短时只读下载地址；内部系统之间不做文件级权限
 * <li>默认签发当前版本，要求扫描 AVAILABLE、对象 ACTIVE；指定版本时要求该版本为 CURRENT 或 SUPERSEDED 且检查 AVAILABLE。
 * 按文件记录上的实例与桶签发，实例或桶已停用返回 503 且不发起存储调用。</li>
 * <li>地址签入下载方式与原文件名：请求 INLINE 且登记 MIME 为 PDF / PNG / JPEG 时内联打开并签入登记的 Content-Type，
 * 否则按附件下载；响应返回实际生效的方式。</li>
 * <li>地址最长 60 秒，只回传给本次调用，不落库；不写业务数据、不加锁、不开事务。</li>
 * <li>调用方带人工操作人时，签发成功后写一条 {@value #AUDIT_ACTION} 审计（操作人、文件、调用服务、指定的版本号，不含地址）；审计写入
 * 在审计模块自己的独立事务中完成，写入失败或审计未启用时不返回地址（失败关闭）。无操作人时不写审计。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "files.enabled", havingValue = "true")
public class AuthorizeFileDownloadProcessor
    extends AbstractProcessor<AuthorizeFileDownloadRequest, AuthorizeFileDownloadResponse> {

    /** 下载授权的最长有效秒数。 */
    static final long DOWNLOAD_SECONDS = 60;

    /** 人工下载审计动作。 */
    static final String AUDIT_ACTION = "FILE_DOWNLOAD_AUTHORIZE";

    /** 审计资源类型。 */
    static final String AUDIT_RESOURCE_TYPE = "FILE";

    /** 审计属性：调用服务名。 */
    static final String CALLER_SERVICE_ATTRIBUTE = "caller_service";

    /** 审计属性：下载的版本号。 */
    static final String VERSION_NO_ATTRIBUTE = "version_no";

    private final Clock clock;

    private final FileLifecycleService files;

    private final FileStorageRegistry registry;

    private final ObjectProvider<OperationsAuditWriter> auditWriters;

    @Override
    protected AuthorizeFileDownloadResponse processRequest(AuthorizeFileDownloadRequest request) {
        // 读取文件版本并确认可下载
        Downloadable target = files.downloadable(request.getFileId(), request.getVersionNo());
        File file = target.file();
        DownloadPresentation presentation = DownloadPresentation.of(request.getDisposition(), target.displayName(),
            file.fileId(), file.verified().mimeType(), file.scanStatus());

        // 事务外签发该版本授权
        Instant expiresAt = clock.instant().truncatedTo(ChronoUnit.MICROS).plusSeconds(DOWNLOAD_SECONDS);
        Download grant = sign(file, expiresAt, presentation);

        // 人工操作人审计后返回
        if (request.getOperatorAccountId() != null) {
            audit(request);
        }
        return new AuthorizeFileDownloadResponse(grant.url(), grant.expiresAt(), presentation.disposition());
    }

    /** 按记录坐标签发；适配器失败或授权越界返回不可用。 */
    Download sign(File file, Instant expiresAt, DownloadPresentation presentation) {
        Download grant;
        try (FileObjectStorage storage = registry.openForRead(file.provider(), file.container())) {
            grant = storage.authorize(file, expiresAt, presentation);
        } catch (ServiceUnavailableException unavailable) {
            throw unavailable;
        } catch (RuntimeException unavailable) {
            throw new ServiceUnavailableException(unavailable);
        }
        if (grant == null || !grant.usableUntil(expiresAt, clock.instant())) {
            throw new ServiceUnavailableException();
        }
        return grant;
    }

    /**
     * 写一条人工下载审计：主体为操作人账号，资源为文件，属性为调用服务名与指定的版本号（有值时），不含下载地址
     * <li>审计未启用（Operations 与 Client 人工认证均关闭）时返回 503，不签发给有操作人的请求。</li>
     */
    void audit(AuthorizeFileDownloadRequest request) {
        OperationsAuditWriter writer = auditWriters.getIfAvailable();
        if (writer == null) {
            throw new ServiceUnavailableException();
        }
        var attributes = new ArrayList<Attribute>();
        if (request.getCallerService() != null) {
            attributes.add(new Attribute(CALLER_SERVICE_ATTRIBUTE, request.getCallerService()));
        }
        if (request.getVersionNo() != null) {
            attributes.add(new Attribute(VERSION_NO_ATTRIBUTE, request.getVersionNo().toString()));
        }
        writer.write(new Event(new Identity(request.getOperatorAccountId().toString(), null), AUDIT_ACTION,
            new Target(AUDIT_RESOURCE_TYPE, Long.toString(request.getFileId())), Result.SUCCEEDED, attributes,
            request.getRequestId(), clock.instant()));
    }
}
