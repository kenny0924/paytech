package com.paytech.channel.connector.protocol.service.processor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.channel.connector.protocol.core.enums.ConfigurationField;
import com.paytech.channel.connector.protocol.domain.ConfigurationFieldChange;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyAccount;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyCase;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ValidationIssue;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelVerifyAccountRepository;
import com.paytech.channel.connector.protocol.domainservice.validator.VerifyCaseValidator;

import lombok.AllArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * 验证用例保存与校验共用的步骤检查：账号必须存在，步骤经 {@link VerifyCaseValidator} 静态校验。
 * 实现合同通过执行快照加载器只读本库，不调用外部服务。
 */
@Component
@AllArgsConstructor
public class VerifyCaseChecks {

    /** 步骤序列化后的上限字节数。 */
    static final int MAX_STEPS_BYTES = 65_536;

    private final VerifySupport verify;
    private final VerifyCaseValidator validator;
    private final ChannelVerifyAccountRepository accounts;

    /** 账号必须存在于本渠道。 */
    ChannelVerifyAccount account(long channelId, String accountCode) {
        ChannelVerifyAccount account = accounts.byCode(channelId, accountCode);
        if (account == null) {
            throw new BizCheckFailException("验证账号不存在：" + accountCode);
        }
        return account;
    }

    /** 步骤的全部问题，位置以 steps 开头。 */
    List<ValidationIssue> issues(String integrationCode, ChannelVerifyAccount account, JsonNode steps) {
        var issues = new ArrayList<ValidationIssue>();
        String text = verify.text(steps);
        if (text != null && text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_STEPS_BYTES) {
            issues.add(new ValidationIssue("STEPS_TOO_LARGE", "steps", "步骤序列化后不能超过 65536 字节"));
            return issues;
        }
        issues.addAll(validator.validate(steps, verify.contracts(integrationCode, steps), verify.sampleScope(account)));
        return issues;
    }

    /** 用例变更：编码、名称、说明、账号与步骤摘要。 */
    static List<ConfigurationFieldChange> changes(ChannelVerifyCase before, String beforeAccount,
        ChannelVerifyCase after, String afterAccount, ExternalApiConfigurationCodec codec) {
        return List.of(
            new ConfigurationFieldChange(ConfigurationField.CODE, before == null ? null : before.getCaseCode(),
                after == null ? null : after.getCaseCode()),
            new ConfigurationFieldChange(ConfigurationField.DISPLAY_NAME, before == null ? null : before.getCaseName(),
                after == null ? null : after.getCaseName()),
            new ConfigurationFieldChange(ConfigurationField.DESCRIPTION, before == null ? null : before.getDescription(),
                after == null ? null : after.getDescription()),
            new ConfigurationFieldChange(ConfigurationField.TARGET, beforeAccount, afterAccount),
            new ConfigurationFieldChange(ConfigurationField.DEFINITION_DIGEST,
                before == null ? null : codec.sha256(before.getStepsJson()),
                after == null ? null : codec.sha256(after.getStepsJson())));
    }
}
