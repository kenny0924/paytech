package com.paytech.platform.support.referencedata.client.internal;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.client.ReferenceCatalog;
import com.paytech.platform.support.referencedata.client.ReferenceCountry;
import com.paytech.platform.support.referencedata.client.ReferenceCurrency;
import com.paytech.platform.support.referencedata.client.ReferenceData;
import com.paytech.platform.support.referencedata.client.ReferenceDataKind;
import com.paytech.platform.support.referencedata.client.ReferenceDataNotFoundException;
import com.paytech.platform.support.referencedata.client.ReferenceDataUnavailableException;
import com.paytech.platform.support.referencedata.client.ReferenceIndustry;
import com.paytech.platform.support.referencedata.client.ReferenceItem;
import com.paytech.platform.support.referencedata.client.ReferenceMcc;
import com.paytech.platform.support.referencedata.client.ReferenceSubdivision;

/**
 * 进程内参考数据快照：每个已开启种类一份不可变 Map，刷新成功时按种类整体原子替换，失败时保留旧快照并累计失败。
 * <li>读方法只读 {@link AtomicReference} 当前值，不发 HTTP、不触发刷新；不同种类之间不要求同一时点。</li>
 * <li>未开启的种类没有状态，读取时报"未配置"；已开启但未加载成功时报"未就绪"。</li>
 */
public final class ReferenceDataSnapshot implements ReferenceData {

    private final Map<ReferenceDataKind, KindState> states = new EnumMap<>(ReferenceDataKind.class);

    private final SnapshotCatalog<ReferenceCurrency> currencies = new SnapshotCatalog<>(ReferenceDataKind.CURRENCY);

    private final SnapshotCatalog<ReferenceCountry> countries = new SnapshotCatalog<>(ReferenceDataKind.COUNTRY);

    private final SnapshotCatalog<ReferenceSubdivision> subdivisions =
        new SnapshotCatalog<>(ReferenceDataKind.SUBDIVISION);

    private final SnapshotCatalog<ReferenceIndustry> industries = new SnapshotCatalog<>(ReferenceDataKind.INDUSTRY);

    private final SnapshotCatalog<ReferenceMcc> mccs = new SnapshotCatalog<>(ReferenceDataKind.MCC);

    /**
     * 按已开启种类建立空快照
     *
     * @param enabledKinds 已开启的种类，非空
     */
    public ReferenceDataSnapshot(Set<ReferenceDataKind> enabledKinds) {
        for (ReferenceDataKind kind : enabledKinds) {
            states.put(kind, new KindState());
        }
    }

    /** 币种目录视图。 */
    @Override
    public ReferenceCatalog<ReferenceCurrency> currencies() {
        return currencies;
    }

    /** 国家目录视图。 */
    @Override
    public ReferenceCatalog<ReferenceCountry> countries() {
        return countries;
    }

    /** 行政区目录视图。 */
    @Override
    public ReferenceCatalog<ReferenceSubdivision> subdivisions() {
        return subdivisions;
    }

    /** 行业目录视图。 */
    @Override
    public ReferenceCatalog<ReferenceIndustry> industries() {
        return industries;
    }

    /** MCC目录视图。 */
    @Override
    public ReferenceCatalog<ReferenceMcc> mccs() {
        return mccs;
    }

    /** 已开启的种类，按声明顺序。 */
    public Set<ReferenceDataKind> enabledKinds() {
        return states.keySet();
    }

    /** 已开启种类中尚未首次加载成功的种类，按声明顺序。 */
    public List<ReferenceDataKind> unloadedKinds() {
        return states.entrySet().stream().filter(entry -> entry.getValue().loaded.get() == null)
            .map(Map.Entry::getKey).toList();
    }

    /** 是否每个已开启的种类都至少成功加载过一次。 */
    public boolean allLoaded() {
        return unloadedKinds().isEmpty();
    }

    /**
     * 以一份完整、已校验的条目整体替换该种类快照，并清零连续失败
     *
     * @param kind 已开启的种类
     * @param items 该种类全部条目，编码已唯一
     * @param loadedAt 加载完成时间
     */
    public void replace(ReferenceDataKind kind, Collection<? extends ReferenceItem> items, Instant loadedAt) {
        KindState state = requireState(kind);
        Map<String, ReferenceItem> sorted = new TreeMap<>();
        for (ReferenceItem item : items) {
            sorted.put(item.code(), item);
        }
        state.loaded.set(new Loaded(Map.copyOf(sorted), List.copyOf(sorted.values()), loadedAt));
        state.consecutiveFailures.set(0);
    }

    /**
     * 记录该种类一次刷新失败，保留旧快照
     *
     * @param kind 已开启的种类
     * @param failure 失败原因；只保留异常类名
     */
    public void recordFailure(ReferenceDataKind kind, Throwable failure) {
        KindState state = requireState(kind);
        state.consecutiveFailures.incrementAndGet();
        state.lastFailure = failure.getClass().getName();
    }

    /**
     * 该种类的健康明细
     *
     * @param kind 已开启的种类
     * @return 条数、最近成功时间、连续失败次数与最近失败类名
     */
    public KindStatus status(ReferenceDataKind kind) {
        KindState state = requireState(kind);
        Loaded loaded = state.loaded.get();
        return new KindStatus(loaded == null ? 0 : loaded.sorted().size(), loaded == null ? null : loaded.loadedAt(),
            state.consecutiveFailures.get(), state.lastFailure);
    }

    private KindState requireState(ReferenceDataKind kind) {
        KindState state = states.get(kind);
        if (state == null) {
            throw ReferenceDataUnavailableException.notConfigured(kind);
        }
        return state;
    }

    /**
     * 一个种类的健康明细。
     *
     * @param count 当前快照条数；未加载时为 0
     * @param loadedAt 最近一次成功加载时间；未加载时为 null
     * @param consecutiveFailures 自最近一次成功以来连续失败次数
     * @param lastFailure 最近一次失败的异常类名；从未失败时为 null
     */
    public record KindStatus(int count, Instant loadedAt, int consecutiveFailures, String lastFailure) {
    }

    /** 一个已开启种类的可变状态；快照本身不可变，只替换引用。 */
    private static final class KindState {
        private final AtomicReference<Loaded> loaded = new AtomicReference<>();

        private final AtomicInteger consecutiveFailures = new AtomicInteger();

        private volatile String lastFailure;
    }

    /** 一份已加载的不可变快照。 */
    private record Loaded(Map<String, ReferenceItem> items, List<ReferenceItem> sorted, Instant loadedAt) {
    }

    /** 某个种类的只读视图，每次读取当前快照引用。 */
    private final class SnapshotCatalog<T extends ReferenceItem> implements ReferenceCatalog<T> {
        private final ReferenceDataKind kind;

        /** 绑定种类。 */
        private SnapshotCatalog(ReferenceDataKind kind) {
            this.kind = kind;
        }

        /** 查不到抛系统异常，保留消息重投语义。 */
        @Override
        public T require(String code) {
            return find(code).orElseThrow(() -> new ReferenceDataNotFoundException(kind, code));
        }

        /** 先要求存在，再要求 ACTIVE；非 ACTIVE 是确定的业务拒绝。 */
        @Override
        public T requireActive(String code) {
            T item = require(code);
            if (item.status() != ReferenceDataStatus.ACTIVE) {
                throw new BizCheckFailException("参考数据未启用: " + kind.getCode() + " " + code + ", 状态 "
                    + item.status().getCode());
            }
            return item;
        }

        /** 未就绪仍抛异常，只有已加载且没有该编码才返回空。 */
        @Override
        @SuppressWarnings("unchecked")
        public Optional<T> find(String code) {
            Loaded loaded = loaded();
            return code == null ? Optional.empty() : Optional.ofNullable((T)loaded.items().get(code));
        }

        /** 当前快照全部条目。 */
        @Override
        @SuppressWarnings("unchecked")
        public List<T> list() {
            return (List<T>)(List<?>)loaded().sorted();
        }

        /** 当前快照；未开启或未加载时抛不可读异常。 */
        private Loaded loaded() {
            Loaded loaded = requireState(kind).loaded.get();
            if (loaded == null) {
                throw ReferenceDataUnavailableException.notReady(kind);
            }
            return loaded;
        }
    }
}
