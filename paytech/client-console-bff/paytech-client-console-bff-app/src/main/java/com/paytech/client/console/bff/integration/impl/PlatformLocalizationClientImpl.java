package com.paytech.client.console.bff.integration.impl;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.integration.PlatformLocalizationClient;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.api.localization.LocalizationDeliveryApi;
import com.paytech.platform.support.api.localization.response.ManifestResponseBody;

import lombok.AllArgsConstructor;

/** 调用 Platform 交付接口一次，不重试，并核对回执与请求对应。 */
@Component
@AllArgsConstructor
public class PlatformLocalizationClientImpl implements PlatformLocalizationClient {
    private final LocalizationDeliveryApi delivery;

    /** 读取 manifest 并核对应用编码、发布号与必需集合。 */
    @Override
    public ManifestResponseBody getManifest(String applicationCode, String locale, Integer releaseNo) {
        var value = delivery.getManifest(applicationCode, locale, releaseNo);
        if (!Objects.equals(applicationCode, value.getApplication()) || value.getLocales() == null
            || value.getLocales().stream().anyMatch(Objects::isNull) || value.getBundles() == null
            || value.getBundles().values().stream().anyMatch(Objects::isNull)
            || releaseNo != null && !releaseNo.equals(value.getReleaseNo())) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        return value;
    }
}
