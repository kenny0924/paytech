package com.paytech.client.master.domain.onboarding;
import java.util.List;
import com.paytech.client.contract.api.enums.ClientFormCatalogStatus;
/** 单次读取的不可变通用目录投影，不按国家区分。 */
public record ClientFormOptions(ClientFormCatalogStatus status, List<Option> organizationTypes,
    List<Option> businessModels, List<Identifier> identifierTypes) {
    /** 受控代码及展示名称。 */
    public record Option(String code, String label) {}
    /** 证件类型及签发国要求：REQUIRED 或 NONE。 */
    public record Identifier(String code, String label, String issuingCountryMode) {}
}
