package com.paytech.platform.support.localization.integration;

import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.localization.core.enums.BundleFileState;

/** 语言包文件检查状态（技术方案 3.3 第 3 步）；经 file-document 进程内查询，在事务外调用。 */
public interface ReleaseBundleFileChecker {

    /**
     * 文件检查结论。
     *
     * @param fileAddress 文件服务地址
     * @return 检查结论；文件不存在视为 REJECTED
     */
    BundleFileState state(FileAddress fileAddress);
}
