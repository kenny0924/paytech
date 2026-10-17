package com.paytech.billing.domainservice.service.impl;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.billing.core.dal.dataobject.ChannelCostPlan;
import com.paytech.billing.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.billing.core.dal.dataobject.ChannelCostScope;
import com.paytech.billing.core.dal.dataobject.CurrencySpec;
import com.paytech.billing.core.enums.ChannelCostVersionStatus;
import com.paytech.billing.domain.ChannelCostVersionContext;
import com.paytech.billing.domain.view.ChannelCostPlanView;
import com.paytech.billing.domain.view.ChannelCostVersionView;
import com.paytech.billing.domainservice.repository.ChannelCostComponentRepository;
import com.paytech.billing.domainservice.repository.ChannelCostPlanRepository;
import com.paytech.billing.domainservice.repository.ChannelCostPlanVerRepository;
import com.paytech.billing.domainservice.repository.ChannelCostScopeRepository;
import com.paytech.billing.domainservice.repository.CurrencySpecRepository;
import com.paytech.billing.domainservice.service.ChannelCostPlanService;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 渠道成本版本的本地保存与查询；锁顺序固定为方案 → 版本。 */
@Service
@AllArgsConstructor
public class ChannelCostPlanServiceImpl implements ChannelCostPlanService {
    private final TransactionTemplate transactionTemplate;
    private final CurrencySpecRepository currencySpecRepository;
    private final ChannelCostPlanRepository channelCostPlanRepository;
    private final ChannelCostScopeRepository channelCostScopeRepository;
    private final ChannelCostPlanVerRepository channelCostPlanVerRepository;
    private final ChannelCostComponentRepository channelCostComponentRepository;

    /** 单行写入方案，唯一键保护 Channel 与方案代码。 */
    @Override
    public void createPlan(ChannelCostPlan plan) {
        channelCostPlanRepository.insert(plan);
    }

    /** 读取方案；不存在时业务拒绝。 */
    @Override
    public ChannelCostPlan loadPlan(String planCode) {
        ChannelCostPlan plan = channelCostPlanRepository.findByCode(planCode);
        if (plan == null) {
            throw new BizCheckFailException("渠道成本方案不存在: " + planCode);
        }
        return plan;
    }

    /** 读取方案、版本、适用范围、成本组成与范围币种精度。 */
    @Override
    public ChannelCostVersionContext loadVersion(String planCode, int costVer) {
        ChannelCostPlan plan = loadPlan(planCode);
        ChannelCostPlanVer ver = channelCostPlanVerRepository.findByPlanVer(plan.getId(), costVer);
        if (ver == null) {
            throw new BizCheckFailException("渠道成本版本不存在: 方案 " + planCode + ", 版本 " + costVer);
        }
        List<ChannelCostScope> scopes = channelCostScopeRepository.findByVersion(ver.getId());
        return new ChannelCostVersionContext(plan, ver, scopes,
            channelCostComponentRepository.findByScopes(scopes.stream().map(ChannelCostScope::getId).toList()),
            loadMinorUnits(scopes));
    }

    /** 锁方案后复验无待处理版本，再写入版本、适用范围与成本组成。 */
    @Override
    public void createVersion(ChannelCostVersionContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            lockPlan(context);
            if (channelCostPlanVerRepository.existsOpenVersion(context.getPlan().getId())) {
                throw new ConcurrentRequestException("渠道成本方案已有待处理版本: " + context.getPlan().getPlanCode());
            }
            channelCostPlanVerRepository.insert(context.getVer());
            channelCostScopeRepository.insertAll(context.getScopes());
            channelCostComponentRepository.insertAll(context.getComponents());
        });
    }

    /** 锁方案与版本后提交待复核。 */
    @Override
    public void submitVersion(ChannelCostVersionContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            lockPlanAndVersion(context);
            channelCostPlanVerRepository.updateStatus(context.getVer(), ChannelCostVersionStatus.PENDING_APPROVAL);
        });
    }

    /** 锁方案与版本后写入批准结果；已到生效时间时停用原当前版本并切换指针。 */
    @Override
    public void approveVersion(ChannelCostVersionContext context, long checkerId, Instant approvedTime) {
        transactionTemplate.executeWithoutResult(status -> {
            lockPlanAndVersion(context);
            ChannelCostPlanVer ver = context.getVer();
            if (!context.effectiveAt(approvedTime)) {
                channelCostPlanVerRepository.review(ver, ChannelCostVersionStatus.APPROVED, checkerId);
                return;
            }
            channelCostPlanVerRepository.review(ver, ChannelCostVersionStatus.ACTIVE, checkerId);
            if (context.getCurrentVer() != null) {
                channelCostPlanVerRepository.deactivate(context.getCurrentVer(), ver.getEffectiveFrom());
            }
            channelCostPlanRepository.switchCurrentVersion(context.getPlan(), ver.getId());
        });
    }

    /** 锁方案与版本后写入驳回结果。 */
    @Override
    public void rejectVersion(ChannelCostVersionContext context, long checkerId) {
        transactionTemplate.executeWithoutResult(status -> {
            lockPlanAndVersion(context);
            channelCostPlanVerRepository.review(context.getVer(), ChannelCostVersionStatus.REJECTED, checkerId);
        });
    }

    /** 查询方案与全部版本摘要。 */
    @Override
    public ChannelCostPlanView getPlan(String planCode) {
        ChannelCostPlan plan = loadPlan(planCode);
        return ChannelCostPlanView.of(plan, channelCostPlanVerRepository.findByPlan(plan.getId()));
    }

    /** 查询版本详情。 */
    @Override
    public ChannelCostVersionView getVersion(String planCode, int costVer) {
        return loadVersion(planCode, costVer).toView();
    }

    /** 范围币种的小数位；已保存范围的币种规则缺失属于数据不变量破坏。 */
    Map<String, Integer> loadMinorUnits(List<ChannelCostScope> scopes) {
        Map<String, Integer> minorUnits = new HashMap<>();
        for (ChannelCostScope scope : scopes) {
            if (minorUnits.containsKey(scope.getCurrency())) {
                continue;
            }
            CurrencySpec spec = currencySpecRepository.findByCurrency(scope.getCurrency());
            if (spec == null) {
                throw new ErrorException("渠道成本适用范围币种规则缺失: " + scope.getCurrency() + ", 范围 " + scope.getId());
            }
            minorUnits.put(scope.getCurrency(), spec.getMinorUnit());
        }
        return minorUnits;
    }

    /** 事务内对方案行加排他锁。 */
    void lockPlan(ChannelCostVersionContext context) {
        if (channelCostPlanRepository.lockByCode(context.getPlan().getPlanCode()) == null) {
            throw new ErrorException("渠道成本方案在写入时不存在: " + context.getPlan().getPlanCode());
        }
    }

    /** 事务内按方案 → 版本顺序加排他锁。 */
    void lockPlanAndVersion(ChannelCostVersionContext context) {
        lockPlan(context);
        ChannelCostPlanVer ver = context.getVer();
        if (channelCostPlanVerRepository.lockByPlanVer(ver.getPlanId(), ver.getCostVer()) == null) {
            throw new ErrorException("渠道成本版本在写入时不存在: " + ver.getId());
        }
    }
}
