package com.paytech.commerce.ext.rest.external.clientapi.v1.refund;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.commerce.client.api.v1.refund.ClientRefundApi;
import com.paytech.commerce.client.api.v1.refund.request.CreateRefundRequestBody;
import com.paytech.commerce.client.api.v1.refund.response.CreateRefundResponseBody;
import com.paytech.commerce.client.api.v1.refund.response.GetRefundResponseBody;
import com.paytech.commerce.client.api.v1.refund.response.GetRefundableAmountResponseBody;
import com.paytech.commerce.client.api.v1.refund.response.ListOrderRefundsResponseBody;
import com.paytech.commerce.service.processor.refund.*;

import lombok.AllArgsConstructor;

/** Client Refund API 的协议适配，业务查询与操作委托对应 Processor。 */
@RestController
@AllArgsConstructor
public class ClientRefundController implements ClientRefundApi {
    private final GetRefundProcessor getRefundProcessor;
    private final CreateRefundProcessor createRefundProcessor;
    private final ListOrderRefundsProcessor listOrderRefundsProcessor;
    private final GetRefundableAmountProcessor getRefundableAmountProcessor;
    private final GetRefundByClientRefundIdProcessor getRefundByClientRefundIdProcessor;

    /** 协议转换后交给 Processor，返回受理回执及公开资源位置。 */
    @Override
    public ResponseEntity<CreateRefundResponseBody> createRefund(long clientId, long credentialId, String orderNo,
        CreateRefundRequestBody body) {
        var response = createRefundProcessor.process(CreateRefundConverter.request(clientId, orderNo, body));
        return ResponseEntity.created(URI.create("/api/v1/refunds/" + response.getRefundRequestNo()))
            .body(CreateRefundConverter.response(response));
    }

    /** 将分页参数交给查询 Processor，按接口类型输出当前页。 */
    @Override
    public ListOrderRefundsResponseBody listOrderRefunds(long clientId, long credentialId, String orderNo, int limit,
        String cursor) {
        var response = listOrderRefundsProcessor.process(ListOrderRefundsConverter.request(clientId, orderNo, limit, cursor));
        return ListOrderRefundsConverter.response(response);
    }

    /** 将路径参数交给查询 Processor，按接口类型输出当前退款。 */
    @Override
    public GetRefundResponseBody getRefund(long clientId, long credentialId, String refundRequestNo) {
        var response = getRefundProcessor.process(GetRefundConverter.request(clientId, refundRequestNo));
        return GetRefundConverter.response(response);
    }

    /** 将业务退款号交给查询 Processor，复用退款详情的响应转换。 */
    @Override
    public GetRefundResponseBody getRefundByClientRefundId(long clientId, long credentialId, String clientRefundId) {
        var response = getRefundByClientRefundIdProcessor
            .process(GetRefundConverter.requestByClientRefundId(clientId, clientRefundId));
        return GetRefundConverter.response(response);
    }

    /** 将订单号交给查询 Processor，返回本地可退款金额快照。 */
    @Override
    public GetRefundableAmountResponseBody getRefundableAmount(long clientId, long credentialId, String orderNo) {
        var response = getRefundableAmountProcessor.process(GetRefundableAmountConverter.request(clientId, orderNo));
        return GetRefundableAmountConverter.response(response);
    }

}
