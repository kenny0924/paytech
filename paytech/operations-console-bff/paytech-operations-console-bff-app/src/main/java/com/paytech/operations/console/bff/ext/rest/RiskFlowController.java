package com.paytech.operations.console.bff.ext.rest;

import org.springframework.web.bind.annotation.*;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.RiskFlowClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.RiskFlowModels.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Operations 实时授权之后的页面适配入口；简单转发不增加空编排层。 */
@RestController
@RequestMapping("/decision-flows/v1")
public class RiskFlowController {
    private static final String ID = "[1-9][0-9]{0,18}";
    private static final String CODE = "[A-Z][A-Z0-9_]{0,63}";
    private final RiskFlowClient client;

    public RiskFlowController(RiskFlowClient client) { this.client = client; }

    @GetMapping("/authoring-context")
    public FlowAuthoringContextResponseBody authoringContext(OperatorContext o,
        @RequestParam(required = false) @Pattern(regexp = CODE) String pointCode) {
        return client.authoringContext(o, pointCode);
    }

    @GetMapping("/flows")
    public PagingResponseBody<FlowListItemResponseBody> list(OperatorContext o,
        @RequestParam @Pattern(regexp = CODE) String pointCode,
        @RequestParam(required = false) @Pattern(regexp = CODE) String flowCode,
        @RequestParam(required = false) @Size(min = 1, max = 100) String keyword,
        @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        var paging = PagingRequestBody.of(page, size);
        return client.list(o, pointCode, flowCode, keyword, paging.page(), paging.size());
    }

    @GetMapping("/flows/{flowId}")
    public FlowDetailResponseBody current(OperatorContext o,
        @PathVariable @Pattern(regexp = ID) @DecimalMax("9223372036854775807") String flowId) {
        return client.current(o, flowId);
    }

    @GetMapping("/flows/{flowId}/versions")
    public PagingResponseBody<FlowHistoryItemResponseBody> history(OperatorContext o,
        @PathVariable @Pattern(regexp = ID) @DecimalMax("9223372036854775807") String flowId,
        @RequestParam @Min(1) Integer maxVersionNo, @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size) {
        var paging = PagingRequestBody.of(page, size);
        return client.history(o, flowId, maxVersionNo, paging.page(), paging.size());
    }

    @GetMapping("/flows/{flowId}/versions/{flowVersionId}")
    public FlowDetailResponseBody version(OperatorContext o,
        @PathVariable @Pattern(regexp = ID) @DecimalMax("9223372036854775807") String flowId,
        @PathVariable @Pattern(regexp = ID) @DecimalMax("9223372036854775807") String flowVersionId) {
        return client.version(o, flowId, flowVersionId);
    }

    @GetMapping("/rule-set-reference")
    public RuleSetReferenceResponseBody ruleSetReference(OperatorContext o,
        @RequestParam @Pattern(regexp = "FLOW_VERSION|LEGACY_POINT") String sourceType,
        @RequestParam @Pattern(regexp = ID) @DecimalMax("9223372036854775807") String sourceVersionId,
        @RequestParam @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{0,63}") String nodeKey) {
        return client.ruleSetReference(o, sourceType, sourceVersionId, nodeKey);
    }

    @GetMapping("/legacy-point-versions/{pointVersionId}")
    public LegacyPointResponseBody legacyPoint(OperatorContext o,
        @PathVariable @Pattern(regexp = ID) @DecimalMax("9223372036854775807") String pointVersionId) {
        return client.legacyPoint(o, pointVersionId);
    }

    @PostMapping("/flows")
    public SaveFlowResponseBody save(OperatorContext o,
        @Valid @RequestBody SaveFlowRequestBody body) {
        return client.save(o, body);
    }

    @PostMapping("/flows/validate")
    public ValidationResponseBody validate(OperatorContext o, @Valid @RequestBody SaveFlowRequestBody body) {
        return client.validate(o, body);
    }

    @DeleteMapping("/flows/{flowId}")
    public DeleteFlowResponseBody delete(OperatorContext o,
        @PathVariable @Pattern(regexp = ID) @DecimalMax("9223372036854775807") String flowId,
        @RequestParam @Pattern(regexp = ID) @DecimalMax("9223372036854775807") String expectedVersion) {
        return client.delete(o, flowId, expectedVersion);
    }

}
