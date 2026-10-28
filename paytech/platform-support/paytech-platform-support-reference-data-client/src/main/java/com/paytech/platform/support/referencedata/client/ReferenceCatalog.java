package com.paytech.platform.support.referencedata.client;

import java.util.List;
import java.util.Optional;

/**
 * 一个种类的进程内参考数据：所有方法只读内存，不发 HTTP、不触发刷新，可在数据库事务内调用。
 * <li>该种类未在配置中开启，或尚未首次加载成功时，所有方法都抛 {@link ReferenceDataUnavailableException}；
 * {@link #find} 在未就绪时也不返回空，避免把未就绪误判为编码不存在。</li>
 *
 * @param <T> 条目类型
 */
public interface ReferenceCatalog<T extends ReferenceItem> {

    /**
     * 按编码读取，要求存在（任何状态）
     *
     * @param code 编码
     * @return 条目
     * @throws ReferenceDataNotFoundException 快照中没有该编码（系统异常，消息消费可重投）
     */
    T require(String code);

    /**
     * 按编码读取，要求存在且为 ACTIVE
     *
     * @param code 编码
     * @return ACTIVE 条目
     * @throws ReferenceDataNotFoundException 快照中没有该编码
     * @throws com.paytech.foundation.common.error.BizCheckFailException 状态不是 ACTIVE
     */
    T requireActive(String code);

    /**
     * 按编码读取，不存在时返回空，由调用方转换为自己的业务错误
     *
     * @param code 编码；null 视为不存在
     * @return 条目或空
     */
    Optional<T> find(String code);

    /**
     * 当前快照的全部条目
     *
     * @return 含 DRAFT / INACTIVE 的全部条目，按编码升序，不可修改
     */
    List<T> list();
}
