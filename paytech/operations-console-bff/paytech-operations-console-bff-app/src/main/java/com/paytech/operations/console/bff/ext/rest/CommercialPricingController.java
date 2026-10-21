package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

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

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.CommercialConsoleQueryClient;
import com.paytech.operations.console.bff.integration.CommercialPricingClient;
import com.paytech.operations.console.bff.integration.CommercialPricingClient.VersionAction;
import com.paytech.operations.console.bff.service.model.CommercialChangeModels.CommercialTermVersionDiffResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialListModels.TermPlanListItem;
import com.paytech.operations.console.bff.service.model.CommercialListModels.TermPlanListRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialListModels.TermVersionListItem;
import com.paytech.operations.console.bff.service.model.CommercialListModels.TermVersionListRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermPlanResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermVersionCommandRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermVersionPricesResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermVersionResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermWorkspaceResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CreateCommercialTermPlanRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CreateCommercialTermVersionRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CreatePricingProfileRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.FillCommercialTermVersionFromTemplateRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.PricingProfileListResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.PricingProfileResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.PricingProfileTransitionRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.RecordTermVersionAcceptanceRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.ReplaceCommercialTermPricesRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.ScheduleCommercialTermVersionRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.UpdatePricingProfileRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.processor.CommercialTermScopedCommandProcessor;
import com.paytech.operations.console.bff.service.processor.CommercialTermVersionDiffProcessor;
import com.paytech.operations.console.bff.service.processor.CommercialTermWorkspaceProcessor;
import com.paytech.operations.console.bff.service.processor.request.CommercialTermScopedCommandRequest;
import com.paytech.operations.console.bff.service.processor.request.CommercialTermScopedCommandRequest.Scope;
import com.paytech.operations.console.bff.service.processor.request.CommercialTermVersionDiffRequest;
import com.paytech.operations.console.bff.service.processor.request.CommercialTermWorkspaceRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 定价画像与商业条款的运营入口
 * <li>Gateway 绑定：GET 为 COMMERCIAL_PRICING_READ；画像写入与条款创建、价格、提交、排期、激活、从模板补齐、登记客户签署为
 * COMMERCIAL_PRICING_MANAGE；批准与驳回为独立的 COMMERCIAL_PRICING_APPROVE，保证 Maker 与 Checker 可由不同角色承担。</li>
 * <li>版本编辑页经 Processor 聚合版本、合同范围（或模板方案）与全部 OE 的价格；写入转发 commercial，操作人取自会话。</li>
 * <li>建版本、价格与状态推进经 CommercialTermScopedCommandProcessor 校验方案为合同方案：标准价目模板只能经
 * CommercialTemplateController 的模板路由（COMMERCIAL_TEMPLATE_*）写入。从模板补齐与签署登记由 commercial 拒绝模板版本。</li>
 * <li>方案 / 版本列表（第二切片 I2）简单转发 commercial CommercialTermQueryApi，版本 status 可重复；版本对比经 Processor 读取两版价格后在
 * BFF 比较。三者均为 COMMERCIAL_PRICING_READ。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/commercial-admin/v1")
public class CommercialPricingController {
    private static final String ID = "[1-9][0-9]{0,18}";
    private static final String CODE = "[A-Z][A-Z0-9_]{0,63}";
    private static final String OFFSET_INSTANT = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?(Z|[+-]\\d{2}:\\d{2})";
    private static final String VERSION_STATUS = "DRAFT|PENDING_APPROVAL|APPROVED|CLIENT_SIGNED|SCHEDULED|ACTIVE|SUPERSEDED|EXPIRED";
    private static final Set<String> PLAN_KEYS = Set.of("agreementId", "clientId", "localPartnerId", "termType", "status",
        "page", "size");
    private static final Set<String> VERSION_KEYS = Set.of("planId", "agreementId", "status", "effectiveFrom", "effectiveTo",
        "page", "size");

    private final CommercialPricingClient client;
    private final CommercialConsoleQueryClient queryClient;
    private final CommercialTermWorkspaceProcessor workspaceProcessor;
    private final CommercialTermVersionDiffProcessor diffProcessor;
    private final CommercialTermScopedCommandProcessor scopedProcessor;

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
        return scopedProcessor.process(CommercialTermScopedCommandRequest.onPlan(Scope.CONTRACT, planId,
            () -> client.createVersion(operator, planId, body)));
    }

    /** T03 整体替换 DRAFT 版本的价格集合。 */
    @PutMapping("/commercial-terms/versions/{versionId}/prices")
    public CommercialTermVersionResponseBody replacePrices(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("versionId") @Pattern(regexp = ID) String versionId,
        @RequestBody @NotNull @Valid ReplaceCommercialTermPricesRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return contractVersion(versionId, () -> client.replacePrices(operator, versionId, body));
    }

    /** T04 提交审批（Maker）。 */
    @PostMapping("/commercial-terms/versions/{versionId}/submit")
    public CommercialTermVersionResponseBody submitVersion(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("versionId") @Pattern(regexp = ID) String versionId,
        @RequestBody @NotNull @Valid CommercialTermVersionCommandRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return contractVersion(versionId, () -> client.transitionVersion(operator, versionId, VersionAction.SUBMIT, body));
    }

    /** T05 批准（Checker）。 */
    @PostMapping("/commercial-terms/versions/{versionId}/approve")
    public CommercialTermVersionResponseBody approveVersion(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("versionId") @Pattern(regexp = ID) String versionId,
        @RequestBody @NotNull @Valid CommercialTermVersionCommandRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return contractVersion(versionId, () -> client.transitionVersion(operator, versionId, VersionAction.APPROVE, body));
    }

    /** T06 驳回（Checker）。 */
    @PostMapping("/commercial-terms/versions/{versionId}/reject")
    public CommercialTermVersionResponseBody rejectVersion(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("versionId") @Pattern(regexp = ID) String versionId,
        @RequestBody @NotNull @Valid CommercialTermVersionCommandRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return contractVersion(versionId, () -> client.transitionVersion(operator, versionId, VersionAction.REJECT, body));
    }

    /** T07 排期并执行激活校验。 */
    @PostMapping("/commercial-terms/versions/{versionId}/schedule")
    public CommercialTermVersionResponseBody scheduleVersion(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("versionId") @Pattern(regexp = ID) String versionId,
        @RequestBody @NotNull @Valid ScheduleCommercialTermVersionRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return contractVersion(versionId, () -> client.scheduleVersion(operator, versionId, body));
    }

    /** T14 从标准价目模板版本补齐 DRAFT 合同版本（只追加缺失价格行）。 */
    @PostMapping("/commercial-terms/versions/{versionId}/fill-from-template")
    public CommercialTermVersionResponseBody fillFromTemplate(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("versionId") @Pattern(regexp = ID) String versionId,
        @RequestBody @NotNull @Valid FillCommercialTermVersionFromTemplateRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.fillFromTemplate(operator, versionId, body);
    }

    /** T15 登记客户对 APPROVED 合同版本的签署（价格附件 + 签署记录）。 */
    @PostMapping("/commercial-terms/versions/{versionId}/acceptance")
    public CommercialTermVersionResponseBody recordAcceptance(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("versionId") @Pattern(regexp = ID) String versionId,
        @RequestBody @NotNull @Valid RecordTermVersionAcceptanceRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.recordAcceptance(operator, versionId, body);
    }

    /** T08 手工激活已到期的排期版本。 */
    @PostMapping("/commercial-terms/versions/{versionId}/activate")
    public CommercialTermVersionResponseBody activateVersion(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("versionId") @Pattern(regexp = ID) String versionId,
        @RequestBody @NotNull @Valid CommercialTermVersionCommandRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return contractVersion(versionId, () -> client.transitionVersion(operator, versionId, VersionAction.ACTIVATE, body));
    }

    /** 版本级合同命令：版本须属于合同方案。 */
    private CommercialTermVersionResponseBody contractVersion(String versionId,
        Supplier<CommercialTermVersionResponseBody> command) {
        return scopedProcessor.process(CommercialTermScopedCommandRequest.onVersion(Scope.CONTRACT, versionId, command));
    }

    /** T11 方案列表：按合同、Client、Partner、条款类型与方案状态筛选分页，带当前 / 已排期版本。 */
    @GetMapping("/commercial-terms/plans")
    public PagingResponseBody<TermPlanListItem> listPlans(HttpServletRequest servletRequest,
        @RequestParam(value = "agreementId", required = false) @Pattern(regexp = ID) String agreementId,
        @RequestParam(value = "clientId", required = false) @Pattern(regexp = ID) String clientId,
        @RequestParam(value = "localPartnerId", required = false) @Pattern(regexp = ID) String localPartnerId,
        @RequestParam(value = "termType", required = false) @Pattern(regexp = CODE) String termType,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "DRAFT|ACTIVE|RETIRED") String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        CommercialQueryParameters.require(servletRequest, PLAN_KEYS, Set.of());
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return queryClient.listPlans(new TermPlanListRequestBody(agreementId, clientId, localPartnerId, termType, status,
            paging.page(), paging.size()));
    }

    /** T12 版本列表：按方案、合同、状态（可多选）与生效窗口筛选分页。 */
    @GetMapping("/commercial-terms/versions")
    public PagingResponseBody<TermVersionListItem> listVersions(HttpServletRequest servletRequest,
        @RequestParam(value = "planId", required = false) @Pattern(regexp = ID) String planId,
        @RequestParam(value = "agreementId", required = false) @Pattern(regexp = ID) String agreementId,
        @RequestParam(value = "status", required = false) List<@Pattern(regexp = VERSION_STATUS) String> status,
        @RequestParam(value = "effectiveFrom", required = false) @Pattern(regexp = OFFSET_INSTANT) String effectiveFrom,
        @RequestParam(value = "effectiveTo", required = false) @Pattern(regexp = OFFSET_INSTANT) String effectiveTo,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        CommercialQueryParameters.require(servletRequest, VERSION_KEYS, Set.of("status"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return queryClient.listVersions(new TermVersionListRequestBody(planId, agreementId,
            status == null ? List.of() : List.copyOf(status), effectiveFrom, effectiveTo, paging.page(), paging.size()));
    }

    /** T13 同一方案两个版本的价格对比：新增 / 删除 / 修改（字段级）/ 未变。 */
    @GetMapping("/commercial-terms/versions/{baseVersionId}/diff/{targetVersionId}")
    public CommercialTermVersionDiffResponseBody diff(HttpServletRequest servletRequest,
        @PathVariable("baseVersionId") @Pattern(regexp = ID) String baseVersionId,
        @PathVariable("targetVersionId") @Pattern(regexp = ID) String targetVersionId) {
        requireQueryKeys(servletRequest, Set.of());
        if (baseVersionId.equals(targetVersionId)) {
            throw new InvalidParameterException("对比的两个版本不能相同");
        }
        return diffProcessor.process(new CommercialTermVersionDiffRequest(baseVersionId, targetVersionId));
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
