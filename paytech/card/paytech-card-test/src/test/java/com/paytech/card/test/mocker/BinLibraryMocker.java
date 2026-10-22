package com.paytech.card.test.mocker;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.card.api.enums.BinLibraryVersionStatus;
import com.paytech.card.api.enums.CardBrand;
import com.paytech.card.api.enums.CardFundingType;
import com.paytech.card.api.enums.CardProduct;
import com.paytech.card.service.handler.CardBinLibraryImportHandler;
import com.paytech.card.test.dal.CardTestMapper;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.platform.support.api.files.FileAddress;

import lombok.RequiredArgsConstructor;

/**
 * BIN 库本地固件：直接插入版本与号段（不经被测导入链路），或插入待导入版本并经真实登记器登记导入任务；由 {@link CardDataMocker} 统一清理。
 * <li>ID 取测试专用的小号段，低于 Snowflake 范围，与生产生成的 ID 不冲突。</li>
 */
@TestComponent
@RequiredArgsConstructor
public class BinLibraryMocker {
    private static final AtomicLong IDS = new AtomicLong(500_000);

    private final CardTestMapper mapper;
    private final TransactionTemplate transactionTemplate;
    private final CompensationEventRegistrar compensationEventRegistrar;

    /**
     * 插入一个待导入版本，并在同一事务经真实登记器登记导入任务（提交后由补偿执行器唤醒 Handler）
     *
     * @param versionNo 版本号
     * @param fileId Platform 文件 ID
     * @param fileContentHash 创建时冻结的文件摘要
     * @return 版本 ID
     */
    public long importingVersion(int versionNo, String fileId, String fileContentHash) {
        long versionId = IDS.incrementAndGet();
        transactionTemplate.executeWithoutResult(status -> {
            mapper.insertImportingVersion(versionId, versionNo, FileAddress.of(Long.parseLong(fileId)).value(), fileContentHash);
            compensationEventRegistrar.register(CardBinLibraryImportHandler.buildEvent(versionId));
        });
        return versionId;
    }

    /** 为已有版本经真实登记器登记导入任务（模拟重复唤醒）。 */
    public void registerImport(long versionId) {
        transactionTemplate.executeWithoutResult(
            status -> compensationEventRegistrar.register(CardBinLibraryImportHandler.buildEvent(versionId)));
    }

    /**
     * 插入一个版本
     *
     * @param versionNo 版本号
     * @param status 状态；ACTIVE 时占用生效占位
     * @param totalRows 数据行数
     * @return 版本 ID
     */
    public long version(int versionNo, BinLibraryVersionStatus status, int totalRows) {
        long versionId = IDS.incrementAndGet();
        mapper.insertBinVersion(versionId, versionNo, status.getCode(), status == BinLibraryVersionStatus.ACTIVE ? 1 : null,
            totalRows);
        return versionId;
    }

    /** 插入一个只有卡组织与发卡国的号段。 */
    public void range(long versionId, String binPrefix, CardBrand cardBrand, String issuerCountry, String issuerRegion) {
        range(versionId, binPrefix, cardBrand, null, null, null, issuerCountry, issuerRegion, null);
    }

    /**
     * 插入一个号段
     *
     * @param versionId 版本 ID
     * @param binPrefix 前缀
     * @param cardBrand 卡组织
     * @param fundingType 借贷记，可空
     * @param cardLevel 卡等级，可空
     * @param cardProduct 卡产品，可空
     * @param issuerCountry 发卡国
     * @param issuerRegion 发卡区域
     * @param issuerName 发卡行，可空
     */
    public void range(long versionId, String binPrefix, CardBrand cardBrand, CardFundingType fundingType,
        String cardLevel, CardProduct cardProduct, String issuerCountry, String issuerRegion, String issuerName) {
        mapper.insertBinRange(IDS.incrementAndGet(), versionId, binPrefix, cardBrand.getCode(),
            fundingType == null ? null : fundingType.getCode(), cardLevel, cardProduct == null ? null : cardProduct.getCode(),
            issuerCountry, issuerRegion, issuerName);
    }
}
