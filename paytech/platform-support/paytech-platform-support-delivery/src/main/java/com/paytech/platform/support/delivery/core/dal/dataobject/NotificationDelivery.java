package com.paytech.platform.support.delivery.core.dal.dataobject;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import com.paytech.platform.support.delivery.core.enums.NotificationDeliveryStatus;

/** 通知领域持久化事实；仅在 Delivery 模块内使用。 */
@Getter
@Setter
public class NotificationDelivery {

    /** 独立投递标识。 */
    private Long deliveryId;

    /** 所属通知请求。 */
    private Long requestId;

    /** EMAIL_ADDRESS_V1 的稳定 Vault 引用，禁止日志输出。 */
    private String addressVaultToken;

    /** 可空语言覆盖。 */
    private String locale;

    /** 按参数名覆盖默认值的 JSON。 */
    private String parameters;

    /** 当前投递状态。 */
    private NotificationDeliveryStatus status;

    /** 已创建发送尝试数；查询不递增。 */
    private int attemptCount;

    /** 下次允许新建发送尝试的时刻；查询不使用。 */
    private Instant nextRetryTime;

    /** 投递状态的条件更新版本。 */
    private int version;

    /** UTC 微秒创建时间。 */
    private Instant createTime;

    /** UTC 微秒更新时间。 */
    private Instant updateTime;
}
