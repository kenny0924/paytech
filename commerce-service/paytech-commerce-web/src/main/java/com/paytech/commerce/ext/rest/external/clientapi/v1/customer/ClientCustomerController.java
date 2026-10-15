package com.paytech.commerce.ext.rest.external.clientapi.v1.customer;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.commerce.client.api.v1.customer.ClientCustomerApi;
import com.paytech.commerce.client.api.v1.customer.request.CreateCustomerRequestBody;
import com.paytech.commerce.client.api.v1.customer.response.CustomerResponseBody;
import com.paytech.commerce.client.api.v1.customer.response.ListStoredCardsResponseBody;
import com.paytech.commerce.service.processor.customer.CreateCustomerProcessor;
import com.paytech.commerce.service.processor.customer.DeleteCustomerProcessor;
import com.paytech.commerce.service.processor.customer.DeleteStoredCardProcessor;
import com.paytech.commerce.service.processor.customer.GetCustomerProcessor;
import com.paytech.commerce.service.processor.customer.ListStoredCardsProcessor;

import lombok.AllArgsConstructor;

/** Customer 与已存卡接口的薄适配。 */
@RestController
@AllArgsConstructor
public class ClientCustomerController implements ClientCustomerApi {
    private final CreateCustomerProcessor createCustomerProcessor;
    private final GetCustomerProcessor getCustomerProcessor;
    private final DeleteCustomerProcessor deleteCustomerProcessor;
    private final ListStoredCardsProcessor listStoredCardsProcessor;
    private final DeleteStoredCardProcessor deleteStoredCardProcessor;

    @Override
    public ResponseEntity<CustomerResponseBody> createCustomer(long clientId, long credentialId, String idempotencyKey,
        CreateCustomerRequestBody requestBody) {
        var response = createCustomerProcessor.process(
            ClientCustomerConverter.request(clientId, null, requestBody.getConsumerReference(), null));
        var body = ClientCustomerConverter.customer(response.customer());
        return response.created()
            ? ResponseEntity.created(URI.create("/api/v1/customers/" + body.getCustomerNo())).body(body)
            : ResponseEntity.ok(body);
    }

    @Override
    public CustomerResponseBody getCustomer(long clientId, long credentialId, String customerNo) {
        var response = getCustomerProcessor.process(ClientCustomerConverter.request(clientId, customerNo, null, null));
        return ClientCustomerConverter.customer(response.customer());
    }

    @Override
    public CustomerResponseBody getCustomerByReference(long clientId, long credentialId, String consumerReference) {
        var response =
            getCustomerProcessor.process(ClientCustomerConverter.request(clientId, null, consumerReference, null));
        return ClientCustomerConverter.customer(response.customer());
    }

    @Override
    public CustomerResponseBody deleteCustomer(long clientId, long credentialId, String customerNo,
        String idempotencyKey) {
        var response =
            deleteCustomerProcessor.process(ClientCustomerConverter.request(clientId, customerNo, null, null));
        return ClientCustomerConverter.customer(response.customer());
    }

    @Override
    public ListStoredCardsResponseBody listStoredCards(long clientId, long credentialId, String customerNo) {
        return ClientCustomerConverter.storedCards(
            listStoredCardsProcessor.process(ClientCustomerConverter.request(clientId, customerNo, null, null)));
    }

    @Override
    public ResponseEntity<Void> deleteStoredCard(long clientId, long credentialId, String customerNo,
        String storedCardToken, String idempotencyKey) {
        deleteStoredCardProcessor
            .process(ClientCustomerConverter.request(clientId, customerNo, null, storedCardToken));
        return ResponseEntity.noContent().build();
    }
}
