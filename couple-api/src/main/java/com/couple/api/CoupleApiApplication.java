package com.couple.api;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 后端 API 服务启动入口。 */
@MapperScan(basePackages = "com.couple.service", annotationClass = Mapper.class)
@SpringBootApplication(scanBasePackages = {"com.couple"})
public class CoupleApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoupleApiApplication.class, args);
    }
}
