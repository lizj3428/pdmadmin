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
                        .title("校园资讯推送平台 · 接口文档")
                        .version("1.0.0")
                        .description("基于 Spring Boot 的校园资讯推送平台，面向管理员、教师和学生，"
                                + "提供资讯投稿与审核、分类与标签、订阅管理、定向与定时推送、站内消息及数据统计接口。"
                                + "\n\n毕业设计题目：《基于 Spring Boot 的校园资讯推送平台设计与实现》。")
                        .contact(new Contact().name("李志军").url("https://github.com/lizj3428")));
    }


}
