package com.paytech.operations.console.bff.service.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.domain.enums.BusinessChangeComparison;
import com.paytech.operations.console.bff.integration.CommercialConsoleQueryClient;
import com.paytech.operations.console.bff.service.model.CommercialChangeModels.BusinessChangeEntry;
import com.paytech.operations.console.bff.service.processor.request.CommercialBusinessChangeRequest;

import lombok.AllArgsConstructor;

/**
 * 商务变更历史（F5）：commercial 快照分页 + 每条快照与同一受审计行上一快照的字段级比较
 * <li>commercial 按聚合版本倒序返回 Root 与 Child 快照；上一快照是列表中其后第一条 recordType + recordId 相同、
 * 版本更小的快照。先在本页查找，本页之后仍有快照时按 200 条一页向后回溯，最多 MAX_LOOKBACK_PAGES 页。</li>
 * <li>回溯到列表末尾仍未找到：该行最早的快照（FIRST_VERSION，全部字段视为变化）；超出回溯上限：NOT_COMPARED，不猜测。</li>
 * <li>条款版本快照的 priceCount、makerAccountId、checkerAccountId 是 commercial 只在部分命令追加的命令事实：本快照没有该键时
 * 不视为变化（否则每次提交 / 排期都会多出一个"变为空"的虚假字段）；本快照记录了该键时照常比较。</li>
 * <li>当前页操作人经 {@link OperatorLabelResolver} 批量补显示名（createByLabel）；查询失败只让显示名为 null，不影响历史。</li>
 * <li>只读组合；commercial 的 4xx（未知类型、非规范 ID）原样透传。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class CommercialBusinessChangeProcessor
    extends AbstractProcessor<CommercialBusinessChangeRequest, PagingResponseBody<BusinessChangeEntry>> {
    /** 回溯每页条数（commercial 允许的最大页）。 */
    static final int LOOKBACK_PAGE_SIZE = 200;

    /** 回溯页数上限，限制一次页面请求对 commercial 的放大。 */
    static final int MAX_LOOKBACK_PAGES = 5;

    /** 条款版本快照中按命令追加的审计事实（BusinessChangeSnapshot.commercialTermVersionData 的说明）。 */
    static final Set<String> VERSION_COMMAND_FACTS = Set.of("priceCount", "makerAccountId", "checkerAccountId");

    /** 条款版本快照的受审计对象类型。 */
    static final String TERM_VERSION_RECORD = "COMMERCIAL_TERM_VERSION";

    private final CommercialConsoleQueryClient client;
    private final OperatorLabelResolver labels;

    @Override
    protected PagingResponseBody<BusinessChangeEntry> processRequest(CommercialBusinessChangeRequest request) {
        PagingResponseBody<BusinessChangeEntry> page = client.listChanges(request.getAggregateRootType(),
            request.getAggregateRootId(), request.getPage(), request.getSize());
        List<BusinessChangeEntry> entries = page.items();
        List<BusinessChangeEntry> unresolved = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            BusinessChangeEntry previous = predecessor(entries.get(index), entries.subList(index + 1, entries.size()));
            if (previous == null) {
                unresolved.add(entries.get(index));
            } else {
                compare(entries.get(index), previous);
            }
        }
        long scanned = (long) (request.getPage() - 1) * request.getSize() + entries.size();
        boolean exhausted = unresolved.isEmpty() || scanned >= page.total()
            || lookBack(request, unresolved, Math.toIntExact(scanned));
        for (BusinessChangeEntry entry : unresolved) {
            unresolvedResult(entry, exhausted);
        }
        var names = labels.resolve(entries.stream().map(BusinessChangeEntry::getCreateBy));
        entries.forEach(entry -> entry.setCreateByLabel(OperatorLabelResolver.labelOf(names, entry.getCreateBy())));
        return page;
    }

    /**
     * 从绝对位置 start 起按 200 条一页向后查找未找到上一快照的条目
     *
     * @param request 页面请求（聚合根）
     * @param unresolved 未找到上一快照的条目；找到的条目会被比较并移出
     * @param start 已扫描条目数（下一条的零基位置）
     * @return 是否已扫描到列表末尾
     */
    boolean lookBack(CommercialBusinessChangeRequest request, List<BusinessChangeEntry> unresolved, int start) {
        int pageNo = start / LOOKBACK_PAGE_SIZE + 1;
        int skip = start % LOOKBACK_PAGE_SIZE;
        for (int fetched = 0; fetched < MAX_LOOKBACK_PAGES && !unresolved.isEmpty(); fetched++, pageNo++, skip = 0) {
            var chunk = client.listChanges(request.getAggregateRootType(), request.getAggregateRootId(), pageNo,
                LOOKBACK_PAGE_SIZE);
            List<BusinessChangeEntry> candidates = chunk.items().subList(Math.min(skip, chunk.items().size()),
                chunk.items().size());
            unresolved.removeIf(entry -> resolve(entry, candidates));
            if ((long) pageNo * LOOKBACK_PAGE_SIZE >= chunk.total()) {
                return true;
            }
        }
        return unresolved.isEmpty();
    }

    /** 在候选中找到上一快照则比较并返回 true。 */
    static boolean resolve(BusinessChangeEntry entry, List<BusinessChangeEntry> candidates) {
        BusinessChangeEntry previous = predecessor(entry, candidates);
        if (previous == null) {
            return false;
        }
        compare(entry, previous);
        return true;
    }

    /** 候选中第一条同一受审计行、聚合版本更小的快照；没有为 null。 */
    static BusinessChangeEntry predecessor(BusinessChangeEntry entry, List<BusinessChangeEntry> laterEntries) {
        for (BusinessChangeEntry candidate : laterEntries) {
            if (entry.getRecordType().equals(candidate.getRecordType()) && entry.getRecordId().equals(candidate.getRecordId())
                && candidate.getVersionNo() < entry.getVersionNo()) {
                return candidate;
            }
        }
        return null;
    }

    /** 与上一快照比较：键的并集中值不同者为变化字段。 */
    static void compare(BusinessChangeEntry entry, BusinessChangeEntry previous) {
        var fields = new TreeSet<String>(entry.getData().keySet());
        fields.addAll(previous.getData().keySet());
        Map<String, Object> before = previous.getData();
        entry.setComparison(BusinessChangeComparison.COMPARED.getCode());
        entry.setPreviousVersionNo(previous.getVersionNo());
        entry.setPreviousData(before);
        entry.setChangedFields(fields.stream().filter(field -> !absentCommandFact(entry, field))
            .filter(field -> !Objects.equals(before.get(field), entry.getData().get(field))).toList());
    }

    /** 条款版本快照没有记录的命令事实不参与比较。 */
    static boolean absentCommandFact(BusinessChangeEntry entry, String field) {
        return TERM_VERSION_RECORD.equals(entry.getRecordType()) && VERSION_COMMAND_FACTS.contains(field)
            && !entry.getData().containsKey(field);
    }

    /** 未找到上一快照：到达列表末尾为首版（全部字段），否则不比较。 */
    static void unresolvedResult(BusinessChangeEntry entry, boolean exhausted) {
        if (exhausted) {
            entry.setComparison(BusinessChangeComparison.FIRST_VERSION.getCode());
            entry.setChangedFields(List.copyOf(new TreeSet<>(entry.getData().keySet())));
            return;
        }
        entry.setComparison(BusinessChangeComparison.NOT_COMPARED.getCode());
        entry.setChangedFields(null);
    }
}
