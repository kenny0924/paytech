package com.paytech.operations.console.bff.service.processor;

import static com.paytech.operations.console.bff.service.processor.ClientReviewDetailsProcessor.withRequestContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.operations.console.bff.integration.CommercialPricingClient;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialPrice;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermVersionResponseBody;

/**
 * 商务页面聚合共用的并发只读调用：若干独立 commercial 查询，以及一个版本在多个 OE 下的价格全集
 * <li>commercial 的价格读取要求准确 OE，按 OE 并发读取后合并，按 OE、币种、优先级排序；被版本编辑页、版本对比与工作台共用。</li>
 * <li>任一子调用失败按原异常抛出（保持 commercial 的错误语义），不做部分降级；子线程沿用当前请求 ID。</li>
 *
 * @author Claude
 */
final class CommercialConcurrentReads {

    /** 只承载静态方法，禁止创建实例。 */
    private CommercialConcurrentReads() {
    }

    /**
     * 读取一个版本在给定 OE 下的全部价格
     *
     * @param pricing commercial 价格端口
     * @param version 条款版本（取版本 ID 与条款类型）
     * @param entityNos 可定价 OE
     * @return 合并后按 OE、币种、优先级排序的价格
     */
    static List<CommercialPrice> termPrices(CommercialPricingClient pricing, CommercialTermVersionResponseBody version,
        List<String> entityNos) {
        var calls = new ArrayList<Supplier<List<CommercialPrice>>>();
        for (String entityNo : entityNos) {
            calls.add(() -> pricing.prices(version.getVersionId(), entityNo, version.getTermType(), null).getPrices());
        }
        var merged = new ArrayList<CommercialPrice>();
        all(calls).forEach(merged::addAll);
        merged.sort(Comparator.comparing(CommercialPrice::getEntityNo, Comparator.nullsLast(String::compareTo))
            .thenComparing(CommercialPrice::getCurrency, Comparator.nullsLast(String::compareTo))
            .thenComparing(CommercialPrice::getPriorityNo, Comparator.nullsLast(Integer::compareTo)));
        return merged;
    }

    /**
     * 并发执行若干只读调用，按入参顺序返回结果
     *
     * @param calls 只读调用
     * @return 各调用结果；任一失败按原异常抛出
     */
    static <T> List<T> all(List<Supplier<T>> calls) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<T>>();
            for (Supplier<T> call : calls) {
                Callable<T> task = withRequestContext(call);
                futures.add(executor.submit(task));
            }
            var results = new ArrayList<T>();
            for (var future : futures) {
                results.add(await(future));
            }
            return results;
        }
    }

    /** 子任务失败按原异常抛出，保持 commercial 的错误语义。 */
    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException();
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE, null, failure);
        }
    }
}
