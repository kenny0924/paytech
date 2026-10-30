package com.paytech.payout.app.configuration;

import org.apache.ibatis.type.TypeHandlerRegistry;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.mybatis.typehandler.CodeEnumTypeHandler;
import com.paytech.foundation.mybatis.typehandler.InstantTypeHandler;
import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryActorType;
import com.paytech.payout.api.enums.BeneficiaryEventAction;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.api.enums.BeneficiaryRailType;
import com.paytech.payout.api.enums.CheckerDecision;
import com.paytech.payout.api.enums.MakerReviewDecision;

/**
 * payout-service MyBatis 装配
 * <li>单 DataSource（mor_payout）/ 单事务管理器；{@code @MapperScan} 只扫描本服务 core 的 Mapper 包，
 * paytech-compensation-event 自带的 Mapper 由其自动配置加载。</li>
 * <li>主键由应用分配；列名按下划线转驼峰映射到 DataObject。</li>
 * <li>DataObject 的每个闭合枚举列类型显式注册 CodeEnumTypeHandler，未知 Code 读取失败；Instant 统一按 UTC 持久化。原因代码为开放目录，用字符串承载。</li>
 */
@Configuration(proxyBeanMethods = false)
@MapperScan("com.paytech.payout.core.dal.mapper")
public class PayoutPersistenceConfiguration {

    /** payout MyBatis 全局定制。 */
    @Bean
    ConfigurationCustomizer payoutMyBatisCustomizer() {
        return configuration -> {
            configuration.setUseGeneratedKeys(false);
            configuration.setMapUnderscoreToCamelCase(true);
            TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
            registry.register(InstantTypeHandler.class);
            register(registry, BeneficiaryAccountStatus.class);
            register(registry, BeneficiaryActorType.class);
            register(registry, BeneficiaryEventAction.class);
            register(registry, BeneficiaryOwnerType.class);
            register(registry, BeneficiaryRailType.class);
            register(registry, CheckerDecision.class);
            register(registry, MakerReviewDecision.class);
        };
    }

    /** 为一个持久化枚举类型注册独立的 CodeEnumTypeHandler 实例。 */
    private static <E extends Enum<E> & CodeEnum> void register(TypeHandlerRegistry registry, Class<E> type) {
        registry.register(type, new CodeEnumTypeHandler<>(type));
    }
}
