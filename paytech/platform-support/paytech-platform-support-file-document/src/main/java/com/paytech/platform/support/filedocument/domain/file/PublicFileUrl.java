package com.paytech.platform.support.filedocument.domain.file;

import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;

/** 公开文件的长期地址：读取时由桶的对外基础地址与对象名计算，不落库。 */
public final class PublicFileUrl {

    private PublicFileUrl() {
    }

    /**
     * 公开地址 = 基础地址 + / + 对象名；只有 PUBLIC、当前版本 AVAILABLE 且桶登记了基础地址时返回
     *
     * @param accessLevel 文件访问级别
     * @param scanStatus 当前版本的扫描状态
     * @param publicBaseUrl 所在存储桶的对外基础地址，可空
     * @param objectRef 对象名
     * @return 公开地址；不满足条件为空
     */
    public static String of(FileAccessLevel accessLevel, FileScanStatus scanStatus, String publicBaseUrl,
        String objectRef) {
        if (accessLevel != FileAccessLevel.PUBLIC || scanStatus != FileScanStatus.AVAILABLE || publicBaseUrl == null
            || objectRef == null) {
            return null;
        }
        return publicBaseUrl + "/" + objectRef;
    }

    /**
     * 带版本参数的公开地址，用于需要绕开 CDN 缓存的场景
     *
     * @param publicUrl 公开地址，可空
     * @param versionNo 当前版本号
     * @return {@code publicUrl?v=versionNo}；公开地址为空时为空
     */
    public static String versioned(String publicUrl, int versionNo) {
        return publicUrl == null ? null : publicUrl + "?v=" + versionNo;
    }
}
