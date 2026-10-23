package com.paytech.platform.support.filedocument.integration.storage;

import java.io.InputStream;
import java.util.List;

import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
/** 完成适配器必须以 uploadId 幂等固定原目标；同一 Upload 的恢复永远打开同一版本。
 * 版本化或 create-once finalization 必须先完成；迟到 PUT 不得改写返回流的对象版本。
 * MIME 必须服务端识别，加密引用须经真实存储验证；不能使用浏览器元数据或 ETag 充当证明。
 */
public interface CompletedUploadStorage {
    /** 无 I/O 的受控提供方代码。 */
    String providerCode();
    /** 在事务外固定并打开准确版本；未知结果须按原 Upload 恢复，不得重新选择 latest。 */
    Opened open(Upload upload);
    /**
     * 替换上传：列出存储中该对象比 base_object_version 新的全部版本（含删除标记），最新在前
     * <li>找不到基准版本时视为不可用，不能把所有版本都当作新版本。</li>
     */
    List<String> newerVersions(Upload upload);
    /** 替换上传：打开已核对唯一性的准确版本，返回其服务端识别类型、加密引用与字节流。 */
    Opened open(Upload upload,String version);
    /**
     * @param version 非空不可变版本
     * @param detectedMime 服务端识别结果
     * @param encryptionKeyRef 已验证的保护引用
     * @param bytes 精确版本字节流，必须有界超时且由调用方关闭
     */
    record Opened(String version,String detectedMime,String encryptionKeyRef,InputStream bytes) implements AutoCloseable {
        /** 释放供应商连接，不记录地址或内容。 */
        @Override public void close() throws java.io.IOException { if (bytes!=null) bytes.close(); }
        /** 不将供应商坐标写入日志。 */
        @Override public String toString() { return "Opened[REDACTED]"; }
    }
}
