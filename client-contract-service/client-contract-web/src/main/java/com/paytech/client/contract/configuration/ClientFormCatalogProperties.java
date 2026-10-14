package com.paytech.client.contract.configuration;
import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import com.paytech.client.contract.domain.enums.ClientType;
/**
 * CCT 独占的通用表单目录。<li>不按国家区分组织类型或证件；签发国由客户从完整 ISO 国家中选择。</li>
 * <li>启动时冻结；修改后重启，不改变已保存资料。</li>
 * <li>每类最多 100 项。</li>
 */
@ConfigurationProperties("onboarding.form-catalog")
public record ClientFormCatalogProperties(List<OrganizationType> organizationTypes,
    List<BusinessModel> businessModels, List<IdentifierType> identifierTypes, List<Industry> industries) {
    /** 校验全部配置后冻结，避免半有效目录对外可用。 */
    public ClientFormCatalogProperties {
        organizationTypes=freeze(organizationTypes); businessModels=freeze(businessModels); identifierTypes=freeze(identifierTypes);
        industries=freeze(industries);
        unique(industries.stream().map(Industry::code).toList());
        unique(organizationTypes.stream().map(OrganizationType::code).toList());
        unique(businessModels.stream().map(BusinessModel::code).toList());
        unique(identifierTypes.stream().map(IdentifierType::code).toList());
    }
    /** 通用法律形态，不按注册国区分。 */
    public record OrganizationType(String code,String label) {
        /** 拒绝不明确的组织类型定义。 */
        public OrganizationType { text(code,64);text(label,128); }
    }
    /** 运营审核时选择的通用行业；MCC 不建目录，只校验四位数字。 */
    public record Industry(String code,String label) {
        /** 拒绝不明确的行业定义。 */
        public Industry { text(code,64);text(label,128); }
    }
    /** 经营模式不替代运营确认的行业或 MCC。 */
    public record BusinessModel(String code,String label,Set<ClientType> clientTypes) {
        /** 冻结适用客户类型。 */
        public BusinessModel { text(code,64);text(label,128);clientTypes=types(clientTypes); }
    }
    /**
     * 通用证件类型。
     * <li>issuingCountryMode=REQUIRED：须选择签发国（如护照、身份证）。</li>
     * <li>issuingCountryMode=NONE：全球通用标识，无签发国（如 LEI）。</li>
     */
    public record IdentifierType(String code,String label,Set<ClientType> clientTypes,String issuingCountryMode) {
        /** 拒绝不明确的证件定义。 */
        public IdentifierType {
            text(code,64);text(label,128);clientTypes=types(clientTypes);
            if(!"REQUIRED".equals(issuingCountryMode) && !"NONE".equals(issuingCountryMode)) { throw invalid(); }
        }
    }
    /** 集合有界且不可变；空目录合法但查询时不可用。 */
    static <T> List<T> freeze(List<T> value) {
        if(value==null) { return List.of(); }
        if(value.size()>100) { throw invalid(); }
        return List.copyOf(value);
    }
    /** 阻止重复配置产生有歧义的选择。 */
    static void unique(List<String> values) { if(new HashSet<>(values).size()!=values.size()) { throw invalid(); } }
    /** 类型必须来自 Client 闭合集合。 */
    static Set<ClientType> types(Set<ClientType> value) {
        if(value==null || value.isEmpty()) { throw invalid(); }
        return Set.copyOf(value);
    }
    /** 目录代码和名称不得携带不可见控制字符或空白边界。 */
    static void text(String value,int max) {
        if(value==null || value.isBlank() || !value.equals(value.strip()) || value.length()>max
            || value.codePoints().anyMatch(Character::isISOControl)) { throw invalid(); }
    }
    /** 配置异常不输出完整配置值。 */
    static IllegalArgumentException invalid() { return new IllegalArgumentException("CCT 表单目录配置无效或重复"); }
}
