package com.paytech.reconciliation.api.summary;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.reconciliation.api.summary.response.ReconSummaryResponseBody;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 对账在途汇总（F5）
 * <li>只读；只做控制台查询与账龄，不做期末报告、分派与到期日。</li>
 */
@HttpExchange(url = "/reconciliation/v1/summary", accept = "application/json")
public interface ReconSummaryApi {

    /**
     * 查询在途账单、开放异常与未匹配银行流水汇总
     *
     * @param entityNo 可选经营主体编号；省略时汇总全部经营主体
     * @param currency 币种，必填（金额不跨币种相加）
     * @return 汇总；参数非法时 400
     */
    @GetExchange
    ReconSummaryResponseBody getReconSummary(
        @RequestParam(value = "entityNo", required = false) @Pattern(regexp = "[A-Z]{2}[0-9]{3}") String entityNo,
        @RequestParam("currency") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency);
}
