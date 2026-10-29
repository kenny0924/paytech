package com.paytech.operations.console.bff.service.processor;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.riskunderwriting.LabeledUnderwritingResponseBody;
import com.paytech.operations.console.bff.service.model.riskunderwriting.LabeledUnderwritingSummaryBody;
import com.paytech.operations.console.bff.service.model.riskunderwriting.UnderwritingCurrentPageBody;
import com.paytech.risk.decision.api.control.response.ClientUnderwritingCurrentResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingSummaryBody;

import lombok.AllArgsConstructor;

/**
 * 承保版本的操作人显示名补全：一次批量查询本次涉及的创建人、提交人与决定人（见 {@link OperatorLabelResolver}）
 * <li>显示名查询失败只让 *Label 为 null，不影响承保数据。</li>
 */
@Component
@AllArgsConstructor
public class UnderwritingLabeler {
    private final OperatorLabelResolver labels;

    /** 单个版本；版本为 null 时返回 null。 */
    public LabeledUnderwritingResponseBody label(UnderwritingResponseBody underwriting) {
        return underwriting == null ? null : labelAll(List.of(underwriting)).getFirst();
    }

    /** 多个版本，顺序不变。 */
    public List<LabeledUnderwritingResponseBody> labelAll(List<UnderwritingResponseBody> underwritings) {
        Map<String, String> names = labels.resolve(underwritings.stream()
            .flatMap(item -> Stream.of(item.getCreatedBy(), item.getSubmittedBy(), item.getDecidedBy())));
        return underwritings.stream().map(item -> labeled(item, names)).toList();
    }

    /** 当前承保三个位置一次查询。 */
    public UnderwritingCurrentPageBody labelCurrent(ClientUnderwritingCurrentResponseBody current) {
        List<UnderwritingResponseBody> present = Stream.of(current.getEffective(), current.getOpen(), current.getLatest())
            .filter(Objects::nonNull).toList();
        Map<String, String> names = labels.resolve(present.stream()
            .flatMap(item -> Stream.of(item.getCreatedBy(), item.getSubmittedBy(), item.getDecidedBy())));
        return new UnderwritingCurrentPageBody(labeledOrNull(current.getEffective(), names),
            labeledOrNull(current.getOpen(), names), labeledOrNull(current.getLatest(), names));
    }

    /** 列表页，分页信息不变。 */
    public PagingResponseBody<LabeledUnderwritingSummaryBody> labelPage(PagingResponseBody<UnderwritingSummaryBody> page) {
        Map<String, String> names = labels.resolve(page.items().stream()
            .flatMap(item -> Stream.of(item.getSubmittedBy(), item.getDecidedBy())));
        return page.map(item -> new LabeledUnderwritingSummaryBody(item, OperatorLabelResolver.labelOf(names, item.getSubmittedBy()),
            OperatorLabelResolver.labelOf(names, item.getDecidedBy())));
    }

    /** 带显示名的版本。 */
    static LabeledUnderwritingResponseBody labeled(UnderwritingResponseBody item, Map<String, String> names) {
        return new LabeledUnderwritingResponseBody(item, OperatorLabelResolver.labelOf(names, item.getCreatedBy()),
            OperatorLabelResolver.labelOf(names, item.getSubmittedBy()), OperatorLabelResolver.labelOf(names, item.getDecidedBy()));
    }

    /** 可空版本。 */
    static LabeledUnderwritingResponseBody labeledOrNull(UnderwritingResponseBody item, Map<String, String> names) {
        return item == null ? null : labeled(item, names);
    }
}
