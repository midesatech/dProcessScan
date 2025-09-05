package com.example.mdt.application.config;

import com.example.mdt.infrastructure.adapter.mqtt.MqttProps;
import com.example.mdt.infrastructure.adapter.mqtt.MqttProps.ProdGroup;
import jakarta.annotation.PostConstruct;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Valida las propiedades críticas SOLO en PROD.
 * Si falta alguna variable requerida, falla temprano con un mensaje claro,
 * sin afectar el comportamiento resiliente de conexión a servicios.
 */
@Configuration
@Profile("prod")
public class ProdPropsValidatorConfig {

    private static final Logger log = LoggerFactory.getLogger(ProdPropsValidatorConfig.class);

    private final MqttProps mqttProps;
    private final Validator validator;

    public ProdPropsValidatorConfig(MqttProps mqttProps, Validator validator) {
        this.mqttProps = mqttProps;
        this.validator = validator;
    }

    @PostConstruct
    public void validate() {
        var violations = validator.validate(mqttProps, ProdGroup.class);
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder("Configuration validation failed (profile=prod):\n");
            for (ConstraintViolation<?> v : violations) {
                sb.append(" - mqtt.").append(v.getPropertyPath()).append(": ").append(v.getMessage());
                Object invalid = v.getInvalidValue();
                if (invalid != null) sb.append(" [value='").append(invalid).append("']");
                sb.append("\n");
            }
            String msg = sb.toString();
            log.error(msg);
            throw new IllegalStateException(msg);
        } else {
            log.info("Configuration validation OK for profile=prod (mqtt props)");
        }
    }
}
