package com.paytech.vault.client.internal;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
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

/** Vault 管理接口的 HTTP 契约（与 api/openapi/admin-v1.yaml 一致）；宿主只依赖 VaultAdminClient。 */
@HttpExchange(url = "/vault/v1/admin", accept = MediaType.APPLICATION_JSON_VALUE)
public interface VaultAdminHttpService {
    /** 数据类型列表。 */
    @GetExchange("/data-types")
    DataTypeListResponseBody listDataTypes(@RequestHeader("X-Operator-Id") String operatorId,
            @RequestHeader("X-Request-Id") String requestId, @RequestParam(value = "status", required = false) String status);

    /** 新建数据类型。 */
    @PostExchange(url = "/data-types", contentType = MediaType.APPLICATION_JSON_VALUE)
    DataTypeResponseBody createDataType(@RequestHeader("X-Operator-Id") String operatorId,
            @RequestHeader("X-Request-Id") String requestId, @RequestBody CreateDataTypeRequestBody body);

    /** 修改数据类型。 */
    @PutExchange(url = "/data-types/{dataTypeCode}", contentType = MediaType.APPLICATION_JSON_VALUE)
    DataTypeResponseBody updateDataType(@RequestHeader("X-Operator-Id") String operatorId,
            @RequestHeader("X-Request-Id") String requestId, @PathVariable("dataTypeCode") String dataTypeCode,
            @RequestBody UpdateDataTypeRequestBody body);

    /** 访问规则分页。 */
    @GetExchange("/access-rules")
    PagingResponseBody<AccessRule> listAccessRules(@RequestHeader("X-Operator-Id") String operatorId,
            @RequestHeader("X-Request-Id") String requestId,
            @RequestParam(value = "dataTypeCode", required = false) String dataTypeCode,
            @RequestParam(value = "callerService", required = false) String callerService,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size);

    /** 新建访问规则。 */
    @PostExchange(url = "/access-rules", contentType = MediaType.APPLICATION_JSON_VALUE)
    AccessRuleResponseBody createAccessRule(@RequestHeader("X-Operator-Id") String operatorId,
            @RequestHeader("X-Request-Id") String requestId, @RequestBody CreateAccessRuleRequestBody body);

    /** 启停访问规则。 */
    @PutExchange(url = "/access-rules/{accessRuleId}", contentType = MediaType.APPLICATION_JSON_VALUE)
    AccessRuleResponseBody updateAccessRule(@RequestHeader("X-Operator-Id") String operatorId,
            @RequestHeader("X-Request-Id") String requestId, @PathVariable("accessRuleId") String accessRuleId,
            @RequestBody UpdateAccessRuleRequestBody body);

    /** 策略变更分页。 */
    @GetExchange("/policy-changes")
    PagingResponseBody<PolicyChange> listPolicyChanges(@RequestHeader("X-Operator-Id") String operatorId,
            @RequestHeader("X-Request-Id") String requestId,
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "targetKey", required = false) String targetKey,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size);

    /** 策略变更详情。 */
    @GetExchange("/policy-changes/{policyVersionNo}")
    PolicyChange getPolicyChange(@RequestHeader("X-Operator-Id") String operatorId,
            @RequestHeader("X-Request-Id") String requestId, @PathVariable("policyVersionNo") int policyVersionNo);

    /** 人工操作审计分页。 */
    @GetExchange("/manual-events")
    PagingResponseBody<ManualEvent> listManualEvents(@RequestHeader("X-Operator-Id") String operatorId,
            @RequestHeader("X-Request-Id") String requestId,
            @RequestParam(value = "actorId", required = false) String actorId,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "dataTypeCode", required = false) String dataTypeCode,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size);

    /** 人工加密。 */
    @PostExchange(url = "/manual/protect", contentType = MediaType.APPLICATION_JSON_VALUE)
    ManualProtectResponseBody manualProtect(@RequestHeader("X-Operator-Id") String operatorId,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @RequestHeader("X-Request-Id") String requestId,
            @RequestBody ManualProtectRequestBody body);

    /** 人工解密。 */
    @PostExchange(url = "/manual/reveal", contentType = MediaType.APPLICATION_JSON_VALUE)
    ManualRevealResponseBody manualReveal(@RequestHeader("X-Operator-Id") String operatorId,
            @RequestHeader("X-Request-Id") String requestId, @RequestBody ManualRevealRequestBody body);
}
