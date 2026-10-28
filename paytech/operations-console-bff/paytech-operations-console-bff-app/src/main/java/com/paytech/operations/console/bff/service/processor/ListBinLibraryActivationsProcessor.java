package com.paytech.operations.console.bff.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.card.api.binlibrary.response.BinLibraryActivationResponseBody;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.CardBinLibraryClient;
import com.paytech.operations.console.bff.service.model.binlibrary.response.LabeledBinLibraryActivationResponseBody;
import com.paytech.operations.console.bff.service.processor.request.ListBinLibraryActivationsRequest;

import lombok.AllArgsConstructor;

/**
 * BIN 库激活历史：转发 Card 激活分页，再为当前页操作人批量补显示名
 * <li>Card 失败整页失败；显示名查询失败只让 operatorLabel 为 null（见 {@link OperatorLabelResolver}）。</li>
 * <li>空页与没有账号操作人（如初始化种子激活）的页不查询 Platform。</li>
 */
@Component
@AllArgsConstructor
public class ListBinLibraryActivationsProcessor extends
    AbstractProcessor<ListBinLibraryActivationsRequest, PagingResponseBody<LabeledBinLibraryActivationResponseBody>> {
    private final CardBinLibraryClient client;
    private final OperatorLabelResolver labels;

    @Override
    protected PagingResponseBody<LabeledBinLibraryActivationResponseBody> processRequest(
        ListBinLibraryActivationsRequest request) {
        var paging = request.getPaging();
        var activations = client.listActivations(paging.page(), paging.size());
        var names = labels.resolve(activations.items().stream().map(BinLibraryActivationResponseBody::getOperatorAccountId));
        return activations.map(activation -> new LabeledBinLibraryActivationResponseBody(activation,
            OperatorLabelResolver.labelOf(names, activation.getOperatorAccountId())));
    }
}
