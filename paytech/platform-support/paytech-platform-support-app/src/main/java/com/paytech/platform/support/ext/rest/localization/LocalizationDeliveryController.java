package com.paytech.platform.support.ext.rest.localization;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.api.localization.LocalizationDeliveryApi;
import com.paytech.platform.support.api.localization.response.ManifestResponseBody;
import com.paytech.platform.support.localization.service.processor.GetManifestProcessor;
import com.paytech.platform.support.localization.service.processor.request.ManifestQuery;

import lombok.AllArgsConstructor;

/** manifest 交付的协议适配；不要求操作人身份，BFF 固定应用编码（技术方案 3.7）。 */
@RestController
@AllArgsConstructor
public class LocalizationDeliveryController implements LocalizationDeliveryApi {

    private final GetManifestProcessor manifestProcessor;

    /** 读取 manifest。 */
    @Override
    public ManifestResponseBody getManifest(String applicationCode, String locale, Integer releaseNo) {
        if (releaseNo != null && releaseNo < 1) {
            throw new InvalidParameterException("releaseNo 必须为正整数");
        }
        return manifestProcessor.process(new ManifestQuery(applicationCode, LocalizationConverter.text(locale),
            releaseNo));
    }
}
