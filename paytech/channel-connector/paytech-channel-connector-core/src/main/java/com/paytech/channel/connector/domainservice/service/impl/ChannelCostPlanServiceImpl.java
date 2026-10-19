package com.paytech.channel.connector.domainservice.service.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlan;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostScope;
import com.paytech.channel.connector.core.enums.ChannelCostVersionStatus;
import com.paytech.channel.connector.domain.ChannelCostPlanView;
import com.paytech.channel.connector.domain.ChannelCostVersionContext;
import com.paytech.channel.connector.domain.ChannelCostVersionView;
import com.paytech.channel.connector.domainservice.repository.ChannelCostComponentRepository;
import com.paytech.channel.connector.domainservice.repository.ChannelCostPlanRepository;
import com.paytech.channel.connector.domainservice.repository.ChannelCostPlanVerRepository;
import com.paytech.channel.connector.domainservice.repository.ChannelCostScopeRepository;
import com.paytech.channel.connector.domainservice.service.ChannelCostPlanService;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 渠道成本版本的本地保存与查询；锁顺序固定为方案 → 版本。 */
@Service
@AllArgsConstructor
public class ChannelCostPlanServiceImpl implements ChannelCostPlanService {
    private final TransactionTemplate transactionTemplate;
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

    /** 读取方案、版本、适用范围与成本组成。 */
    @Override
    public ChannelCostVersionContext loadVersion(String planCode, int costVer) {
        ChannelCostPlan plan = loadPlan(planCode);
        ChannelCostPlanVer ver = channelCostPlanVerRepository.findByPlanVer(plan.getId(), costVer);
        if (ver == null) {
            throw new BizCheckFailException("渠道成本版本不存在: 方案 " + planCode + ", 版本 " + costVer);
        }
        List<ChannelCostScope> scopes = channelCostScopeRepository.findByVersion(ver.getId());
        return new ChannelCostVersionContext(plan, ver, scopes,
            channelCostComponentRepository.findByScopes(scopes.stream().map(ChannelCostScope::getId).toList()));
    }

    /** 读取方案当前版本；指针无对应版本时按系统错误失败。 */
    @Override
    public ChannelCostPlanVer findCurrentVersion(ChannelCostPlan plan) {
        if (plan.getCurrentVerId() == null) {
            return null;
        }
        ChannelCostPlanVer currentVer = channelCostPlanVerRepository.findById(plan.getCurrentVerId());
        if (currentVer == null) {
            throw new ErrorException("渠道成本方案当前版本指针无对应版本: " + plan.getPlanCode() + ", " + plan.getCurrentVerId());
        }
        return currentVer;
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
            switchCurrent(context);
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

    /** 锁方案与版本后把 APPROVED 版本切换为当前版本。 */
    @Override
    public void activateVersion(ChannelCostVersionContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            lockPlanAndVersion(context);
            channelCostPlanVerRepository.activate(context.getVer());
            switchCurrent(context);
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

    /** 事务内停用被替代的原当前版本（若有），再以方案版本 CAS 切换当前指针。 */
    void switchCurrent(ChannelCostVersionContext context) {
        ChannelCostPlanVer ver = context.getVer();
        if (context.getCurrentVer() != null) {
            channelCostPlanVerRepository.deactivate(context.getCurrentVer(), ver.getEffectiveFrom());
        }
        channelCostPlanRepository.switchCurrentVersion(context.getPlan(), ver.getId());
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
