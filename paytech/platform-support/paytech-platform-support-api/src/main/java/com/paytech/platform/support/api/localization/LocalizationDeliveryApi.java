package com.paytech.platform.support.api.localization;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.platform.support.api.localization.response.ManifestResponseBody;

/**
 * 语言包交付：各 Console BFF 匿名读取自身应用的 manifest（技术方案 3.7）；不要求操作人身份。
 * <li>BFF 固定 applicationCode 为自身应用，并负责 ETag（发布号 + locale）与 304；Platform 返回完整 DTO。</li>
 * <li>语言包由浏览器按 manifest 中的公开地址直接拉取，不经 BFF 与 Platform。</li>
 */
@HttpExchange(url = "/internal/localization/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface LocalizationDeliveryApi {

    /**
     * 读取应用当前或指定发布的 manifest
     *
     * @param applicationCode 应用编码
     * @param locale 可选：目标语言代码；bundles 只含 en 与该语言，缺省时只含 en
     * @param releaseNo 可选：指定发布号（预览），必须是 READY 的发布
     * @return manifest；应用还没有 READY 发布时 releaseNo 为 null、bundles 为空对象
     *         <li>应用不存在或已停用、指定发布不存在或不是 READY 返回 404；语言包公开地址不可用返回 503。</li>
     */
    @GetExchange("/applications/{applicationCode}/manifest")
    ManifestResponseBody getManifest(@PathVariable("applicationCode") String applicationCode,
        @RequestParam(value = "locale", required = false) String locale,
        @RequestParam(value = "releaseNo", required = false) Integer releaseNo);
}
