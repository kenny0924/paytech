package com.paytech.foundation.idempotency.core.autoconfigure;

import java.io.IOException;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.idempotency.api.CommandIdempotencyService;
import com.paytech.foundation.idempotency.persistence.mapper.CommandIdempotencyMapper;
import com.paytech.foundation.idempotency.persistence.repository.CommandIdempotencyRepository;
import com.paytech.foundation.idempotency.persistence.repository.CommandIdempotencyRepositoryImpl;
import com.paytech.foundation.idempotency.service.CommandIdempotencyServiceImpl;

/** 显式启用的服务本地命令幂等自动配置。 */
@AutoConfiguration
@AutoConfigureAfter(MybatisAutoConfiguration.class)
@ConditionalOnClass({SqlSessionFactory.class, CommandIdempotencyService.class})
@ConditionalOnProperty(prefix = "command-idempotency", name = "enabled", havingValue = "true")
public class CommandIdempotencyAutoConfiguration {

    private static final String MAPPER_RESOURCE = "META-INF/mybatis/CommandIdempotencyMapper.xml";

    @Bean
    @ConditionalOnMissingBean(CommandIdempotencyMapper.class)
    MapperFactoryBean<CommandIdempotencyMapper> commandIdempotencyMapper(SqlSessionFactory sqlSessionFactory) {
        loadMapperXml(sqlSessionFactory);
        MapperFactoryBean<CommandIdempotencyMapper> factory = new MapperFactoryBean<>(CommandIdempotencyMapper.class);
        factory.setSqlSessionFactory(sqlSessionFactory);
        factory.setAddToConfig(false);
        return factory;
    }

    @Bean
    @ConditionalOnMissingBean(CommandIdempotencyRepository.class)
    CommandIdempotencyRepository commandIdempotencyRepository(CommandIdempotencyMapper mapper) {
        return new CommandIdempotencyRepositoryImpl(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(CommandIdempotencyService.class)
    CommandIdempotencyService commandIdempotencyService(CommandIdempotencyRepository repository) {
        return new CommandIdempotencyServiceImpl(repository);
    }

    private void loadMapperXml(SqlSessionFactory sqlSessionFactory) {
        Configuration configuration = sqlSessionFactory.getConfiguration();
        synchronized (configuration) {
            if (configuration.hasStatement(CommandIdempotencyMapper.class.getName() + ".insertOrKeepWinner", false)) {
                return;
            }
            try (var input = Resources.getResourceAsStream(MAPPER_RESOURCE)) {
                XMLMapperBuilder builder =
                    new XMLMapperBuilder(input, configuration, MAPPER_RESOURCE, configuration.getSqlFragments());
                builder.parse();
            } catch (IOException exception) {
                throw new ErrorException("无法加载命令幂等 MyBatis Mapper: " + MAPPER_RESOURCE, exception);
            }
        }
    }
}
