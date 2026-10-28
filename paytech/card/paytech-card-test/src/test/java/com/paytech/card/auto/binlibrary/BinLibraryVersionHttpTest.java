package com.paytech.card.auto.binlibrary;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.binlibrary.CardBinLibraryApi;
import com.paytech.card.api.enums.BinImportErrorCode;
import com.paytech.card.api.enums.BinLibraryVersionStatus;
import com.paytech.card.api.enums.CardBrand;
import com.paytech.card.api.enums.CardFundingType;
import com.paytech.card.api.enums.CardProduct;
import com.paytech.card.domain.binlibrary.BinLibrarySourceFile;
import com.paytech.card.test.base.AbstractBinLibraryTest;
import com.paytech.card.test.builder.BinLibraryCsvBuilder;
import com.paytech.platform.support.api.files.FileAddress;

/**
 * BIN 库运营接口的版本创建、列表、详情、错误明细与单个 BIN 查询：经 card 真实 HTTP 入口、Processor、mor_card 四张 BIN 表，Platform 文件接口与
 * 对象存储下载由测试 HTTP 服务返回 DTO 与 CSV 字节，导入任务由真实补偿执行器在提交后执行。
 *
 * <p>
 * 覆盖：合法文件 201 VALIDATING → 异步 VALID，列表与详情显示行数，查询仍用原生效版本（AC-01）；同一文件重复提交 200 返回原版本；文件不存在、来源 /
 * 原因 / 类型不符、扫描隔离 400 且不建版本；非运营调用方 404、缺操作人 400；校验失败显示行号与原因、长数字不回显、不能激活（AC-05、AC-11）；
 * 8 位优先、7 / 6 位前缀、未识别、指定版本、没有生效版本与 BIN 格式错误（AC-03、AC-04）。
 *
 * @see CardBinLibraryApi#createVersion(String, String, com.paytech.card.api.binlibrary.request.CreateBinLibraryVersionRequestBody)
 * @see CardBinLibraryApi#listVersions(String, String, Integer, Integer)
 * @see CardBinLibraryApi#getVersion(String, String)
 * @see CardBinLibraryApi#listImportErrors(String, String, Integer, Integer)
 * @see CardBinLibraryApi#lookupBin(String, String, String)
 */
class BinLibraryVersionHttpTest extends AbstractBinLibraryTest {
    private static final String FILE_ID = "7409912387612";
    private static final String FILE_ADDRESS = FileAddress.of(Long.parseLong(FILE_ID)).value();

    /** 合法文件：201 VALIDATING，异步写入后 VALID，行数与进度正确，号段区域按映射推导；生效版本仍是 v1，查询结果不变。 */
    @Test
    void test_createVersionImportsValidFile() {
        seedActiveVersion();
        platform.mockCsvFile(FILE_ID, BinLibraryCsvBuilder.template()
            .row("411111", CardBrand.MASTERCARD.getCode(), CardFundingType.DEBIT.getCode(), null, null, "AE", "\"ISSUER, AE\"")
            .row("52008200", CardBrand.MASTERCARD.getCode(), null, "WORLD", CardProduct.COMMERCIAL.getCode(), "SA", " SA BANK ")
            .row("6011110", CardBrand.DISCOVER.getCode(), CardFundingType.PREPAID.getCode(), null, null, "JP", null).build());

        var created = createVersion(FILE_ID);

        var body = json(created);
        assertAll(() -> assertEquals(201, created.status()), () -> assertEquals(2, body.get("versionNo")),
            () -> assertEquals(BinLibraryVersionStatus.VALIDATING.getCode(), body.get("status")),
            () -> assertEquals(false, body.get("active")), () -> assertEquals(FILE_ADDRESS, body.get("fileAddress")),
            () -> assertEquals("bin-" + FILE_ID + ".csv", body.get("fileName")), () -> assertEquals(ACTOR, body.get("createdBy")),
            () -> assertNull(body.get("totalRows")), () -> assertEquals(0, body.get("processedRows")));

        var imported = awaitImported(2);
        long versionId = ((Number)imported.get("versionId")).longValue();
        var ranges = mapper.binRanges(versionId);
        var detail = json(ops(HttpMethod.GET, "/versions/2", null));
        assertAll(() -> assertEquals(BinLibraryVersionStatus.VALID.getCode(), imported.get("status")),
            () -> assertEquals(3, imported.get("totalRows")), () -> assertEquals(3, imported.get("processedRows")),
            () -> assertEquals(3, detail.get("totalRows")), () -> assertEquals(BinLibraryVersionStatus.VALID.getCode(), detail.get("status")),
            () -> assertEquals(3, ranges.size()),
            () -> assertEquals("GCC", ranges.get(0).get("issuerRegion")), () -> assertEquals("ISSUER, AE", ranges.get(0).get("issuerName")),
            () -> assertEquals("SA BANK", ranges.get(1).get("issuerName")), () -> assertEquals(8, ((Number)ranges.get(1).get("prefixLength")).intValue()),
            () -> assertEquals("APAC", ranges.get(2).get("issuerRegion")),
            () -> assertEquals(CardBrand.VISA.getCode(), json(lookup("41111199")).get("cardBrand"), "未激活前查询仍用 v1"),
            () -> assertEquals(1, json(lookup("41111199")).get("binLibraryVersionNo")),
            () -> assertEquals(2, platform.downloadCount(FILE_ID), "两遍各下载一次"));
        var list = items(ops(HttpMethod.GET, "/versions", null));
        assertAll(() -> assertEquals(List.of(2, 1), list.stream().map(row -> row.get("versionNo")).toList()),
            () -> assertEquals(List.of(true), items(ops(HttpMethod.GET, "/versions?status=ACTIVE", null)).stream()
                .map(row -> row.get("active")).toList()));
    }

    /** 同一文件重复提交：200 返回原版本，不再查询 Platform，也不新增版本。 */
    @Test
    void test_createVersionReplaysSameFile() {
        platform.mockCsvFile(FILE_ID, BinLibraryCsvBuilder.template()
            .row("411111", CardBrand.VISA.getCode(), null, null, null, "US", null).build());
        var first = createVersion(FILE_ID);
        awaitImported(1);

        var again = createVersion(FILE_ID);

        assertAll(() -> assertEquals(201, first.status()), () -> assertEquals(200, again.status()),
            () -> assertEquals(1, json(again).get("versionNo")),
            () -> assertEquals(BinLibraryVersionStatus.VALID.getCode(), json(again).get("status")),
            () -> assertEquals(1, mapper.binVersions().size()), () -> assertEquals(2, platform.fileQueryCount(FILE_ID),
                "创建一次、导入任务一次；重放不查询"));
    }

    /** 文件不存在、来源 / 上传原因 / 类型不符、扫描隔离、Platform 登记地址与请求不一致：400 业务检查失败，不建版本，不登记导入任务。 */
    @Test
    void test_createVersionRejectsUnusableFile() {
        byte[] csv = BinLibraryCsvBuilder.template().row("411111", CardBrand.VISA.getCode(), null, null, null, "US", null).build();
        platform.mockCsvFile("101", csv).setSourceSystem(CardApiConstants.ACQUIRING_SERVICE);
        platform.mockCsvFile("102", csv).setReasonCode("ONBOARDING_DOCUMENT");
        platform.mockCsvFile("103", csv).setMimeType("application/pdf");
        platform.mockCsvFile("104", csv).setScanStatus("QUARANTINED");
        platform.mockCsvFile("105", csv).setFileAddress(FileAddress.of(106).value());

        var statuses = List.of(createVersion("100"), createVersion("101"), createVersion("102"), createVersion("103"),
            createVersion("104"), createVersion("105"));

        assertAll(() -> assertEquals(List.of(400, 400, 400, 400, 400, 400), statuses.stream().map(Response::status).toList()),
            () -> assertTrue(statuses.stream().allMatch(response -> response.body().contains("BUSINESS_CHECK_FAILED"))),
            () -> assertTrue(mapper.binVersions().isEmpty()), () -> assertTrue(mapper.compensationEvents().isEmpty()),
            () -> assertEquals(0, platform.downloadCount("104")));
    }

    /** 扫描中允许创建版本（导入任务稍后重试）；文件来源常量与 BFF 上传约定一致。 */
    @Test
    void test_createVersionWhileScanning() {
        platform.mockCsvFile(FILE_ID, BinLibraryCsvBuilder.template()
            .row("411111", CardBrand.VISA.getCode(), null, null, null, "US", null).build()).setScanStatus("SCANNING");

        var created = createVersion(FILE_ID);

        assertAll(() -> assertEquals(201, created.status()),
            () -> assertEquals(BinLibraryVersionStatus.VALIDATING.getCode(), json(created).get("status")),
            () -> assertEquals(BinLibrarySourceFile.SOURCE_SYSTEM, CardApiConstants.OPERATIONS_CONSOLE_BFF));
        await().atMost(IMPORT_WAIT).until(() -> !mapper.compensationEvents().isEmpty()
            && "ERROR".equals(mapper.compensationEvents().getFirst().get("executeStatus")));
        assertAll(() -> assertEquals("BIN_LIBRARY_FILE_SCANNING", mapper.compensationEvents().getFirst().get("lastErrorCode")),
            () -> assertEquals(0, platform.downloadCount(FILE_ID)));
    }

    /** 调用方白名单与操作人：非运营调用方 404；缺调用方头、写接口缺操作人或格式错误 400；格式错误的文件地址 422 字段校验失败，格式通过但文件 ID 超出范围 400。 */
    @Test
    void test_callerAndActorRules() {
        var paymentList = callWithActor(HttpMethod.GET, BASE + "/versions", CardApiConstants.PAYMENT_SERVICE, null, null);
        var acquiringLookup = callWithActor(HttpMethod.GET, BASE + "/bins/411111", CardApiConstants.ACQUIRING_SERVICE, null, null);
        var noCaller = callWithActor(HttpMethod.GET, BASE + "/activations", null, null, null);
        var noActor = callWithActor(HttpMethod.POST, BASE + "/versions", CardApiConstants.OPERATIONS_CONSOLE_BFF, null,
            Map.of("fileAddress", FILE_ADDRESS));
        var badActor = callWithActor(HttpMethod.POST, BASE + "/versions", CardApiConstants.OPERATIONS_CONSOLE_BFF, "abc",
            Map.of("fileAddress", FILE_ADDRESS));
        var badAddress = ops(HttpMethod.POST, "/versions", Map.of("fileAddress", "https://minio/bucket/" + FILE_ID));
        var overflowAddress = ops(HttpMethod.POST, "/versions", Map.of("fileAddress", "/files/v1/9999999999999999999"));
        var wrongCallerCreate = callWithActor(HttpMethod.POST, BASE + "/versions", CardApiConstants.PAYMENT_SERVICE, ACTOR,
            Map.of("fileAddress", FILE_ADDRESS));

        assertAll(() -> assertEquals(404, paymentList.status()), () -> assertEquals(404, acquiringLookup.status()),
            () -> assertEquals(400, noCaller.status()), () -> assertEquals(400, noActor.status()),
            () -> assertEquals(400, badActor.status()), () -> assertEquals(422, badAddress.status()),
            () -> assertEquals(400, overflowAddress.status(), "格式通过但 FileAddress 解析失败"),
            () -> assertEquals(404, wrongCallerCreate.status()), () -> assertTrue(mapper.binVersions().isEmpty()));
    }

    /** 校验失败：INVALID，错误行数与按行号的明细；超过 8 位的数字不回显；失败版本不能激活。 */
    @Test
    void test_invalidFileShowsErrors() {
        platform.mockCsvFile(FILE_ID, BinLibraryCsvBuilder.template()
            .row("4111111111111111", CardBrand.VISA.getCode(), null, null, null, "US", null)
            .row("411111", "visa", null, null, null, "US", null)
            .row("422222", CardBrand.VISA.getCode(), "DEBITCARD", null, null, "XX", null)
            .row("433333", CardBrand.VISA.getCode(), null, null, null, "US", null)
            .row("433333", CardBrand.VISA.getCode(), null, null, null, "US", null)
            .raw("444444,VISA,,,,US").build());

        createVersion(FILE_ID);
        var imported = awaitImported(1);

        var errors = items(ops(HttpMethod.GET, "/versions/1/errors?size=20", null));
        assertAll(() -> assertEquals(BinLibraryVersionStatus.INVALID.getCode(), imported.get("status")),
            () -> assertEquals(6, imported.get("totalRows")), () -> assertEquals(6, imported.get("errorRows")),
            () -> assertNull(imported.get("failureReason")),
            () -> assertEquals(List.of(2, 3, 4, 4, 5, 6, 7), errors.stream().map(row -> row.get("lineNo")).toList()),
            () -> assertEquals(BinImportErrorCode.INVALID_FORMAT.getCode(), errors.get(0).get("errorCode")),
            () -> assertNull(errors.get(0).get("valueExcerpt"), "超过 8 位的数字不回显"),
            () -> assertEquals(BinImportErrorCode.INVALID_VALUE.getCode(), errors.get(1).get("errorCode")),
            () -> assertEquals("visa", errors.get(1).get("valueExcerpt")),
            () -> assertEquals(List.of(BinImportErrorCode.INVALID_VALUE.getCode(), BinImportErrorCode.COUNTRY_REGION_MISSING.getCode()),
                List.of(errors.get(2).get("errorCode"), errors.get(3).get("errorCode"))),
            () -> assertEquals(List.of(BinImportErrorCode.DUPLICATE_PREFIX.getCode(), BinImportErrorCode.DUPLICATE_PREFIX.getCode()),
                List.of(errors.get(4).get("errorCode"), errors.get(5).get("errorCode"))),
            () -> assertEquals(BinImportErrorCode.COLUMN_COUNT_MISMATCH.getCode(), errors.get(6).get("errorCode")),
            () -> assertNull(errors.get(6).get("columnName")),
            () -> assertFalse(String.valueOf(mapper.binImportErrors(((Number)imported.get("versionId")).longValue()))
                .contains("4111111111111111")),
            () -> assertEquals(0, mapper.countBinRanges(((Number)imported.get("versionId")).longValue())),
            () -> assertEquals(400, activate(1, null).status()));
    }

    /** 版本与错误明细不存在 404；非法状态筛选 400。 */
    @Test
    void test_versionNotFound() {
        assertAll(() -> assertEquals(404, ops(HttpMethod.GET, "/versions/9", null).status()),
            () -> assertEquals(404, ops(HttpMethod.GET, "/versions/9/errors", null).status()),
            () -> assertEquals(400, ops(HttpMethod.GET, "/versions?status=DRAFT", null).status()),
            () -> assertEquals(400, ops(HttpMethod.GET, "/versions?size=7", null).status()),
            () -> assertEquals(List.of(), items(ops(HttpMethod.GET, "/versions", null))));
    }

    /** 查询：8 位优先于 6 位；7 / 6 位 BIN 只取其前缀；未识别 200；指定版本；BIN 格式错误 400。 */
    @Test
    void test_lookupBin() {
        seedActiveVersion();
        long retired = binLibrary.version(2, BinLibraryVersionStatus.RETIRED, 1);
        binLibrary.range(retired, "411111", CardBrand.MASTERCARD, "AE", "GCC");
        binLibrary.version(3, BinLibraryVersionStatus.VALIDATING, 0);

        var eightDigit = json(lookup("40128888"));
        var sixDigitFallback = json(lookup("40128899"));
        var unrecognized = json(lookup("999999"));
        var retiredLookup = json(ops(HttpMethod.GET, "/bins/41111100?versionNo=2", null));

        assertAll(() -> assertEquals(true, eightDigit.get("recognized")), () -> assertEquals("40128888", eightDigit.get("binPrefix")),
            () -> assertEquals(8, eightDigit.get("matchedPrefixLength")), () -> assertEquals("GB", eightDigit.get("issuerCountry")),
            () -> assertEquals(CardFundingType.DEBIT.getCode(), eightDigit.get("fundingType")),
            () -> assertEquals("SYNTHETIC TEST ISSUER GB", eightDigit.get("issuerName")),
            () -> assertEquals("401288", sixDigitFallback.get("binPrefix")), () -> assertEquals("US", sixDigitFallback.get("issuerCountry")),
            () -> assertEquals(false, unrecognized.get("recognized")), () -> assertEquals(1, unrecognized.get("binLibraryVersionNo")),
            () -> assertNull(unrecognized.get("cardBrand")),
            () -> assertEquals(CardBrand.MASTERCARD.getCode(), retiredLookup.get("cardBrand")),
            () -> assertEquals(2, retiredLookup.get("binLibraryVersionNo")),
            () -> assertEquals(400, ops(HttpMethod.GET, "/bins/41111100?versionNo=3", null).status(), "校验中的版本不能查询"),
            () -> assertEquals(404, ops(HttpMethod.GET, "/bins/411111?versionNo=8", null).status()),
            () -> assertEquals(400, lookup("41111").status()), () -> assertEquals(400, lookup("411111111").status()),
            () -> assertEquals(400, lookup("41111a").status()));
    }

    /** 没有生效版本：查询 200，未识别且版本号为 null。 */
    @Test
    void test_lookupWithoutActiveVersion() {
        var result = json(lookup("411111"));

        assertAll(() -> assertEquals(false, result.get("recognized")), () -> assertNull(result.get("binLibraryVersionNo")),
            () -> assertNull(result.get("binPrefix")));
    }
}
