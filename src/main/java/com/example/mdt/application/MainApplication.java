package com.example.mdt.application;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.example.mdt")
@ConfigurationPropertiesScan(basePackages = "com.example.mdt")
@EnableJpaRepositories(basePackages = "com.example.mdt.infrastructure.adapter.mariadb.repository")
@EntityScan(basePackages = "com.example.mdt.infrastructure.adapter.mariadb.entity")
public class MainApplication {
    public static void main(String[] args) { SpringApplication.run(MainApplication.class, args); }
}
