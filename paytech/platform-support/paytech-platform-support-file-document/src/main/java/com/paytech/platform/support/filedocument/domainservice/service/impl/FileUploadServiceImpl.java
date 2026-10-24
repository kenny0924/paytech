package com.paytech.platform.support.filedocument.domainservice.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileScanPolicy;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileUpload;
import com.paytech.platform.support.filedocument.domain.enums.FileUploadStatus;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Replaceable;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Constraints;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.ReplacementStart;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Start;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.domain.storage.StorageWriteTarget;
import com.paytech.platform.support.filedocument.domainservice.repository.FileUploadRepository;
import com.paytech.platform.support.filedocument.domainservice.service.FileUploadService;

import tools.jackson.databind.ObjectMapper;

/** 上传创建固定策略和高熵对象身份；缺少有效策略时关闭入口。 */
@Service
@lombok.AllArgsConstructor
public class FileUploadServiceImpl implements FileUploadService {
    private final ObjectMapper json;
    private final FileUploadRepository repository;

    /** Scan Policy 结构损坏与未配置都不能被当作允许任意文件；上传期限取自路由。 */
    @Override
    public Upload create(long id, Start start, StorageWriteTarget target, Instant now) {
        FileScanPolicy policy = admit(target.getScanPolicyCode(), start.mimeType(), start.sizeBytes(), now);
        var value = new FileUpload(id, start.tenantId(), start.uploaderId(), start.sourceSystem(), start.reasonCode(),
            start.displayName(), start.sizeBytes(), start.mimeType(), target.getProvider(),
            target.getContainer(), objectRef(start.objectKeyPrefix()), policy.policyId(),
            now.plusSeconds(target.getMaxUploadSeconds()), FileUploadStatus.CREATED, start.accessLevel(), null, 0,
            null);
        repository.insert(value, now);
        return result(value);
    }

    /**
     * 新上传的对象名：可选前缀加 Platform 生成的随机部分，保证每次上传写到从未使用过的对象名
     *
     * @param prefix 进程内调用方指定的前缀，可空；格式已由入口校验
     * @return 对象名
     */
    static String objectRef(String prefix) {
        String random = UUID.randomUUID().toString();
        return prefix == null ? random : prefix + "/" + random;
    }

    /** 新内容按原文件策略代码当前的 ACTIVE 修订核对类型与大小，写到原对象名。 */
    @Override
    public Upload createReplacement(long id, ReplacementStart start, Instant now) {
        Replaceable file = start.file();
        FileScanPolicy policy = admit(file.policyCode(), start.mimeType(), start.sizeBytes(), now);
        var value = new FileUpload(id, null, start.uploaderId(), file.sourceSystem(), file.reasonCode(),
            start.displayName(), start.sizeBytes(), start.mimeType(), file.provider(), file.container(),
            file.objectRef(), policy.policyId(), now.plusSeconds(start.maxUploadSeconds()), FileUploadStatus.CREATED,
            file.accessLevel(), file.fileId(), file.nextReplacementNo(), start.baseObjectVersion());
        repository.insert(value, now);
        return result(value);
    }

    /** 读取有效修订并按与开始上传相同的规则解析允许类型。 */
    @Override
    public Constraints constraints(String policyCode, Instant now) {
        FileScanPolicy policy = activePolicy(policyCode, now);
        return new Constraints(policy.revisionNo(), new ArrayList<>(allowedTypes(policy)), policy.maxSizeBytes());
    }

    /**
     * 读取策略代码唯一的有效修订并核对声明的类型与大小
     *
     * @return 将冻结到上传的修订
     */
    FileScanPolicy admit(String policyCode, String mimeType, long sizeBytes, Instant now) {
        FileScanPolicy policy = activePolicy(policyCode, now);
        if (!allowedTypes(policy).contains(mimeType) || sizeBytes > policy.maxSizeBytes()) {
            throw new FailException(FileErrorCode.FILE_POLICY_REJECTED);
        }
        return policy;
    }

    /** 策略代码唯一的有效修订；没有、多于一个或结构不支持时关闭入口。 */
    FileScanPolicy activePolicy(String policyCode, Instant now) {
        var policies = repository.policies(policyCode, now);
        if (policies.size() != 1) {
            throw new ServiceUnavailableException();
        }
        var policy = policies.getFirst();
        if (policy.ruleSchemaVersion() != 1 || policy.requirementCount() < 1 || policy.maxSizeBytes() < 1) {
            throw new ServiceUnavailableException();
        }
        return policy;
    }

    /** 严格读取允许类型，保留登记顺序；结构损坏时关闭入口。 */
    Set<String> allowedTypes(FileScanPolicy policy) {
        var allowed = new LinkedHashSet<String>();
        try {
            if (policy.allowedMimeRules().length() > 8192) {
                throw new IllegalArgumentException();
            }
            var rules = json.readTree(policy.allowedMimeRules());
            if (!rules.isObject() || rules.size() != 1 || !rules.path("mimeTypes").isArray()
                || rules.path("mimeTypes").isEmpty() || rules.path("mimeTypes").size() > 64) {
                throw new IllegalArgumentException();
            }
            for (var mime : rules.path("mimeTypes")) {
                if (!mime.isTextual() || !mime.asText().matches("[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*")
                    || mime.asText().length() > 128 || !allowed.add(mime.asText())) {
                    throw new IllegalArgumentException();
                }
            }
        } catch (RuntimeException invalid) {
            throw new ServiceUnavailableException();
        }
        return allowed;
    }

    /** 转为文件能力发布的业务事实。 */
    private Upload result(FileUpload value) {
        return new Upload(value.uploadId(), value.tenantId(), value.uploaderId(), value.sourceSystem(),
            value.reasonCode(), value.displayName(),
            value.expectedSize(), value.expectedMime(), value.storageProvider(), value.containerRef(), value.objectRef(),
            value.scanPolicyId(), value.expiresAt(), value.status(), value.accessLevel(), value.replacesFileId(),
            value.replacementNo(), value.baseObjectVersion());
    }
}
