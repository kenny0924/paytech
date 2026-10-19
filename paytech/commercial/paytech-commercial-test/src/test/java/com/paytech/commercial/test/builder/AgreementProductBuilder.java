package com.paytech.commercial.test.builder;

import java.time.Instant;

import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.core.dal.dataobject.AgreementProduct;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;

/**
 * t_agreement_product 测试行的 Builder：template() 给出合法默认值，用例只覆盖与场景相关的字段。
 * <li>不访问数据库；可显式设置 null 或非法组合构造负例，build() 不修复输入。</li>
 */
public final class AgreementProductBuilder {
    /** 默认订阅行 ID */
    public static final long AGREEMENT_PRODUCT_ID = 920000000000301L;

    /** 合成 Checkout 夹具（checkout-query.json）第一份 CLIENT 合同 */
    public static final long AGREEMENT_ID = 910000000000106L;

    private final AgreementProduct agreementProduct = new AgreementProduct();

    private AgreementProductBuilder() {
    }

    /**
     * 合成 Checkout 夹具第一份 CLIENT 合同订阅 ACQUIRING。
     *
     * @return 新 Builder
     */
    public static AgreementProductBuilder template() {
        return new AgreementProductBuilder()
            .agreementProductId(AGREEMENT_PRODUCT_ID)
            .agreementId(AGREEMENT_ID)
            .productCode(ProductCode.ACQUIRING)
            .createTime(CheckoutFixture.NOW)
            .updateTime(CheckoutFixture.NOW);
    }

    /** 设置合同订阅产品行的内部唯一标识。 */
    public AgreementProductBuilder agreementProductId(Long value) {
        agreementProduct.setAgreementProductId(value);
        return this;
    }

    /** 设置所属合同。 */
    public AgreementProductBuilder agreementId(Long value) {
        agreementProduct.setAgreementId(value);
        return this;
    }

    /** 设置订阅的产品代码。 */
    public AgreementProductBuilder productCode(ProductCode value) {
        agreementProduct.setProductCode(value);
        return this;
    }

    /** 设置记录创建的 UTC 微秒时间。 */
    public AgreementProductBuilder createTime(Instant value) {
        agreementProduct.setCreateTime(value);
        return this;
    }

    /** 设置记录最近一次持久化更新的 UTC 微秒时间。 */
    public AgreementProductBuilder updateTime(Instant value) {
        agreementProduct.setUpdateTime(value);
        return this;
    }

    /** 返回本 Builder 持有的对象；多次调用返回同一实例。 */
    public AgreementProduct build() {
        return agreementProduct;
    }
}
