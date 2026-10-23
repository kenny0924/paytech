package com.paytech.client.console.bff.ext.rest;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.console.bff.integration.PlatformLocalizationClient;
import com.paytech.platform.support.api.localization.response.ManifestResponseBody;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;

/**
 * Client 控制台语言包 manifest 的匿名入口（技术方案 3.7）：Gateway PUBLIC 路由，不要求会话与工作区
 * <li>应用编码固定 client-console；页面传入的其他 Query（含应用编码）忽略，前端读不到其他应用的文案。</li>
 * <li>Cache-Control: no-cache；ETag 由发布号、语言与 manifest 内容摘要组成，If-None-Match 命中返回 304。</li>
 */
@RestController
@AllArgsConstructor
public class ClientLocalizationManifestController {
    /** 本控制台在 Platform 多语言中的应用编码。 */
    static final String APPLICATION_CODE = "client-console";

    /** 匿名入口路径；不在任何会话拦截器的路径范围内。 */
    public static final String PATH = "/i18n/v1/manifest";

    /** BCP 47 语言代码，与 Platform 语言代码格式一致。 */
    private static final String LOCALE = "[a-z]{2,3}(-[A-Z][a-z]{3})?(-([A-Z]{2}|[0-9]{3}))?";

    private final PlatformLocalizationClient client;

    /** 读取当前或指定 READY 发布的 manifest。 */
    @GetMapping(PATH)
    public ResponseEntity<ManifestResponseBody> getManifest(
        @RequestParam(value = "locale", required = false) @Pattern(regexp = LOCALE) String locale,
        @RequestParam(value = "releaseNo", required = false) @Positive Integer releaseNo) {
        ManifestResponseBody manifest = client.getManifest(APPLICATION_CODE, locale, releaseNo);
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).eTag(ManifestEtag.of(manifest, locale))
            .body(manifest);
    }
}
