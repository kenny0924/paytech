package com.paytech.platform.support.workflow.core.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 工作流写入口的资源锁与通知回调投递参数（workflow.*）；启动时读取，修改需重启。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties("workflow")
public class WorkflowProperties {

    /**
     * TRY_LOCK 等待时间
     * <li>workflow-instance:{id}、workflow-definition:{id}、workflow-action-template:{id} 共用；默认 500ms。</li>
     */
    @NotNull
    private Duration lockWaitTime = Duration.ofMillis(500);

    /**
     * 资源锁租约
     * <li>默认 30s；版本 CAS 与条件更新仍为最终保护。</li>
     */
    @NotNull
    private Duration lockLeaseTime = Duration.ofSeconds(30);

    /**
     * 通知与回调的尝试上限
     * <li>达到上限置 FAILED 并保留，可人工重试；默认 5（技术方案 4.2）。</li>
     */
    @Min(1)
    @Max(20)
    private int callbackMaxAttempts = 5;

    /**
     * 工作流通知邮件模板编码
     * <li>Delivery 普通模板，需在注册 DML 中登记；默认 WORKFLOW_NOTIFICATION。</li>
     */
    @NotNull
    private String notificationTemplateCode = "WORKFLOW_NOTIFICATION";

    /**
     * 回调事件信封的 sourceService
     * <li>必须与 messaging.producer.service 一致；默认 platform-support。</li>
     */
    @NotNull
    @Pattern(regexp = "[a-z][a-z0-9-]{0,63}")
    private String eventSourceService = "platform-support";

    /**
     * 回调事件信封的 instanceId
     * <li>实例清单 instance.id，通常配置为 ${paytech.instance-id}；默认 main。</li>
     */
    @NotNull
    @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}")
    private String eventInstanceId = "main";
}
