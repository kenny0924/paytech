package com.paytech.operations.console.bff.ext.rest;

import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.RiskExecutionReleaseClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.riskrelease.CreateExecutionReleaseRequestBody;
import com.paytech.operations.console.bff.service.model.riskrelease.ExecutionReleaseItemResponseBody;
import com.paytech.operations.console.bff.service.model.riskrelease.ExecutionReleaseResponseBody;
import com.paytech.operations.console.bff.service.model.riskrelease.StartShadowRunRequestBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 执行发布的浏览器入口：列表、创建、详情、校验、影子运行与激活。
 *
 * <p>准备类操作与激活由 Gateway 绑定不同权限；状态机、影子门槛与激活前置由 Risk 判断，BFF 只转发并核对回执身份。
 * 回滚即用旧发布的决策流版本新建发布，不提供重新激活。校验与激活没有请求 Body。
 */
@RestController
@AllArgsConstructor
@RequestMapping("/decision-releases/v1/executions")
public class RiskExecutionReleaseController {
    private static final String CODE = "[A-Z][A-Z0-9_]{0,63}";
    private static final String ID = "[1-9][0-9]{0,18}";

    private final RiskExecutionReleaseClient client;

    /** 某决策点的执行发布分页，按创建时间倒序；status=ACTIVE 即当前生效发布。 */
    @GetMapping
    public PagingResponseBody<ExecutionReleaseItemResponseBody> executions(HttpServletRequest servletRequest,
        OperatorContext context, @RequestParam("pointCode") @Pattern(regexp = CODE) String pointCode,
        @RequestParam(value = "status", required = false) @Pattern(regexp = CODE) String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        RiskListController.requireQueryKeys(servletRequest, Set.of("pointCode", "status", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listExecutions(context, pointCode, status, paging.page(), paging.size());
    }

    /** 创建执行发布。 */
    @PostMapping
    public ExecutionReleaseResponseBody create(HttpServletRequest servletRequest, OperatorContext context,
        @RequestBody @NotNull @Valid CreateExecutionReleaseRequestBody body) {
        RiskListController.requireQueryKeys(servletRequest, Set.of());
        return client.createExecution(context, body);
    }

    /** 执行发布详情（含静态检查与最近一次影子运行）。 */
    @GetMapping("/{releaseId}")
    public ExecutionReleaseResponseBody detail(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("releaseId") @Pattern(regexp = ID) String releaseId) {
        RiskListController.requireQueryKeys(servletRequest, Set.of());
        return client.getExecution(context, releaseId);
    }

    /** 静态校验。 */
    @PostMapping("/{releaseId}/validate")
    public ExecutionReleaseResponseBody validate(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("releaseId") @Pattern(regexp = ID) String releaseId) {
        RiskListController.requireQueryKeys(servletRequest, Set.of());
        return client.validateExecution(context, releaseId);
    }

    /** 发起影子运行；回放异步执行，页面轮询详情。 */
    @PostMapping("/{releaseId}/shadow-runs")
    public ExecutionReleaseResponseBody shadowRun(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("releaseId") @Pattern(regexp = ID) String releaseId,
        @RequestBody @NotNull @Valid StartShadowRunRequestBody body) {
        RiskListController.requireQueryKeys(servletRequest, Set.of());
        return client.startShadowRun(context, releaseId, body);
    }

    /** 激活执行发布。 */
    @PostMapping("/{releaseId}/activate")
    public ExecutionReleaseResponseBody activate(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("releaseId") @Pattern(regexp = ID) String releaseId) {
        RiskListController.requireQueryKeys(servletRequest, Set.of());
        return client.activateExecution(context, releaseId);
    }
}
