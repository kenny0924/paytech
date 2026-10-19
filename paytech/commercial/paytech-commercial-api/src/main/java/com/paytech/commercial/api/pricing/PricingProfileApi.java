package com.paytech.commercial.api.pricing;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.commercial.api.pricing.request.CreatePricingProfileRequestBody;
import com.paytech.commercial.api.pricing.request.PricingProfileTransitionRequestBody;
import com.paytech.commercial.api.pricing.request.UpdatePricingProfileRequestBody;
import com.paytech.commercial.api.pricing.response.PricingProfileListResponseBody;
import com.paytech.commercial.api.pricing.response.PricingProfileResponseBody;

import jakarta.validation.Valid;

/**
 * 定价画像的运营维护合同（technique.md §2.2；仅私网，调用方 operations-console-bff）
 * <li>画像全局复用：DRAFT 可改，ACTIVE 后冻结，RETIRED 只阻止新引用。写入以画像 BCR Root 版本做 CAS，不处理 Idempotency-Key。</li>
 */
@HttpExchange("/pricing-profiles/v1")
public interface PricingProfileApi {
    /** Gateway 传入、BFF 透传的操作者 Account ID 请求头。 */
    String ACTOR_HEADER = "X-Gateway-Actor-Subject";

    /**
     * 创建 DRAFT 画像及其条件行
     *
     * @param actorId 操作者 Account ID，规范正十进制
     * @param body 画像内容
     * @return 画像；同 pricingProfileCode 同内容返回原画像，不同内容返回 409
     */
    @PostExchange
    PricingProfileResponseBody createProfile(@RequestHeader(ACTOR_HEADER) String actorId,
        @Valid @RequestBody CreatePricingProfileRequestBody body);

    /**
     * DRAFT 内改写名称、支付方式并整体替换条件行
     *
     * @param actorId 操作者 Account ID
     * @param pricingProfileId 画像 ID
     * @param body 画像内容与版本条件
     * @return 画像；内容不变时不产生变更并返回当前版本
     */
    @PutExchange("/{pricingProfileId}")
    PricingProfileResponseBody updateProfile(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("pricingProfileId") String pricingProfileId, @Valid @RequestBody UpdatePricingProfileRequestBody body);

    /**
     * DRAFT → ACTIVE，激活后种类、支付方式与条件永久冻结
     *
     * @param actorId 操作者 Account ID
     * @param pricingProfileId 画像 ID
     * @param body 版本条件
     * @return 画像；已是 ACTIVE 且版本为期望版本加一时按重放返回当前结果
     */
    @PostExchange("/{pricingProfileId}/activate")
    PricingProfileResponseBody activate(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("pricingProfileId") String pricingProfileId,
        @Valid @RequestBody PricingProfileTransitionRequestBody body);

    /**
     * ACTIVE → RETIRED，阻止新引用，已引用的价格不受影响
     *
     * @param actorId 操作者 Account ID
     * @param pricingProfileId 画像 ID
     * @param body 版本条件
     * @return 画像；已是 RETIRED 且版本为期望版本加一时按重放返回当前结果
     */
    @PostExchange("/{pricingProfileId}/retire")
    PricingProfileResponseBody retire(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("pricingProfileId") String pricingProfileId,
        @Valid @RequestBody PricingProfileTransitionRequestBody body);

    /**
     * 按种类与状态筛选画像（运营选择用，不分页）
     *
     * @param kind 种类，可省略：PAYMENT_METHOD、PAYOUT_RAIL、FX_PAIR、DISPUTE、PERIODIC
     * @param status 状态，可省略：DRAFT、ACTIVE、RETIRED
     * @return 画像列表，按 ID 升序；未知种类或状态返回 400
     */
    @GetExchange
    PricingProfileListResponseBody listProfiles(@RequestParam(value = "kind", required = false) String kind,
        @RequestParam(value = "status", required = false) String status);
}
