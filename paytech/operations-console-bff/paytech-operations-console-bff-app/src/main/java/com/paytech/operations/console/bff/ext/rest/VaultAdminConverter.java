package com.paytech.operations.console.bff.ext.rest;

import java.util.List;

import com.paytech.operations.console.bff.service.model.vaultadmin.CreateVaultAccessRuleRequestBody;
import com.paytech.operations.console.bff.service.model.vaultadmin.CreateVaultDataTypeRequestBody;
import com.paytech.operations.console.bff.service.model.vaultadmin.UpdateVaultAccessRuleRequestBody;
import com.paytech.operations.console.bff.service.model.vaultadmin.UpdateVaultDataTypeRequestBody;
import com.paytech.operations.console.bff.service.model.vaultadmin.VaultManualProtectRequestBody;
import com.paytech.operations.console.bff.service.model.vaultadmin.VaultManualRevealRequestBody;
import com.paytech.vault.client.admin.request.CreateAccessRuleRequestBody;
import com.paytech.vault.client.admin.request.CreateDataTypeRequestBody;
import com.paytech.vault.client.admin.request.ManualProtectRequestBody;
import com.paytech.vault.client.admin.request.ManualRevealRequestBody;
import com.paytech.vault.client.admin.request.UpdateAccessRuleRequestBody;
import com.paytech.vault.client.admin.request.UpdateDataTypeRequestBody;

/**
 * 浏览器请求到 Vault 管理请求的转换；人工加/解密允许的数据级别只由路由决定，不接受页面传入。
 */
final class VaultAdminConverter {
    /** 普通路由只允许普通敏感级别。 */
    static final List<String> STANDARD_ONLY = List.of("STANDARD");

    /** 密钥路由同时允许普通敏感与密钥级别。 */
    static final List<String> STANDARD_AND_SECRET = List.of("STANDARD", "SECRET");

    private VaultAdminConverter() {
    }

    /** 新建数据类型。 */
    static CreateDataTypeRequestBody toVault(CreateVaultDataTypeRequestBody body) {
        var vault = new CreateDataTypeRequestBody();
        vault.setDataTypeCode(body.getDataTypeCode());
        vault.setTokenMode(body.getTokenMode());
        vault.setDataClass(body.getDataClass());
        vault.setDescription(body.getDescription());
        vault.setValueFormat(body.getValueFormat());
        vault.setValuePattern(body.getValuePattern());
        vault.setSampleValues(body.getSampleValues());
        vault.setManualProtectEnabled(body.getManualProtectEnabled());
        vault.setManualRevealEnabled(body.getManualRevealEnabled());
        vault.setReason(body.getReason());
        return vault;
    }

    /** 修改数据类型。 */
    static UpdateDataTypeRequestBody toVault(UpdateVaultDataTypeRequestBody body) {
        var vault = new UpdateDataTypeRequestBody();
        vault.setExpectedVersion(body.getExpectedVersion());
        vault.setDataClass(body.getDataClass());
        vault.setDescription(body.getDescription());
        vault.setValueFormat(body.getValueFormat());
        vault.setValuePattern(body.getValuePattern());
        vault.setSampleValues(body.getSampleValues());
        vault.setManualProtectEnabled(body.getManualProtectEnabled());
        vault.setManualRevealEnabled(body.getManualRevealEnabled());
        vault.setStatus(body.getStatus());
        vault.setReason(body.getReason());
        return vault;
    }

    /** 新建访问规则。 */
    static CreateAccessRuleRequestBody toVault(CreateVaultAccessRuleRequestBody body) {
        var vault = new CreateAccessRuleRequestBody();
        vault.setCallerService(body.getCallerService());
        vault.setAction(body.getAction());
        vault.setDataTypeCode(body.getDataTypeCode());
        vault.setPurposeCode(body.getPurposeCode());
        vault.setReason(body.getReason());
        return vault;
    }

    /** 启停访问规则。 */
    static UpdateAccessRuleRequestBody toVault(UpdateVaultAccessRuleRequestBody body) {
        var vault = new UpdateAccessRuleRequestBody();
        vault.setExpectedVersion(body.getExpectedVersion());
        vault.setStatus(body.getStatus());
        vault.setReason(body.getReason());
        return vault;
    }

    /** 人工加密；allowedDataClasses 由调用路由给出。 */
    static ManualProtectRequestBody toVault(VaultManualProtectRequestBody body, List<String> allowedDataClasses) {
        var vault = new ManualProtectRequestBody();
        vault.setDataTypeCode(body.getDataTypeCode());
        vault.setValue(body.getValue());
        vault.setReasonCode(body.getReasonCode());
        vault.setTicketRef(body.getTicketRef());
        vault.setAllowedDataClasses(allowedDataClasses);
        return vault;
    }

    /** 人工解密；allowedDataClasses 由调用路由给出。 */
    static ManualRevealRequestBody toVault(VaultManualRevealRequestBody body, List<String> allowedDataClasses) {
        var vault = new ManualRevealRequestBody();
        vault.setToken(body.getToken());
        vault.setReasonCode(body.getReasonCode());
        vault.setTicketRef(body.getTicketRef());
        vault.setAllowedDataClasses(allowedDataClasses);
        return vault;
    }
}
