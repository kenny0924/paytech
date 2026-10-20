package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.operations.console.bff.integration.CommercialPricePreviewClient;
import com.paytech.operations.console.bff.service.model.CommercialPricePreviewModels.CommercialPriceSimulationResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricePreviewModels.RateCardResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricePreviewModels.SimulateCommercialPriceRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialWorkbenchModels.CommercialDashboardResponseBody;
import com.paytech.operations.console.bff.service.processor.CommercialDashboardProcessor;
import com.paytech.operations.console.bff.service.processor.CommercialPriceSimulationProcessor;
import com.paytech.operations.console.bff.service.processor.request.CommercialDashboardRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 商务工作台、费率卡预览与价格试算的运营入口（只读）
 * <li>Gateway 绑定：工作台为 COMMERCIAL_CONTRACT_READ；费率卡与价格试算为 COMMERCIAL_PRICING_READ（试算是 POST 查询，不写任何数据）。</li>
 * <li>费率卡简单转发 commercial RateCardApi（与 Client 门户同源）；试算经 Processor 命中价格并计算示例费用；工作台经 Processor 组装指标。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/commercial-admin/v1")
public class CommercialPricePreviewController {
    private static final String ID = "[1-9][0-9]{0,18}";
    private static final String UTC_INSTANT = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,6})?Z";

    private final CommercialPricePreviewClient client;
    private final CommercialDashboardProcessor dashboardProcessor;
    private final CommercialPriceSimulationProcessor simulationProcessor;

    /** W01 工作台指标卡片。 */
    @GetMapping("/dashboard")
    public CommercialDashboardResponseBody dashboard(HttpServletRequest servletRequest) {
        requireQueryKeys(servletRequest, Set.of());
        return dashboardProcessor.process(new CommercialDashboardRequest(Instant.now().truncatedTo(ChronoUnit.MICROS)));
    }

    /** R01 Client 费率卡预览。 */
    @GetMapping("/rate-cards")
    public RateCardResponseBody rateCard(HttpServletRequest servletRequest,
        @RequestParam("clientId") @Pattern(regexp = ID) String clientId,
        @RequestParam(value = "asOf", required = false) @Pattern(regexp = UTC_INSTANT) String asOf,
        @RequestParam(value = "currency", required = false) @Pattern(regexp = "[A-Z]{3}") String currency) {
        requireQueryKeys(servletRequest, Set.of("clientId", "asOf", "currency"));
        return client.rateCard(clientId, asOf, currency);
    }

    /** S01 价格试算：命中价格与示例费用。 */
    @PostMapping("/commercial-prices/simulate")
    public CommercialPriceSimulationResponseBody simulate(HttpServletRequest servletRequest,
        @RequestBody @NotNull @Valid SimulateCommercialPriceRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return simulationProcessor.process(body);
    }
}
