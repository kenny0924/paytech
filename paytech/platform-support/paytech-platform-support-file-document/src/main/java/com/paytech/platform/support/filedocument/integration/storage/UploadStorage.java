package com.paytech.platform.support.filedocument.integration.storage;

import com.paytech.platform.support.filedocument.domain.file.UploadModels.Grant;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;

/** 对象存储出站端口；生产必须提供真实适配器，没有默认成功实现。 */
public interface UploadStorage {
    /** 返回适配器受控代码，用于核对原 Upload，禁止重放切换供应商。 */
    String providerCode();
    /**
     * 在事务提交后为原目标签发权限，截止时间不得晚于 Upload.expiresAt
     * <li>首次上传签入 Content-Type 与 If-None-Match: *（只允许对象不存在时写入）；替换上传只签 Content-Type，由版本控制保留旧版本。</li>
     */
    Grant authorize(Upload upload);
}
