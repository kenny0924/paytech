package com.paytech.platform.support.service.processor.filestorage.response;

import java.util.List;

import com.paytech.platform.support.filedocument.domain.storage.FileScanPolicyView;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 检查策略列表与格式检查器能识别的类型。 */
@Getter
@AllArgsConstructor
public class ScanPolicyCatalog {

    /** 每个策略代码一行。 */
    private final List<FileScanPolicyView> policies;

    /** 修订允许类型可选的规范 MIME。 */
    private final List<String> supportedMimeTypes;
}
