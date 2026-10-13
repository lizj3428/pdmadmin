package com.example.pdmadmin.config;


import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * 【接口文档配置类】
 * 作用：配置Knife4j(新版Swagger)文档的基础信息
 * @Configuration 代表这是一个配置类，项目启动自动加载里面的配置
 */
@Configuration
public class SwaggerConfig {
    @Bean
    /**
     * @Bean ：把这个方法返回的OpenAPI对象交给Spring管理
     * 项目启动的时候，自动执行这个方法，生成文档的基础信息
     * @return OpenAPI 文档对象
     */
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("潘多码SpringBoot3+Vue3脚手架")
                        .version("1.0")
                        .description( "潘多码SpringBoot3+Vue3脚手架接口文档")
                        .contact(new Contact().name("潘多码").url("www.panduoma.com")));
    }


}
