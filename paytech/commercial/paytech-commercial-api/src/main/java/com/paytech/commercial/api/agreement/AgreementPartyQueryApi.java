package com.paytech.commercial.api.agreement;

import java.util.List;

import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.commercial.api.agreement.response.AgreementPartyUsageResponseBody;

/**
 * 合同参与方引用查询（design/06 §2.2）：client-master 据此判断人员是否已被合同引用而禁止修改。
 * <li>只读本库，不区分合同状态；任一合同参与方引用该 Party 或资料即视为已引用。</li>
 */
@HttpExchange(accept = "application/json")
public interface AgreementPartyQueryApi {
    /**
     * 返回被任一合同参与方引用的 Party ID 与资料 ID。
     *
     * @param callerService 私网调用范围；仅允许 client-master-service
     * @param partyIds 待查 Party ID，规范正十进制 BIGINT 字符串；可省略
     * @param partyProfileIds 待查资料 ID；可省略；两者合计 1 到 1000 个
     * @return 被引用的子集，升序去重
     */
    @GetExchange("/commercial/v1/agreement-parties/exists")
    AgreementPartyUsageResponseBody exists(@RequestHeader(value = "X-Caller-Service", required = false) String callerService,
        @RequestParam(value = "partyIds", required = false) List<String> partyIds,
        @RequestParam(value = "partyProfileIds", required = false) List<String> partyProfileIds);
}
