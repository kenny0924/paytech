package com.paytech.platform.support.localization.domainservice.service;

import java.time.Instant;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nRelease;
import com.paytech.platform.support.localization.domain.PublishContext;
import com.paytech.platform.support.localization.domain.ReleaseCheckTarget;

/**
 * 发布的本地保存（技术方案 3.3 第 2、3 步与回滚）；不访问外部能力，写方法须在调用方的本地事务内调用。
 * <li>所有写入先锁应用行，与文案写入、其他发布登记和当前发布切换串行。</li>
 */
public interface ReleaseService {

    /**
     * 读取待检查的发布与其语言包；只读，不开事务。
     *
     * @param releaseId 发布 ID
     * @return 发布与语言包；发布不存在时为 null
     */
    ReleaseCheckTarget loadCheckTarget(long releaseId);

    /**
     * 登记发布：核对文案修订号未变，插入 PENDING_CHECK 发布（含变化条数）、语言包与相对上一发布号的文案变化，并登记上线补偿任务。
     *
     * @param context 发布上下文
     * @throws com.paytech.foundation.common.error.FailException 文案修订号已变（I18N_CONTENT_CHANGED）
     */
    void registerRelease(PublishContext context);

    /**
     * 检查全部通过：PENDING_CHECK 变为 READY；应用当前没有发布或当前发布号更小时切换到它。
     *
     * @param release 待上线发布
     * @param now 本次时间
     * @return 是否切换了当前发布
     */
    boolean completeCheck(I18nRelease release, Instant now);

    /**
     * 检查被拒绝：PENDING_CHECK 变为 FAILED 并记录原因。
     *
     * @param release 发布
     * @param reason 失败原因
     * @param now 本次时间
     */
    void failCheck(I18nRelease release, String reason, Instant now);

    /**
     * 把应用当前发布切换到指定 READY 发布；已是当前发布时不写入。
     *
     * @param application 事务前读取的应用
     * @param release 目标发布
     * @param now 本次时间
     * @return 是否发生切换
     */
    boolean activateRelease(I18nApplication application, I18nRelease release, Instant now);
}
