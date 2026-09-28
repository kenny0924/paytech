package com.paytech.platform.support.delivery.core.dal.mapper;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationTemplate;
import org.apache.ibatis.annotations.Param;

/** 通知单表 SQL 绑定。 */
public interface NotificationTemplateMapper {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationTemplate find(@Param("id") long id);

    /** 按唯一模板编码读取当前定义。 */
    NotificationTemplate findCode(@Param("code") String code);
}
