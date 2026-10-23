package com.paytech.billing.test.mocker.external;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.commercial.api.price.request.ResolveCommercialPriceRequestBody;
import com.paytech.commercial.api.price.response.CommercialPriceResponseBody;

/**
 * commercial 价格命中 Mock：按"条款类型 + 条款版本"配置响应或故障，并保存每次请求的最小快照（Owner、合同、OE、维度事实）供断言
 * <li>匹配标识见 {@link #key(String, long)}。</li>
 */
@TestComponent
public class CommercialPriceMocker extends DtoResponseMocker<String, CommercialPriceResponseBody> {
    private final List<ResolveCommercialPriceRequestBody> requests = new ArrayList<>();

    /** 上游名 commercial。 */
    public CommercialPriceMocker() {
        super("commercial");
    }

    /** 匹配标识：条款类型与条款版本。 */
    public static String key(String termType, long termVersionId) {
        return termType + ":" + termVersionId;
    }

    /** 由 Mock Controller 调用：保存请求并按条款类型与版本应答。 */
    public CommercialPriceResponseBody resolve(ResolveCommercialPriceRequestBody body) {
        synchronized (this) {
            requests.add(body);
        }
        return answer("resolve", body.getTermType() + ":" + body.getTermVersionId());
    }

    /** 给定条款类型的唯一一次请求。 */
    public synchronized ResolveCommercialPriceRequestBody onlyRequest(String termType) {
        List<ResolveCommercialPriceRequestBody> matched =
            requests.stream().filter(request -> termType.equals(request.getTermType())).toList();
        if (matched.size() != 1) {
            throw new AssertionError("commercial " + termType + " 请求次数应为 1，实际 " + matched.size());
        }
        return matched.getFirst();
    }

    /** 每例清除配置、调用与请求快照。 */
    @Override
    public synchronized void clear() {
        super.clear();
        requests.clear();
    }
}
