package com.paytech.client.master.api.onboarding;

import java.util.List;

import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.client.master.api.onboarding.response.FormSchemaResponseBody;
import com.paytech.client.master.api.onboarding.response.JurisdictionResponseBody;

/** CM-1 / CM-2：Client BFF 读取可入驻辖区与表单渲染规格；身份来自可信入口 Header，每次核验 Platform 会话。 */
@HttpExchange("/client-onboarding/v1")
public interface ClientOnboardingCatalogApi {
    /**
     * 可入驻辖区（ACTIVE 且准入策略不是 CLOSED）
     *
     * @param caller 私网调用方，固定为 client-console-bff
     * @param accountId 当前 CLIENT 账号
     * @param sessionId 当前 CLIENT 会话
     * @return 辖区列表，按代码排序
     */
    @GetExchange("/jurisdictions")
    List<JurisdictionResponseBody> jurisdictions(@RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId, @RequestHeader("X-Gateway-Session-Id") String sessionId);

    /**
     * 表单渲染规格
     *
     * @param caller 私网调用方，固定为 client-console-bff
     * @param accountId 当前 CLIENT 账号
     * @param sessionId 当前 CLIENT 会话
     * @param applicationId 已创建的申请 ID，可空；提供时返回该申请冻结的 Schema 版本与按其责任 PayFac 解析的材料，忽略其余参数
     * @param jurisdiction 辖区代码；未提供 applicationId 时必填
     * @param partyType INDIVIDUAL 或 ORGANIZATION；未提供 applicationId 时必填
     * @param organizationType 组织类型代码，可空；有专用 Schema 时返回专用版本，否则返回 * 默认版本
     * @return Schema、目录、规则、材料槽与声明
     *         <li>辖区不存在、未启用、准入 CLOSED 或无 ACTIVE Schema：422 JURISDICTION_NOT_ACCEPTED。</li>
     *         <li>compliance 程序不可用：503 ONBOARDING_DEPENDENCY_UNAVAILABLE（不返回缺少材料槽的半成品）。</li>
     */
    @GetExchange("/form-schema")
    FormSchemaResponseBody formSchema(@RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId, @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "applicationId", required = false) String applicationId,
        @RequestParam(value = "jurisdiction", required = false) String jurisdiction,
        @RequestParam(value = "partyType", required = false) String partyType,
        @RequestParam(value = "organizationType", required = false) String organizationType);
}
