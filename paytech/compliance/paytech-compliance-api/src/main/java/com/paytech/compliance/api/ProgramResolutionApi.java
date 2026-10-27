package com.paytech.compliance.api;

import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.compliance.api.response.ProgramResolutionResponseBody;

/**
 * CP-1 尽调程序解析：按辖区、主体类型、角色、档次与责任 PayFac 解析当前生效的程序版本（私网，client-master 调用；取代原材料目录接口）
 * <li>只读；不创建案件。解析顺序：责任 PayFac 定制程序优先于平台程序，辖区精确匹配优先于全局程序（辖区为空）。</li>
 * <li>同一优先级命中多个程序或一个都没有时返回 503，调用方不得回退到其他程序或自行编造材料。</li>
 */
@HttpExchange("/compliance/v1")
public interface ProgramResolutionApi {

    /**
     * 解析生效程序及其材料、人工检查与筛查模板
     *
     * @param caller 私网调用方服务名（X-Caller-Service）
     * @param jurisdiction 辖区代码，例如 SA、AE、EG；须为大写字母、数字或连字符
     * @param partyType 主体类型：ORGANIZATION 或 INDIVIDUAL
     * @param roleType 角色：CLIENT 或 RELATED_PERSON
     * @param tier 尽调档次：SIMPLIFIED、STANDARD 或 ENHANCED；缺省 STANDARD
     * @param responsiblePartnerId 责任 PayFac 的 Local Partner ID；平台自营为空
     * @return 程序版本与模板
     *         <li>参数非法 400；无可用程序或程序配置歧义 503。</li>
     */
    @GetExchange("/programs/resolve")
    ProgramResolutionResponseBody resolve(@RequestHeader("X-Caller-Service") String caller,
        @RequestParam("jurisdiction") String jurisdiction, @RequestParam("partyType") String partyType,
        @RequestParam("roleType") String roleType, @RequestParam(value = "tier", required = false) String tier,
        @RequestParam(value = "responsiblePartnerId", required = false) String responsiblePartnerId);
}
