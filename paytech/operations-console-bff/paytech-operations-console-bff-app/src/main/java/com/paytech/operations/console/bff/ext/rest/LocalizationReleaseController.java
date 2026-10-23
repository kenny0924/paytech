package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.LocalizationPaths.APPLICATION;
import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

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
import com.paytech.operations.console.bff.integration.PlatformLocalizationClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.platform.support.api.localization.request.ActivateReleaseRequestBody;
import com.paytech.platform.support.api.localization.request.PublishReleaseRequestBody;
import com.paytech.platform.support.api.localization.response.CurrentReleaseResponseBody;
import com.paytech.platform.support.api.localization.response.ReleaseDetailResponseBody;
import com.paytech.platform.support.api.localization.response.ReleaseDiffResponseBody;
import com.paytech.platform.support.api.localization.response.ReleasePrecheckResponseBody;
import com.paytech.platform.support.api.localization.response.ReleaseSummaryResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;

/**
 * 多语言「发布记录」页与发布弹窗的浏览器入口（发布 6），全部简单转发 Platform 同路径
 * <li>Gateway 绑定：预检、历史、详情与差异 I18N_VIEW；发布与切换当前发布 I18N_PUBLISH，并经 PLT-I02 实时授权。</li>
 * <li>覆盖率不足 422 I18N_COVERAGE_INSUFFICIENT（errors 列出缺口与总数）与文案已变 409 I18N_CONTENT_CHANGED 原样返回。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping(LocalizationPaths.ADMIN)
public class LocalizationReleaseController {
    private static final Set<String> PAGING = Set.of("page", "size");

    private final PlatformLocalizationClient client;

    /** 发布预检，只读。 */
    @GetMapping("/applications/{applicationCode}/release-precheck")
    public ReleasePrecheckResponseBody precheckRelease(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("applicationCode") @Pattern(regexp = APPLICATION) String applicationCode) {
        requireQueryKeys(servletRequest, Set.of());
        return client.precheckRelease(context, applicationCode);
    }

    /** 发布当前文案，返回 PENDING_CHECK 的新发布。 */
    @PostMapping("/applications/{applicationCode}/releases")
    public ReleaseSummaryResponseBody publishRelease(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("applicationCode") @Pattern(regexp = APPLICATION) String applicationCode,
        @RequestBody @NotNull @Valid PublishReleaseRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.publishRelease(context, applicationCode, body);
    }

    /** 发布历史，按发布号倒序。 */
    @GetMapping("/applications/{applicationCode}/releases")
    public PagingResponseBody<ReleaseSummaryResponseBody> listReleases(HttpServletRequest servletRequest,
        OperatorContext context, @PathVariable("applicationCode") @Pattern(regexp = APPLICATION) String applicationCode,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, PAGING);
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listReleases(context, applicationCode, paging.page(), paging.size());
    }

    /** 发布详情。 */
    @GetMapping("/applications/{applicationCode}/releases/{releaseNo}")
    public ReleaseDetailResponseBody getRelease(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("applicationCode") @Pattern(regexp = APPLICATION) String applicationCode,
        @PathVariable("releaseNo") @Positive Integer releaseNo) {
        requireQueryKeys(servletRequest, Set.of());
        return client.getRelease(context, applicationCode, releaseNo);
    }

    /** 两次发布的差异；缺省基准为前一个发布。 */
    @GetMapping("/applications/{applicationCode}/releases/{releaseNo}/diff")
    public ReleaseDiffResponseBody diffRelease(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("applicationCode") @Pattern(regexp = APPLICATION) String applicationCode,
        @PathVariable("releaseNo") @Positive Integer releaseNo,
        @RequestParam(value = "baseReleaseNo", required = false) @Positive Integer baseReleaseNo) {
        requireQueryKeys(servletRequest, Set.of("baseReleaseNo"));
        return client.diffRelease(context, applicationCode, releaseNo, baseReleaseNo);
    }

    /** 切换应用当前发布（上线或回滚），原因写审计。 */
    @PostMapping("/applications/{applicationCode}/current-release")
    public CurrentReleaseResponseBody activateRelease(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("applicationCode") @Pattern(regexp = APPLICATION) String applicationCode,
        @RequestBody @NotNull @Valid ActivateReleaseRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.activateRelease(context, applicationCode, body);
    }
}
