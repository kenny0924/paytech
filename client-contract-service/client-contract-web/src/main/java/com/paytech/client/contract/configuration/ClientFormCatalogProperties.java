package com.paytech.client.contract.configuration;
import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import com.paytech.client.contract.domain.enums.ClientType;
/**
 * CCT 独占的表单目录。<li>默认无配置，不推断国家政策。</li>
 * <li>启动时冻结；修改后重启，不改变已保存资料。</li>
 * <li>每类最多 100 项，每种证件最多 50 个签发机构，总计最多 300 个。</li>
 */
@ConfigurationProperties("onboarding.form-catalog")
public record ClientFormCatalogProperties(List<OrganizationType> organizationTypes,
    List<BusinessModel> businessModels, List<IdentifierType> identifierTypes) {
    /** 校验全部配置后冻结，避免半有效目录对外可用。 */
    public ClientFormCatalogProperties {
        organizationTypes=freeze(organizationTypes); businessModels=freeze(businessModels); identifierTypes=freeze(identifierTypes);
        unique(organizationTypes.stream().map(v -> v.countryCode()+":"+v.code()).toList());
        unique(businessModels.stream().map(BusinessModel::code).toList());
        unique(identifierTypes.stream().map(IdentifierType::code).toList());
        if(identifierTypes.stream().mapToInt(v -> v.issuers().size()).sum()>300) { throw invalid(); }
    }
    /** 注册国目录明确法律形态，不从通用英文简称生成。 */
    public record OrganizationType(String code,String label,String countryCode) {
        /** 拒绝不明确的组织类型定义。 */
        public OrganizationType { text(code,64);text(label,128);country(countryCode); }
    }
    /** 经营模式不替代运营确认的行业或 MCC。 */
    public record BusinessModel(String code,String label,Set<ClientType> clientTypes) {
        /** 冻结适用客户类型。 */
        public BusinessModel { text(code,64);text(label,128);clientTypes=types(clientTypes); }
    }
    /** 每种证件保留完整签发范围，不能按居住国裁剪。 */
    public record IdentifierType(String code,String label,Set<ClientType> clientTypes,List<Issuer> issuers) {
        /** 同类型同作用域只允许一个明确签发定义。 */
        public IdentifierType {
            text(code,64);text(label,128);clientTypes=types(clientTypes);issuers=freeze(issuers);
            if(issuers.isEmpty() || issuers.size()>50) { throw invalid(); }
            unique(issuers.stream().map(Issuer::scopeKey).toList());
        }
    }
    /** GLOBAL 仅能由显式目录提供，浏览器不能据国家拼造作用域。 */
    public record Issuer(String scopeKey,String label,String issuingCountry) {
        /** 确保 GLOBAL 与国家签发两种范围互斥。 */
        public Issuer {
            text(scopeKey,64);text(label,128);
            if("GLOBAL".equals(scopeKey)) { if(issuingCountry!=null) { throw invalid(); } }
            else { country(issuingCountry); }
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
    /** 国家语法与现行字段合同保持一致，不声称该国家已经准入。 */
    static void country(String value) { if(value==null || !value.matches("[A-Z]{2}")) { throw invalid(); } }
    /** 配置异常不输出完整配置值。 */
    static IllegalArgumentException invalid() { return new IllegalArgumentException("CCT 表单目录配置无效或重复"); }
}
