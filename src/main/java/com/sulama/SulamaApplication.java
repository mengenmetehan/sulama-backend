package com.sulama;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SulamaApplication {

    private static final Logger log = LoggerFactory.getLogger(SulamaApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(SulamaApplication.class, args);
        log.info("Sulama Backend basariyla basladi");
    }
}
