package com.paytech.risk.control.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.risk.control.core.dal.dataobject.RiskList;
import com.paytech.risk.control.core.dal.dataobject.RiskListEntry;
import com.paytech.risk.control.core.enums.RiskListEntryStatus;
import com.paytech.risk.control.domain.list.AddRiskListEntryContext;
import com.paytech.risk.control.domain.list.DeactivateRiskListEntryContext;
import com.paytech.risk.control.domain.list.RiskListEntryView;
import com.paytech.risk.control.domain.list.RiskListView;
import com.paytech.risk.control.domain.list.RiskSubjectKeys;
import com.paytech.risk.control.domainservice.repository.RiskListEntryRepository;
import com.paytech.risk.control.domainservice.repository.RiskListRepository;
import com.paytech.risk.control.service.RiskListService;

import lombok.AllArgsConstructor;

/** 名单本地保存；调用方已完成归属、状态与格式校验。 */
@Service
@AllArgsConstructor
public class RiskListServiceImpl implements RiskListService {
    private final RiskListRepository lists;
    private final RiskListEntryRepository entries;
    private final PlatformTransactionManager transactionManager;

    /** 单行插入，编码唯一由数据库保证。 */
    @Override
    public void createList(RiskList list) {
        lists.insert(list);
    }

    /** 先释放过期占位再插入，二者同一事务提交。 */
    @Override
    public void addEntry(AddRiskListEntryContext context) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            RiskListEntry expired = context.getExpiredEntry();
            if (expired != null) {
                entries.close(expired, RiskListEntryStatus.EXPIRED, null, null, expired.getEffectiveTo(),
                    context.getEntry().getCreateTime());
            }
            entries.insert(context.getEntry());
        });
    }

    /** 单行条件更新。 */
    @Override
    public void deactivateEntry(DeactivateRiskListEntryContext context) {
        entries.close(context.getEntry(), RiskListEntryStatus.RELEASED, context.getActorAccountId(), context.getReason(),
            context.effectiveTo(), context.getNow());
    }

    /** 名单与总数取自相邻读取，管理查询允许观察相邻已提交状态。 */
    @Override
    public Page<RiskListView> findLists(int limit, long offset) {
        return new Page<>(lists.findPage(limit, offset).stream().map(RiskListView::of).toList(), lists.count());
    }

    /** 主体过滤值按名单主体类型规范化，与成员入库键一致。 */
    @Override
    public Page<RiskListEntryView> findEntries(String listCode, String subjectValue, RiskListEntryStatus status,
        int limit, long offset) {
        RiskList list = lists.findByCode(listCode)
            .orElseThrow(() -> new BizCheckFailException("名单不存在: " + listCode));
        String subjectKey = subjectValue == null ? null
            : RiskSubjectKeys.normalize(list.getSupportedSubjectType(), subjectValue);
        List<RiskListEntryView> items = entries.findPage(list.getListId(), subjectKey, status, limit, offset).stream()
            .map(RiskListEntryView::of).toList();
        return new Page<>(items, entries.count(list.getListId(), subjectKey, status));
    }
}
