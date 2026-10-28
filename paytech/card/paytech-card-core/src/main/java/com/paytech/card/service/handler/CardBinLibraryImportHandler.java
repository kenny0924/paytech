package com.paytech.card.service.handler;

import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.paytech.card.api.enums.BinLibraryVersionStatus;
import com.paytech.card.core.dal.dataobject.CardBinLibraryVersion;
import com.paytech.card.domain.binlibrary.BinLibraryCsv;
import com.paytech.card.domain.binlibrary.BinLibrarySourceFile;
import com.paytech.card.domain.binlibrary.BinLibraryValidation;
import com.paytech.card.integration.PlatformFileClient;
import com.paytech.card.service.CardBinLibraryQueryService;
import com.paytech.card.service.CardBinLibraryService;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.platform.support.referencedata.client.ReferenceCountry;
import com.paytech.platform.support.referencedata.client.ReferenceData;


/**
 * 本地任务 CARD_BIN_LIBRARY_IMPORT：扫描、校验并写入一个 BIN 库版本的号段（技术方案 §3.3）
 * <li>事件键为版本 ID，没有业务子类型（{@code "-"}）；与版本创建同事务登记，提交后唤醒，进程崩溃由扫描兜底。</li>
 * <li>每次执行从头开始、可重入：版本不是 VALIDATING 直接成功；扫描中返回可重试；隔离 / 拒绝 / 不存在写 INVALID。</li>
 * <li>第一遍下载、计算 SHA-256 并逐行校验：哈希不符或文件级失败写 INVALID 与原因；有行错误时一个事务写前 200 条错误与 INVALID。</li>
 * <li>第二遍先分批删除上次中断的残留号段，再重新申请下载地址流式读取，每 1000 行一个短事务写入并推进进度；最后 VALIDATING → VALID。</li>
 * <li>Platform 与下载在事务外；读流不在事务内，每批先读入内存再开事务。不记录文件行内容，失败摘要不含卡号。</li>
 * <li>国家 → 区域映射取自进程内国家参考数据快照（含全部状态，与原 Platform 国家区域接口一致），不发 HTTP；快照未就绪时
 * {@code ReferenceDataUnavailableException}（系统异常）由补偿框架按可重试处理。</li>
 * <li>写入服务经 {@link ObjectProvider} 在执行时取得：它依赖补偿登记器，而登记器依赖全部 Handler，直接注入会形成启动循环。</li>
 */
@Component
public class CardBinLibraryImportHandler implements CompensationEventHandler {
    /** 补偿事件类型。 */
    public static final String EVENT_TYPE = "CARD_BIN_LIBRARY_IMPORT";

    /** 没有业务子类型时的占位值。 */
    static final String NO_SUB_EVENT = "-";

    /** 每个写入事务的号段行数。 */
    static final int RANGE_BATCH_SIZE = 1000;

    /** 合法事件键：版本 ID。 */
    static final Pattern KEY = Pattern.compile("[1-9][0-9]{0,18}");

    private final PlatformFileClient platformFileClient;
    private final CardBinLibraryQueryService cardBinLibraryQueryService;
    private final ReferenceData referenceData;
    private final ObjectProvider<CardBinLibraryService> cardBinLibraryService;

    /**
     * 注入 Platform 端口、只读查询与延迟取得的写入服务
     *
     * @param platformFileClient Platform 文件端口
     * @param cardBinLibraryQueryService 版本读取
     * @param referenceData 进程内参考数据（国家）
     * @param cardBinLibraryService 版本写入服务（执行时取得）
     */
    public CardBinLibraryImportHandler(PlatformFileClient platformFileClient,
        CardBinLibraryQueryService cardBinLibraryQueryService, ReferenceData referenceData,
        ObjectProvider<CardBinLibraryService> cardBinLibraryService) {
        this.platformFileClient = platformFileClient;
        this.cardBinLibraryQueryService = cardBinLibraryQueryService;
        this.referenceData = referenceData;
        this.cardBinLibraryService = cardBinLibraryService;
    }

    /**
     * 纯工厂：创建版本时同事务登记
     *
     * @param versionId BIN 库版本 ID
     * @return 登记请求
     */
    public static CompensationEventRequest buildEvent(long versionId) {
        return new CompensationEventRequest(Long.toString(versionId), EVENT_TYPE, NO_SUB_EVENT);
    }

    /** 补偿事件类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 每次尝试 15 分钟（宿主 Lease 须更长），最多 30 次，10 秒起 ×2 退避至 2 分钟，覆盖扫描排队。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofMinutes(15), 30, Duration.ofSeconds(10), Duration.ofMinutes(2),
            2, 0.2);
    }

    /**
     * 导入一个版本
     * <li>事件身份非法或版本不存在为终态失败；扫描中、Platform 不可用、国家参考数据未就绪、下载中断与版本 CAS 冲突为可重试（未分类异常按结果未知有界重试）。</li>
     */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (event.payloadJson() != null || !KEY.matcher(event.eventKey()).matches()) {
            return CompensationHandlerResult.terminalFailure("BIN_LIBRARY_IMPORT_INVALID", "BIN library import event is malformed");
        }
        CardBinLibraryVersion version = cardBinLibraryQueryService.findVersion(Long.parseLong(event.eventKey()));
        if (version == null) {
            return CompensationHandlerResult.terminalFailure("BIN_LIBRARY_VERSION_MISSING", "BIN library version does not exist");
        }
        if (version.getStatus() != BinLibraryVersionStatus.VALIDATING) {
            return CompensationHandlerResult.success();
        }
        BinLibrarySourceFile file = platformFileClient.getFile(version.fileId());
        if (file == null || file.isRejected()) {
            service().rejectVersion(version, file == null ? "导入文件已不存在" : "导入文件扫描未通过或已不可用");
            return CompensationHandlerResult.success();
        }
        if (!file.isAvailable()) {
            return CompensationHandlerResult.retryableFailure("BIN_LIBRARY_FILE_SCANNING", "BIN library file is being scanned");
        }
        importVersion(version, countryRegions());
        return CompensationHandlerResult.success();
    }

    /** 国家代码 → 区域代码：快照中全部国家，不按状态过滤（与原 Platform 国家区域接口的返回口径一致）。 */
    Map<String, String> countryRegions() {
        return referenceData.countries().list().stream()
            .collect(Collectors.toUnmodifiableMap(ReferenceCountry::code, ReferenceCountry::regionCode));
    }

    /** 第一遍校验，通过后清理残留并第二遍写入。 */
    void importVersion(CardBinLibraryVersion version, Map<String, String> regions) {
        BinLibraryValidation validation =
            platformFileClient.download(version.fileId(), content -> BinLibraryCsv.validate(content, regions));
        if (validation.getFailureReason() != null) {
            service().rejectVersion(version, validation.getFailureReason());
            return;
        }
        if (!version.getFileContentHash().equals(validation.getContentHash())) {
            service().rejectVersion(version, "文件内容摘要与创建版本时不一致");
            return;
        }
        if (validation.getErrorRows() > 0) {
            service().saveValidationFailure(version, validation);
            return;
        }
        service().clearRanges(version);
        writeRanges(version, regions);
        service().completeVersion(version, validation.getTotalRows());
    }

    /** 第二遍：每批一个短事务写入号段与进度；读到的摘要必须仍等于冻结值。 */
    void writeRanges(CardBinLibraryVersion version, Map<String, String> regions) {
        int[] processedRows = {0};
        String contentHash = platformFileClient.download(version.fileId(),
            content -> BinLibraryCsv.readRanges(content, regions, RANGE_BATCH_SIZE, batch -> {
                processedRows[0] += batch.size();
                service().saveRangeBatch(version, batch, processedRows[0]);
            }));
        if (!version.getFileContentHash().equals(contentHash)) {
            throw new ErrorException("BIN 库文件第二遍读取的摘要与冻结值不一致: v" + version.getVersionNo());
        }
    }

    /** 执行时取得的写入服务。 */
    CardBinLibraryService service() {
        return cardBinLibraryService.getObject();
    }
}
