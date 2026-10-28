package com.paytech.operations.console.bff.service.processor;

import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.CardBinLibraryClient;
import com.paytech.operations.console.bff.service.model.binlibrary.response.LabeledBinLibraryVersionResponseBody;
import com.paytech.operations.console.bff.service.processor.request.GetBinLibraryVersionRequest;

import lombok.AllArgsConstructor;

/**
 * BIN 库版本详情：转发 Card 版本详情，再补导入人显示名
 * <li>Card 失败（含 404）整页失败；显示名查询失败只让 createdByLabel 为 null。</li>
 */
@Component
@AllArgsConstructor
public class GetBinLibraryVersionProcessor
    extends AbstractProcessor<GetBinLibraryVersionRequest, LabeledBinLibraryVersionResponseBody> {
    private final CardBinLibraryClient client;
    private final OperatorLabelResolver labels;

    @Override
    protected LabeledBinLibraryVersionResponseBody processRequest(GetBinLibraryVersionRequest request) {
        var version = client.getVersion(request.getVersionNo());
        var names = labels.resolve(Stream.of(version.getCreatedBy()));
        return new LabeledBinLibraryVersionResponseBody(version,
            OperatorLabelResolver.labelOf(names, version.getCreatedBy()));
    }
}
