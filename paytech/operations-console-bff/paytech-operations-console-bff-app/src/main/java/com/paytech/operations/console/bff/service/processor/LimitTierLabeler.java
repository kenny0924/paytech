package com.paytech.operations.console.bff.service.processor;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.risklimittier.LabeledTierChangeBody;
import com.paytech.operations.console.bff.service.model.risklimittier.LabeledTierPendingChangeBody;
import com.paytech.operations.console.bff.service.model.risklimittier.LimitTierHistoryPageBody;
import com.paytech.risk.decision.api.control.response.ClientTierChangeListResponseBody;
import com.paytech.risk.decision.api.control.response.ClientTierChangeResponseBody;
import com.paytech.risk.decision.api.control.response.ClientTierDetailResponseBody;
import com.paytech.risk.decision.api.control.response.ClientTierSummaryBody;

import lombok.AllArgsConstructor;

/**
 * 限额档位的操作人显示名补全：一次批量查询本次涉及的申请人与审批人（见 {@link OperatorLabelResolver}）
 * <li>列表行与详情的待审批摘要替换为带 requestedByLabel 的页面模型；变更记录平铺加 requestedByLabel、decidedByLabel。</li>
 * <li>显示名查询失败只让 *Label 为 null，不影响档位数据。</li>
 */
@Component
@AllArgsConstructor
public class LimitTierLabeler {
    private final OperatorLabelResolver labels;

    /** 列表页：分页信息不变，各行待审批摘要补申请人显示名。 */
    public PagingResponseBody<ClientTierSummaryBody> labelPage(PagingResponseBody<ClientTierSummaryBody> page) {
        Map<String, String> names = labels.resolve(page.items().stream()
            .filter(item -> item.getPendingChange() != null).map(item -> item.getPendingChange().getRequestedBy()));
        page.items().forEach(item -> labelPending(item, names));
        return page;
    }

    /** 单条变更记录。 */
    public LabeledTierChangeBody label(ClientTierChangeResponseBody change) {
        Map<String, String> names = labels.resolve(Stream.of(change.getRequestedBy(), change.getDecidedBy()));
        return labeled(change, names);
    }

    /** 变更历史，顺序不变。 */
    public LimitTierHistoryPageBody labelHistory(ClientTierChangeListResponseBody history) {
        return labelWorkspace(null, history);
    }

    /**
     * 工作台的档位详情与历史一次查询显示名
     *
     * @param detail 档位详情，可为 null；其待审批摘要被就地替换
     * @param history 变更历史
     * @return 带显示名的历史
     */
    public LimitTierHistoryPageBody labelWorkspace(ClientTierDetailResponseBody detail, ClientTierChangeListResponseBody history) {
        Stream<String> pending = detail == null || detail.getPendingChange() == null ? Stream.empty()
            : Stream.of(detail.getPendingChange().getRequestedBy());
        Map<String, String> names = labels.resolve(Stream.concat(pending, history.getItems().stream()
            .flatMap(item -> Stream.of(item.getRequestedBy(), item.getDecidedBy()))));
        if (detail != null) {
            labelPending(detail, names);
        }
        List<LabeledTierChangeBody> items = history.getItems().stream().map(item -> labeled(item, names)).toList();
        return new LimitTierHistoryPageBody(items);
    }

    /** 待审批摘要替换为带显示名的页面模型。 */
    static void labelPending(ClientTierSummaryBody summary, Map<String, String> names) {
        var pending = summary.getPendingChange();
        if (pending != null) {
            summary.setPendingChange(LabeledTierPendingChangeBody.of(pending,
                OperatorLabelResolver.labelOf(names, pending.getRequestedBy())));
        }
    }

    /** 带显示名的变更记录。 */
    static LabeledTierChangeBody labeled(ClientTierChangeResponseBody change, Map<String, String> names) {
        return new LabeledTierChangeBody(change, OperatorLabelResolver.labelOf(names, change.getRequestedBy()),
            OperatorLabelResolver.labelOf(names, change.getDecidedBy()));
    }
}
