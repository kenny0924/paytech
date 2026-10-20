package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.ChannelCostClient;
import com.paytech.operations.console.bff.service.model.ChannelModels.CancelChannelCostVersionRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelCostPlanResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelCostPlanSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelCostVersionComparisonResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelCostVersionResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.CreateChannelCostPlanRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.CreateChannelCostVersionRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ReviewChannelCostVersionRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.processor.ChannelCostVersionComparisonProcessor;
import com.paytech.operations.console.bff.service.processor.request.ChannelCostVersionComparisonRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 渠道成本方案与版本的运营入口（块 92 的 9 条成本路由 + 版本对比）
 * <li>Gateway 绑定：GET 为 CHANNEL_COST_READ；方案与版本创建、提交、作废为 CHANNEL_COST_MANAGE；批准与驳回为独立的
 * CHANNEL_COST_APPROVE，保证 Maker 与 Checker 可由不同角色承担。</li>
 * <li>写入简单转发 channel-connector，makerId / checkerId / operatorId 由 Client 取自会话；版本对比经 Processor 读取两版后在 BFF 比较。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/channel-admin/v1/channel-cost-plans")
public class ChannelCostController {
    private static final String ID = "[1-9][0-9]{0,18}";
    private static final String PLAN_CODE = "[A-Z][A-Z0-9_]{0,63}";
    private static final String COST_VER = "[1-9][0-9]{0,8}";

    private final ChannelCostClient client;
    private final ChannelCostVersionComparisonProcessor comparisonProcessor;

    /** 方案分页：按渠道与方案状态筛选。 */
    @GetMapping
    public PagingResponseBody<ChannelCostPlanSummaryResponseBody> listPlans(HttpServletRequest servletRequest,
        @RequestParam(value = "channelId", required = false) @Pattern(regexp = ID) String channelId,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "DRAFT|ACTIVE|INACTIVE") String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("channelId", "status", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listPlans(channelId, status, paging.page(), paging.size());
    }

    /** 方案详情与版本列表。 */
    @GetMapping("/{planCode}")
    public ChannelCostPlanResponseBody plan(HttpServletRequest servletRequest,
        @PathVariable("planCode") @Pattern(regexp = PLAN_CODE) String planCode) {
        requireQueryKeys(servletRequest, Set.of());
        return client.plan(planCode);
    }

    /** 版本详情，含范围与组成。 */
    @GetMapping("/{planCode}/versions/{costVer}")
    public ChannelCostVersionResponseBody version(HttpServletRequest servletRequest,
        @PathVariable("planCode") @Pattern(regexp = PLAN_CODE) String planCode,
        @PathVariable("costVer") @Pattern(regexp = COST_VER) String costVer) {
        requireQueryKeys(servletRequest, Set.of());
        return client.version(planCode, costVer);
    }

    /** 为渠道创建成本方案。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChannelCostPlanResponseBody createPlan(HttpServletRequest servletRequest,
        @RequestBody @NotNull @Valid CreateChannelCostPlanRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createPlan(body);
    }

    /** 创建 DRAFT 版本（Maker）。 */
    @PostMapping("/{planCode}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public ChannelCostVersionResponseBody createVersion(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("planCode") @Pattern(regexp = PLAN_CODE) String planCode,
        @RequestBody @NotNull @Valid CreateChannelCostVersionRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createVersion(operator, planCode, body);
    }

    /** 提交待复核（Maker，无 Body）。 */
    @PostMapping("/{planCode}/versions/{costVer}/submit")
    public ChannelCostVersionResponseBody submitVersion(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("planCode") @Pattern(regexp = PLAN_CODE) String planCode,
        @PathVariable("costVer") @Pattern(regexp = COST_VER) String costVer) {
        requireQueryKeys(servletRequest, Set.of());
        return client.submitVersion(operator, planCode, costVer);
    }

    /** 批准（Checker）。 */
    @PostMapping("/{planCode}/versions/{costVer}/approve")
    public ChannelCostVersionResponseBody approveVersion(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("planCode") @Pattern(regexp = PLAN_CODE) String planCode,
        @PathVariable("costVer") @Pattern(regexp = COST_VER) String costVer,
        @RequestBody @NotNull @Valid ReviewChannelCostVersionRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.approveVersion(operator, planCode, costVer, body);
    }

    /** 驳回（Checker）。 */
    @PostMapping("/{planCode}/versions/{costVer}/reject")
    public ChannelCostVersionResponseBody rejectVersion(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("planCode") @Pattern(regexp = PLAN_CODE) String planCode,
        @PathVariable("costVer") @Pattern(regexp = COST_VER) String costVer,
        @RequestBody @NotNull @Valid ReviewChannelCostVersionRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.rejectVersion(operator, planCode, costVer, body);
    }

    /** 作废未到生效时间的已批准版本。 */
    @PostMapping("/{planCode}/versions/{costVer}/cancel")
    public ChannelCostVersionResponseBody cancelVersion(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("planCode") @Pattern(regexp = PLAN_CODE) String planCode,
        @PathVariable("costVer") @Pattern(regexp = COST_VER) String costVer,
        @RequestBody @NotNull @Valid CancelChannelCostVersionRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.cancelVersion(operator, planCode, costVer, body);
    }

    /** 同一方案两个版本的范围与组成对比：新增 / 删除 / 修改 / 未变。 */
    @GetMapping("/{planCode}/versions/{baseCostVer}/diff/{targetCostVer}")
    public ChannelCostVersionComparisonResponseBody compareVersions(HttpServletRequest servletRequest,
        @PathVariable("planCode") @Pattern(regexp = PLAN_CODE) String planCode,
        @PathVariable("baseCostVer") @Pattern(regexp = COST_VER) String baseCostVer,
        @PathVariable("targetCostVer") @Pattern(regexp = COST_VER) String targetCostVer) {
        requireQueryKeys(servletRequest, Set.of());
        if (baseCostVer.equals(targetCostVer)) {
            throw new InvalidParameterException("对比的两个版本不能相同");
        }
        return comparisonProcessor.process(new ChannelCostVersionComparisonRequest(planCode, baseCostVer, targetCostVer));
    }
}
