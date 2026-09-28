package com.paytech.platform.support.delivery.core.dal.mapper;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationRequest;
import org.apache.ibatis.annotations.Param;

/** 通知单表 SQL 绑定。 */
public interface NotificationRequestMapper {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationRequest find(@Param("id") long id);

    /** 插入新事实；唯一约束冲突使整个受理事务回滚。 */
    int insert(NotificationRequest item);

    /** 查找来源系统内已受理的通知意图。 */
    NotificationRequest findSource(@Param("sourceSystem") String sourceSystem, @Param("sourceEventKey") String sourceEventKey);
}
