package com.paytech.platform.support.api.notificationmanagement.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 一个收件人的投递；不含邮箱与参数值。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryItem {

    /** 投递 ID。 */
    private String deliveryId;

    /**
     * 收件人语言
     * <li>为空表示继承请求默认语言。</li>
     */
    private String locale;

    /** 收件人覆盖参数的名称。 */
    private List<String> parameterKeys;

    /** 投递状态。 */
    private String status;

    /** 已创建尝试数。 */
    private int attemptCount;

    /** 下次重试时间，可空。 */
    private Instant nextRetryTime;

    /** 更新时间，UTC。 */
    private Instant updateTime;

    /** 发送尝试，按先后。 */
    private List<AttemptItem> attempts;
}
