package com.dev.turkim.youtubemcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class YoutubeMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(YoutubeMcpServerApplication.class, args);
    }
}
