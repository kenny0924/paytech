package com.paytech.vault.client.internal;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.vault.client.VaultClientErrorCode;
import com.paytech.vault.client.admin.VaultAdminClient;
import com.paytech.vault.client.admin.request.CreateAccessRuleRequestBody;
import com.paytech.vault.client.admin.request.CreateDataTypeRequestBody;
import com.paytech.vault.client.admin.request.ManualProtectRequestBody;
import com.paytech.vault.client.admin.request.ManualRevealRequestBody;
import com.paytech.vault.client.admin.request.UpdateAccessRuleRequestBody;
import com.paytech.vault.client.admin.request.UpdateDataTypeRequestBody;
import com.paytech.vault.client.admin.response.AccessRule;
import com.paytech.vault.client.admin.response.AccessRuleResponseBody;
import com.paytech.vault.client.admin.response.DataTypeListResponseBody;
import com.paytech.vault.client.admin.response.DataTypeResponseBody;
import com.paytech.vault.client.admin.response.ManualEvent;
import com.paytech.vault.client.admin.response.ManualProtectResponseBody;
import com.paytech.vault.client.admin.response.ManualRevealResponseBody;
import com.paytech.vault.client.admin.response.PolicyChange;

import lombok.AllArgsConstructor;

/** 校验操作人与提交 ID 后委托 HTTP 契约；请求体字段由 Vault 严格校验，客户端不重复。 */
@AllArgsConstructor
public final class DefaultVaultAdminClient implements VaultAdminClient {
    private static final Pattern OPERATOR = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private final VaultAdminHttpService service;

    /** 查询数据类型列表。 */
    @Override
    public DataTypeListResponseBody listDataTypes(String operatorId, String status) {
        return service.listDataTypes(operator(operatorId), requestId(), status);
    }

    /** 新建数据类型。 */
    @Override
    public DataTypeResponseBody createDataType(String operatorId, CreateDataTypeRequestBody body) {
        VaultValidation.required(body);
        return service.createDataType(operator(operatorId), requestId(), body);
    }

    /** 修改数据类型；编码按大写 Code 校验，避免拼出其他路径。 */
    @Override
    public DataTypeResponseBody updateDataType(String operatorId, String dataTypeCode, UpdateDataTypeRequestBody body) {
        VaultValidation.code(dataTypeCode);
        VaultValidation.required(body);
        return service.updateDataType(operator(operatorId), requestId(), dataTypeCode, body);
    }

    /** 访问规则分页。 */
    @Override
    public PagingResponseBody<AccessRule> listAccessRules(String operatorId, AccessRuleFilter filter, Integer page,
        Integer size) {
        VaultValidation.required(filter);
        return service.listAccessRules(operator(operatorId), requestId(), filter.dataTypeCode(),
            filter.callerService(), filter.action(), filter.status(), page, size);
    }

    /** 新建访问规则。 */
    @Override
    public AccessRuleResponseBody createAccessRule(String operatorId, CreateAccessRuleRequestBody body) {
        VaultValidation.required(body);
        return service.createAccessRule(operator(operatorId), requestId(), body);
    }

    /** 启停访问规则；规则 ID 必须是十进制 64 位整数字符串。 */
    @Override
    public AccessRuleResponseBody updateAccessRule(String operatorId, String accessRuleId,
        UpdateAccessRuleRequestBody body) {
        if (accessRuleId == null || !accessRuleId.matches("[1-9][0-9]{0,18}")) {
            throw new FailException(VaultClientErrorCode.INVALID_REQUEST);
        }
        VaultValidation.required(body);
        return service.updateAccessRule(operator(operatorId), requestId(), accessRuleId, body);
    }

    /** 策略变更分页。 */
    @Override
    public PagingResponseBody<PolicyChange> listPolicyChanges(String operatorId, String targetType, String targetKey,
        Integer page, Integer size) {
        return service.listPolicyChanges(operator(operatorId), requestId(), targetType, targetKey, page, size);
    }

    /** 策略变更详情。 */
    @Override
    public PolicyChange getPolicyChange(String operatorId, int policyVersionNo) {
        if (policyVersionNo < 1) {
            throw new FailException(VaultClientErrorCode.INVALID_REQUEST);
        }
        return service.getPolicyChange(operator(operatorId), requestId(), policyVersionNo);
    }

    /** 人工操作审计分页；时间以 RFC 3339 传输。 */
    @Override
    public PagingResponseBody<ManualEvent> listManualEvents(String operatorId, ManualEventFilter filter, Integer page,
        Integer size) {
        VaultValidation.required(filter);
        return service.listManualEvents(operator(operatorId), requestId(), filter.actorId(), filter.action(),
            filter.dataTypeCode(), text(filter.from()), text(filter.to()), page, size);
    }

    /** 人工加密；提交 ID 作为 Vault 幂等键。 */
    @Override
    public ManualProtectResponseBody manualProtect(String operatorId, String submissionId,
        ManualProtectRequestBody body) {
        VaultValidation.safeHeader(submissionId, 128);
        VaultValidation.required(body);
        return service.manualProtect(operator(operatorId), submissionId, requestId(), body);
    }

    /** 人工解密。 */
    @Override
    public ManualRevealResponseBody manualReveal(String operatorId, ManualRevealRequestBody body) {
        VaultValidation.required(body);
        return service.manualReveal(operator(operatorId), requestId(), body);
    }

    /** 校验操作人账号 ID 格式（与 Vault X-Operator-Id 规则一致）。 */
    private static String operator(String operatorId) {
        if (operatorId == null || !OPERATOR.matcher(operatorId).matches()) {
            throw new FailException(VaultClientErrorCode.INVALID_REQUEST);
        }
        return operatorId;
    }

    /** 可空时间转 RFC 3339 文本。 */
    private static String text(Instant value) {
        return value == null ? null : value.toString();
    }

    /** 沿用当前请求 ID，缺失或非法时生成新值。 */
    private static String requestId() {
        String current = MDC.get("requestId");
        return ApiProblemProfile.isValidRequestId(current) ? current : UUID.randomUUID().toString();
    }
}
