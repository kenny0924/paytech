package com.paytech.platform.support.api.operations;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.platform.support.api.operations.response.OperationsAccountLabelListResponseBody;

/**
 * Operations 账号显示名批量查询；本期调用方只有 operations-console-bff，经私网调用，不经 Gateway、不注册路由。
 * <li>X-Caller-Service 为过渡信任（携带已验证内部 Token 时以 Token sub 为准）：只接受 operations-console-bff，其他调用方返回 400
 * BUSINESS_CHECK_FAILED。</li>
 * <li>只读，供 BFF 把页面中的操作人账号 ID 展示为可读名称；不返回完整邮箱、账号状态或角色。</li>
 */
@HttpExchange(url = "/operations-accounts/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface OperationsAccountLabelQueryApi {

    /** 调用方服务名 Header。 */
    String CALLER_SERVICE_HEADER = "X-Caller-Service";

    /**
     * 按账号 ID 批量查询显示名
     * <li>显示名取登录名，登录名为空时取脱敏邮箱；只返回 OPERATIONS Realm 账号。</li>
     * <li>不存在、不属于 OPERATIONS Realm 或两者都为空的账号不出现在结果中，由调用方回退展示。</li>
     *
     * @param callerService 调用方服务名
     * @param accountIds 账号 ID，十进制，逗号分隔，1 到 50 个
     * @return 按账号 ID 升序的显示名
     *         <li>accountIds 为空、超过 50 个或含非数字返回 400 INVALID_REQUEST；调用方未获准返回 400 BUSINESS_CHECK_FAILED。</li>
     */
    @GetExchange("/labels")
    OperationsAccountLabelListResponseBody listLabels(@RequestHeader(CALLER_SERVICE_HEADER) String callerService,
        @RequestParam(value = "accountIds", required = false) String accountIds);
}
