package com.paytech.platform.support.filedocument.domainservice.service.impl;

import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileUpload;
import com.paytech.platform.support.filedocument.domain.enums.FileUploadStatus;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Start;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.domainservice.repository.FileUploadRepository;
import com.paytech.platform.support.filedocument.domainservice.service.FileUploadService;

import tools.jackson.databind.ObjectMapper;

/** 上传创建固定策略和高熵对象身份；缺少有效策略时关闭入口。 */
@Service
@lombok.AllArgsConstructor
public class FileUploadServiceImpl implements FileUploadService {
    private final ObjectMapper json;
    private final FileUploadRepository repository;
    /** Scan Policy 结构损坏与未配置都不能被当作允许任意文件。 */
    @Override public Upload create(long id, long accountId, long tenantId, Start start, String policyCode,
        String provider, String container, Instant now, Instant expiresAt) {
        var policies = repository.policies(policyCode, now);
        if (policies.size() != 1) throw new ServiceUnavailableException();
        var policy = policies.getFirst();
        if (policy.ruleSchemaVersion() != 1 || policy.requirementCount() < 1 || policy.maxSizeBytes() < 1)
            throw new ServiceUnavailableException();
        var allowed = new HashSet<String>();
        try {
            if (policy.allowedMimeRules().length() > 8192) throw new IllegalArgumentException();
            var rules = json.readTree(policy.allowedMimeRules());
            if (!rules.isObject() || rules.size() != 1 || !rules.path("mimeTypes").isArray()
                || rules.path("mimeTypes").isEmpty() || rules.path("mimeTypes").size() > 64) throw new IllegalArgumentException();
            for (var mime : rules.path("mimeTypes")) {
                if (!mime.isTextual() || !mime.asText().matches("[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*")
                    || mime.asText().length() > 128 || !allowed.add(mime.asText())) throw new IllegalArgumentException();
            }
        } catch (RuntimeException invalid) { throw new ServiceUnavailableException(); }
        if (!allowed.contains(start.mimeType()) || start.sizeBytes() > policy.maxSizeBytes())
            throw new FailException(OnboardingFileErrorCode.FILE_POLICY_REJECTED);
        var value = new FileUpload(id, tenantId, accountId, start.applicationId() + ":" + start.subjectPartyId(),
            start.displayName(), start.sizeBytes(), start.mimeType(), provider, container,
            UUID.randomUUID().toString(), policy.policyId(), expiresAt, FileUploadStatus.CREATED);
        repository.insert(value, now);
        return result(value);
    }
    /** 转为文件能力发布的业务事实。 */
    private Upload result(FileUpload value) {
        return new Upload(value.uploadId(), value.tenantId(), value.uploaderId(), value.ownerRefId(), value.displayName(),
            value.expectedSize(), value.expectedMime(), value.storageProvider(), value.containerRef(), value.objectRef(),
            value.scanPolicyId(), value.expiresAt(), value.status());
    }
}
