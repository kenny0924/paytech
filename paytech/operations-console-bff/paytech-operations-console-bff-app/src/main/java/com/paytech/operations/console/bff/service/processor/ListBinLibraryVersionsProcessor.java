package com.paytech.operations.console.bff.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.card.api.binlibrary.response.BinLibraryVersionResponseBody;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.CardBinLibraryClient;
import com.paytech.operations.console.bff.service.model.binlibrary.response.LabeledBinLibraryVersionResponseBody;
import com.paytech.operations.console.bff.service.processor.request.ListBinLibraryVersionsRequest;

import lombok.AllArgsConstructor;

/**
 * BIN 库版本列表：转发 Card 版本分页，再为当前页导入人批量补显示名
 * <li>Card 失败整页失败；显示名查询失败只让 createdByLabel 为 null（见 {@link OperatorLabelResolver}）。</li>
 * <li>空页与没有账号导入人（如初始化种子版本）的页不查询 Platform。</li>
 */
@Component
@AllArgsConstructor
public class ListBinLibraryVersionsProcessor
    extends AbstractProcessor<ListBinLibraryVersionsRequest, PagingResponseBody<LabeledBinLibraryVersionResponseBody>> {
    private final CardBinLibraryClient client;
    private final OperatorLabelResolver labels;

    @Override
    protected PagingResponseBody<LabeledBinLibraryVersionResponseBody> processRequest(ListBinLibraryVersionsRequest request) {
        var paging = request.getPaging();
        var versions = client.listVersions(request.getStatus(), paging.page(), paging.size());
        var names = labels.resolve(versions.items().stream().map(BinLibraryVersionResponseBody::getCreatedBy));
        return versions.map(version -> new LabeledBinLibraryVersionResponseBody(version,
            OperatorLabelResolver.labelOf(names, version.getCreatedBy())));
    }
}
