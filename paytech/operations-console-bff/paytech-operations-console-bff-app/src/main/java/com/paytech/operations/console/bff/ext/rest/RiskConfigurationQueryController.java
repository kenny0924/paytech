package com.paytech.operations.console.bff.ext.rest;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import com.paytech.foundation.web.paging.*;
import com.paytech.operations.console.bff.integration.RiskConfigurationQueryClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.RiskConfigurationQueryModels.*;
/** 整体规则集的历史与发布关系，版本详情仍走原接口。 */
@RestController
@AllArgsConstructor
@RequestMapping("/decision-rule-sets/v1/rule-sets")
public class RiskConfigurationQueryController {
    private static final String ID = "[1-9][0-9]{0,18}";
    private final RiskConfigurationQueryClient client;
    /** 完成当前路由授权后查询固定上界历史，返回 BFF 本地模型。 */
    @GetMapping("/{ruleSetId}/versions")
    public PagingResponseBody<RuleSetHistoryItem> history(OperatorContext o, @PathVariable @Pattern(regexp = ID) @DecimalMax("9223372036854775807") String ruleSetId,
        @RequestParam @Min(1) Integer maxVersionNo, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        var p = PagingRequestBody.of(page, size); return client.history(o, ruleSetId, maxVersionNo, p.page(), p.size());
    }
    /** 完成当前路由授权后转发整体规则集发布引用筛选。 */
    @GetMapping("/{ruleSetId}/release-references")
    public RuleSetReleaseReferencesResponseBody releases(OperatorContext o, @PathVariable @Pattern(regexp = ID) @DecimalMax("9223372036854775807") String ruleSetId,
        @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
        @RequestParam(required = false) @Pattern(regexp = "TEST|PRODUCTION") String environmentCode,
        @RequestParam(required = false) @Pattern(regexp = "DRAFT|VALIDATING|SHADOWING|REVIEW_PENDING|APPROVED|ACTIVE|SUPERSEDED|REJECTED") String status,
        @RequestParam(required = false) @Pattern(regexp = ID) @DecimalMax("9223372036854775807") String ruleSetVersionId) {
        var p = PagingRequestBody.of(page, size); return client.releases(o, ruleSetId, p.page(), p.size(), environmentCode, status, ruleSetVersionId);
    }
}
