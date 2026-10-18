package com.paytech.platform.support.delivery.service;

import java.time.Instant;

import com.paytech.platform.support.delivery.domain.ChangeWebhookEndpointStatusCommand;
import com.paytech.platform.support.delivery.domain.CreateWebhookEndpointCommand;
import com.paytech.platform.support.delivery.domain.ReplayWebhookDeliveryCommand;
import com.paytech.platform.support.delivery.domain.RotateWebhookSecretCommand;
import com.paytech.platform.support.delivery.domain.RotateWebhookSecretResult;
import com.paytech.platform.support.delivery.domain.UpdateWebhookEndpointCommand;
import com.paytech.platform.support.delivery.domain.WebhookConfigurationChange;
import com.paytech.platform.support.delivery.domain.WebhookSigningTarget;

/**
 * 运营维护端点、密钥与 Replay 的本地规则与写入
 * <li>参与调用方事务，不自行开启事务；只读写本库，Vault 与 HTTP 由调用方在事务外完成</li>
 */
public interface WebhookManagementService {

    /** 新建端点与首个 ACTIVE 密钥；超过每个 Client 的端点上限返回业务错误。 */
    WebhookConfigurationChange createEndpoint(CreateWebhookEndpointCommand command);

    /** 修改地址与订阅；没有变化返回业务错误，版本不符返回并发冲突。 */
    WebhookConfigurationChange updateEndpoint(UpdateWebhookEndpointCommand command);

    /** 启用或停用；状态未变化返回业务错误，版本不符返回并发冲突。 */
    WebhookConfigurationChange changeStatus(ChangeWebhookEndpointStatusCommand command);

    /** 宽限中的旧密钥立即截止，当前 ACTIVE 退役到指定时刻，插入新 ACTIVE 密钥。 */
    RotateWebhookSecretResult rotate(RotateWebhookSecretCommand command);

    /** 对已结束且端点仍启用、仍订阅的投递生成新投递并登记补偿任务，返回新投递 ID。 */
    long replay(ReplayWebhookDeliveryCommand command);

    /** 读取端点地址与当前可签名密钥引用；端点不存在返回 NOT_FOUND。 */
    WebhookSigningTarget signingTarget(long endpointId, Instant now);

    /** 端点所属租户；端点不存在返回 NOT_FOUND。 */
    long endpointTenant(long endpointId);
}
