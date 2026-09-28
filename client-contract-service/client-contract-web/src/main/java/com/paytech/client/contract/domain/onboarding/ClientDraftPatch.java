package com.paytech.client.contract.domain.onboarding;
import java.net.URI;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.DraftPatchRequestBody;
import com.paytech.client.contract.domain.enums.ClientType;
import com.paytech.foundation.common.error.FailException;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
/** 草稿只校验已修改字段的语法和存储边界；完整性和稳定身份绑定留给正式提交。 */
public final class ClientDraftPatch {
    private enum Kind { TEXT, DATE, BOUNDARY, COUNTRY, CURRENCY, URL, MONEY, EMAIL, PHONE }
    private record Field(Kind kind, int length) {}
    private record EmailValue(@Email String value) {}
    private static final Map<String,Field> FIELDS=Map.ofEntries(
        Map.entry("individual.givenName", new Field(Kind.TEXT,64)),
        Map.entry("individual.middleName", new Field(Kind.TEXT,64)),
        Map.entry("individual.familyName", new Field(Kind.TEXT,64)),
        Map.entry("individual.birthDate", new Field(Kind.DATE,10)),
        Map.entry("individual.nationalityCountry", new Field(Kind.COUNTRY,2)),
        Map.entry("individual.residenceCountry", new Field(Kind.COUNTRY,2)),
        Map.entry("organization.legalName", new Field(Kind.TEXT,64)),
        Map.entry("organization.tradeName", new Field(Kind.TEXT,64)),
        Map.entry("organization.incorporationCountry", new Field(Kind.COUNTRY,2)),
        Map.entry("organization.organizationType", new Field(Kind.TEXT,64)),
        Map.entry("organization.incorporationDate", new Field(Kind.DATE,10)),
        Map.entry("address.addressLine1", new Field(Kind.TEXT,255)),
        Map.entry("address.addressLine2", new Field(Kind.TEXT,255)),
        Map.entry("address.city", new Field(Kind.TEXT,128)),
        Map.entry("address.stateOrProvince", new Field(Kind.TEXT,128)),
        Map.entry("address.postalCode", new Field(Kind.TEXT,32)),
        Map.entry("address.countryCode", new Field(Kind.COUNTRY,2)),
        Map.entry("identifier.identifierType", new Field(Kind.TEXT,64)),
        Map.entry("identifier.issuingCountry", new Field(Kind.COUNTRY,2)),
        Map.entry("identifier.issuerScopeKey", new Field(Kind.TEXT,64)),
        Map.entry("identifier.identifierValue", new Field(Kind.TEXT,128)),
        Map.entry("identifier.validFrom", new Field(Kind.BOUNDARY,10)),
        Map.entry("identifier.validTo", new Field(Kind.BOUNDARY,10)),
        Map.entry("business.businessModelCode", new Field(Kind.TEXT,64)),
        Map.entry("business.websiteUrl", new Field(Kind.URL,2048)),
        Map.entry("business.businessDescription", new Field(Kind.TEXT,2000)),
        Map.entry("business.expectedVolume", new Field(Kind.MONEY,25)),
        Map.entry("business.expectedTicket", new Field(Kind.MONEY,25)),
        Map.entry("business.currency", new Field(Kind.CURRENCY,3)),
        Map.entry("contact.email", new Field(Kind.EMAIL,320)),
        Map.entry("contact.phone", new Field(Kind.PHONE,32)));
    private final Map<String,String> values;
    private ClientDraftPatch(Map<String,String> values) { this.values=Collections.unmodifiableMap(values); }
    public Map<String,String> values() { return values; }
    public static Set<String> paths() { return FIELDS.keySet(); }
    public static ClientDraftPatch normalize(DraftPatchRequestBody request, Validator validator) {
        if (request==null || request.dirtyFields()==null || request.values()==null
            || request.dirtyFields().isEmpty() || request.dirtyFields().size()>50) throw invalid();
        var dirty=new HashSet<>(request.dirtyFields());
        if (dirty.size()!=request.dirtyFields().size() || !FIELDS.keySet().containsAll(dirty)) throw invalid();
        Map<String,String> result=new TreeMap<>();
        for (var group:request.values().entrySet()) {
            if (group.getValue()==null || group.getValue().isEmpty()) throw invalid();
            for (var field:group.getValue().entrySet()) {
                String path=group.getKey()+"."+field.getKey();
                if (!dirty.contains(path)) throw invalid();
                result.put(path,normalize(FIELDS.get(path),field.getValue(),validator));
            }
        }
        if (!result.keySet().equals(dirty)) throw invalid();
        return new ClientDraftPatch(result);
    }
    public void requirePerson() {
        if (values.keySet().stream().anyMatch(path -> !(path.startsWith("individual.")
            || path.startsWith("address.") || path.startsWith("identifier.")))) throw invalid();
    }
    public void requireType(ClientType type) {
        String forbidden=type==ClientType.INDIVIDUAL?"organization.":"individual.";
        if (values.keySet().stream().anyMatch(path -> path.startsWith(forbidden))) throw invalid();
    }
    public Map<String,Object> databaseValues() {
        Map<String,Object> result=new HashMap<>();
        values.forEach((path,value) -> result.put(path.replace('.','_'), value==null?null:switch(FIELDS.get(path).kind()) {
            case DATE -> LocalDate.parse(value);
            case BOUNDARY -> LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC);
            case MONEY -> new BigDecimal(value);
            default -> value;
        }));
        return result;
    }
    private static String normalize(Field field,String raw,Validator validator) {
        if (raw==null) return null;
        String value=raw.strip();
        if (value.isEmpty()) return null;
        if (value.indexOf(0)>=0 || value.codePointCount(0,value.length())>field.length()) throw invalid();
        try {
            return switch(field.kind()) {
                case COUNTRY, CURRENCY -> {
                    String code=value.toUpperCase(Locale.ROOT);
                    if (!code.matches(field.kind()==Kind.COUNTRY?"[A-Z]{2}":"[A-Z]{3}")) throw invalid();
                    yield code;
                }
                case DATE, BOUNDARY -> {
                    if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || LocalDate.parse(value).getYear()<1000) throw invalid();
                    yield value;
                }
                case MONEY -> {
                    if (!value.matches("[0-9]+(\\.[0-9]{1,8})?")) throw invalid();
                    BigDecimal number=new BigDecimal(value);
                    if (number.precision()-number.scale()>16) throw invalid();
                    yield number.stripTrailingZeros().toPlainString();
                }
                case EMAIL -> {
                    String email=value.toLowerCase(Locale.ROOT);
                    if (!validator.validate(new EmailValue(email)).isEmpty()) throw invalid();
                    yield email;
                }
                case PHONE -> {
                    String phone=value.replaceAll("[ ()-]", "");
                    if (!phone.matches("\\+[1-9][0-9]{7,14}")) throw invalid();
                    yield phone;
                }
                case URL -> {
                    URI uri=URI.create(value);
                    if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost()==null || uri.getUserInfo()!=null) throw invalid();
                    yield value;
                }
                default -> value;
            };
        } catch (IllegalArgumentException | DateTimeException invalid) { throw invalid(); }
    }
    private static FailException invalid() { return new FailException(INVALID_DRAFT); }
    @Override public String toString() { return "ClientDraftPatch[redacted]"; }
}
