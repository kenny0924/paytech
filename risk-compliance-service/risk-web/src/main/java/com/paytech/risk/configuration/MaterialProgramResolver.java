package com.paytech.risk.configuration;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.core.StreamReadFeature;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.risk.api.enums.MaterialQualificationType;
/** 有界的受控适用程序配置，默认没有国家材料规则。 */
@Component
public class MaterialProgramResolver {
    private final Environment environment;
    private final JsonMapper json;
    /** 单独严格解析此配置，不改变其他业务 JSON 规则。 */
    public MaterialProgramResolver(Environment environment,JsonMapper json) {
        this.environment=environment;this.json=json.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    }
    /** 明确映射必须唯一；未配置国家不继承其他国家程序。 */
    public String resolve(MaterialQualificationType type,String country) {
        String source=environment.getProperty("onboarding.materials.programs-json", "");
        try {
            if (source.isBlank() || source.length()>65536) throw new IllegalArgumentException();
            var rows=json.readTree(source);
            if (!rows.isArray() || rows.isEmpty() || rows.size()>512) throw new IllegalArgumentException();
            String match=null;var keys=new HashSet<String>();
            for (var row:rows) {
                if (!row.isObject() || !row.propertyNames().equals(Set.of("qualificationType","countryCode","programCode"))) throw new IllegalArgumentException();
                for (var value:row.properties()) if (!value.getValue().isString()) throw new IllegalArgumentException();
                String qualification=row.path("qualificationType").asText(),countryCode=row.path("countryCode").asText(),code=row.path("programCode").asText();
                MaterialQualificationType.fromCode(qualification);
                if (!countryCode.matches("[A-Z]{2}") || !code.matches("[A-Z][A-Z0-9_]{0,63}") || !keys.add(qualification+":"+countryCode)) throw new IllegalArgumentException();
                if (type.getCode().equals(qualification) && country.equals(countryCode)) match=code;
            }
            if (match==null) throw new IllegalArgumentException();
            return match;
        } catch (RuntimeException invalid) { throw new ServiceUnavailableException(); }
    }
}
