package com.paytech.platform.support.localization.integration;

import com.paytech.platform.support.api.files.FileAddress;

/**
 * 语言包写入与公开地址端口（技术方案 3.3 第 1 步、3.7）；在事务外调用。
 * <li>实现 {@code FileServiceReleaseBundleStore}（paytech-platform-support-core，依赖 Platform 文件入口）：经文件服务以 PUBLIC 访问级别、
 * 原因码 I18N_RELEASE_BUNDLE 上传，检查策略由 platform-support × PUBLIC 写入路由决定（I18N_BUNDLE_JSON）；对象名带应用、发布号、语言、
 * 命名空间、摘要与随机部分，每个文件只写一次，永不覆盖。</li>
 * <li>files.enabled 关闭时没有实现：发布与 manifest 返回 503，不伪造地址。</li>
 */
public interface ReleaseBundleStore {

    /**
     * 上传一个语言包。
     *
     * @param applicationCode 应用编码
     * @param releaseNo 发布号
     * @param locale 语言代码
     * @param namespaceCode 命名空间编码
     * @param content 语言包字节（application/json，UTF-8）
     * @param sha256 内容 SHA-256，小写十六进制
     * @return 文件服务地址 /files/v1/{fileId}
     */
    FileAddress store(String applicationCode, int releaseNo, String locale, String namespaceCode, byte[] content,
        String sha256);

    /**
     * 文件的公开访问地址；由文件服务计算，不落库。
     *
     * @param fileAddress 文件服务地址
     * @return 浏览器可直接拉取的公开地址
     */
    String publicUrl(FileAddress fileAddress);
}
