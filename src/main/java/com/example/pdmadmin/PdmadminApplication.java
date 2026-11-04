package com.example.pdmadmin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.example.pdmadmin.mapper")
// 开启定时任务：推送模块的 PushScheduler 靠它按计划时间扫表执行
@EnableScheduling
public class PdmadminApplication {

	public static void main(String[] args) {
		SpringApplication.run(PdmadminApplication.class, args);
	}

}
