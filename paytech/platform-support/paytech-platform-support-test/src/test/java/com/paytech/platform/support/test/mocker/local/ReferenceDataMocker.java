package com.paytech.platform.support.test.mocker.local;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.api.enums.FileStorageCredentialMode;
import com.paytech.platform.support.api.enums.FileStorageInstanceStatus;
import com.paytech.platform.support.api.enums.FileStorageType;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.test.builder.FileBuilder;
import com.paytech.platform.support.test.dal.ReferenceDataTestMapper;

import lombok.RequiredArgsConstructor;

/**
 * 参考数据前置条件：核对人工初始化的种子，按需登记 logo 文件，并临时改写种子行的 logo 或状态。
 * <li>种子（国家、行政区、行业、MCC）由人工初始化，测试不插入、不删除；本例改写过的种子行在清理时逐行恢复为无 logo、ACTIVE。</li>
 * <li>logo 文件只登记文件服务元数据（停用的测试实例、公开桶与文件行），不写入存储；这些表由 {@link PlatformDataCleaner} 整表回收。</li>
 */
@TestComponent
@RequiredArgsConstructor
public class ReferenceDataMocker implements Clear {

    /** 测试 logo 所在的存储实例；状态为 DISABLED，存储快照不加载它。 */
    static final String INSTANCE = "UT_REFDATA";

    /** 测试公开桶。 */
    static final String PUBLIC_BUCKET = "ut-refdata-public";

    /** 测试公开桶的对外基础地址。 */
    public static final String PUBLIC_BASE_URL = "https://cdn.example.com/refdata";

    private static final String SETUP = "；请在 mor_platform 人工执行 paytech/platform-support/docs/database/sql/changes/"
        + "2026-10-27-reference-data.sql（或 paytech-platform-support-test/src/test/resources/init/ddl.sql 中同样的建表与种子语句）";

    private final FileMocker files;
    private final ReferenceDataTestMapper mapper;
    private final StorageInstanceMocker storageInstances;
    private final Set<String> countries = new LinkedHashSet<>();
    private final Set<String> industries = new LinkedHashSet<>();
    private final Set<String> subdivisions = new LinkedHashSet<>();
    private final Set<String> mccs = new LinkedHashSet<>();
    private boolean bucketReady;

    /** 五张参考数据表与完整种子已人工准备，且没有上次中断遗留的 logo 或状态改写。 */
    public void assertSeeded() {
        List<String> tables = mapper.referenceTables();
        if (tables.size() != 5) {
            fail("缺少参考数据表，当前只有 " + tables + SETUP);
        }
        Map<String, Object> seeds = mapper.seedCounts();
        assertAll(() -> assertEquals(249, mapper.countrySeedCount(), "t_ref_country 种子不完整" + SETUP),
            () -> assertEquals(249, mapper.completeCountryCount(), "t_ref_country 未回填 ISO 编码与英文名" + SETUP),
            () -> assertEquals(47L, ((Number) seeds.get("subdivisions")).longValue(), "行政区种子不完整" + SETUP),
            () -> assertEquals(42L, ((Number) seeds.get("industries")).longValue(), "行业种子不完整" + SETUP),
            () -> assertTrue(((Number) seeds.get("mccs")).longValue() > 0, "MCC 种子缺失" + SETUP),
            () -> assertEquals(0, mapper.modifiedSeedCount(), "参考数据种子存在 logo 或非 ACTIVE 状态，可能是上次中断的残留，请人工恢复"));
    }

    /**
     * 登记一个已通过检查的公开 logo 文件
     *
     * @return 文件地址与预期公开地址
     */
    public Logo publicLogo() {
        return logo(FileAccessLevel.PUBLIC);
    }

    /**
     * 登记一个私有文件（不是公开文件，没有公开地址）
     *
     * @return 文件地址；预期公开地址为空
     */
    public Logo privateLogo() {
        return logo(FileAccessLevel.PRIVATE);
    }

    /** 临时给国家种子设置 logo。 */
    public void countryLogo(String countryCode, String fileAddress) {
        countries.add(countryCode);
        assertEquals(1, mapper.countryLogo(countryCode, fileAddress));
    }

    /** 临时给行业种子设置图标。 */
    public void industryLogo(String industryCode, String fileAddress) {
        industries.add(industryCode);
        assertEquals(1, mapper.industryLogo(industryCode, fileAddress));
    }

    /** 临时改写国家种子状态。 */
    public void countryStatus(String countryCode, ReferenceDataStatus status) {
        countries.add(countryCode);
        assertEquals(1, mapper.countryStatus(countryCode, status.getCode()));
    }

    /** 临时改写行政区种子状态。 */
    public void subdivisionStatus(String subdivisionCode, ReferenceDataStatus status) {
        subdivisions.add(subdivisionCode);
        assertEquals(1, mapper.subdivisionStatus(subdivisionCode, status.getCode()));
    }

    /** 临时改写 MCC 种子状态。 */
    public void mccStatus(String mccCode, ReferenceDataStatus status) {
        mccs.add(mccCode);
        assertEquals(1, mapper.mccStatus(mccCode, status.getCode()));
    }

    /** 逐行恢复本例改写过的种子；文件元数据由公共清理整表回收。 */
    @Override
    public void clear() {
        try {
            String active = ReferenceDataStatus.ACTIVE.getCode();
            countries.forEach(code -> {
                mapper.countryLogo(code, null);
                mapper.countryStatus(code, active);
            });
            industries.forEach(code -> mapper.industryLogo(code, null));
            subdivisions.forEach(code -> mapper.subdivisionStatus(code, active));
            mccs.forEach(code -> mapper.mccStatus(code, active));
        } finally {
            countries.clear();
            industries.clear();
            subdivisions.clear();
            mccs.clear();
            bucketReady = false;
        }
    }

    /** 首次使用时登记停用的测试实例与公开桶，再登记指定访问级别、检查已通过的文件。 */
    private Logo logo(FileAccessLevel accessLevel) {
        if (!bucketReady) {
            storageInstances.instance(INSTANCE, FileStorageType.MINIO, FileStorageCredentialMode.STATIC, "ut-refdata",
                false, FileStorageInstanceStatus.DISABLED);
            storageInstances.publicBucket(INSTANCE, PUBLIC_BUCKET, PUBLIC_BASE_URL, FileStorageBucketStatus.ACTIVE);
            bucketReady = true;
        }
        String objectRef = "refdata/" + UUID.randomUUID() + ".png";
        var spec = files.file(FileBuilder.template(INSTANCE, PUBLIC_BUCKET, objectRef).mime("image/png")
            .bytes(FileBuilder.PNG).accessLevel(accessLevel).version("v1").build());
        String publicUrl = accessLevel == FileAccessLevel.PUBLIC ? PUBLIC_BASE_URL + "/" + objectRef : null;
        return new Logo(FileAddress.of(spec.fileId()).value(), publicUrl);
    }

    /**
     * 登记的 logo 文件。
     *
     * @param fileAddress 文件服务地址
     * @param publicUrl 预期公开地址；不是公开文件时为空
     */
    public record Logo(String fileAddress, String publicUrl) {
    }
}
