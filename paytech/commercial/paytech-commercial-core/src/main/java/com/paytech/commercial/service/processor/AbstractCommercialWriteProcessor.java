package com.paytech.commercial.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

/**
 * commercial 写入 Processor 的公共查询与版本判断：只提供读 BCR Root、比较期望版本与识别已完成的同版本状态推进
 * <li>入口事务检查、基础校验与资源锁由 Foundation AbstractLockProcessor 负责（LOCK，租约 -1）；本类不打开事务、不写库。</li>
 *
 * @param <Q> 内部请求
 * @param <R> 内部响应
 */
public abstract class AbstractCommercialWriteProcessor<Q, R> extends AbstractLockProcessor<Q, R> {
    protected final Clock clock;
    protected final BusinessChangeRecordRepository businessChangeRecordRepository;

    /**
     * 注入时钟与 BCR 仓储
     *
     * @param clock 业务时钟
     * @param businessChangeRecordRepository BCR 仓储
     */
    protected AbstractCommercialWriteProcessor(Clock clock, BusinessChangeRecordRepository businessChangeRecordRepository) {
        this.clock = clock;
        this.businessChangeRecordRepository = businessChangeRecordRepository;
    }

    /** 本次命令的业务时刻，截断到 DATETIME(6) 微秒。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * 读取聚合 BCR Root；业务行存在而 Root 缺失说明数据不一致
     *
     * @param rootType 聚合根类型
     * @param rootId 聚合根 ID
     * @return Root
     */
    BusinessChangeRecord getRoot(BusinessChangeRecordRecordType rootType, long rootId) {
        BusinessChangeRecord root = businessChangeRecordRepository.findByRecord(rootType, Long.toString(rootId));
        if (root == null || root.getParentHistoryId() != null) {
            throw new ErrorException("聚合缺少 BCR Root: " + rootType.getCode() + " " + rootId);
        }
        return root;
    }

    /**
     * 期望版本必须等于 Root 当前版本；写入时仍由 Root CAS 兜底
     *
     * @param root Root
     * @param expectedVersion 期望版本
     */
    static void checkVersion(BusinessChangeRecord root, long expectedVersion) {
        if (root.getCurrentVersion() != expectedVersion) {
            throw new ConcurrentRequestException("业务版本已变化，请刷新后重试: " + root.getRecordType().getCode() + " "
                + root.getRecordId() + "，当前版本 " + root.getCurrentVersion() + "，期望版本 " + expectedVersion);
        }
    }

    /**
     * 状态推进的原请求判断：Root 恰好比期望版本多一且已处于目标状态，说明同一推进已提交
     *
     * @param root Root
     * @param expectedVersion 期望版本
     * @param inTargetStatus 当前是否已处于目标状态
     * @return 是否按原请求直接响应
     */
    static boolean alreadyApplied(BusinessChangeRecord root, long expectedVersion, boolean inTargetStatus) {
        return inTargetStatus && root.getCurrentVersion() == expectedVersion + 1;
    }
}
