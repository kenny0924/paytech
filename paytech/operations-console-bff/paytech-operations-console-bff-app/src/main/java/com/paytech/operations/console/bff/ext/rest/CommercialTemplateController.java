package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

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

import com.paytech.operations.console.bff.integration.CommercialConsoleQueryClient;
import com.paytech.operations.console.bff.integration.CommercialPricingClient;
import com.paytech.operations.console.bff.integration.CommercialPricingClient.VersionAction;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermPlanResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermTemplateListResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermVersionCommandRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermVersionResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CreateCommercialTermTemplateRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CreateCommercialTermVersionRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.ReplaceCommercialTermPricesRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.ScheduleCommercialTermVersionRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.processor.CommercialTermScopedCommandProcessor;
import com.paytech.operations.console.bff.service.processor.request.CommercialTermScopedCommandRequest;
import com.paytech.operations.console.bff.service.processor.request.CommercialTermScopedCommandRequest.Scope;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 平台标准价目模板的运营入口：模板方案列表与创建、模板版本的建立、价格、提交、复核、排期与激活
 * <li>Gateway 绑定：列表为 COMMERCIAL_PRICING_READ；模板方案与版本维护（建方案、建版本、价格、提交、排期、激活）为
 * COMMERCIAL_TEMPLATE_MANAGE；批准与驳回为独立的 COMMERCIAL_TEMPLATE_APPROVE，模板 Maker 与 Checker 可由不同角色承担。</li>
 * <li>版本写入复用 commercial 同一组版本接口；转发前经 CommercialTermScopedCommandProcessor 校验方案为 PLATFORM_TEMPLATE，
 * 合同方案不能经模板路由写入。模板版本的详情、价格全集、版本列表与对比复用价目的只读路由。</li>
 * <li>模板不需要合同与客户签署，不参与运行时取价；操作人取自会话。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/commercial-admin/v1/commercial-terms")
public class CommercialTemplateController {
    private static final String ID = "[1-9][0-9]{0,18}";

    private final CommercialPricingClient client;
    private final CommercialConsoleQueryClient queryClient;
    private final CommercialTermScopedCommandProcessor scopedProcessor;

    /** M01 模板方案列表：按 OE、条款类型、方案状态筛选，带当前生效与最新版本。 */
    @GetMapping("/templates")
    public CommercialTermTemplateListResponseBody list(HttpServletRequest servletRequest,
        @RequestParam(value = "entityNo", required = false) @Pattern(regexp = "[A-Z]{2}[0-9]{3}") String entityNo,
        @RequestParam(value = "termType", required = false) @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String termType,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "DRAFT|ACTIVE|RETIRED") String status) {
        requireQueryKeys(servletRequest, Set.of("entityNo", "termType", "status"));
        return queryClient.listTemplates(entityNo, termType, status);
    }

    /** M02 创建模板方案（按 OE、命名，可多套）。 */
    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public CommercialTermPlanResponseBody create(HttpServletRequest servletRequest, OperatorContext operator,
        @RequestBody @NotNull @Valid CreateCommercialTermTemplateRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createTemplate(operator, body);
    }

    /** M03 在模板方案下创建 DRAFT 版本（可复制已有模板版本）。 */
    @PostMapping("/templates/{planId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public CommercialTermVersionResponseBody createVersion(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("planId") @Pattern(regexp = ID) String planId,
        @RequestBody @NotNull @Valid CreateCommercialTermVersionRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return scopedProcessor.process(CommercialTermScopedCommandRequest.onPlan(Scope.TEMPLATE, planId,
            () -> client.createVersion(operator, planId, body)));
    }

    /** M04 整体替换 DRAFT 模板版本的价格集合（只做结构校验）。 */
    @PutMapping("/template-versions/{versionId}/prices")
    public CommercialTermVersionResponseBody replacePrices(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("versionId") @Pattern(regexp = ID) String versionId,
        @RequestBody @NotNull @Valid ReplaceCommercialTermPricesRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return templateVersion(versionId, () -> client.replacePrices(operator, versionId, body));
    }

    /** M05 提交复核（Maker）。 */
    @PostMapping("/template-versions/{versionId}/submit")
    public CommercialTermVersionResponseBody submit(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("versionId") @Pattern(regexp = ID) String versionId,
        @RequestBody @NotNull @Valid CommercialTermVersionCommandRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return templateVersion(versionId, () -> client.transitionVersion(operator, versionId, VersionAction.SUBMIT, body));
    }

    /** M06 复核通过（Checker）。 */
    @PostMapping("/template-versions/{versionId}/approve")
    public CommercialTermVersionResponseBody approve(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("versionId") @Pattern(regexp = ID) String versionId,
        @RequestBody @NotNull @Valid CommercialTermVersionCommandRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return templateVersion(versionId, () -> client.transitionVersion(operator, versionId, VersionAction.APPROVE, body));
    }

    /** M07 复核驳回（Checker）。 */
    @PostMapping("/template-versions/{versionId}/reject")
    public CommercialTermVersionResponseBody reject(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("versionId") @Pattern(regexp = ID) String versionId,
        @RequestBody @NotNull @Valid CommercialTermVersionCommandRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return templateVersion(versionId, () -> client.transitionVersion(operator, versionId, VersionAction.REJECT, body));
    }

    /** M08 排期（APPROVED 即可，无签署要求）。 */
    @PostMapping("/template-versions/{versionId}/schedule")
    public CommercialTermVersionResponseBody schedule(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("versionId") @Pattern(regexp = ID) String versionId,
        @RequestBody @NotNull @Valid ScheduleCommercialTermVersionRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return templateVersion(versionId, () -> client.scheduleVersion(operator, versionId, body));
    }

    /** M09 手工激活已到期的排期模板版本。 */
    @PostMapping("/template-versions/{versionId}/activate")
    public CommercialTermVersionResponseBody activate(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("versionId") @Pattern(regexp = ID) String versionId,
        @RequestBody @NotNull @Valid CommercialTermVersionCommandRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return templateVersion(versionId, () -> client.transitionVersion(operator, versionId, VersionAction.ACTIVATE, body));
    }

    /** 版本级模板命令：版本须属于标准价目模板方案。 */
    private CommercialTermVersionResponseBody templateVersion(String versionId,
        Supplier<CommercialTermVersionResponseBody> command) {
        return scopedProcessor.process(CommercialTermScopedCommandRequest.onVersion(Scope.TEMPLATE, versionId, command));
    }
}
