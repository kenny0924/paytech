package com.paytech.platform.support.filedocument.integration.storage;

import java.time.Instant;

import com.paytech.platform.support.filedocument.domain.file.DownloadPresentation;
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
     * @param presentation 签入地址的 response-content-disposition 与可选 response-content-type
     * @return 服务端签发的受控授权
     */
    Download authorize(File file,Instant expiresAt,DownloadPresentation presentation);
    /**
     * @param url 准确版本的短时 URL
     * @param expiresAt 实际到期时间，不得晚于请求期限
     */
    record Download(String url,Instant expiresAt) {
        /** 下载地址的最大长度。 */
        static final int MAX_URL_LENGTH = 8192;

        /**
         * 授权仍有效、不晚于签发期限，地址为有主机、无用户信息与片段的有界 HTTPS 地址
         *
         * @param deadline 本次签发期限
         * @param now 当前时间
         */
        public boolean usableUntil(Instant deadline, Instant now) {
            if (url == null || expiresAt == null || url.length() > MAX_URL_LENGTH || !now.isBefore(expiresAt)
                || expiresAt.isAfter(deadline)) {
                return false;
            }
            try {
                java.net.URI uri = java.net.URI.create(url);
                return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null
                    && uri.getFragment() == null;
            } catch (IllegalArgumentException invalid) {
                return false;
            }
        }
        /** 临时 URL 不进入日志。 */
        @Override public String toString() { return "Download[REDACTED]"; }
    }
}
