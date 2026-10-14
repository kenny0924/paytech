package com.paytech.risk.control.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.control.core.dal.dataobject.RiskList;
import com.paytech.risk.control.core.enums.RiskListStatus;
import com.paytech.risk.control.domain.list.RiskListView;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.control.domainservice.port.ControlIdentifierGenerator;
import com.paytech.risk.control.domainservice.repository.RiskListRepository;
import com.paytech.risk.control.service.RiskListService;
import com.paytech.risk.control.service.processor.request.CreateRiskListRequest;

import lombok.AllArgsConstructor;

/**
 * 创建风险名单
 * <li>编码全库唯一；同编码重复创建为业务错误，并发创建由唯一索引转为 409。</li>
 * <li>一期只支持精确匹配，暂不支持邮箱、手机号 Token 主体。</li>
 */
@Component
@AllArgsConstructor
public class CreateRiskListProcessor extends AbstractProcessor<CreateRiskListRequest, RiskListView> {
    /** 一期唯一的匹配方式。 */
    static final String EXACT = "EXACT";

    private final Clock clock;
    private final RiskListService service;
    private final ControlIdentifierGenerator identifiers;
    private final RiskListRepository lists;

    @Override
    protected RiskListView processRequest(CreateRiskListRequest request) {
        // 业务防重
        if (lists.findByCode(request.getListCode()).isPresent()) {
            throw new BizCheckFailException("名单编码已存在: " + request.getListCode());
        }
        if (request.getSubjectType() == RiskSubjectType.EMAIL_TOKEN || request.getSubjectType() == RiskSubjectType.PHONE_TOKEN) {
            throw new BizCheckFailException("一期不支持的名单主体类型: " + request.getSubjectType().getCode());
        }

        // 保存
        RiskList list = buildList(request, clock.instant().truncatedTo(ChronoUnit.MICROS));
        service.createList(list);
        return RiskListView.of(list);
    }

    /** 构造启用状态的新名单。 */
    RiskList buildList(CreateRiskListRequest request, Instant now) {
        RiskList list = new RiskList();
        list.setListId(identifiers.nextIdentifier());
        list.setListCode(request.getListCode());
        list.setListName(request.getListName());
        list.setSupportedSubjectType(request.getSubjectType());
        list.setMatchingMode(EXACT);
        list.setListPurpose(request.getPurpose());
        list.setStatus(RiskListStatus.ACTIVE);
        list.setVersion(1);
        list.setCreateTime(now);
        list.setUpdateTime(now);
        return list;
    }
}
