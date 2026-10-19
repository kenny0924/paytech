package com.paytech.operations.console.bff.integration;

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

/**
 * commercial 定价画像与商业条款的本地端口；Maker-Checker、激活校验、错误与事实归 commercial
 * <li>命令以会话 Account ID 作为 X-Gateway-Actor-Subject 转发（提交为 Maker，批准 / 驳回为 Checker）；查询不携带操作人。</li>
 *
 * @author Claude
 */
public interface CommercialPricingClient {

    /**
     * 画像列表
     *
     * @param kind 可选种类
     * @param status 可选状态
     * @return 画像列表
     */
    PricingProfileListResponseBody listProfiles(String kind, String status);

    /**
     * 创建 DRAFT 画像
     *
     * @param operator 操作人
     * @param request 画像内容
     * @return 画像
     */
    PricingProfileResponseBody createProfile(OperatorContext operator, CreatePricingProfileRequestBody request);

    /**
     * DRAFT 内改写画像
     *
     * @param operator 操作人
     * @param pricingProfileId 画像 ID
     * @param request 画像内容与版本条件
     * @return 画像
     */
    PricingProfileResponseBody updateProfile(OperatorContext operator, String pricingProfileId,
        UpdatePricingProfileRequestBody request);

    /**
     * 激活画像
     *
     * @param operator 操作人
     * @param pricingProfileId 画像 ID
     * @param request 版本条件
     * @return 画像
     */
    PricingProfileResponseBody activateProfile(OperatorContext operator, String pricingProfileId,
        PricingProfileTransitionRequestBody request);

    /**
     * 退役画像
     *
     * @param operator 操作人
     * @param pricingProfileId 画像 ID
     * @param request 版本条件
     * @return 画像
     */
    PricingProfileResponseBody retireProfile(OperatorContext operator, String pricingProfileId,
        PricingProfileTransitionRequestBody request);

    /**
     * 创建条款方案
     *
     * @param operator 操作人
     * @param request 方案内容
     * @return 方案
     */
    CommercialTermPlanResponseBody createPlan(OperatorContext operator, CreateCommercialTermPlanRequestBody request);

    /**
     * 创建 DRAFT 版本
     *
     * @param operator 操作人
     * @param planId 方案 ID
     * @param request 版本属性
     * @return 版本
     */
    CommercialTermVersionResponseBody createVersion(OperatorContext operator, String planId,
        CreateCommercialTermVersionRequestBody request);

    /**
     * 整体替换价格集合
     *
     * @param operator 操作人
     * @param versionId 版本 ID
     * @param request 完整价格集合
     * @return 版本
     */
    CommercialTermVersionResponseBody replacePrices(OperatorContext operator, String versionId,
        ReplaceCommercialTermPricesRequestBody request);

    /**
     * 版本状态推进：submit、approve、reject、activate
     *
     * @param operator 操作人
     * @param versionId 版本 ID
     * @param action 动作
     * @param request 版本条件
     * @return 版本
     */
    CommercialTermVersionResponseBody transitionVersion(OperatorContext operator, String versionId, VersionAction action,
        CommercialTermVersionCommandRequestBody request);

    /**
     * 排期
     *
     * @param operator 操作人
     * @param versionId 版本 ID
     * @param request 版本条件与生效起点
     * @return 版本
     */
    CommercialTermVersionResponseBody scheduleVersion(OperatorContext operator, String versionId,
        ScheduleCommercialTermVersionRequestBody request);

    /**
     * 版本概要
     *
     * @param versionId 版本 ID
     * @return 版本
     */
    CommercialTermVersionResponseBody version(String versionId);

    /**
     * 版本下某 OE 的价格全集
     *
     * @param versionId 版本 ID
     * @param entityNo 准确 OE
     * @param termType 条款类型
     * @param currency 可选币种
     * @return 价格全集
     */
    CommercialTermVersionPricesResponseBody prices(String versionId, String entityNo, String termType, String currency);

    /** 无额外参数的版本状态推进动作。 */
    enum VersionAction {
        /** DRAFT → PENDING_APPROVAL */
        SUBMIT,

        /** PENDING_APPROVAL → APPROVED */
        APPROVE,

        /** PENDING_APPROVAL → DRAFT */
        REJECT,

        /** SCHEDULED → ACTIVE */
        ACTIVATE
    }
}
