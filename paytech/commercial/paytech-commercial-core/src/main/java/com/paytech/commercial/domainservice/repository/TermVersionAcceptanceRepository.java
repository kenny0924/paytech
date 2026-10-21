package com.paytech.commercial.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.TermVersionAcceptance;

/** t_term_version_acceptance 的本地仓储；签署记录追加式，登记后不修改。 */
public interface TermVersionAcceptanceRepository {
    /**
     * 插入签署记录；uk(term_version_id) 或 uk(acceptance_reference) 冲突时抛出 DuplicateKeyException。
     *
     * @param acceptance 已准备 ID 的新行
     */
    void insert(TermVersionAcceptance acceptance);

    /**
     * 按签署编号读取。
     *
     * @param acceptanceReference 签署编号
     * @return 签署记录，不存在为 null
     */
    TermVersionAcceptance findByReference(String acceptanceReference);

    /**
     * 读取一组版本的签署记录；空集合不访问数据库。
     *
     * @param termVersionIds 版本 ID
     * @return 按 term_version_id 升序
     */
    List<TermVersionAcceptance> findByVersionIds(Collection<Long> termVersionIds);
}
