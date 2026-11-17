package com.tearag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TeaRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeaRagApplication.class, args);
    }
}
