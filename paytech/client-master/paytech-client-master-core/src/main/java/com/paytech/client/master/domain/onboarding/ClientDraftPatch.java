package com.paytech.client.master.domain.onboarding;
import java.net.URI;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.DraftPatchRequestBody;
import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
/**
 * 草稿只校验已修改字段的语法和存储边界；完整性和稳定身份绑定留给正式提交。
 * <li>一次收集全部字段问题，以 INVALID_DRAFT 加 errors[] 抛出；字段路径与 dirtyFields 同名（如 contact.phone），
 * 结构问题落在 dirtyFields 或 values；消息说明约束，不回显提交的原值。</li>
 */
public final class ClientDraftPatch {
    /** 号码原值只在内存中规范化并交给 Vault 保护，不作为数据库列写入。 */
    public static final String IDENTIFIER_VALUE="identifier.identifierValue";
    /** 扩展属性分区前缀，字段名为属性代码。 */
    public static final String ATTRIBUTES="attributes.";
    private enum Kind {
        TEXT,

        DATE,

        COUNTRY,

        CURRENCY,

        URL,

        MONEY,

        EMAIL,

        PHONE,

        BOOLEAN,

        /** 逗号分隔的代码列表：规范化为大写、去重、按代码升序。 */
        CODE_LIST,

        /** 只收 https 的网址。 */
        HTTPS_URL,

        /** 非负十进制整数，去前导零。 */
        INTEGER
    }
    private record Field(Kind kind, int length) {}
    /** 单字段校验结果：合规时 value 为规范化值，否则 message 为外显约束说明。 */
    private record Checked(String value, String message) {
        static Checked ok(String value) { return new Checked(value, null); }
        static Checked fail(String message) { return new Checked(null, message); }
    }
    private record EmailValue(@Email String value) {}
    /** 代码列表的单项格式与项数上限。 */
    private static final String CODE_PATTERN="[A-Z][A-Z0-9_]{0,63}";
    private static final int MAX_CODES=20;
    private static final Map<String,Field> FIELDS=Map.ofEntries(
        Map.entry("individual.givenName", new Field(Kind.TEXT,128)),
        Map.entry("individual.middleName", new Field(Kind.TEXT,128)),
        Map.entry("individual.familyName", new Field(Kind.TEXT,128)),
        Map.entry("individual.birthDate", new Field(Kind.DATE,10)),
        Map.entry("individual.nationalityCountry", new Field(Kind.COUNTRY,2)),
        Map.entry("individual.residenceCountry", new Field(Kind.COUNTRY,2)),
        Map.entry("individual.birthPlace", new Field(Kind.TEXT,128)),
        Map.entry("individual.birthCountry", new Field(Kind.COUNTRY,2)),
        Map.entry("individual.gender", new Field(Kind.TEXT,16)),
        Map.entry("individual.sourceOfFundsCode", new Field(Kind.TEXT,64)),
        Map.entry("individual.pepSelfDeclared", new Field(Kind.BOOLEAN,5)),
        Map.entry("organization.legalName", new Field(Kind.TEXT,128)),
        Map.entry("organization.tradeName", new Field(Kind.TEXT,128)),
        Map.entry("organization.incorporationCountry", new Field(Kind.COUNTRY,2)),
        Map.entry("organization.organizationType", new Field(Kind.TEXT,64)),
        Map.entry("organization.incorporationDate", new Field(Kind.DATE,10)),
        Map.entry("organization.registrationAuthorityCode", new Field(Kind.TEXT,64)),
        Map.entry("organization.licenseExpiryDate", new Field(Kind.DATE,10)),
        Map.entry("organization.listedCompany", new Field(Kind.BOOLEAN,5)),
        Map.entry("address.addressLine1", new Field(Kind.TEXT,255)),
        Map.entry("address.addressLine2", new Field(Kind.TEXT,255)),
        Map.entry("address.city", new Field(Kind.TEXT,128)),
        Map.entry("address.stateOrProvince", new Field(Kind.TEXT,128)),
        Map.entry("address.postalCode", new Field(Kind.TEXT,32)),
        Map.entry("address.countryCode", new Field(Kind.COUNTRY,2)),
        Map.entry("address.addressFormatCode", new Field(Kind.TEXT,32)),
        Map.entry("address.buildingNumber", new Field(Kind.TEXT,32)),
        Map.entry("address.additionalNumber", new Field(Kind.TEXT,32)),
        Map.entry("address.district", new Field(Kind.TEXT,128)),
        Map.entry("address.poBox", new Field(Kind.TEXT,32)),
        Map.entry("identifier.identifierType", new Field(Kind.TEXT,64)),
        Map.entry("identifier.issuingCountry", new Field(Kind.COUNTRY,2)),
        Map.entry(IDENTIFIER_VALUE, new Field(Kind.TEXT,128)),
        Map.entry("identifier.issueDate", new Field(Kind.DATE,10)),
        Map.entry("identifier.expiryDate", new Field(Kind.DATE,10)),
        Map.entry("business.businessModelCode", new Field(Kind.TEXT,64)),
        Map.entry("business.websiteUrl", new Field(Kind.URL,2048)),
        Map.entry("business.businessDescription", new Field(Kind.TEXT,2000)),
        Map.entry("business.expectedVolume", new Field(Kind.MONEY,25)),
        Map.entry("business.expectedTicket", new Field(Kind.MONEY,25)),
        Map.entry("business.currency", new Field(Kind.CURRENCY,3)),
        Map.entry("business.expectedPaymentMethods", new Field(Kind.CODE_LIST,512)),
        Map.entry("business.refundPolicyUrl", new Field(Kind.HTTPS_URL,2048)),
        Map.entry("business.fulfillmentType", new Field(Kind.TEXT,32)),
        Map.entry("business.fulfillmentDays", new Field(Kind.INTEGER,3)),
        Map.entry("contact.email", new Field(Kind.EMAIL,320)),
        Map.entry("contact.phone", new Field(Kind.PHONE,32)));
    /** 扩展属性分区：字段名为属性代码，类型与辖区适用性由辖区包属性定义在保存前校验。 */
    private static final Field ATTRIBUTE=new Field(Kind.TEXT,512);
    private static final int MAX_DIRTY_FIELDS=50;
    private final Map<String,String> values;
    private ClientDraftPatch(Map<String,String> values) { this.values=Collections.unmodifiableMap(values); }
    /** 规范化后的脏字段值，键为字段路径，null 表示清空。 */
    public Map<String,String> values() { return values; }
    /** 草稿支持的全部固定字段路径（不含 attributes.*）。 */
    public static Set<String> paths() { return FIELDS.keySet(); }
    /** 路径的字段定义：固定字段或扩展属性；不支持的路径为 null。 */
    private static Field field(String path) {
        if (path==null) {
            return null;
        }
        if (path.startsWith(ATTRIBUTES)) {
            return path.substring(ATTRIBUTES.length()).matches("[A-Z][A-Z0-9_]{0,63}")?ATTRIBUTE:null;
        }
        return FIELDS.get(path);
    }

    /**
     * 校验并规范化一次草稿修改；任一问题都不保存，全部问题一次返回。
     *
     * @param request 页面提交的脏字段与取值
     * @param validator 邮箱语法校验
     * @return 规范化后的修改
     * @throws RequestBodyValidationException INVALID_DRAFT 加字段清单
     */
    public static ClientDraftPatch normalize(DraftPatchRequestBody request, Validator validator) {
        List<ProblemFieldError> errors=new ArrayList<>();
        Set<String> dirty=dirtyFields(request,errors);
        if (!errors.isEmpty()) {
            throw invalid(errors);
        }
        Map<String,String> result=new TreeMap<>();
        for (var group:request.values().entrySet()) {
            normalizeGroup(group.getKey(),group.getValue(),dirty,validator,result,errors);
        }
        dirty.stream().filter(path -> !result.containsKey(path) && errors.stream().noneMatch(e -> e.field().equals(path)))
            .forEach(path -> errors.add(new ProblemFieldError(path,"已列入 dirtyFields，但 values 中没有该字段")));
        if (!errors.isEmpty()) {
            throw invalid(errors);
        }
        return new ClientDraftPatch(result);
    }

    /** 校验 dirtyFields 的数量、唯一性和支持范围；values 缺失也在此报告。 */
    private static Set<String> dirtyFields(DraftPatchRequestBody request,List<ProblemFieldError> errors) {
        if (request==null || request.dirtyFields()==null || request.dirtyFields().isEmpty()
            || request.dirtyFields().size()>MAX_DIRTY_FIELDS) {
            errors.add(new ProblemFieldError("dirtyFields","须列出 1 到 "+MAX_DIRTY_FIELDS+" 个修改字段"));
            return Set.of();
        }
        if (request.values()==null) {
            errors.add(new ProblemFieldError("values","须提供修改字段的取值"));
        }
        var dirty=new HashSet<String>();
        for (String path:request.dirtyFields()) {
            if (field(path)==null) {
                errors.add(new ProblemFieldError("dirtyFields","包含不支持的字段"));
            } else if (!dirty.add(path)) {
                errors.add(new ProblemFieldError("dirtyFields","同一字段不能重复列出"));
            }
        }
        return dirty;
    }

    /** 校验一个分组（如 contact）下的字段；不在 dirtyFields 中的字段逐项报告。 */
    private static void normalizeGroup(String group,Map<String,String> fields,Set<String> dirty,Validator validator,
        Map<String,String> result,List<ProblemFieldError> errors) {
        if (fields==null || fields.isEmpty()) {
            errors.add(new ProblemFieldError("values","分组 "+group+" 不能为空"));
            return;
        }
        for (var field:fields.entrySet()) {
            String path=group+"."+field.getKey();
            if (!dirty.contains(path)) {
                errors.add(new ProblemFieldError(path,"该字段未列入 dirtyFields"));
                continue;
            }
            Checked checked=normalize(field(path),field.getValue(),validator);
            if (checked.message()!=null) {
                errors.add(new ProblemFieldError(path,checked.message()));
                continue;
            }
            result.put(path,checked.value());
        }
    }

    /** 人员资料只允许个人、地址和证件字段。 */
    public void requirePerson() {
        rejectPaths(path -> !(path.startsWith("individual.") || path.startsWith("address.") || path.startsWith("identifier.")
            || path.startsWith(ATTRIBUTES)), "人员资料不支持此字段");
    }

    /** 个人申请不能写组织字段，组织申请不能写个人字段。 */
    public void requireType(ClientType type) {
        String forbidden=type==ClientType.INDIVIDUAL?"organization.":"individual.";
        rejectPaths(path -> path.startsWith(forbidden),"当前申请类型不支持此字段");
    }

    /** 命中条件的路径逐项报告为字段错误。 */
    private void rejectPaths(java.util.function.Predicate<String> rejected,String message) {
        List<ProblemFieldError> errors=values.keySet().stream().filter(rejected)
            .map(path -> new ProblemFieldError(path,message)).toList();
        if (!errors.isEmpty()) {
            throw invalid(errors);
        }
    }

    /**
     * 按字段类型转换为数据库列值，列名由路径的点换成下划线
     * <li>不含号码原值与扩展属性：前者由 Vault 保护后另行写入，后者写 t_party_attribute。</li>
     */
    public Map<String,Object> databaseValues() {
        Map<String,Object> result=new HashMap<>();
        values.forEach((path,value) -> {
            if (path.equals(IDENTIFIER_VALUE) || path.startsWith(ATTRIBUTES)) {
                return;
            }
            result.put(path.replace('.','_'), value==null?null:switch(FIELDS.get(path).kind()) {
                case DATE -> LocalDate.parse(value);
                case MONEY -> new BigDecimal(value);
                case BOOLEAN -> Boolean.valueOf(value);
                case INTEGER -> Integer.valueOf(value);
                default -> value;
            });
        });
        return result;
    }

    /** 扩展属性的修改：属性代码到规范化文本，null 表示清空。 */
    public Map<String,String> attributeValues() {
        Map<String,String> result=new TreeMap<>();
        values.forEach((path,value) -> {
            if (path.startsWith(ATTRIBUTES)) {
                result.put(path.substring(ATTRIBUTES.length()),value);
            }
        });
        return result;
    }

    /** 是否修改了某分区。 */
    public boolean touches(String group) {
        return values.keySet().stream().anyMatch(path -> path.startsWith(group+"."));
    }

    /** 去首尾空白后校验字符、长度和类型格式；空串视为清空。 */
    private static Checked normalize(Field field,String raw,Validator validator) {
        if (raw==null) {
            return Checked.ok(null);
        }
        String value=raw.strip();
        if (value.isEmpty()) {
            return Checked.ok(null);
        }
        if (value.indexOf(0)>=0) {
            return Checked.fail("包含不支持的字符");
        }
        if (value.codePointCount(0,value.length())>field.length()) {
            return Checked.fail(fixedFormat(field.kind())?message(field.kind()):"长度不能超过 "+field.length()+" 个字符");
        }
        try {
            return check(field.kind(),value,validator);
        } catch (IllegalArgumentException | DateTimeException invalid) {
            return Checked.fail(message(field.kind()));
        }
    }

    /** 按字段类型校验并规范化；不合规返回该类型的约束说明。 */
    private static Checked check(Kind kind,String value,Validator validator) {
        return switch(kind) {
            case COUNTRY, CURRENCY -> {
                String code=value.toUpperCase(Locale.ROOT);
                yield code.matches(kind==Kind.COUNTRY?"[A-Z]{2}":"[A-Z]{3}")?Checked.ok(code):Checked.fail(message(kind));
            }
            case DATE -> value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") && LocalDate.parse(value).getYear()>=1000
                ?Checked.ok(value):Checked.fail(message(kind));
            case MONEY -> money(value);
            case EMAIL -> {
                String email=value.toLowerCase(Locale.ROOT);
                yield validator.validate(new EmailValue(email)).isEmpty()?Checked.ok(email):Checked.fail(message(kind));
            }
            case PHONE -> {
                String phone=value.replaceAll("[ ()-]", "");
                yield phone.matches("\\+[1-9][0-9]{7,14}")?Checked.ok(phone):Checked.fail(message(kind));
            }
            case BOOLEAN -> "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)
                ?Checked.ok(value.toLowerCase(Locale.ROOT)):Checked.fail(message(kind));
            case URL, HTTPS_URL -> {
                URI uri=URI.create(value);
                boolean web="https".equalsIgnoreCase(uri.getScheme()) || (kind==Kind.URL && "http".equalsIgnoreCase(uri.getScheme()));
                yield web && uri.getHost()!=null && uri.getUserInfo()==null?Checked.ok(value):Checked.fail(message(kind));
            }
            case CODE_LIST -> codeList(value);
            case INTEGER -> value.matches("[0-9]+")?Checked.ok(Integer.valueOf(value).toString()):Checked.fail(message(kind));
            default -> Checked.ok(value);
        };
    }

    /** 金额为非负十进制，整数位不超过 16、小数位不超过 8，规范化去掉末尾零。 */
    private static Checked money(String value) {
        if (!value.matches("[0-9]+(\\.[0-9]{1,8})?")) {
            return Checked.fail(message(Kind.MONEY));
        }
        BigDecimal number=new BigDecimal(value);
        if (number.precision()-number.scale()>16) {
            return Checked.fail(message(Kind.MONEY));
        }
        return Checked.ok(number.stripTrailingZeros().toPlainString());
    }

    /**
     * 代码列表的唯一规范化：按英文逗号拆分，逐项去空白转大写、丢弃空项，校验格式后去重并按代码升序以逗号连接
     * <li>全部为空项时视为清空；超过 20 项或任一项不合规时拒绝。页面与需求计算只拆分，不再改写，摘要输入即存储值。</li>
     */
    private static Checked codeList(String value) {
        var codes=new TreeSet<String>();
        for (String item:value.split(",",-1)) {
            String code=item.strip().toUpperCase(Locale.ROOT);
            if (code.isEmpty()) {
                continue;
            }
            if (!code.matches(CODE_PATTERN)) {
                return Checked.fail(message(Kind.CODE_LIST));
            }
            codes.add(code);
        }
        if (codes.size()>MAX_CODES) {
            return Checked.fail(message(Kind.CODE_LIST));
        }
        return Checked.ok(codes.isEmpty()?null:String.join(",",codes));
    }

    /** 代码和日期是定长格式，超长时直接说明格式比说明长度更有用。 */
    private static boolean fixedFormat(Kind kind) {
        return kind==Kind.COUNTRY || kind==Kind.CURRENCY || kind==Kind.DATE;
    }

    /** 各字段类型的外显约束说明，不含提交值。 */
    private static String message(Kind kind) {
        return switch(kind) {
            case COUNTRY -> "须为两位国家代码，如 CN";
            case CURRENCY -> "须为三位币种代码，如 USD";
            case DATE -> "须为 YYYY-MM-DD 格式的有效日期";
            case MONEY -> "须为非负金额，整数最多 16 位、小数最多 8 位";
            case EMAIL -> "邮箱格式不正确";
            case PHONE -> "手机号须为 E.164 格式，如 +8613800138000";
            case URL -> "须为 http 或 https 网址";
            case HTTPS_URL -> "须为 https 网址";
            case CODE_LIST -> "须为以逗号分隔的代码，最多 "+MAX_CODES+" 项";
            case INTEGER -> "须为整数";
            case BOOLEAN -> "须为 true 或 false";
            default -> "字段不符合约定";
        };
    }

    /** INVALID_DRAFT 加字段清单。 */
    private static RequestBodyValidationException invalid(List<ProblemFieldError> errors) {
        return new RequestBodyValidationException(INVALID_DRAFT,errors,null);
    }
    @Override public String toString() { return "ClientDraftPatch[redacted]"; }
}
