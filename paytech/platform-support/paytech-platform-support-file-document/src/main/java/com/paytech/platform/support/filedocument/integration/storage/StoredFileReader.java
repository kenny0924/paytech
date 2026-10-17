package com.paytech.platform.support.filedocument.integration.storage;

import java.io.InputStream;

import com.paytech.platform.support.filedocument.domain.file.FileModels.File;

/** 已登记文件的准确版本读取端口；供文件检查复核原字节，不签发浏览器授权。 */
public interface StoredFileReader {
    /** 无 I/O 的受控提供方代码，与原文件的 provider 一致才可使用。 */
    String providerCode();

    /**
     * 打开原文件登记的不可变版本，不读取 latest。
     *
     * @param file
     *            已完成登记的文件坐标与版本
     * @return 准确版本字节流，调用方负责关闭
     */
    InputStream open(File file);
}
