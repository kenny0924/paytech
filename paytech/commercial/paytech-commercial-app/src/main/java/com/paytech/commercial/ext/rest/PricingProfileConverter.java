package com.paytech.commercial.ext.rest;

import static com.paytech.commercial.ext.rest.WireValues.code;
import static com.paytech.commercial.ext.rest.WireValues.id;
import static com.paytech.commercial.ext.rest.WireValues.text;

import java.util.List;

import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.PricingProfileKind;
import com.paytech.commercial.api.pricing.request.CreatePricingProfileRequestBody;
import com.paytech.commercial.api.pricing.request.PricingProfileTransitionRequestBody;
import com.paytech.commercial.api.pricing.request.ProfileConditionEntry;
import com.paytech.commercial.api.pricing.request.UpdatePricingProfileRequestBody;
import com.paytech.commercial.api.pricing.response.PricingProfileListResponseBody;
import com.paytech.commercial.api.pricing.response.PricingProfileResponseBody;
import com.paytech.commercial.api.pricing.response.ProfileConditionDetail;
import com.paytech.commercial.domain.PricingProfileView;
import com.paytech.commercial.service.processor.request.CreatePricingProfileRequest;
import com.paytech.commercial.service.processor.request.PricingProfileTransitionRequest;
import com.paytech.commercial.service.processor.request.UpdatePricingProfileRequest;

/** 定价画像 Wire 与内部请求 / 结果的协议转换，不访问数据库或执行业务校验。 */
final class PricingProfileConverter {
    /** 纯静态工具。 */
    private PricingProfileConverter() {
    }

    /** 创建画像请求。 */
    static CreatePricingProfileRequest createRequest(String actorId, CreatePricingProfileRequestBody body) {
        CreatePricingProfileRequest request = new CreatePricingProfileRequest();
        request.setActorId(AgreementConverter.actor(actorId));
        request.setPricingProfileCode(body.getPricingProfileCode());
        request.setProfileName(body.getProfileName());
        request.setProfileKind(code(PricingProfileKind.class, "profileKind", body.getProfileKind()));
        request.setPaymentMethodCode(body.getPaymentMethodCode());
        request.setConditions(conditions(body.getConditions()));
        return request;
    }

    /** 修改画像请求。 */
    static UpdatePricingProfileRequest updateRequest(String actorId, String pricingProfileId,
        UpdatePricingProfileRequestBody body) {
        UpdatePricingProfileRequest request = new UpdatePricingProfileRequest();
        request.setActorId(AgreementConverter.actor(actorId));
        request.setPricingProfileId(id("pricingProfileId", pricingProfileId));
        request.setExpectedChangeVersion(body.getExpectedChangeVersion());
        request.setProfileName(body.getProfileName());
        request.setPaymentMethodCode(body.getPaymentMethodCode());
        request.setConditions(conditions(body.getConditions()));
        return request;
    }

    /** 激活与退役请求。 */
    static PricingProfileTransitionRequest transitionRequest(String actorId, String pricingProfileId,
        PricingProfileTransitionRequestBody body) {
        PricingProfileTransitionRequest request = new PricingProfileTransitionRequest();
        request.setActorId(AgreementConverter.actor(actorId));
        request.setPricingProfileId(id("pricingProfileId", pricingProfileId));
        request.setExpectedChangeVersion(body.getExpectedChangeVersion());
        return request;
    }

    /** 画像结果。 */
    static PricingProfileResponseBody response(PricingProfileView view) {
        PricingProfileResponseBody body = new PricingProfileResponseBody();
        body.setPricingProfileId(Long.toString(view.getPricingProfileId()));
        body.setPricingProfileCode(view.getPricingProfileCode());
        body.setProfileName(view.getProfileName());
        body.setProfileKind(text(view.getProfileKind()));
        body.setPaymentMethodCode(view.getPaymentMethodCode());
        body.setStatus(text(view.getStatus()));
        body.setChangeVersion(view.getChangeVersion());
        body.setConditions(view.getConditions().stream().map(condition -> {
            ProfileConditionDetail detail = new ProfileConditionDetail();
            detail.setDimensionCode(text(condition.getDimensionCode()));
            detail.setConditionValue(condition.getConditionValue());
            return detail;
        }).toList());
        return body;
    }

    /** 画像列表结果。 */
    static PricingProfileListResponseBody list(List<PricingProfileView> views) {
        PricingProfileListResponseBody body = new PricingProfileListResponseBody();
        body.setProfiles(views.stream().map(PricingProfileConverter::response).toList());
        return body;
    }

    /** 条件行。 */
    static List<CreatePricingProfileRequest.Condition> conditions(List<ProfileConditionEntry> entries) {
        return entries.stream().map(entry -> {
            CreatePricingProfileRequest.Condition condition = new CreatePricingProfileRequest.Condition();
            condition.setDimensionCode(code(PricingDimension.class, "dimensionCode", entry.getDimensionCode()));
            condition.setConditionValue(entry.getConditionValue());
            return condition;
        }).toList();
    }
}
