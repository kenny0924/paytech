package com.paytech.risk.control.service.processor;

import java.time.Duration;
import java.time.Instant;

import com.paytech.risk.control.core.dal.dataobject.ClientUnderwriting;
import com.paytech.risk.control.core.enums.UnderwritingStatus;
import com.paytech.risk.control.domain.underwriting.UnderwritingContent;
import com.paytech.risk.control.domain.underwriting.UnderwritingException;
import com.paytech.risk.control.domain.underwriting.UnderwritingException.Reason;
import com.paytech.risk.control.domainservice.repository.ClientUnderwritingRepository;

/**
 * 承保写入口共用的资源锁与前置检查
 * <li>同一 Client 的建草稿、保存、提交、复核、自动承保与修订共用一把锁；Key 只含 Client ID。</li>
 * <li>锁只减少竞争，最终由版本 CAS 与进行中 / 有效槽位唯一索引保护。</li>
 */
final class UnderwritingLocks {
    /** 锁等待时长。 */
    static final Duration WAIT_TIME = Duration.ofMillis(500);

    /** 锁租约。 */
    static final Duration LEASE_TIME = Duration.ofSeconds(30);

    /** 纯工具。 */
    private UnderwritingLocks() { }

    /** Client 承保锁 Key。 */
    static String client(long clientId) {
        return "risk-underwriting:client:" + clientId;
    }

    /** 版本行必须存在；ID 不存在返回 NOT_FOUND。 */
    static ClientUnderwriting requireUnderwriting(ClientUnderwritingRepository underwritings, long underwritingId) {
        return underwritings.findById(underwritingId)
            .orElseThrow(() -> new UnderwritingException(Reason.NOT_FOUND, "承保记录不存在: " + underwritingId));
    }

    /** 版本行须处于期望状态。 */
    static void requireStatus(ClientUnderwriting row, UnderwritingStatus expected) {
        if (row.getStatus() != expected) {
            throw new UnderwritingException(Reason.STATE_INVALID,
                "承保 " + row.getUnderwritingId() + " 当前为 " + row.getStatus().getCode() + "，不是 " + expected.getCode());
        }
    }

    /** 调用方看到的版本须是当前版本。 */
    static void requireVersion(ClientUnderwriting row, int expectedVersion) {
        if (row.getVersion() != expectedVersion) {
            throw new UnderwritingException(Reason.VERSION_CONFLICT,
                "承保 " + row.getUnderwritingId() + " 已被修改，当前版本 " + row.getVersion() + "，期望 " + expectedVersion);
        }
    }

    /** 同一 Client 不能已有进行中的承保。 */
    static void requireNoOpen(ClientUnderwritingRepository underwritings, long clientId) {
        underwritings.findOpen(clientId).ifPresent(open -> {
            throw new UnderwritingException(Reason.OPEN_EXISTS,
                "Client " + clientId + " 已有进行中的承保: " + open.getUnderwritingId());
        });
    }

    /** 下一版本号：最新版本号加一，首个版本为 1。 */
    static int nextVersionNo(ClientUnderwritingRepository underwritings, long clientId) {
        return underwritings.findLatest(clientId).map(latest -> latest.getVersionNo() + 1).orElse(1);
    }

    /**
     * 构造新版本行（草稿或自动承保），内容、版本号与审计时间已就绪
     *
     * @param underwritingId 新版本 ID
     * @param clientId Client ID
     * @param versionNo 版本号
     * @param content 已校验的内容
     * @param createdBy 创建人
     * @param now 创建时间
     * @return 进行中占位的 DRAFT 行；自动承保由调用方再改为 APPROVED
     */
    static ClientUnderwriting newDraft(long underwritingId, long clientId, int versionNo, UnderwritingContent content,
        long createdBy, Instant now) {
        ClientUnderwriting row = new ClientUnderwriting();
        row.setUnderwritingId(underwritingId);
        row.setClientId(clientId);
        row.setVersionNo(versionNo);
        row.setStatus(UnderwritingStatus.DRAFT);
        row.setOpenSlot(Boolean.TRUE);
        content.applyTo(row);
        row.setCreatedBy(createdBy);
        row.setVersion(1);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }
}
