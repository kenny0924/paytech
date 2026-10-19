package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.CommercialContractConverter.convert;
import static com.paytech.operations.console.bff.integration.impl.CommercialContractConverter.request;
import static com.paytech.operations.console.bff.integration.impl.CommercialContractConverter.require;
import static com.paytech.operations.console.bff.integration.impl.CommercialContractConverter.requireSame;
import static com.paytech.operations.console.bff.integration.impl.CommercialContractConverter.write;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.paytech.commercial.api.price.CommercialTermVersionPriceApi;
import com.paytech.commercial.api.pricing.PricingProfileApi;
import com.paytech.commercial.api.term.CommercialTermApi;
import com.paytech.operations.console.bff.integration.CommercialPricingClient;
import com.paytech.operations.console.bff.integration.CommercialTermCommandExchange;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermPlanResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermVersionCommandRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermVersionPricesResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermVersionResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CreateCommercialTermPlanRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CreateCommercialTermVersionRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CreatePricingProfileRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.PricingProfileListResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.PricingProfileResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.PricingProfileTransitionRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.ReplaceCommercialTermPricesRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.ScheduleCommercialTermVersionRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.UpdatePricingProfileRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import lombok.AllArgsConstructor;

/**
 * commercial 定价画像与条款接口的 HTTP 转发：每个入口只调用一次 commercial，不重试
 * <li>命令把会话 Account ID 填入 X-Gateway-Actor-Subject；Maker / Checker 由 commercial 依据该操作人判定。</li>
 * <li>回执的画像、方案与版本身份必须与请求一致；画像列表与价格全集必须带集合。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class CommercialPricingClientImpl implements CommercialPricingClient {
    private final PricingProfileApi profileApi;
    private final CommercialTermApi termApi;
    private final CommercialTermCommandExchange termCommands;
    private final CommercialTermVersionPriceApi priceApi;

    /** 画像列表。 */
    @Override
    public PricingProfileListResponseBody listProfiles(String kind, String status) {
        var profiles = convert(profileApi.listProfiles(kind, status), PricingProfileListResponseBody.class);
        require(profiles.getProfiles() != null);
        return profiles;
    }

    /** 创建画像；回执画像代码与请求一致。 */
    @Override
    public PricingProfileResponseBody createProfile(OperatorContext operator, CreatePricingProfileRequestBody request) {
        var profile = write(request, () -> profileApi.createProfile(operator.accountId(), request(request,
            com.paytech.commercial.api.pricing.request.CreatePricingProfileRequestBody.class)),
            PricingProfileResponseBody.class);
        requireSame(request.getPricingProfileCode(), profile.getPricingProfileCode());
        return profile;
    }

    /** 改写画像。 */
    @Override
    public PricingProfileResponseBody updateProfile(OperatorContext operator, String pricingProfileId,
        UpdatePricingProfileRequestBody request) {
        return sameProfile(pricingProfileId, request, () -> profileApi.updateProfile(operator.accountId(),
            pricingProfileId, request(request,
                com.paytech.commercial.api.pricing.request.UpdatePricingProfileRequestBody.class)));
    }

    /** 激活画像。 */
    @Override
    public PricingProfileResponseBody activateProfile(OperatorContext operator, String pricingProfileId,
        PricingProfileTransitionRequestBody request) {
        return sameProfile(pricingProfileId, request, () -> profileApi.activate(operator.accountId(), pricingProfileId,
            profileTransition(request)));
    }

    /** 退役画像。 */
    @Override
    public PricingProfileResponseBody retireProfile(OperatorContext operator, String pricingProfileId,
        PricingProfileTransitionRequestBody request) {
        return sameProfile(pricingProfileId, request, () -> profileApi.retire(operator.accountId(), pricingProfileId,
            profileTransition(request)));
    }

    /** 创建方案；回执方案编号、条款类型与请求一致。 */
    @Override
    public CommercialTermPlanResponseBody createPlan(OperatorContext operator, CreateCommercialTermPlanRequestBody request) {
        var plan = write(request, () -> body(termCommands.createPlan(operator.accountId(), request)),
            CommercialTermPlanResponseBody.class);
        requireSame(request.getPlanCode(), plan.getPlanCode());
        requireSame(request.getTermType(), plan.getTermType());
        return plan;
    }

    /** 创建版本；回执方案 ID 与路径一致。 */
    @Override
    public CommercialTermVersionResponseBody createVersion(OperatorContext operator, String planId,
        CreateCommercialTermVersionRequestBody request) {
        var version = write(request, () -> body(termCommands.createVersion(operator.accountId(), planId, request)),
            CommercialTermVersionResponseBody.class);
        requireSame(planId, version.getPlanId());
        return version;
    }

    /** 整体替换价格集合。 */
    @Override
    public CommercialTermVersionResponseBody replacePrices(OperatorContext operator, String versionId,
        ReplaceCommercialTermPricesRequestBody request) {
        return sameVersion(versionId, request, () -> termCommands.replacePrices(operator.accountId(), versionId, request));
    }

    /** 版本状态推进。 */
    @Override
    public CommercialTermVersionResponseBody transitionVersion(OperatorContext operator, String versionId,
        VersionAction action, CommercialTermVersionCommandRequestBody request) {
        String actor = operator.accountId();
        return sameVersion(versionId, request, () -> switch (action) {
            case SUBMIT -> termCommands.submitVersion(actor, versionId, request);
            case APPROVE -> termCommands.approveVersion(actor, versionId, request);
            case REJECT -> termCommands.rejectVersion(actor, versionId, request);
            case ACTIVATE -> termCommands.activateVersion(actor, versionId, request);
        });
    }

    /** 排期。 */
    @Override
    public CommercialTermVersionResponseBody scheduleVersion(OperatorContext operator, String versionId,
        ScheduleCommercialTermVersionRequestBody request) {
        return sameVersion(versionId, request, () -> termCommands.scheduleVersion(operator.accountId(), versionId, request));
    }

    /** 版本概要；回执版本 ID 与请求一致。 */
    @Override
    public CommercialTermVersionResponseBody version(String versionId) {
        var version = convert(termApi.getVersion(versionId), CommercialTermVersionResponseBody.class);
        requireSame(versionId, version.getVersionId());
        return version;
    }

    /** 价格全集；回执版本、条款类型与 OE 与请求一致。 */
    @Override
    public CommercialTermVersionPricesResponseBody prices(String versionId, String entityNo, String termType,
        String currency) {
        var prices = convert(priceApi.listPrices(versionId, entityNo, termType, currency),
            CommercialTermVersionPricesResponseBody.class);
        requireSame(versionId, prices.getTermVersionId());
        requireSame(entityNo, prices.getEntityNo());
        requireSame(termType, prices.getTermType());
        require(prices.getPrices() != null);
        return prices;
    }

    private static com.paytech.commercial.api.pricing.request.PricingProfileTransitionRequestBody profileTransition(
        PricingProfileTransitionRequestBody request) {
        return request(request, com.paytech.commercial.api.pricing.request.PricingProfileTransitionRequestBody.class);
    }

    private static PricingProfileResponseBody sameProfile(String pricingProfileId, Object request, Supplier<Object> call) {
        var profile = write(request, call, PricingProfileResponseBody.class);
        requireSame(pricingProfileId, profile.getPricingProfileId());
        return profile;
    }

    private static CommercialTermVersionResponseBody sameVersion(String versionId, Object request, Supplier<Object> call) {
        var version = write(request, call, CommercialTermVersionResponseBody.class);
        requireSame(versionId, version.getVersionId());
        return version;
    }

    /** 201 / 200 回执取 Body；Owner 的新建与重放状态不向浏览器区分。 */
    private static Object body(org.springframework.http.ResponseEntity<?> response) {
        return response == null ? null : response.getBody();
    }
}
