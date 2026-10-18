package com.paytech.operations.console.bff.ext.rest;

import java.time.Instant;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.vaultadmin.CreateVaultAccessRuleRequestBody;
import com.paytech.operations.console.bff.service.model.vaultadmin.CreateVaultDataTypeRequestBody;
import com.paytech.operations.console.bff.service.model.vaultadmin.UpdateVaultAccessRuleRequestBody;
import com.paytech.operations.console.bff.service.model.vaultadmin.UpdateVaultDataTypeRequestBody;
import com.paytech.operations.console.bff.service.model.vaultadmin.VaultManualProtectRequestBody;
import com.paytech.operations.console.bff.service.model.vaultadmin.VaultManualRevealRequestBody;
import com.paytech.vault.client.admin.VaultAdminClient;
import com.paytech.vault.client.admin.VaultAdminClient.AccessRuleFilter;
import com.paytech.vault.client.admin.VaultAdminClient.ManualEventFilter;
import com.paytech.vault.client.admin.response.AccessRule;
import com.paytech.vault.client.admin.response.AccessRuleResponseBody;
import com.paytech.vault.client.admin.response.DataTypeListResponseBody;
import com.paytech.vault.client.admin.response.DataTypeResponseBody;
import com.paytech.vault.client.admin.response.ManualEvent;
import com.paytech.vault.client.admin.response.ManualProtectResponseBody;
import com.paytech.vault.client.admin.response.ManualRevealResponseBody;
import com.paytech.vault.client.admin.response.PolicyChange;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * Vault 数据类型、访问规则、变更记录与人工加/解密的浏览器入口。
 *
 * <p>沿用公共 Origin、PLT-I02 实时授权与可信身份边界：每个路由对应一个权限点，密钥类加/解密走独立路由。
 * 简单转发直接委托 VaultAdminClient（已是类型化的出站端口），不另建同义转发层；操作人取自可信上下文，
 * 页面不能传入操作人或数据级别。响应原样使用 Vault 管理合同的模型，整个前缀只记元数据日志。
 */
@RestController
@Validated
@AllArgsConstructor
@RequestMapping("/vault-admin/v1")
public class VaultAdminController {
    private final VaultAdminClient vaultAdminClient;

    /** 数据类型列表与当前策略版本。 */
    @GetMapping("/data-types")
    public DataTypeListResponseBody dataTypes(OperatorContext context,
        @RequestParam(value = "status", required = false) String status) {
        return vaultAdminClient.listDataTypes(context.accountId(), status);
    }

    /** 新建数据类型。 */
    @PostMapping("/data-types")
    public DataTypeResponseBody createDataType(OperatorContext context,
        @Valid @RequestBody CreateVaultDataTypeRequestBody body) {
        return vaultAdminClient.createDataType(context.accountId(), VaultAdminConverter.toVault(body));
    }

    /** 修改数据类型。 */
    @PutMapping("/data-types/{dataTypeCode}")
    public DataTypeResponseBody updateDataType(OperatorContext context,
        @PathVariable("dataTypeCode") String dataTypeCode, @Valid @RequestBody UpdateVaultDataTypeRequestBody body) {
        return vaultAdminClient.updateDataType(context.accountId(), dataTypeCode, VaultAdminConverter.toVault(body));
    }

    /** 访问规则分页；分页参数按公共分页规则校验。 */
    @GetMapping("/access-rules")
    public PagingResponseBody<AccessRule> accessRules(OperatorContext context,
        @RequestParam(value = "dataTypeCode", required = false) String dataTypeCode,
        @RequestParam(value = "callerService", required = false) String callerService,
        @RequestParam(value = "action", required = false) String action,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return vaultAdminClient.listAccessRules(context.accountId(),
            new AccessRuleFilter(dataTypeCode, callerService, action, status), paging.page(), paging.size());
    }

    /** 新建访问规则。 */
    @PostMapping("/access-rules")
    public AccessRuleResponseBody createAccessRule(OperatorContext context,
        @Valid @RequestBody CreateVaultAccessRuleRequestBody body) {
        return vaultAdminClient.createAccessRule(context.accountId(), VaultAdminConverter.toVault(body));
    }

    /** 启用或停用访问规则。 */
    @PutMapping("/access-rules/{accessRuleId}")
    public AccessRuleResponseBody updateAccessRule(OperatorContext context,
        @PathVariable("accessRuleId") String accessRuleId, @Valid @RequestBody UpdateVaultAccessRuleRequestBody body) {
        return vaultAdminClient.updateAccessRule(context.accountId(), accessRuleId, VaultAdminConverter.toVault(body));
    }

    /** 策略变更记录分页（不含快照）。 */
    @GetMapping("/policy-changes")
    public PagingResponseBody<PolicyChange> policyChanges(OperatorContext context,
        @RequestParam(value = "targetType", required = false) String targetType,
        @RequestParam(value = "targetKey", required = false) String targetKey,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return vaultAdminClient.listPolicyChanges(context.accountId(), targetType, targetKey, paging.page(),
            paging.size());
    }

    /** 策略变更详情（含变更前后快照）。 */
    @GetMapping("/policy-changes/{policyVersionNo}")
    public PolicyChange policyChange(OperatorContext context, @PathVariable("policyVersionNo") int policyVersionNo) {
        return vaultAdminClient.getPolicyChange(context.accountId(), policyVersionNo);
    }

    /** 人工加密普通敏感数据。 */
    @PostMapping("/manual/protect")
    public ManualProtectResponseBody manualProtect(OperatorContext context,
        @Valid @RequestBody VaultManualProtectRequestBody body) {
        return vaultAdminClient.manualProtect(context.accountId(), body.getSubmissionId(),
            VaultAdminConverter.toVault(body, VaultAdminConverter.STANDARD_ONLY));
    }

    /** 人工加密（含密钥类），需单独的密钥类权限点。 */
    @PostMapping("/manual/secret-protect")
    public ManualProtectResponseBody manualSecretProtect(OperatorContext context,
        @Valid @RequestBody VaultManualProtectRequestBody body) {
        return vaultAdminClient.manualProtect(context.accountId(), body.getSubmissionId(),
            VaultAdminConverter.toVault(body, VaultAdminConverter.STANDARD_AND_SECRET));
    }

    /** 人工解密普通敏感数据。 */
    @PostMapping("/manual/reveal")
    public ManualRevealResponseBody manualReveal(OperatorContext context,
        @Valid @RequestBody VaultManualRevealRequestBody body) {
        return vaultAdminClient.manualReveal(context.accountId(),
            VaultAdminConverter.toVault(body, VaultAdminConverter.STANDARD_ONLY));
    }

    /** 人工解密（含密钥类），需单独的密钥类权限点。 */
    @PostMapping("/manual/secret-reveal")
    public ManualRevealResponseBody manualSecretReveal(OperatorContext context,
        @Valid @RequestBody VaultManualRevealRequestBody body) {
        return vaultAdminClient.manualReveal(context.accountId(),
            VaultAdminConverter.toVault(body, VaultAdminConverter.STANDARD_AND_SECRET));
    }

    /** 人工加/解密审计分页；时间为 RFC 3339。 */
    @GetMapping("/manual-events")
    public PagingResponseBody<ManualEvent> manualEvents(OperatorContext context,
        @RequestParam(value = "actorId", required = false) String actorId,
        @RequestParam(value = "action", required = false) String action,
        @RequestParam(value = "dataTypeCode", required = false) String dataTypeCode,
        @RequestParam(value = "from", required = false) Instant from,
        @RequestParam(value = "to", required = false) Instant to,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return vaultAdminClient.listManualEvents(context.accountId(),
            new ManualEventFilter(actorId, action, dataTypeCode, from, to), paging.page(), paging.size());
    }
}
