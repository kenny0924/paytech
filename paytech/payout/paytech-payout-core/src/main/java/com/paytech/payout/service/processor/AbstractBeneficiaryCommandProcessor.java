package com.paytech.payout.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.payout.api.enums.BeneficiaryActorType;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.api.enums.PayoutErrorCode;
import com.paytech.payout.core.constant.PayoutLocks;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;
import com.paytech.payout.domain.BeneficiaryAccountView;
import com.paytech.payout.domainservice.repository.BeneficiaryAccountRepository;

/**
 * 单个收款账户写命令的公共骨架：按账户所属 Owner 加锁，锁内重新读取并校验归属、状态与版本
 * <li>锁 Key 与提交命令相同（PAYOUT_BENEFICIARY_OWNER:{ownerType}:{ownerId}），同 Owner 的默认切换、事件序号与查重串行。</li>
 * <li>账户不存在在加锁前即 404；锁内读取的才是判定依据，版本与状态仍由条件 SQL 最终保护。</li>
 *
 * @param <R> 命令请求
 * @param <S> 命令结果
 */
public abstract class AbstractBeneficiaryCommandProcessor<R, S> extends AbstractLockProcessor<R, S> {
    /** 时间源 */
    protected final Clock clock;

    /** 收款账户仓储 */
    protected final BeneficiaryAccountRepository beneficiaryAccountRepository;

    /**
     * 以时间源与仓储构造。
     *
     * @param clock 时间源
     * @param beneficiaryAccountRepository 收款账户仓储
     */
    protected AbstractBeneficiaryCommandProcessor(Clock clock, BeneficiaryAccountRepository beneficiaryAccountRepository) {
        this.clock = clock;
        this.beneficiaryAccountRepository = beneficiaryAccountRepository;
    }

    /**
     * 命令目标账户
     *
     * @param request 命令
     * @return 账户主键
     */
    protected abstract Long accountId(R request);

    /** 锁账户所属 Owner；账户不存在直接 404。 */
    @Override
    protected LockCommand lockCommand(R request) {
        BeneficiaryAccount account = loadAccount(accountId(request));
        return LockCommand.tryLock(PayoutLocks.beneficiaryOwner(account.getOwnerType(), account.getOwnerId()),
            PayoutLocks.WAIT_MILLIS, PayoutLocks.LEASE_MILLIS);
    }

    /** 读取账户，不存在为 404。 */
    BeneficiaryAccount loadAccount(Long beneficiaryAccountId) {
        BeneficiaryAccount account = beneficiaryAccountRepository.findById(beneficiaryAccountId);
        if (account == null) {
            throw notFound(beneficiaryAccountId);
        }
        return account;
    }

    /** 给出 Owner 时账户必须属于它，否则按不存在处理（不泄露其他 Owner 的账户）。 */
    static void checkOwner(BeneficiaryAccount account, BeneficiaryOwnerType ownerType, Long ownerId) {
        if (ownerType != null && !account.ownedBy(ownerType, ownerId)) {
            throw notFound(account.getId());
        }
    }

    /** Client 成员的命令必须给出其 Owner（由 Client BFF 取自当前工作区）。 */
    static void requireOwnerForClientMember(BeneficiaryActorType actorType, BeneficiaryOwnerType ownerType) {
        if (actorType == BeneficiaryActorType.CLIENT_MEMBER && ownerType == null) {
            throw new InvalidParameterException("Client 成员的操作必须给出 ownerType 与 ownerId");
        }
    }

    /** 期望版本必须等于当前版本。 */
    static void checkVersion(BeneficiaryAccount account, Long expectedVersion) {
        if (!account.getVersion().equals(expectedVersion)) {
            throw new ConcurrentRequestException("收款账户已变更，请刷新后重试: " + account.getId() + ", 当前版本 "
                + account.getVersion());
        }
    }

    /** 审阅的摘要必须等于当前冻结摘要（B3）。 */
    static void checkContentHash(BeneficiaryAccount account, String contentHash) {
        if (!account.getContentHash().equals(contentHash)) {
            throw new ConcurrentRequestException("审阅的内容摘要与当前账户不一致，请重新审阅: " + account.getId());
        }
    }

    /**
     * 原因代码必须属于给定受控目录
     *
     * @param reasonCode 请求的原因代码
     * @param catalog 受控目录
     * @param <E> 目录枚举
     * @return 原因代码
     */
    static <E extends Enum<E> & CodeEnum> String requireReason(String reasonCode, Class<E> catalog) {
        boolean known = reasonCode != null
            && Arrays.stream(catalog.getEnumConstants()).anyMatch(reason -> reason.getCode().equals(reasonCode));
        if (!known) {
            throw new InvalidParameterException("原因代码缺失或不在 " + catalog.getSimpleName() + " 目录中: " + reasonCode);
        }
        return reasonCode;
    }

    /** 不需要原因的命令不接受原因代码。 */
    static void rejectReason(String reasonCode) {
        if (reasonCode != null) {
            throw new InvalidParameterException("该操作不接受原因代码: " + reasonCode);
        }
    }

    /**
     * 规范化运营填写的文字：去首尾空白，空白视为未填写
     *
     * @param text 原文字
     * @return 规范化文字；未填写时为 null
     */
    static String normalizeNote(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    /** 不是拒绝的命令不接受给 Owner 的说明。 */
    static void rejectClientNote(String clientNote) {
        if (normalizeNote(clientNote) != null) {
            throw new InvalidParameterException("只有拒绝时可以填写给 Owner 的说明");
        }
    }

    /** 当前状态不允许该操作。 */
    static FailException statusConflict(BeneficiaryAccount account, String detail) {
        return new FailException(PayoutErrorCode.BENEFICIARY_STATUS_CONFLICT, detail + ": 收款账户 " + account.getId()
            + ", 状态 " + account.getStatus().getCode());
    }

    /** 账户不存在。 */
    static FailException notFound(Long beneficiaryAccountId) {
        return new FailException(PayoutErrorCode.BENEFICIARY_ACCOUNT_NOT_FOUND, "收款账户不存在: " + beneficiaryAccountId);
    }

    /** 命令结果视图：账户与实时可用判定。 */
    BeneficiaryAccountView view(BeneficiaryAccount account) {
        return new BeneficiaryAccountView(account, account.usableAt(now()), null, null);
    }

    /** 当前时刻。 */
    Instant now() {
        return clock.instant();
    }
}
