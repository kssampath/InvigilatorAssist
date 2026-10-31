package com.invigilatorassist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class InvigilatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(InvigilatorApplication.class, args);
    }
}
