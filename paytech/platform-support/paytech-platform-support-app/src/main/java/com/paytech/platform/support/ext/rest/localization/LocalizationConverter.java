package com.paytech.platform.support.ext.rest.localization;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.api.localization.request.ActivateReleaseRequestBody;
import com.paytech.platform.support.api.localization.request.CreateApplicationRequestBody;
import com.paytech.platform.support.api.localization.request.CreateLocaleRequestBody;
import com.paytech.platform.support.api.localization.request.CreateNamespaceRequestBody;
import com.paytech.platform.support.api.localization.request.SaveTranslationsRequestBody;
import com.paytech.platform.support.api.localization.request.UpdateApplicationRequestBody;
import com.paytech.platform.support.api.localization.request.UpdateLocaleRequestBody;
import com.paytech.platform.support.api.localization.request.UpdateNamespaceRequestBody;
import com.paytech.platform.support.localization.core.enums.LocaleStatus;
import com.paytech.platform.support.localization.core.enums.TextDirection;
import com.paytech.platform.support.localization.core.enums.TranslationState;
import com.paytech.platform.support.localization.service.processor.request.ActivateReleaseCommand;
import com.paytech.platform.support.localization.service.processor.request.ApplicationCommand;
import com.paytech.platform.support.localization.service.processor.request.LocaleCommand;
import com.paytech.platform.support.localization.service.processor.request.NamespaceCommand;
import com.paytech.platform.support.localization.service.processor.request.SaveTranslationsCommand;
import com.paytech.platform.support.localization.service.processor.request.TranslationItem;

/** 多语言 HTTP 请求到内部命令的协议转换：Code 字符串转枚举、十进制 ID 与可选文本规范化。 */
@Component
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class LocalizationConverter {

    /** 新增语言。 */
    LocaleCommand createLocale(String actor, String session, CreateLocaleRequestBody body) {
        return new LocaleCommand(actor, session, body.getLocaleCode(), null, body.getNativeName().strip(),
            body.getEnglishName().strip(), code(TextDirection.class, body.getTextDirection(), "textDirection"), null,
            body.getSortOrder());
    }

    /** 修改语言。 */
    LocaleCommand updateLocale(String actor, String session, String localeCode, UpdateLocaleRequestBody body) {
        return new LocaleCommand(actor, session, localeCode, body.getExpectedVersion(), body.getNativeName().strip(),
            body.getEnglishName().strip(), code(TextDirection.class, body.getTextDirection(), "textDirection"),
            code(LocaleStatus.class, body.getStatus(), "status"), body.getSortOrder());
    }

    /** 新增应用。 */
    ApplicationCommand createApplication(String actor, String session, CreateApplicationRequestBody body) {
        return new ApplicationCommand(actor, session, body.getApplicationCode(), null, body.getApplicationName().strip(),
            body.getDefaultLocale(), List.copyOf(body.getRequiredLocales()), body.getCoverageThresholdPercent());
    }

    /** 修改应用。 */
    ApplicationCommand updateApplication(String actor, String session, String applicationCode,
        UpdateApplicationRequestBody body) {
        return new ApplicationCommand(actor, session, applicationCode, body.getExpectedVersion(),
            body.getApplicationName().strip(), body.getDefaultLocale(), List.copyOf(body.getRequiredLocales()),
            body.getCoverageThresholdPercent());
    }

    /** 新增命名空间。 */
    NamespaceCommand createNamespace(String actor, String session, String applicationCode,
        CreateNamespaceRequestBody body) {
        return new NamespaceCommand(actor, session, applicationCode, body.getNamespaceCode(), null,
            body.getNamespaceName().strip(), text(body.getDescription()), body.getSortOrder());
    }

    /** 修改命名空间。 */
    NamespaceCommand updateNamespace(String actor, String session, String applicationCode, String namespaceCode,
        UpdateNamespaceRequestBody body) {
        return new NamespaceCommand(actor, session, applicationCode, namespaceCode, body.getExpectedVersion(),
            body.getNamespaceName().strip(), text(body.getDescription()), body.getSortOrder());
    }

    /** 删除命名空间。 */
    NamespaceCommand deleteNamespace(String actor, String session, String applicationCode, String namespaceCode,
        Integer expectedVersion) {
        if (expectedVersion == null || expectedVersion < 1) {
            throw new InvalidParameterException("expectedVersion 必须为正整数");
        }
        return new NamespaceCommand(actor, session, applicationCode, namespaceCode, expectedVersion, null, null, 0);
    }

    /** 批量保存翻译；文案原样保留，不去除首尾空白。 */
    SaveTranslationsCommand saveTranslations(String actor, String session, String applicationCode,
        SaveTranslationsRequestBody body) {
        List<TranslationItem> items = body.getTranslations().stream()
            .map(item -> new TranslationItem(item.getKeyCode(), item.getText(), item.getExpectedVersion())).toList();
        return new SaveTranslationsCommand(actor, session, applicationCode, body.getLocale(), items);
    }

    /** 切换当前发布。 */
    ActivateReleaseCommand activateRelease(String actor, String session, String applicationCode,
        ActivateReleaseRequestBody body) {
        return new ActivateReleaseCommand(actor, session, applicationCode, body.getReleaseNo(),
            body.getReason().strip());
    }

    /** 可选的填写状态筛选。 */
    TranslationState state(String value) {
        return value == null || value.isBlank() ? null : code(TranslationState.class, value, "state");
    }

    /** 十进制正整数 ID；DTO 已按格式校验。 */
    static long id(String value, String field) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) {
            throw new InvalidParameterException(field + " 必须是十进制字符串 ID", invalid);
        }
    }

    /** 可选文本：去除首尾空白，空白视为 null。 */
    static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** 关键字：去除首尾空白，最长 64 字符；空白视为不筛选。 */
    static String keyword(String value) {
        String keyword = text(value);
        if (keyword != null && keyword.length() > 64) {
            throw new InvalidParameterException("keyword 不能超过 64 个字符");
        }
        return keyword;
    }

    /** 按稳定 Code 解析枚举；未知 Code 返回 400。 */
    static <E extends Enum<E> & CodeEnum> E code(Class<E> type, String value, String field) {
        for (E constant : type.getEnumConstants()) {
            if (constant.getCode().equals(value)) {
                return constant;
            }
        }
        throw new InvalidParameterException(field + " 取值不合法：" + value);
    }
}
