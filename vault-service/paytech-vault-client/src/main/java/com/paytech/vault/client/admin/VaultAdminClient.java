package com.paytech.vault.client.admin;

import java.time.Instant;

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

/**
 * Vault 管理接口客户端：策略维护、审计查询与运营人工加/解密。
 *
 * <p>只供 Operations BFF 使用（Vault 以 VAULT_ADMIN_CALLERS 限定调用方），由 {@code vault.client.admin-enabled=true}
 * 显式开启；业务服务继续只使用 {@link com.paytech.vault.client.VaultClient}。每个方法都携带当前运营账号 ID，
 * Vault 校验格式并写入审计。客户端不重试；Problem 错误按稳定错误码转换为 FailException / ErrorException /
 * ServiceUnavailableException，绝不透传远端自由文本。
 */
public interface VaultAdminClient {

    /**
     * 查询全部数据类型与当前策略版本
     *
     * @param operatorId 当前运营账号 ID
     * @param status 状态过滤，ACTIVE / DISABLED；为空时返回全部
     * @return 数据类型列表
     */
    DataTypeListResponseBody listDataTypes(String operatorId, String status);

    /**
     * 新建数据类型
     *
     * @param operatorId 当前运营账号 ID
     * @param body 新建内容
     * @return 保存后的数据类型与新策略版本；编码已存在时抛出 POLICY_ENTRY_EXISTS
     */
    DataTypeResponseBody createDataType(String operatorId, CreateDataTypeRequestBody body);

    /**
     * 修改数据类型
     *
     * @param operatorId 当前运营账号 ID
     * @param dataTypeCode 数据类型编码
     * @param body 修改内容
     * @return 保存后的数据类型与新策略版本；版本过期时抛出 POLICY_VERSION_CONFLICT
     */
    DataTypeResponseBody updateDataType(String operatorId, String dataTypeCode, UpdateDataTypeRequestBody body);

    /**
     * 分页查询访问规则
     *
     * @param operatorId 当前运营账号 ID
     * @param filter 过滤条件，各字段可空
     * @param page 页码，为空时默认 1
     * @param size 每页条数，为空时默认 10，最大 200
     * @return 访问规则分页
     */
    PagingResponseBody<AccessRule> listAccessRules(String operatorId, AccessRuleFilter filter, Integer page,
        Integer size);

    /**
     * 新建访问规则
     *
     * @param operatorId 当前运营账号 ID
     * @param body 新建内容
     * @return 保存后的规则与新策略版本
     */
    AccessRuleResponseBody createAccessRule(String operatorId, CreateAccessRuleRequestBody body);

    /**
     * 启用或停用访问规则
     *
     * @param operatorId 当前运营账号 ID
     * @param accessRuleId 访问规则 ID
     * @param body 修改内容
     * @return 保存后的规则与新策略版本
     */
    AccessRuleResponseBody updateAccessRule(String operatorId, String accessRuleId, UpdateAccessRuleRequestBody body);

    /**
     * 分页查询策略变更记录（版本倒序，不含快照）
     *
     * @param operatorId 当前运营账号 ID
     * @param targetType 对象类型过滤，可空
     * @param targetKey 对象标识过滤，可空
     * @param page 页码，可空
     * @param size 每页条数，可空
     * @return 变更记录分页
     */
    PagingResponseBody<PolicyChange> listPolicyChanges(String operatorId, String targetType, String targetKey,
        Integer page, Integer size);

    /**
     * 查询一条策略变更详情（含前后快照）
     *
     * @param operatorId 当前运营账号 ID
     * @param policyVersionNo 策略版本号
     * @return 变更详情；不存在时抛出 POLICY_ENTRY_NOT_FOUND
     */
    PolicyChange getPolicyChange(String operatorId, int policyVersionNo);

    /**
     * 分页查询人工加/解密审计
     *
     * @param operatorId 当前运营账号 ID
     * @param filter 过滤条件，各字段可空
     * @param page 页码，可空
     * @param size 每页条数，可空
     * @return 审计分页，不含 Token 与原值
     */
    PagingResponseBody<ManualEvent> listManualEvents(String operatorId, ManualEventFilter filter, Integer page,
        Integer size);

    /**
     * 人工加密
     *
     * @param operatorId 当前运营账号 ID
     * @param submissionId 页面提交 ID，作为 Vault 的 Idempotency-Key；同一提交重放返回同一 Token
     * @param body 加密内容与本次路由允许的数据级别
     * @return Token，只向操作人展示一次
     */
    ManualProtectResponseBody manualProtect(String operatorId, String submissionId, ManualProtectRequestBody body);

    /**
     * 人工解密
     *
     * @param operatorId 当前运营账号 ID
     * @param body Token、理由与本次路由允许的数据级别
     * @return 数据类型与原值；Vault 先提交成功审计再返回
     */
    ManualRevealResponseBody manualReveal(String operatorId, ManualRevealRequestBody body);

    /**
     * 访问规则过滤条件
     *
     * @param dataTypeCode 数据类型编码
     * @param callerService 调用服务名
     * @param action 动作
     * @param status 状态
     */
    record AccessRuleFilter(String dataTypeCode, String callerService, String action, String status) {
    }

    /**
     * 人工操作审计过滤条件
     *
     * @param actorId 操作人账号 ID
     * @param action 动作
     * @param dataTypeCode 数据类型编码
     * @param from 起始时间（含）
     * @param to 截止时间（不含）
     */
    record ManualEventFilter(String actorId, String action, String dataTypeCode, Instant from, Instant to) {
    }
}
