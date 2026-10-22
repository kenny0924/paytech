package com.paytech.card.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.enums.BinLibraryActivationType;
import com.paytech.card.api.enums.BinLibraryVersionStatus;
import com.paytech.card.core.dal.dataobject.CardBinLibraryActivation;
import com.paytech.card.core.dal.dataobject.CardBinLibraryVersion;
import com.paytech.card.domain.binlibrary.BinLibraryActivationContext;
import com.paytech.card.domain.binlibrary.BinLibraryLock;
import com.paytech.card.domainservice.repository.CardBinLibraryVersionRepository;
import com.paytech.card.service.CardBinLibraryService;
import com.paytech.card.service.CardCallerService;
import com.paytech.card.service.processor.request.ActivateBinLibraryVersionRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 激活或回切 BIN 库版本（技术方案 §3.3）：锁 card-bin-library（与创建版本同一把）→ 核对页面看到的生效版本号（不符 409）→ 目标 VALID 为激活、
 * RETIRED 为回切，其他状态 400 → 一个本地事务：原 ACTIVE 停用并释放占位、目标占用占位、追加激活记录（操作人 = X-Gateway-Actor-Subject）。
 * <li>查询每次在数据库内按生效占位关联、不缓存，提交即对所有实例生效；已冻结在交易上的卡属性不受影响。</li>
 */
@Component
@AllArgsConstructor
public class ActivateBinLibraryVersionProcessor
    extends AbstractLockProcessor<ActivateBinLibraryVersionRequest, BinLibraryActivationContext> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final CardCallerService cardCallerService;
    private final CardBinLibraryService cardBinLibraryService;
    private final CardBinLibraryVersionRepository cardBinLibraryVersionRepository;

    /** 只接受 operations-console-bff。 */
    @Override
    protected void validate(ActivateBinLibraryVersionRequest request) {
        cardCallerService.require(request.getCaller(), CardApiConstants.OPERATIONS_CONSOLE_BFF);
    }

    /** 立即尝试获取 BIN 库锁。 */
    @Override
    protected LockCommand lockCommand(ActivateBinLibraryVersionRequest request) {
        return LockCommand.tryLock(BinLibraryLock.KEY);
    }

    @Override
    protected BinLibraryActivationContext processWithinLock(ActivateBinLibraryVersionRequest request) {
        // 目标版本与当前生效版本
        CardBinLibraryVersion target = getTarget(request.getVersionNo());
        CardBinLibraryVersion current = cardBinLibraryVersionRepository.findActive();
        checkExpectedActive(request, current);

        // 构建激活上下文
        BinLibraryActivationContext context =
            new BinLibraryActivationContext(target, current, buildActivation(request, target, current));

        // 停用、激活与留痕一起提交
        cardBinLibraryService.activateVersion(context);
        return context;
    }

    /** 目标版本必须存在。 */
    CardBinLibraryVersion getTarget(int versionNo) {
        CardBinLibraryVersion target = cardBinLibraryVersionRepository.findByVersionNo(versionNo);
        if (target == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "BIN 库版本不存在: v" + versionNo);
        }
        return target;
    }

    /** 页面看到的生效版本必须仍是当前生效版本（都没有时为 null）。 */
    static void checkExpectedActive(ActivateBinLibraryVersionRequest request, CardBinLibraryVersion current) {
        Integer currentNo = current == null ? null : current.getVersionNo();
        if (!Objects.equals(request.getExpectedActiveVersionNo(), currentNo)) {
            throw new ConcurrentRequestException("生效版本已被他人修改，请刷新后重试");
        }
    }

    /** 激活记录：VALID 为激活，RETIRED 为回切，其他状态不能激活。 */
    CardBinLibraryActivation buildActivation(ActivateBinLibraryVersionRequest request, CardBinLibraryVersion target,
        CardBinLibraryVersion current) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        CardBinLibraryActivation activation = new CardBinLibraryActivation();
        activation.setActivationId(idGenerator.nextId());
        activation.setVersionId(target.getVersionId());
        activation.setPreviousVersionId(current == null ? null : current.getVersionId());
        activation.setActivationType(activationType(target));
        activation.setOperatorAccountId(request.getActorAccountId());
        activation.setCreateTime(now);
        activation.setUpdateTime(now);
        return activation;
    }

    /** 目标状态对应的激活类型。 */
    static BinLibraryActivationType activationType(CardBinLibraryVersion target) {
        if (target.getStatus() == BinLibraryVersionStatus.VALID) {
            return BinLibraryActivationType.ACTIVATE;
        }
        if (target.getStatus() == BinLibraryVersionStatus.RETIRED) {
            return BinLibraryActivationType.ROLLBACK;
        }
        if (target.getStatus() == BinLibraryVersionStatus.ACTIVE) {
            throw new BizCheckFailException("BIN 库版本 v" + target.getVersionNo() + " 已是生效版本");
        }
        throw new BizCheckFailException("BIN 库版本 v" + target.getVersionNo() + " 状态为 " + target.getStatus().getCode()
            + "，不能激活");
    }
}
