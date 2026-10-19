package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.operations.console.bff.integration.CommercialPricingClient;
import com.paytech.operations.console.bff.integration.CommercialPricingClient.VersionAction;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermPlanResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermVersionCommandRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermVersionPricesResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermVersionResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermWorkspaceResponseBody;
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
import com.paytech.operations.console.bff.service.processor.CommercialTermWorkspaceProcessor;
import com.paytech.operations.console.bff.service.processor.request.CommercialTermWorkspaceRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 定价画像与商业条款的运营入口
 * <li>Gateway 绑定：GET 为 COMMERCIAL_PRICING_READ；画像写入与条款创建、价格、提交、排期、激活为 COMMERCIAL_PRICING_MANAGE；
 * 批准与驳回为独立的 COMMERCIAL_PRICING_APPROVE，保证 Maker 与 Checker 可由不同角色承担。</li>
 * <li>版本编辑页经 Processor 聚合版本、合同范围与全部 OE 的价格；写入简单转发 commercial，操作人取自会话。</li>
 * <li>commercial 本期没有方案 / 版本列表接口，页面从合同详情的当前 / 已排期版本或版本 ID 进入。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/commercial-admin/v1")
public class CommercialPricingController {
    private static final String ID = "[1-9][0-9]{0,18}";

    private final CommercialPricingClient client;
    private final CommercialTermWorkspaceProcessor workspaceProcessor;

    /** P01 画像列表。 */
    @GetMapping("/pricing-profiles")
    public PricingProfileListResponseBody listProfiles(HttpServletRequest servletRequest,
        @RequestParam(value = "kind", required = false) @Pattern(regexp = "PAYMENT_METHOD|PAYOUT_RAIL|FX_PAIR|DISPUTE|PERIODIC") String kind,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "DRAFT|ACTIVE|RETIRED") String status) {
        requireQueryKeys(servletRequest, Set.of("kind", "status"));
        return client.listProfiles(kind, status);
    }

    /** P02 创建 DRAFT 画像。 */
    @PostMapping("/pricing-profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public PricingProfileResponseBody createProfile(HttpServletRequest servletRequest, OperatorContext operator,
        @RequestBody @NotNull @Valid CreatePricingProfileRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createProfile(operator, body);
    }

    /** P03 DRAFT 内改写画像。 */
    @PutMapping("/pricing-profiles/{pricingProfileId}")
    public PricingProfileResponseBody updateProfile(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("pricingProfileId") @Pattern(regexp = ID) String pricingProfileId,
        @RequestBody @NotNull @Valid UpdatePricingProfileRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.updateProfile(operator, pricingProfileId, body);
    }

    /** P04 激活画像。 */
    @PostMapping("/pricing-profiles/{pricingProfileId}/activate")
    public PricingProfileResponseBody activateProfile(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("pricingProfileId") @Pattern(regexp = ID) String pricingProfileId,
        @RequestBody @NotNull @Valid PricingProfileTransitionRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.activateProfile(operator, pricingProfileId, body);
    }

    /** P05 退役画像。 */
    @PostMapping("/pricing-profiles/{pricingProfileId}/retire")
    public PricingProfileResponseBody retireProfile(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("pricingProfileId") @Pattern(regexp = ID) String pricingProfileId,
        @RequestBody @NotNull @Valid PricingProfileTransitionRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.retireProfile(operator, pricingProfileId, body);
    }

    /** T01 创建条款方案。 */
    @PostMapping("/commercial-terms/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public CommercialTermPlanResponseBody createPlan(HttpServletRequest servletRequest, OperatorContext operator,
        @RequestBody @NotNull @Valid CreateCommercialTermPlanRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createPlan(operator, body);
    }

    /** T02 创建 DRAFT 版本（可复制）。 */
    @PostMapping("/commercial-terms/plans/{planId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public CommercialTermVersionResponseBody createVersion(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("planId") @Pattern(regexp = ID) String planId,
        @RequestBody @NotNull @Valid CreateCommercialTermVersionRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createVersion(operator, planId, body);
    }

    /** T03 整体替换 DRAFT 版本的价格集合。 */
    @PutMapping("/commercial-terms/versions/{versionId}/prices")
    public CommercialTermVersionResponseBody replacePrices(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("versionId") @Pattern(regexp = ID) String versionId,
        @RequestBody @NotNull @Valid ReplaceCommercialTermPricesRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.replacePrices(operator, versionId, body);
    }

    /** T04 提交审批（Maker）。 */
    @PostMapping("/commercial-terms/versions/{versionId}/submit")
    public CommercialTermVersionResponseBody submitVersion(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("versionId") @Pattern(regexp = ID) String versionId,
        @RequestBody @NotNull @Valid CommercialTermVersionCommandRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.transitionVersion(operator, versionId, VersionAction.SUBMIT, body);
    }

    /** T05 批准（Checker）。 */
    @PostMapping("/commercial-terms/versions/{versionId}/approve")
    public CommercialTermVersionResponseBody approveVersion(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("versionId") @Pattern(regexp = ID) String versionId,
        @RequestBody @NotNull @Valid CommercialTermVersionCommandRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.transitionVersion(operator, versionId, VersionAction.APPROVE, body);
    }

    /** T06 驳回（Checker）。 */
    @PostMapping("/commercial-terms/versions/{versionId}/reject")
    public CommercialTermVersionResponseBody rejectVersion(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("versionId") @Pattern(regexp = ID) String versionId,
        @RequestBody @NotNull @Valid CommercialTermVersionCommandRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.transitionVersion(operator, versionId, VersionAction.REJECT, body);
    }

    /** T07 排期并执行激活校验。 */
    @PostMapping("/commercial-terms/versions/{versionId}/schedule")
    public CommercialTermVersionResponseBody scheduleVersion(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("versionId") @Pattern(regexp = ID) String versionId,
        @RequestBody @NotNull @Valid ScheduleCommercialTermVersionRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.scheduleVersion(operator, versionId, body);
    }

    /** T08 手工激活已到期的排期版本。 */
    @PostMapping("/commercial-terms/versions/{versionId}/activate")
    public CommercialTermVersionResponseBody activateVersion(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("versionId") @Pattern(regexp = ID) String versionId,
        @RequestBody @NotNull @Valid CommercialTermVersionCommandRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.transitionVersion(operator, versionId, VersionAction.ACTIVATE, body);
    }

    /** T09 版本编辑页聚合：版本、合同范围与全部 OE 的价格。 */
    @GetMapping("/commercial-terms/versions/{versionId}")
    public CommercialTermWorkspaceResponseBody version(HttpServletRequest servletRequest,
        @PathVariable("versionId") @Pattern(regexp = ID) String versionId) {
        requireQueryKeys(servletRequest, Set.of());
        return workspaceProcessor.process(new CommercialTermWorkspaceRequest(versionId));
    }

    /** T10 固定版本下某 OE 的价格全集（可按币种筛选）。 */
    @GetMapping("/commercial-terms/versions/{versionId}/prices")
    public CommercialTermVersionPricesResponseBody prices(HttpServletRequest servletRequest,
        @PathVariable("versionId") @Pattern(regexp = ID) String versionId,
        @RequestParam("entityNo") @Pattern(regexp = "[A-Z]{2}[0-9]{3}") String entityNo,
        @RequestParam("termType") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String termType,
        @RequestParam(value = "currency", required = false) @Pattern(regexp = "[A-Z]{3}") String currency) {
        requireQueryKeys(servletRequest, Set.of("entityNo", "termType", "currency"));
        return client.prices(versionId, entityNo, termType, currency);
    }
}
