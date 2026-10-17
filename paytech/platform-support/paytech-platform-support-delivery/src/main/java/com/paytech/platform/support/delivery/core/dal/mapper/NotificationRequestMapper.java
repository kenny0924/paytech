package com.paytech.platform.support.delivery.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationRequest;
import com.paytech.platform.support.delivery.domain.NotificationRequestFilter;

/** 通知单表 SQL 绑定。 */
public interface NotificationRequestMapper {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationRequest find(@Param("id") long id);

    /** 插入新事实；唯一约束冲突使整个受理事务回滚。 */
    int insert(NotificationRequest item);

    /** 按运营筛选条件、创建时间倒序分页。 */
    List<NotificationRequest> page(@Param("filter") NotificationRequestFilter filter, @Param("offset") long offset,
        @Param("size") int size);

    /** 与分页相同条件的总数。 */
    long count(@Param("filter") NotificationRequestFilter filter);

    /** 查找来源系统内已受理的通知意图。 */
    NotificationRequest findSource(@Param("sourceSystem") String sourceSystem, @Param("sourceEventKey") String sourceEventKey);
}
