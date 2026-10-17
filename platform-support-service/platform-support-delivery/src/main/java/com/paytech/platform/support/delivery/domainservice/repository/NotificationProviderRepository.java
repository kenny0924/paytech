package com.paytech.platform.support.delivery.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationProvider;
import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;

/** NotificationProvider 单表仓储。 */
public interface NotificationProviderRepository {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationProvider find(long id);

    /** 按唯一账号编码读取，缺失返回空。 */
    NotificationProvider findCode(String code);

    /** 按身份批量读取；空集合直接返回空列表。 */
    List<NotificationProvider> findIds(List<Long> ids);

    /** 按编码升序分页。 */
    List<NotificationProvider> page(long offset, int size);

    /** 账号总数。 */
    long count();

    /** 按原版本和原状态切换启停；成功后同步原对象。 */
    void changeStatus(NotificationProvider item, NotificationConfigurationStatus status, Instant now);

    /** 插入新账号；编码重复由唯一索引拒绝。 */
    void insert(NotificationProvider item);

    /** 按原版本修改区域、发件地址、Configuration Set 与事件队列；成功后同步原对象。 */
    void updateConfiguration(NotificationProvider item, String region, String fromAddress, String configurationSet,
        String eventQueueUrl, Instant now);

    /** 按原版本替换密钥 Token；成功后同步原对象。 */
    void replaceCredential(NotificationProvider item, String token, Instant now);

    /** 指定服务商类型下已配置事件队列且已录入密钥的账号，按编码升序。 */
    List<NotificationProvider> findEventSources(String providerType);

    /**
     * 记录一轮事件拉取的运行状态
     * <li>error 为空表示成功：写最近拉取时间并清空错误；否则只写错误码</li>
     * <li>received 为真时写最近收到事件时间；不推进版本、不改更新时间，账号已不存在时忽略</li>
     */
    void recordEventPoll(long providerId, String error, boolean received, Instant now);
}
