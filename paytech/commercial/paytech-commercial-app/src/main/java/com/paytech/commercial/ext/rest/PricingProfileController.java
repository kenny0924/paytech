package com.paytech.commercial.ext.rest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.commercial.api.enums.PricingProfileKind;
import com.paytech.commercial.api.pricing.PricingProfileApi;
import com.paytech.commercial.api.pricing.request.CreatePricingProfileRequestBody;
import com.paytech.commercial.api.pricing.request.PricingProfileTransitionRequestBody;
import com.paytech.commercial.api.pricing.request.UpdatePricingProfileRequestBody;
import com.paytech.commercial.api.pricing.response.PricingProfileListResponseBody;
import com.paytech.commercial.api.pricing.response.PricingProfileResponseBody;
import com.paytech.commercial.domain.enums.PricingProfileStatus;
import com.paytech.commercial.domainservice.service.PricingProfileService;
import com.paytech.commercial.service.processor.ActivatePricingProfileProcessor;
import com.paytech.commercial.service.processor.CreatePricingProfileProcessor;
import com.paytech.commercial.service.processor.RetirePricingProfileProcessor;
import com.paytech.commercial.service.processor.UpdatePricingProfileProcessor;

import lombok.AllArgsConstructor;

/** 定价画像运营维护的入站适配（technique.md §2.2）：写入走 Processor，列表直接委托 PricingProfileService。 */
@RestController
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@AllArgsConstructor
public class PricingProfileController implements PricingProfileApi {
    private final PricingProfileService pricingProfileService;
    private final RetirePricingProfileProcessor retirePricingProfileProcessor;
    private final CreatePricingProfileProcessor createPricingProfileProcessor;
    private final UpdatePricingProfileProcessor updatePricingProfileProcessor;
    private final ActivatePricingProfileProcessor activatePricingProfileProcessor;

    /** 创建画像。 */
    @Override
    public PricingProfileResponseBody createProfile(String actorId, CreatePricingProfileRequestBody body) {
        return PricingProfileConverter.response(createPricingProfileProcessor.process(
            PricingProfileConverter.createRequest(actorId, body)));
    }

    /** 修改 DRAFT 画像。 */
    @Override
    public PricingProfileResponseBody updateProfile(String actorId, String pricingProfileId,
        UpdatePricingProfileRequestBody body) {
        return PricingProfileConverter.response(updatePricingProfileProcessor.process(
            PricingProfileConverter.updateRequest(actorId, pricingProfileId, body)));
    }

    /** 激活画像。 */
    @Override
    public PricingProfileResponseBody activate(String actorId, String pricingProfileId,
        PricingProfileTransitionRequestBody body) {
        return PricingProfileConverter.response(activatePricingProfileProcessor.process(
            PricingProfileConverter.transitionRequest(actorId, pricingProfileId, body)));
    }

    /** 退役画像。 */
    @Override
    public PricingProfileResponseBody retire(String actorId, String pricingProfileId,
        PricingProfileTransitionRequestBody body) {
        return PricingProfileConverter.response(retirePricingProfileProcessor.process(
            PricingProfileConverter.transitionRequest(actorId, pricingProfileId, body)));
    }

    /** 画像列表。 */
    @Override
    public PricingProfileListResponseBody listProfiles(String kind, String status) {
        return PricingProfileConverter.list(pricingProfileService.listProfiles(
            WireValues.code(PricingProfileKind.class, "kind", kind),
            WireValues.code(PricingProfileStatus.class, "status", status)));
    }
}
