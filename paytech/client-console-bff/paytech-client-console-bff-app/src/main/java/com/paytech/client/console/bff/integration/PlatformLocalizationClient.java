package com.paytech.client.console.bff.integration;

import com.paytech.platform.support.api.localization.response.ManifestResponseBody;

/**
 * Platform 多语言交付接口的出站端口：匿名读取本控制台应用的 manifest，不携带身份
 * <li>回执的应用编码与请求不一致、指定发布号与回执不一致或缺少语言列表、语言包映射，按上游无效响应抛出。</li>
 */
public interface PlatformLocalizationClient {

    /**
     * 读取当前或指定 READY 发布的 manifest
     *
     * @param applicationCode BFF 固定的应用编码
     * @param locale 可选目标语言
     * @param releaseNo 可选指定发布号（预览）
     * @return manifest；应用还没有 READY 发布时 releaseNo 为 null、bundles 为空
     */
    ManifestResponseBody getManifest(String applicationCode, String locale, Integer releaseNo);
}
