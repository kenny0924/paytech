package com.paytech.platform.support.delivery.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.delivery.core.configuration.NotificationProperties;
import com.paytech.platform.support.delivery.domain.NotificationInput;
import com.paytech.platform.support.delivery.domain.NotificationMessage;
import com.paytech.platform.support.delivery.domain.NotificationRouting;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationTemplateContentRepository;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderRequest;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderResult;
import com.paytech.platform.support.delivery.integration.provider.EmailProviders;
import com.paytech.platform.support.delivery.service.NotificationConfigurationService;
import com.paytech.platform.support.delivery.service.processor.request.SendTransientEmailRequest;
import com.paytech.platform.support.delivery.service.processor.response.SendTransientEmailResponse;

import lombok.RequiredArgsConstructor;

/**
 * 模块公开入口：即时邮件只发送一次，用于不允许持久化的临时内容（如认证链接）。
 * <li>没有事务、不写库、不登记补偿任务、不重试、不切备用账号；失败由调用方决定是否让用户重新发起</li>
 * <li>只接受 notification.transient-template-codes 中的模板，这些模板也不能经普通通知使用</li>
 * <li>Vault 与供应商调用都在事务外；邮箱、参数、正文不进入日志或结果</li>
 * <li>配置、Vault 与供应商异常原样抛出，调用方不得把原异常写入普通日志</li>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.enabled", havingValue = "true")
public class SendTransientEmailProcessor
    extends AbstractProcessor<SendTransientEmailRequest, SendTransientEmailResponse> {

    private final IdGenerator ids;
    private final EmailProviders adapters;
    private final NotificationProperties properties;
    private final NotificationConfigurationService configuration;
    private final NotificationTemplateContentRepository contents;

    /** 规范化邮箱与语言，临时参数只做有界检查。 */
    @Override
    protected void validate(SendTransientEmailRequest request) {
        NotificationInput.canonicalEmail(request.getEmail());
        NotificationInput.locale(request.getLocale());
        NotificationInput.transientParameters(request.getParameters());
    }

    @Override
    protected SendTransientEmailResponse processRequest(SendTransientEmailRequest request) {
        if (!properties.isTransientTemplate(request.getTemplateCode())) {
            throw new BizCheckFailException("该模板不允许即时发送");
        }
        var routing = configuration.resolve(request.getTemplateCode());
        var message = render(request, routing);
        var provider = adapters.require(routing.getPrimaryProvider());
        var sender = provider.prepare(routing.getPrimaryProvider());
        var providerRequest = new EmailProviderRequest(provider.requestNo(ids.nextId()),
            NotificationInput.canonicalEmail(request.getEmail()), message.getSubject(), message.getBody());
        return response(sender.send(providerRequest));
    }

    /** 读取精确语言内容并渲染；缺语言或缺参数在调用 Vault 前失败。 */
    NotificationMessage render(SendTransientEmailRequest request, NotificationRouting routing) {
        var content = contents.findLocale(routing.getTemplate().getTemplateId(),
            NotificationInput.locale(request.getLocale()));
        if (content == null) {
            throw new BizCheckFailException("邮件模板缺少所需语言");
        }
        return NotificationInput.render(content.getSubjectTemplate(), content.getBodyTemplate(),
            NotificationInput.transientParameters(request.getParameters()));
    }

    /** 只返回有界结果码；缺分类或结果码不合法时按未知处理。 */
    SendTransientEmailResponse response(EmailProviderResult result) {
        if (result.getOutcome() == null || result.getCode() == null
            || !result.getCode().matches("[A-Z][A-Z0-9_]{0,63}")) {
            return new SendTransientEmailResponse(EmailProviderResult.Outcome.UNKNOWN, "RESULT_UNKNOWN");
        }
        return new SendTransientEmailResponse(result.getOutcome(), result.getCode());
    }
}
