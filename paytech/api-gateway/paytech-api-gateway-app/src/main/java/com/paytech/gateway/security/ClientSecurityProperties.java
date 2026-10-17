package com.paytech.gateway.security;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
/** CUSTOMER 的 issuer 必须与 Operations 隔离，CLIENT 的 Audience 固定。 */
@ConfigurationProperties("gateway.client.security")
public record ClientSecurityProperties(String issuer,String audience) {
    public ClientSecurityProperties {
        URI uri=URI.create(issuer);
        if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
            || uri.getQuery()!=null || uri.getFragment()!=null || !"client-console".equals(audience)) {
            throw new IllegalArgumentException("Client JWT 必须配置独立 HTTPS issuer 和 client-console audience");
        }
    }
}
