package com.paytech.card.auto.binlibrary;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.paytech.card.api.enums.BinImportErrorCode;
import com.paytech.card.api.enums.BinLibraryVersionStatus;
import com.paytech.card.api.enums.CardBrand;
import com.paytech.card.api.enums.CardFundingType;
import com.paytech.card.api.enums.CardProduct;
import com.paytech.card.domain.binlibrary.BinLibraryCsv;
import com.paytech.card.service.handler.CardBinLibraryImportHandler;
import com.paytech.card.test.base.AbstractBinLibraryTest;
import com.paytech.card.test.builder.BinLibraryCsvBuilder;
import com.paytech.card.test.mocker.PlatformMocker;
import com.paytech.foundation.compensation.api.CompensationEventScanner;
import com.paytech.foundation.compensation.api.CompensationScanRequest;

/**
 * BIN 库导入任务 CARD_BIN_LIBRARY_IMPORT 的独立入口：待导入版本与任务经真实登记器同事务写入，提交后由真实 Dispatcher 执行，重试经真实 Scanner 唤醒；
 * Platform 文件元数据、下载地址与国家区域映射由测试 HTTP 服务返回 DTO 与字节。
 *
 * <p>
 * 覆盖：两遍读取写入 2500 行（BOM、CRLF，三批）；扫描中可重试、扫描通过后经 Scanner 完成；隔离与文件不存在、表头不符、摘要不符为 INVALID 与原因；
 * 行错误超过 200 条只记录前 200 条；第二遍下载中断后重入先清理残留再完整写入；非 VALIDATING 版本直接成功不访问 Platform；日志与错误明细不含完整卡号
 * （AC-11）。
 *
 * @see CardBinLibraryImportHandler#handle(com.paytech.foundation.compensation.api.CompensationEvent)
 */
@ExtendWith(OutputCaptureExtension.class)
class CardBinLibraryImportHandlerTest extends AbstractBinLibraryTest {
    private static final String FILE_ID = "880001";
    private static final String PAN_LIKE = "4111111111111111";
    private static final CompensationScanRequest SCAN =
        new CompensationScanRequest(100, 0, 1, Set.of(CardBinLibraryImportHandler.EVENT_TYPE));

    @Autowired
    private CompensationEventScanner scanner;

    /** 本类每例都使用 6 国区域映射。 */
    @BeforeEach
    void setUpClassMocks() {
        platform.mockCountryRegions(REGIONS);
    }

    /** 2500 行合法文件（BOM、CRLF）：三批写入，进度等于总行数，任务完成后删除；两遍各下载一次、映射查询一次。 */
    @Test
    void test_importLargeFile() {
        byte[] csv = rows(2500, BinLibraryCsvBuilder.template().bom().crlf());
        platform.mockCsvFile(FILE_ID, csv);

        long versionId = binLibrary.importingVersion(1, FILE_ID, PlatformMocker.sha256(csv));

        var imported = awaitImported(1);
        awaitTaskDeleted();
        var ranges = mapper.binRanges(versionId);
        assertAll(() -> assertEquals(BinLibraryVersionStatus.VALID.getCode(), imported.get("status")),
            () -> assertEquals(2500, imported.get("totalRows")), () -> assertEquals(2500, imported.get("processedRows")),
            () -> assertEquals(2500, ranges.size()), () -> assertEquals("400000", ranges.getFirst().get("binPrefix")),
            () -> assertEquals("NORTH_AMERICA", ranges.getFirst().get("issuerRegion")),
            () -> assertEquals(CardFundingType.CREDIT.getCode(), ranges.getFirst().get("fundingType")),
            () -> assertEquals(2, platform.downloadCount(FILE_ID)), () -> assertEquals(1, platform.countryRegionQueryCount()));
    }

    /** 扫描中：可重试失败且不下载；扫描通过后经真实 Scanner 唤醒，完成导入。 */
    @Test
    void test_retryWhileScanning() {
        byte[] csv = rows(3, BinLibraryCsvBuilder.template());
        platform.mockCsvFile(FILE_ID, csv).setScanStatus("PENDING_SCAN");
        long versionId = binLibrary.importingVersion(1, FILE_ID, PlatformMocker.sha256(csv));
        await().atMost(IMPORT_WAIT).until(() -> "ERROR".equals(task().get("executeStatus")));
        assertAll(() -> assertEquals("BIN_LIBRARY_FILE_SCANNING", task().get("lastErrorCode")),
            () -> assertEquals(0, platform.downloadCount(FILE_ID)),
            () -> assertEquals(BinLibraryVersionStatus.VALIDATING.getCode(), version(1).get("status")));

        platform.scanStatus(FILE_ID, "AVAILABLE");
        mapper.makeCompensationDue(Long.toString(versionId));
        scanner.scan(SCAN);

        assertEquals(BinLibraryVersionStatus.VALID.getCode(), awaitImported(1).get("status"));
        awaitTaskDeleted();
        assertEquals(3, mapper.countBinRanges(versionId));
    }

    /** 扫描隔离与文件已不存在：INVALID 与文件级原因，不下载。 */
    @Test
    void test_rejectUnavailableFile() {
        byte[] csv = rows(1, BinLibraryCsvBuilder.template());
        platform.mockCsvFile(FILE_ID, csv).setScanStatus("QUARANTINED");

        binLibrary.importingVersion(1, FILE_ID, PlatformMocker.sha256(csv));
        binLibrary.importingVersion(2, "880002", PlatformMocker.sha256(csv));

        var quarantined = awaitImported(1);
        var missing = awaitImported(2);
        awaitTaskDeleted();
        assertAll(() -> assertEquals(BinLibraryVersionStatus.INVALID.getCode(), quarantined.get("status")),
            () -> assertEquals("导入文件扫描未通过或已不可用", quarantined.get("failureReason")),
            () -> assertEquals(BinLibraryVersionStatus.INVALID.getCode(), missing.get("status")),
            () -> assertEquals("导入文件已不存在", missing.get("failureReason")),
            () -> assertEquals(0, platform.downloadCount(FILE_ID)));
    }

    /** 表头不符：文件级失败，不记录行错误、不写号段。 */
    @Test
    void test_headerMismatch() {
        byte[] csv = BinLibraryCsvBuilder.template().header("bin,brand,funding,level,product,country,issuer")
            .row("411111", CardBrand.VISA.getCode(), null, null, null, "US", null).build();
        platform.mockCsvFile(FILE_ID, csv);

        long versionId = binLibrary.importingVersion(1, FILE_ID, PlatformMocker.sha256(csv));

        var imported = awaitImported(1);
        awaitTaskDeleted();
        assertAll(() -> assertEquals(BinLibraryVersionStatus.INVALID.getCode(), imported.get("status")),
            () -> assertTrue(String.valueOf(imported.get("failureReason")).startsWith("表头不符")),
            () -> assertNull(imported.get("totalRows")), () -> assertTrue(mapper.binImportErrors(versionId).isEmpty()),
            () -> assertEquals(0, mapper.countBinRanges(versionId)));
    }

    /** 实际字节摘要与创建时冻结值不一致：INVALID，不写号段，只下载一次。 */
    @Test
    void test_contentHashMismatch() {
        byte[] csv = rows(2, BinLibraryCsvBuilder.template());
        platform.mockCsvFile(FILE_ID, csv);

        long versionId = binLibrary.importingVersion(1, FILE_ID, PlatformMocker.sha256(new byte[] {1}));

        var imported = awaitImported(1);
        awaitTaskDeleted();
        assertAll(() -> assertEquals("文件内容摘要与创建版本时不一致", imported.get("failureReason")),
            () -> assertEquals(0, mapper.countBinRanges(versionId)), () -> assertEquals(1, platform.downloadCount(FILE_ID)));
    }

    /** 250 行错误只记录前 200 条，错误行数为 250；含卡号样式的值不回显，日志也不出现。 */
    @Test
    void test_errorRowsAreCapped(CapturedOutput output) {
        BinLibraryCsvBuilder builder = BinLibraryCsvBuilder.template();
        builder.row(PAN_LIKE, CardBrand.VISA.getCode(), null, null, null, "US", null);
        for (int index = 1; index < 250; index++) {
            builder.row(Integer.toString(500000 + index), CardBrand.VISA.getCode(), null, null, null, "ZZ", null);
        }
        byte[] csv = builder.build();
        platform.mockCsvFile(FILE_ID, csv);

        long versionId = binLibrary.importingVersion(1, FILE_ID, PlatformMocker.sha256(csv));

        var imported = awaitImported(1);
        awaitTaskDeleted();
        var errors = mapper.binImportErrors(versionId);
        assertAll(() -> assertEquals(BinLibraryVersionStatus.INVALID.getCode(), imported.get("status")),
            () -> assertEquals(250, imported.get("errorRows")), () -> assertEquals(250, imported.get("totalRows")),
            () -> assertEquals(BinLibraryCsv.MAX_RECORDED_ERRORS, errors.size()),
            () -> assertEquals(BinImportErrorCode.INVALID_FORMAT.getCode(), errors.getFirst().get("errorCode")),
            () -> assertNull(errors.getFirst().get("valueExcerpt")),
            () -> assertEquals(BinImportErrorCode.COUNTRY_REGION_MISSING.getCode(), errors.get(1).get("errorCode")),
            () -> assertFalse(errors.toString().contains(PAN_LIKE)), () -> assertFalse(output.getAll().contains(PAN_LIKE)));
    }

    /** 第二遍下载中断：任务可重试并留下部分号段；Scanner 重入后先清理残留，最终恰好 2500 行。 */
    @Test
    void test_resumeAfterInterruptedWrite() {
        byte[] csv = rows(2500, BinLibraryCsvBuilder.template());
        platform.mockCsvFile(FILE_ID, csv);
        platform.interruptDownload(FILE_ID, 2, csv.length * 3 / 4);

        long versionId = binLibrary.importingVersion(1, FILE_ID, PlatformMocker.sha256(csv));
        await().atMost(IMPORT_WAIT).until(() -> "ERROR".equals(task().get("executeStatus")));
        int partial = mapper.countBinRanges(versionId);
        assertAll(() -> assertTrue(partial >= 1000 && partial < 2500, "中断前已写入部分批次: " + partial),
            () -> assertEquals(BinLibraryVersionStatus.VALIDATING.getCode(), version(1).get("status")));

        mapper.makeCompensationDue(Long.toString(versionId));
        scanner.scan(SCAN);

        var imported = awaitImported(1);
        awaitTaskDeleted();
        assertAll(() -> assertEquals(BinLibraryVersionStatus.VALID.getCode(), imported.get("status")),
            () -> assertEquals(2500, mapper.countBinRanges(versionId)), () -> assertEquals(2500, imported.get("processedRows")),
            () -> assertEquals(4, platform.downloadCount(FILE_ID)));
    }

    /** 版本已不在 VALIDATING（重复唤醒或已处理）：任务直接成功并删除，不访问 Platform。 */
    @Test
    void test_skipFinishedVersion() {
        long versionId = binLibrary.version(1, BinLibraryVersionStatus.VALID, 0);

        binLibrary.registerImport(versionId);

        awaitTaskDeleted();
        assertAll(() -> assertEquals(BinLibraryVersionStatus.VALID.getCode(), version(1).get("status")),
            () -> assertEquals(0, platform.fileQueryTotal()), () -> assertEquals(0, platform.countryRegionQueryCount()));
    }

    /** n 行合法号段：前缀 400000 起递增，交替卡组织，发卡国 US。 */
    static byte[] rows(int count, BinLibraryCsvBuilder builder) {
        for (int index = 0; index < count; index++) {
            builder.row(Integer.toString(400000 + index), index % 2 == 0 ? CardBrand.VISA.getCode() : CardBrand.MASTERCARD.getCode(),
                CardFundingType.CREDIT.getCode(), "CLASSIC", CardProduct.CONSUMER.getCode(), "US", "SYNTHETIC ISSUER " + index);
        }
        return builder.build();
    }

    /** 唯一的导入任务。 */
    Map<String, Object> task() {
        var events = mapper.compensationEvents();
        assertEquals(1, events.size(), "应只有一个导入任务");
        return events.getFirst();
    }

    /** 等待导入任务成功后被删除。 */
    void awaitTaskDeleted() {
        await().atMost(IMPORT_WAIT).pollInterval(Duration.ofMillis(100)).until(() -> mapper.compensationEvents().isEmpty());
    }
}
