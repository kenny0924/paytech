package com.paytech.platform.support.filedocument.integration.storage;
import java.time.Instant;
import com.paytech.platform.support.filedocument.domain.file.FileModels.File;
/** 下载供应商端口；生产供应商未选定时没有默认实现。 */
public interface FileDownloadStorage {
    /** 显式供应商身份。 */
    String providerCode();
    /**
     * 必须签发原 provider/container/object/version 的短时只读 GET URL；禁止 latest、重定向到可变对象或返回存储凭据。
     * 适配器负责受信存储域名与有界网络超时，调用者始终在事务外调用。
     * @param file 原不可变文件坐标
     * @param expiresAt 不可延长的本次授权期限
     * @return 服务端签发的受控授权
     */
    Download authorize(File file,Instant expiresAt);
    /**
     * @param url 准确版本的短时 URL
     * @param expiresAt 实际到期时间，不得晚于请求期限
     */
    record Download(String url,Instant expiresAt) {
        /** 临时 URL 不进入日志。 */
        @Override public String toString() { return "Download[REDACTED]"; }
    }
}
