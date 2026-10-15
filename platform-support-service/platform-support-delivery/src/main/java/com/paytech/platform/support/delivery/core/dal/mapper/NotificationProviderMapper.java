package com.paytech.platform.support.delivery.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationProvider;
import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;

/** 通知单表 SQL 绑定。 */
public interface NotificationProviderMapper {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationProvider find(@Param("id") long id);

    /** 按唯一账号编码读取。 */
    NotificationProvider findCode(@Param("code") String code);

    /** 按身份批量读取。 */
    List<NotificationProvider> findIds(@Param("ids") List<Long> ids);

    /** 按编码升序分页。 */
    List<NotificationProvider> page(@Param("offset") long offset, @Param("size") int size);

    /** 账号总数。 */
    long count();

    /** 按原版本切换启停状态。 */
    int changeStatus(@Param("item") NotificationProvider item,
        @Param("status") NotificationConfigurationStatus status, @Param("now") Instant now);

    /** 插入新账号。 */
    int insert(@Param("item") NotificationProvider item);

    /** 按原版本修改区域、发件地址与 Configuration Set。 */
    int updateConfiguration(@Param("item") NotificationProvider item, @Param("region") String region,
        @Param("fromAddress") String fromAddress, @Param("configurationSet") String configurationSet,
        @Param("now") Instant now);

    /** 按原版本替换密钥 Token 与更换时间。 */
    int replaceCredential(@Param("item") NotificationProvider item, @Param("token") String token,
        @Param("now") Instant now);
}
