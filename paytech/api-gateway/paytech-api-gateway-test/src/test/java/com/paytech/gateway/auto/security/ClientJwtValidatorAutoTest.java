package com.paytech.gateway.auto.security;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;
import com.paytech.gateway.security.ClientJwtValidator;
import com.paytech.gateway.security.ClientSecurityProperties;
import static org.junit.jupiter.api.Assertions.*;
/** CUSTOMER/CLIENT 独立信任域与 ACCOUNT/TENANT 上下文矩阵。 */
class ClientJwtValidatorAutoTest {
    static final Instant NOW=Instant.parse("2026-09-15T00:00:00Z");
    static Jwt.Builder token() {
        return Jwt.withTokenValue("fixture").header("alg","RS256").issuer("https://customer.example.test")
            .subject("1").audience(List.of("client-console")).issuedAt(NOW.minusSeconds(10)).notBefore(NOW.minusSeconds(10))
            .expiresAt(NOW.plusSeconds(800)).claim("token_use","ACCESS").claim("realm","CUSTOMER")
            .claim("channel","CLIENT").claim("actor_type","PLATFORM_USER").claim("sid","2").claim("jti","3").claim("context_type","ACCOUNT");
    }
    static ClientJwtValidator validator() { return new ClientJwtValidator(new ClientSecurityProperties("https://customer.example.test","client-console"),Clock.fixed(NOW,ZoneOffset.UTC)); }
    @Test void accountContextCannotCarryTenant() {
        assertFalse(validator().validate(token().build()).hasErrors());
        for(String claim:List.of("tenant_id","tenant_type","tenant_member_id")) {
            assertTrue(validator().validate(token().claim(claim,"1").build()).hasErrors());
        }
    }
    @Test void tenantContextRequiresClientTenantAndMember() {
        Jwt.Builder valid=token().claim("context_type","TENANT").claim("tenant_id","4").claim("tenant_type","CLIENT").claim("tenant_member_id","5");
        assertFalse(validator().validate(valid.build()).hasErrors());
        assertTrue(validator().validate(valid.claim("tenant_type","PARTNER").build()).hasErrors());
    }
    @ParameterizedTest @CsvSource({"realm,OPERATIONS","channel,PARTNER","channel,OPERATIONS","actor_type,CLIENT","context_type,BOOTSTRAP","token_use,REFRESH","sub,01","sid,-1"})
    void foreignOrMalformedClaimsRejected(String claim,String value) {
        assertTrue(validator().validate(token().claim(claim,value).build()).hasErrors());
    }
    @Test void issuerAudienceAndLifetimeAreExact() {
        assertTrue(validator().validate(token().issuer("https://operations.example.test").build()).hasErrors());
        assertTrue(validator().validate(token().audience(List.of("client-console","partner-console")).build()).hasErrors());
        assertTrue(validator().validate(token().expiresAt(NOW.plusSeconds(901)).build()).hasErrors());
    }

    /** 时间声明不可省略，不能倒序、生效即过期或远期签发。 */
    @ParameterizedTest @ValueSource(strings={"iat","nbf","exp"})
    void missingTimeClaimIsRejected(String claim) {
        assertTrue(validator().validate(token().claims(claims -> claims.remove(claim)).build()).hasErrors());
    }

    @Test void invalidTimeOrderingAndClockSkewAreRejected() {
        assertTrue(validator().validate(token().issuedAt(NOW).notBefore(NOW.minusSeconds(1)).build()).hasErrors());
        assertTrue(validator().validate(token().notBefore(NOW.plusSeconds(800)).build()).hasErrors());
        assertTrue(validator().validate(token().issuedAt(NOW.plusSeconds(31)).notBefore(NOW.plusSeconds(31)).build()).hasErrors());
        assertFalse(validator().validate(token().issuedAt(NOW.plusSeconds(30)).notBefore(NOW.plusSeconds(30)).build()).hasErrors());
    }

    /** JWT 身份是规范正数 BIGINT 字符串；不能接受 JSON 数值或溢出值。 */
    @Test void identityTypesAndLongBoundaryAreEnforced() {
        for (String claim : List.of("sub", "sid", "jti")) {
            assertTrue(validator().validate(token().claims(claims -> claims.remove(claim)).build()).hasErrors());
            assertTrue(validator().validate(token().claim(claim, 123L).build()).hasErrors());
            assertTrue(validator().validate(token().claim(claim, "9223372036854775808").build()).hasErrors());
            assertFalse(validator().validate(token().claim(claim, "9223372036854775807").build()).hasErrors());
        }
        for (String claim : List.of("tenant_id", "tenant_member_id")) {
            var invalid = token().claim("context_type", "TENANT").claim("tenant_type", "CLIENT")
                .claim("tenant_id", "4").claim("tenant_member_id", "5").claims(claims -> claims.remove(claim));
            assertTrue(validator().validate(invalid.build()).hasErrors());
        }
    }

    /** 部署不能把 CLIENT 接到另一个 Audience 或带用户信息、查询参数的 issuer。 */
    @ParameterizedTest
    @ValueSource(strings={"http://customer.test","https:/customer.test","https://user@customer.test",
        "https://customer.test?query","https://customer.test#fragment"})
    void unsafeIssuerConfigurationIsRejected(String issuer) {
        assertThrows(IllegalArgumentException.class, () -> new ClientSecurityProperties(issuer,"client-console"));
    }

    @Test void audienceConfigurationCannotBeOperationsOrPartner() {
        assertThrows(IllegalArgumentException.class, () -> new ClientSecurityProperties("https://customer.test","operations-console"));
        assertThrows(IllegalArgumentException.class, () -> new ClientSecurityProperties("https://customer.test","partner-console"));
    }
}
