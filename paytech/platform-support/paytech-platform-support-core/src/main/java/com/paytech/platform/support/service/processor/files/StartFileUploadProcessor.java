package com.paytech.platform.support.service.processor.files;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.file.FileDisplayName;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Grant;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Start;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.domain.storage.StorageWriteTarget;
import com.paytech.platform.support.filedocument.domainservice.service.FileUploadService;
import com.paytech.platform.support.filedocument.integration.storage.FileObjectStorage;
import com.paytech.platform.support.filedocument.integration.storage.FileStorageRegistry;
import com.paytech.platform.support.service.processor.files.request.StartFileUploadRequest;
import com.paytech.platform.support.service.processor.files.response.StartFileUploadResponse;

import lombok.AllArgsConstructor;

/**
 * 开始一次内部文件上传：按 (来源系统, 访问级别) 路由选择存储 → 登记上传 → 事务外签发 PUT 授权
 * <li>内部 HTTP 入口与 Platform 内部模块（如工作流，来源系统 platform-support）共用；不做文件级权限，不记录业务归属。</li>
 * <li>每次请求新建上传，没有可比较的原业务操作身份；上传登记为单条插入，不需要显式事务。</li>
 * <li>Platform 进程内调用方可指定对象名前缀，对象名仍以随机部分结尾；内部 HTTP 入口不能指定。</li>
 * <li>未声明访问级别按 PRIVATE；没有该级别的可用路由、桶或实例返回 503 且不登记，不回落到另一级别；类型或大小不符合路由扫描策略
 * 返回 422。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "files.enabled", havingValue = "true")
public class StartFileUploadProcessor extends AbstractProcessor<StartFileUploadRequest, StartFileUploadResponse> {

    private final Clock clock;

    private final IdGenerator ids;

    private final FileUploadService files;

    private final FileStorageRegistry registry;

    /** 文件名长度与归一化后的展示名只依赖输入。 */
    @Override
    protected void validate(StartFileUploadRequest request) {
        if (!FileDisplayName.withinLimit(request.getFilename())) {
            throw new InvalidParameterException("文件名最多 255 个字符");
        }
        if (!FileDisplayName.usable(request.displayName())) {
            throw new InvalidParameterException("文件展示名无效");
        }
    }

    @Override
    protected StartFileUploadResponse processRequest(StartFileUploadRequest request) {
        // 按来源系统与访问级别路由选择存储
        FileAccessLevel accessLevel = request.effectiveAccessLevel();
        StorageWriteTarget target = registry.forWrite(request.getSourceSystem(), accessLevel);

        // 登记上传
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Start start = new Start(null, request.getUploaderAccountId(), request.getSourceSystem(),
            request.getReasonCode(), request.displayName(), request.getMimeType(), request.getSizeBytes(), accessLevel,
            request.getObjectKeyPrefix());
        Upload upload = files.create(ids.nextId(), start, target, now);

        // 事务外签发上传授权
        return new StartFileUploadResponse(upload.uploadId(), authorize(upload), upload.expiresAt());
    }

    /** 由原上传固定的实例与桶签发 PUT 授权；适配器失败或授权越界返回不可用。 */
    Grant authorize(Upload upload) {
        Grant grant;
        try (FileObjectStorage storage = registry.openForWrite(upload.storageProvider(), upload.containerRef())) {
            grant = storage.authorize(upload);
        } catch (ServiceUnavailableException unavailable) {
            throw unavailable;
        } catch (RuntimeException unavailable) {
            throw new ServiceUnavailableException(unavailable);
        }
        if (grant == null || !grant.usableFor(upload, clock.instant())) {
            throw new ServiceUnavailableException();
        }
        return grant;
    }
}
