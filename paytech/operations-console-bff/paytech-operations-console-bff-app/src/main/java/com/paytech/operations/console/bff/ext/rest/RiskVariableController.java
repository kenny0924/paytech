package com.paytech.operations.console.bff.ext.rest;

import com.paytech.operations.console.bff.service.model.RiskVariableModels.*;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import com.paytech.operations.console.bff.integration.RiskVariableClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** 变量配置入口；所有操作由 OperationsRequestInterceptor 实时调用 PLT-I02。 */
@RestController
@RequestMapping("/decision-variables/v1")
@Validated
@AllArgsConstructor
public class RiskVariableController {
    private final RiskVariableClient client;
    /** getAuthoringContext；可信身份由全局上下文解析器提供。 */
    @GetMapping("/authoring-context")
    public VariableManagementContextResponseBody getAuthoringContext(OperatorContext context, @RequestParam(value = "pointCode", required = false) String pointCode) {
        return client.getAuthoringContext(context, pointCode);
    }
    /** listVariables；可信身份由全局上下文解析器提供。 */
    @GetMapping("/variables")
    public VariableListResponseBody listVariables(OperatorContext context, @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "sourceType", required = false) String sourceType,
        @RequestParam(value = "valueType", required = false) String valueType,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "pointCode", required = false) String pointCode,
        @RequestParam(value = "usage", required = false) String usage,
        @RequestParam(value = "nodeType", required = false) String nodeType,
        @RequestParam(value = "codes", required = false) String codes,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return client.listVariables(context, keyword, sourceType, valueType, status, pointCode, usage, nodeType, codes, page, size);
    }
    /** getVariable；可信身份由全局上下文解析器提供。 */
    @GetMapping("/variables/{variableCode}")
    public VariableDetailResponseBody getVariable(OperatorContext context, @PathVariable("variableCode") String variableCode) {
        return client.getVariable(context, variableCode);
    }
    /** createVariable；可信身份由全局上下文解析器提供。 */
    @PostMapping("/variables")
    @ResponseStatus(HttpStatus.CREATED)
    public VariableDetailResponseBody createVariable(OperatorContext context, @Valid @RequestBody CreateVariableRequestBody body) {
        return client.createVariable(context, body);
    }
    /** updateVariable；可信身份由全局上下文解析器提供。 */
    @PutMapping("/variables/{variableCode}")
    public VariableDetailResponseBody updateVariable(OperatorContext context, @PathVariable("variableCode") String variableCode,
        @Valid @RequestBody UpdateVariableRequestBody body) {
        return client.updateVariable(context, variableCode, body);
    }
    /** deleteVariable；可信身份由全局上下文解析器提供。 */
    @DeleteMapping("/variables/{variableCode}")
    public DeleteVariableResponseBody deleteVariable(OperatorContext context, @PathVariable("variableCode") String variableCode,
        @RequestParam(value = "expectedVersion", required = true) String expectedVersion) {
        return client.deleteVariable(context, variableCode, expectedVersion);
    }
    /** listMembers；可信身份由全局上下文解析器提供。 */
    @GetMapping("/variables/{variableCode}/members")
    public VariableMembersResponseBody listMembers(OperatorContext context, @PathVariable("variableCode") String variableCode,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "expectedVersion", required = false) String expectedVersion) {
        return client.listMembers(context, variableCode, keyword, page, size, expectedVersion);
    }
    /** replaceMembers；可信身份由全局上下文解析器提供。 */
    @PutMapping("/variables/{variableCode}/members")
    public ReplaceMembersResponseBody replaceMembers(OperatorContext context, @PathVariable("variableCode") String variableCode,
        @Valid @RequestBody ReplaceMembersRequestBody body) {
        return client.replaceMembers(context, variableCode, body);
    }
    /** getReferences；可信身份由全局上下文解析器提供。 */
    @GetMapping("/variables/{variableCode}/references")
    public VariableReferencesResponseBody getReferences(OperatorContext context, @PathVariable("variableCode") String variableCode,
        @RequestParam(value = "scope", required = false) String scope,
        @RequestParam(value = "cursor", required = false) String cursor,
        @RequestParam(value = "size", required = false) Integer size) {
        return client.getReferences(context, variableCode, scope, cursor, size);
    }
}
