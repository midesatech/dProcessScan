package com.example.mdt.application.config;

import com.example.mdt.domain.gateway.DeteccionesGateway;
import com.example.mdt.domain.gateway.MetadataGateway;
import com.example.mdt.domain.usecase.ProcessScanUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {
    @Bean
    public ProcessScanUseCase processScanUseCase(DeteccionesGateway detGateway, MetadataGateway metadataGateway) {
        return new ProcessScanUseCase(detGateway, metadataGateway);
    }
}
