package com.paytech.risk.control.service.impl;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.paytech.risk.control.core.dal.dataobject.RiskList;
import com.paytech.risk.control.core.enums.RiskListStatus;
import com.paytech.risk.control.domain.list.RiskListView;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.control.domainservice.repository.RiskListEntryRepository;
import com.paytech.risk.control.domainservice.repository.RiskListRepository;
import com.paytech.risk.control.service.RiskListMembershipService;

import lombok.AllArgsConstructor;

/** 名单定义与命中的只读查询。 */
@Service
@AllArgsConstructor
public class RiskListMembershipServiceImpl implements RiskListMembershipService {
    private final RiskListRepository lists;
    private final RiskListEntryRepository entries;

    /** 按 ID 读取名单定义。 */
    @Override
    public Optional<RiskListView> findList(long listId) {
        return lists.findById(listId).map(RiskListView::of);
    }

    /** 按编码读取名单定义。 */
    @Override
    public Optional<RiskListView> findListByCode(String listCode) {
        return lists.findByCode(listCode).map(RiskListView::of);
    }

    /** 名单停用或主体类型不符时视为未命中，不抛错。 */
    @Override
    public boolean isMember(long listId, RiskSubjectType subjectType, String subjectKey, Instant at) {
        RiskList list = lists.findById(listId).orElse(null);
        if (list == null || list.getStatus() != RiskListStatus.ACTIVE || list.getSupportedSubjectType() != subjectType) {
            return false;
        }
        return entries.isEffective(listId, subjectType, subjectKey, at);
    }
}
