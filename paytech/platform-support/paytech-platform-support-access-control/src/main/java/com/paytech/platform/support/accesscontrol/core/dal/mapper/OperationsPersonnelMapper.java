package com.paytech.platform.support.accesscontrol.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPersonnel;

/** OPERATIONS 人员账号的窄查询，SQL 位于 mapper/access-control/OperationsPersonnelMapper.xml。 */
public interface OperationsPersonnelMapper {

    /** 按账号批量读取人员账号（不含 Bootstrap 与非 OPERATIONS 账号）。 */
    List<OperationsPersonnel> selectByIds(@Param("ids") Collection<Long> ids);

    /**
     * 按账号批量读取显示名事实：OPERATIONS Realm 的全部账号（含 Bootstrap），只含登录名与脱敏邮箱。
     *
     * @param ids 账号，非空
     * @return 按账号升序的行；不存在或不是 OPERATIONS Realm 的账号不在结果中
     */
    List<OperationsPersonnel> selectLabelsByIds(@Param("ids") Collection<Long> ids);

    /**
     * 选人：ACTIVE 的 OPERATIONS 人员账号，按登录名排序。
     *
     * @param fragment 已转义 LIKE 通配符的关键字；为空时不过滤
     * @param emailToken 登录邮箱稳定 Token；非空时只按它精确匹配
     * @param limit 最多条数
     * @return 人员账号（不含 Token）
     */
    List<OperationsPersonnel> searchActive(@Param("fragment") String fragment, @Param("emailToken") String emailToken,
        @Param("limit") int limit);
}
