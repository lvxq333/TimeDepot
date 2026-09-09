package com.couple.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.couple"})
public class CoupleApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoupleApiApplication.class, args);
    }
}
