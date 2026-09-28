package com.paytech.client.contract.api.onboarding;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import com.paytech.client.contract.api.onboarding.response.ClientFormOptionsResponseBody;
/** C03 的 CCT 目录事实；BFF 另行组合 Risk 材料要求。 */
public interface ClientFormOptionsApi {
    /**
     * 查询当前会话可用的表单目录，不创建申请。
     * @param caller 私网调用方
     * @param accountId 当前账号
     * @param sessionId 当前 CLIENT 会话
     * @param clientType 个人或组织
     * @param countryCode 居住国或注册国，可空；不代替证件签发国
     * @return 目录及其可用性
     */
    @GetExchange("/client-onboarding/v1/form-options")
    ClientFormOptionsResponseBody options(@RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam("clientType") String clientType,
        @RequestParam(value="countryCode",required=false) String countryCode);
}
