package com.paytech.operations.console.bff.ext.rest;

import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import jakarta.validation.constraints.DecimalMax;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.RiskDecisionConfigurationClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.*;
import com.paytech.operations.console.bff.service.model.RiskTestCandidateModels.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;

/** Risk Rule Set 与 TEST Candidate 的浏览器协议入口；每个方法只委托一个 Owner 调用。 */
@RestController
@AllArgsConstructor
public class RiskDecisionConfigurationController {
    private static final String RULE_SET_ROOT = "/decision-rule-sets/v1";
    private static final String RELEASE_ROOT = "/decision-releases/v1";
    private static final String CODE = "^[A-Z][A-Z0-9_]{0,63}$";
    private static final String POSITIVE_DECIMAL_ID = "^[1-9][0-9]{0,18}$";
    private static final Set<String> RULE_SET_LIST_QUERY =
        Set.of("page", "size", "pointCode", "ruleSetCode", "status");
    private static final Set<String> CANDIDATE_LIST_QUERY = Set.of("page", "size", "pointCode", "status");

    private final RiskDecisionConfigurationClient client;

    @GetMapping(RULE_SET_ROOT + "/authoring-context/{pointCode}")
    public RuleAuthoringContextResponseBody authoringContext(HttpServletRequest servletRequest,
        OperatorContext operator, @PathVariable @Pattern(regexp = CODE) String pointCode) {
        requireQueryKeys(servletRequest, Set.of());
        return client.authoringContext(operator, pointCode);
    }

    @GetMapping(RULE_SET_ROOT + "/rule-sets")
    public PagingResponseBody<RuleSetListItemResponseBody> ruleSets(HttpServletRequest servletRequest,
        OperatorContext operator, @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) @Pattern(regexp = CODE) String pointCode,
        @RequestParam(required = false) @Pattern(regexp = CODE) String ruleSetCode,
        @RequestParam(required = false) @Pattern(regexp = "^(DRAFT|FROZEN|RETIRED)$") String status) {
        requireQueryKeys(servletRequest, RULE_SET_LIST_QUERY);
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.ruleSets(operator, paging.page(), paging.size(),
            new RuleSetQueryRequestBody(pointCode, ruleSetCode, status));
    }

    @GetMapping(RULE_SET_ROOT + "/rule-sets/{ruleSetVersionId}")
    public RuleSetDetailResponseBody ruleSet(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable @Pattern(regexp = POSITIVE_DECIMAL_ID) String ruleSetVersionId) {
        requireQueryKeys(servletRequest, Set.of());
        return client.ruleSet(operator, ruleSetVersionId);
    }

    @PostMapping(RULE_SET_ROOT + "/rule-sets")
    public CreateRuleSetVersionResponseBody createRuleSetVersion(HttpServletRequest servletRequest,
        OperatorContext operator,
        @RequestBody @NotNull @Valid CreateRuleSetVersionRequestBody request) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createRuleSetVersion(operator, request);
    }

    @PostMapping(RULE_SET_ROOT + "/rule-sets/{ruleSetVersionId}/freeze")
    public FreezeRuleSetVersionResponseBody freezeRuleSetVersion(HttpServletRequest servletRequest,
        OperatorContext operator, @PathVariable @Pattern(regexp = POSITIVE_DECIMAL_ID) String ruleSetVersionId,
        @RequestBody @NotNull @Valid FreezeRuleSetVersionRequestBody request) {
        requireQueryKeys(servletRequest, Set.of());
        return client.freezeRuleSetVersion(operator, ruleSetVersionId, request);
    }

    /** 整体删除只委托一次 Owner 命令，当前权限已由入口拦截器检查。 */
    @DeleteMapping(RULE_SET_ROOT + "/rule-sets/{ruleSetId}")
    public DeleteRuleSetResponseBody deleteRuleSet(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable @Pattern(regexp = POSITIVE_DECIMAL_ID) @DecimalMax("9223372036854775807") String ruleSetId,
        @RequestBody @NotNull @Valid DeleteRuleSetRequestBody request) {
        requireQueryKeys(servletRequest, Set.of());
        return client.deleteRuleSet(operator, ruleSetId, request);
    }

    @GetMapping(RELEASE_ROOT + "/candidates")
    public PagingResponseBody<TestCandidateListItemResponseBody> candidates(HttpServletRequest servletRequest,
        OperatorContext operator, @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) @Pattern(regexp = CODE) String pointCode,
        @RequestParam(required = false) @Pattern(regexp = "^(DRAFT|VALIDATING|REJECTED)$") String status) {
        requireQueryKeys(servletRequest, CANDIDATE_LIST_QUERY);
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.candidates(operator, paging.page(), paging.size(), new CandidateQueryRequestBody(pointCode, status));
    }

    @GetMapping(RELEASE_ROOT + "/candidates/{releaseId}")
    public TestCandidateDetailResponseBody candidate(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable @Pattern(regexp = POSITIVE_DECIMAL_ID) String releaseId) {
        requireQueryKeys(servletRequest, Set.of());
        return client.candidate(operator, releaseId);
    }

    @PostMapping(RELEASE_ROOT + "/candidates")
    public CreateTestCandidateResponseBody createCandidate(HttpServletRequest servletRequest, OperatorContext operator,
        @RequestBody @NotNull @Valid CreateTestCandidateRequestBody request) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createCandidate(operator, request);
    }

    @PostMapping(RELEASE_ROOT + "/candidates/{releaseId}/validate")
    public ValidateTestCandidateResponseBody validateCandidate(HttpServletRequest servletRequest,
        OperatorContext operator, @PathVariable @Pattern(regexp = POSITIVE_DECIMAL_ID) String releaseId,
        @RequestBody @NotNull @Valid ValidateTestCandidateRequestBody request) {
        requireQueryKeys(servletRequest, Set.of());
        return client.validateCandidate(operator, releaseId, request);
    }

    private void requireQueryKeys(HttpServletRequest request, Set<String> allowedKeys) {
        if (!allowedKeys.containsAll(request.getParameterMap().keySet())) {
            throw new InvalidParameterException("请求包含不支持的 Query 参数");
        }
    }
}
