package com.paytech.platform.support.service.processor.files.response;

import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Constraints;
import com.paytech.platform.support.filedocument.domain.storage.StorageWriteTarget;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 写入限制的查询结果；未配置时只有访问级别，写入目标与策略限制为空。 */
@Getter
@AllArgsConstructor
public class QueryFileUploadConstraintsResponse {

    /** 查询的访问级别。 */
    private final FileAccessLevel accessLevel;

    /** 路由选择的写入目标；未配置为空。 */
    private final StorageWriteTarget target;

    /** 扫描策略有效修订的限制；未配置为空。 */
    private final Constraints constraints;

    /** 是否能开始上传。 */
    public boolean configured() {
        return target != null && constraints != null;
    }
}
