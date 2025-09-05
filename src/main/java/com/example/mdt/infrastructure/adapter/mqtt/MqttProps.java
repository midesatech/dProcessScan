package com.example.mdt.infrastructure.adapter.mqtt;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Mantiene los nombres de propiedades existentes para no romper la inyección.
 * Se usan grupos de validación para aplicar @NotBlank SOLO en PROD.
 */
@ConfigurationProperties(prefix = "mqtt")
@Validated
public class MqttProps {

    /** Grupo de validación que solo se ejecutará en perfil PROD. */
    public interface ProdGroup {}

    @NotNull
    private Integer qos = 1;

    @NotNull
    private Boolean cleanStart = Boolean.TRUE;

    /** Requerido en PROD */
    @NotBlank(groups = ProdGroup.class)
    private String brokerUrl;

    /** Requerido en PROD */
    @NotBlank(groups = ProdGroup.class)
    private String clientId;

    /** Opcionales en la mayoría de despliegues */
    private String username;
    private String password;

    /** Requeridos en PROD */
    @NotBlank(groups = ProdGroup.class)
    private String topicPass;

    @NotBlank(groups = ProdGroup.class)
    private String topicAck;

    // ---- getters usados por el código existente ----
    public Integer qos() { return qos; }
    public Boolean cleanStart() { return cleanStart; }
    public String brokerUrl() { return brokerUrl; }
    public String clientId() { return clientId; }
    public String username() { return username; }
    public String password() { return password; }
    public String topicPass() { return topicPass; }
    public String topicAck() { return topicAck; }

    // setters para binding de ConfigurationProperties
    public void setQos(Integer qos) { this.qos = qos; }
    public void setCleanStart(Boolean cleanStart) { this.cleanStart = cleanStart; }
    public void setBrokerUrl(String brokerUrl) { this.brokerUrl = brokerUrl; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
    public void setTopicPass(String topicPass) { this.topicPass = topicPass; }
    public void setTopicAck(String topicAck) { this.topicAck = topicAck; }
}
