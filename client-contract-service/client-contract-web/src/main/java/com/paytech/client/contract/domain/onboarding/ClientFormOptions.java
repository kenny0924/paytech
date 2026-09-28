package com.paytech.client.contract.domain.onboarding;
import java.util.List;
import com.paytech.client.contract.api.enums.ClientFormCatalogStatus;
/** 单次读取的不可变目录投影；资料国家与签发机构维度分开。 */
public record ClientFormOptions(ClientFormCatalogStatus status, List<Option> organizationTypes,
    List<Option> businessModels, List<Identifier> identifierTypes) {
    /** 受控代码及展示名称。 */
    public record Option(String code, String label) {}
    /** 签发作用域独立于申请的国家选择。 */
    public record Issuer(String scopeKey, String label, String issuingCountry) {}
    /** 证件类型及全部批准签发作用域。 */
    public record Identifier(String code, String label, List<Issuer> issuers) {}
}
