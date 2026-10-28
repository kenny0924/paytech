package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPersonnel;

/** OPERATIONS 人员账号的窄查询。 */
public interface OperationsPersonnelRepository {

    /** 按账号批量读取；空集合直接返回空列表。 */
    List<OperationsPersonnel> findByIds(Collection<Long> ids);

    /**
     * 按账号批量读取显示名事实（含 Bootstrap 账号）。
     *
     * @param ids 账号，非空
     * @return 按账号升序的 OPERATIONS 账号，只含登录名与脱敏邮箱
     */
    List<OperationsPersonnel> findLabelsByIds(Collection<Long> ids);

    /**
     * 选人：ACTIVE 的 OPERATIONS 人员账号。
     *
     * @param fragment 关键字（登录名前缀或脱敏邮箱片段）；为空时不过滤
     * @param emailToken 登录邮箱稳定 Token；非空时只按它精确匹配
     * @param limit 最多条数
     * @return 人员账号
     */
    List<OperationsPersonnel> searchActive(String fragment, String emailToken, int limit);
}
