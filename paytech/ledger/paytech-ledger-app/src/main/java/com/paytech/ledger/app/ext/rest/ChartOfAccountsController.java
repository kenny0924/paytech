package com.paytech.ledger.app.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.ledger.api.ChartOfAccountsCommandApi;
import com.paytech.ledger.api.request.CreateChartAccountRequestBody;
import com.paytech.ledger.api.request.UpdateChartAccountRequestBody;
import com.paytech.ledger.api.response.ChartAccountResponseBody;
import com.paytech.ledger.api.response.CreateChartAccountResponseBody;
import com.paytech.ledger.app.ext.rest.converter.ChartOfAccountsConverter;
import com.paytech.ledger.app.ext.rest.converter.LedgerReadConverter;
import com.paytech.ledger.service.processor.CreateChartAccountProcessor;
import com.paytech.ledger.service.processor.DisableChartAccountProcessor;
import com.paytech.ledger.service.processor.EnableChartAccountProcessor;
import com.paytech.ledger.service.processor.UpdateChartAccountProcessor;

import lombok.AllArgsConstructor;

/** 科目维护的 HTTP 协议适配：操作人 Header 作为变更记录的操作人传入 Processor，只做转换与委托。 */
@RestController
@AllArgsConstructor
public class ChartOfAccountsController implements ChartOfAccountsCommandApi {
    private final LedgerReadConverter readConverter;
    private final ChartOfAccountsConverter converter;
    private final CreateChartAccountProcessor createChartAccountProcessor;
    private final EnableChartAccountProcessor enableChartAccountProcessor;
    private final UpdateChartAccountProcessor updateChartAccountProcessor;
    private final DisableChartAccountProcessor disableChartAccountProcessor;

    /** 新增科目并返回 201。 */
    @Override
    public ResponseEntity<CreateChartAccountResponseBody> createChartAccount(String actorAccountId, String sessionId,
        String chartCode, CreateChartAccountRequestBody body) {
        CreateChartAccountResponseBody response = new CreateChartAccountResponseBody();
        response.setChartAccountId(createChartAccountProcessor.process(converter.toRequest(chartCode, body,
            actorAccountId)).getId().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 修改科目并返回修改后的科目。 */
    @Override
    public ChartAccountResponseBody updateChartAccount(String actorAccountId, String sessionId, String chartCode,
        String chartAccountId, UpdateChartAccountRequestBody body) {
        return readConverter.toChartBody(updateChartAccountProcessor.process(
            converter.toRequest(chartCode, chartAccountId, body, actorAccountId)));
    }

    /** 停用科目并返回停用后的科目。 */
    @Override
    public ChartAccountResponseBody disableChartAccount(String actorAccountId, String sessionId, String chartCode,
        String chartAccountId) {
        return readConverter.toChartBody(disableChartAccountProcessor.process(
            converter.toStatusRequest(chartCode, chartAccountId, actorAccountId)));
    }

    /** 启用科目并返回启用后的科目。 */
    @Override
    public ChartAccountResponseBody enableChartAccount(String actorAccountId, String sessionId, String chartCode,
        String chartAccountId) {
        return readConverter.toChartBody(enableChartAccountProcessor.process(
            converter.toStatusRequest(chartCode, chartAccountId, actorAccountId)));
    }
}
