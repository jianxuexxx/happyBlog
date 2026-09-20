package com.blog.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 * 本切片的 category/list 不分页（分类量级小），分页插件在此就位供后续 article 列表直接使用。
 * MetaObjectHandler 由容器自动侦测，不在此注册。
 *
 * 【@MapperScan 为何在本类而不是 MyBlogApplication —— 勿挪回去】
 * 若把 @MapperScan 标注在 @SpringBootApplication 类上，它会随该类一起被每一个
 * @WebMvcTest 切片加载（切片以启动类为配置类，而 MapperScannerRegistrar 是
 * ImportBeanDefinitionRegistrar，不受切片的类型过滤影响）。切片里没有
 * MybatisPlusAutoConfiguration，因而没有 SqlSessionFactory，每个 MapperFactoryBean
 * 都会在 afterPropertiesSet 时抛
 * `IllegalArgumentException: Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required`，
 * 整个切片上下文加载失败 —— 表现为 Web 层测试全红，却与 Web 层代码毫无关系。
 * 放在这个普通 @Configuration 上则相反：本类不属于 @WebMvcTest 的纳入类型
 * （只纳入 @Controller / @ControllerAdvice / Filter / WebMvcConfigurer /
 * HandlerInterceptor 等），故切片不扫它，Mapper 不会被注册。
 * 运行时行为不变：@SpringBootApplication 组件扫描 com.blog.** 仍会扫到本类。
 */
@Configuration
@MapperScan("com.blog.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
