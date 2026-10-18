package com.paytech.platform.support.filedocument.domainservice.service;

import java.time.Instant;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.*;

/** 向组合根发布上传规则，跨模块不暴露 DataObject。 */
public interface FileUploadService {
    /** 以当前有效策略登记一次上传。 */
    Upload create(long id, long accountId, long tenantId, Start start, String policyCode,
        String provider, String container, Instant now, Instant expiresAt);
}
