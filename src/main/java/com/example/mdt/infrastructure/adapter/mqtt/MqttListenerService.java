package com.example.mdt.infrastructure.adapter.mqtt;

import com.example.mdt.domain.model.Scan;
import com.example.mdt.domain.usecase.ProcessScanUseCase;
import com.example.mdt.infrastructure.adapter.backlog.BacklogStore;
import com.example.mdt.infrastructure.adapter.db.DbHealthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MqttListenerService implements MqttCallback {

    private static final Logger log = LoggerFactory.getLogger(MqttListenerService.class);

    private final MqttProps props;
    private final ProcessScanUseCase useCase;
    private final DbHealthService dbHealth;
    private final BacklogStore backlogStore;
    private final ObjectMapper mapper = new ObjectMapper();

    private MqttAsyncClient client;

    /**
     * true once we've had at least one successful connection.
     * After that, Paho's automatic reconnect takes over; we don't
     * keep doing manual reconnect attempts from the @Scheduled task.
     */
    private final AtomicBoolean everConnected = new AtomicBoolean(false);

    /**
     * Prevents "connect already in progress" by ensuring only one
     * explicit connect() attempt at a time (before the first successful connect).
     */
    private final AtomicBoolean connecting = new AtomicBoolean(false);

    public MqttListenerService(MqttProps props,
                               ProcessScanUseCase useCase,
                               DbHealthService dbHealth,
                               BacklogStore backlogStore) {
        this.props = props;
        this.useCase = useCase;
        this.dbHealth = dbHealth;
        this.backlogStore = backlogStore;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @PostConstruct
    public void start() {
        try {
            connectAndSubscribe();
        } catch (Exception e) {
            log.warn("Initial MQTT connect failed: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (client != null) {
                log.info("Disconnecting MQTT client...");
                client.disconnect();
                client.close();
            }
        } catch (Exception e) {
            log.warn("Error while disconnecting MQTT client: {}", e.getMessage(), e);
        }
    }

    /**
     * Startup helper: while we have NEVER connected, periodically try to connect.
     * Once a connection succeeds, {@link #everConnected} becomes true in connectComplete()
     * and this task effectively becomes a no-op.
     *
     * Important: we only use this for the first connection; reconnections after
     * that are handled by Paho's automatic reconnect.
     */
    @Scheduled(fixedDelayString = "${mqtt.initial-connect.check-interval-ms:5000}")
    public void ensureInitialConnection() {
        if (everConnected.get()) {
            return; // we've connected at least once; auto-reconnect handles the rest
        }

        try {
            if (client != null && client.isConnected()) {
                everConnected.set(true);
                return;
            }
            log.info("MQTT not yet connected, attempting initial connect...");
            connectAndSubscribe();
        } catch (Exception e) {
            log.warn("Initial connect retry failed: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Connection / subscription
    // -------------------------------------------------------------------------

    private synchronized void connectAndSubscribe() throws Exception {
        if (client != null && client.isConnected()) {
            log.debug("MQTT already connected, skipping connectAndSubscribe");
            return;
        }

        if (connecting.get()) {
            log.debug("MQTT connect already in progress, skipping connectAndSubscribe");
            return;
        }

        // Lazily create client if needed
        if (client == null) {
            String clientId = buildClientId(props);
            client = new MqttAsyncClient(props.brokerUrl(), clientId, new MemoryPersistence());
            client.setCallback(this);
            log.info("Created MQTT client with clientId={}", clientId);
        }

        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(props.cleanStart());
        options.setAutomaticReconnect(true);   // let Paho handle reconnects after first success
        options.setSessionExpiryInterval(0L);  // no server-side session persistence
        options.setKeepAliveInterval(30);      // seconds; tune if needed

        if (props.username() != null && !props.username().isBlank()) {
            options.setUserName(props.username());
        }
        if (props.password() != null && !props.password().isBlank()) {
            options.setPassword(props.password().getBytes(StandardCharsets.UTF_8));
        }

        connecting.set(true);
        try {
            log.info("Connecting to MQTT broker {} ...", props.brokerUrl());
            // Async connect; success/failure will be reported via callbacks.
            client.connect(options);
        } catch (Exception e) {
            connecting.set(false);
            log.warn("MQTT connect() call failed: {}", e.getMessage());
            throw e;
        }
    }

    private void subscribeAll() throws Exception {
        if (client == null || !client.isConnected()) {
            log.warn("Cannot subscribe: MQTT client is not connected");
            return;
        }

        if (props.topicPass() != null && !props.topicPass().isBlank()) {
            client.subscribe(props.topicPass(), props.qos());
            log.info("Subscribed to PASS topic '{}', qos={}", props.topicPass(), props.qos());
        } else {
            log.warn("MQTT PASS topic is not configured; no subscriptions performed");
        }
    }

    // -------------------------------------------------------------------------
    // MQTT Callback
    // -------------------------------------------------------------------------

    @Override
    public void disconnected(MqttDisconnectResponse disconnectResponse) {
        Integer code = disconnectResponse != null ? disconnectResponse.getReturnCode() : null;
        String msg = disconnectResponse != null ? disconnectResponse.getReasonString() : null;

        log.warn("MQTT disconnected: code={} msg={}", code, msg);

        // 142 = Session taken over (duplicate ClientID)
        if (Integer.valueOf(142).equals(code)) {
            log.warn("MQTT disconnect reason 142 (Session taken over). " +
                     "Another client likely connected using the same base ClientID='{}'. " +
                     "If you are running multiple instances, ensure their MQTT_CLIENT_ID " +
                     "values are not identical, or rely on the unique-suffix behavior.",
                     props.clientId());
        }

        // We do NOT trigger manual reconnect here; Paho automatic reconnect handles it
        connecting.set(false);
    }

    @Override
    public void mqttErrorOccurred(MqttException e) {
        log.error("MQTT error occurred", e);
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        connecting.set(false);
        everConnected.set(true);

        log.info("MQTT connectComplete: reconnect={} serverURI={}", reconnect, serverURI);
        try {
            if (client != null && client.isConnected()) {
                subscribeAll();
                log.info("Re-subscribed after connectComplete");
            }
        } catch (Exception e) {
            log.error("Failed to subscribe after connectComplete", e);
        }
    }

    @Override
    public void authPacketArrived(int reasonCode, MqttProperties properties) {
        // Optional: implement if you use enhanced auth.
        log.debug("MQTT authPacketArrived: reasonCode={}", reasonCode);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        final String body = new String(message.getPayload(), StandardCharsets.UTF_8);
        log.info("Message arrived on {}: {}", topic, body);
        try {
            JsonNode root = mapper.readTree(body);
            String datatype = root.path("DATATYPE").asText(null);
            if (datatype == null || !"SCAN".equalsIgnoreCase(datatype)) {
                log.warn("Ignoring message: unsupported DATATYPE='{}' on topic={}", datatype, topic);
                publishNegativeAck("bad_datatype");
                return;
            }
            JsonNode obj = root.path("OBJECT");

            List<String> csnList = new ArrayList<>();
            if (obj.has("CSN") && obj.get("CSN").isArray()) {
                for (JsonNode n : obj.get("CSN")) {
                    csnList.add(n.asText());
                }
            }

            // If DB is down, enqueue to backlog and NACK
            if (!dbHealth.isAvailable()) {
                log.warn("DB unavailable, enqueuing backlog and NACK (topic={})", topic);
                try {
                    if (backlogStore.isEnabled()) {
                        backlogStore.enqueue(body, "db_unavailable");
                    }
                } catch (Exception ignored) {
                }
                publishNegativeAck("db_unavailable");
                return;
            }

            Scan scan = new Scan(
                    root.path("DATATYPE").asText(null),
                    obj.path("STAGE").asText(null),
                    obj.path("DEVICE").asText(null),
                    obj.path("MACHINE").asText(null),
                    obj.path("VERSION").asText(null),
                    csnList
            );

            int inserted = useCase.process(scan);
            log.info("Processed SCAN: {} detections inserted", inserted);

            if (props.topicAck() != null && !props.topicAck().isBlank()) {
                ObjectNode ackNode = mapper.createObjectNode();
                ackNode.put("ok", true);
                ackNode.put("inserted", inserted);
                String ack = mapper.writeValueAsString(ackNode);

                MqttMessage ackMsg = new MqttMessage(ack.getBytes(StandardCharsets.UTF_8));
                ackMsg.setQos(props.qos());
                client.publish(props.topicAck(), ackMsg);
                log.debug("Published ACK to {}: {}", props.topicAck(), ack);
            }
        } catch (DataIntegrityViolationException ex) {
            // FK/NOT NULL/etc violations → explicit NACK
            String detail = (ex.getMostSpecificCause() != null)
                    ? ex.getMostSpecificCause().getMessage()
                    : ex.getMessage();
            log.warn("Data integrity error while processing SCAN: {}", detail);
            publishNegativeAck("fk_violation_or_constraint");
        } catch (IllegalArgumentException iae) {
            // Validation from use case (e.g., unknown_device / unknown_stage)
            log.warn("Validation failed: {}", iae.getMessage());
            publishNegativeAck(iae.getMessage()); // e.g. "unknown_device"
        } catch (Exception e) {
            log.error("Failed to process message", e);
            publishNegativeAck("processing_error");
        }
    }

    @Override
    public void deliveryComplete(org.eclipse.paho.mqttv5.client.IMqttToken token) {
        log.debug("MQTT deliveryComplete: {}", token.getMessageId());
    }

    // -------------------------------------------------------------------------
    // ACK / NACK helpers
    // -------------------------------------------------------------------------

    private void publishNegativeAck(String reason) {
        try {
            if (props.topicAck() == null || props.topicAck().isBlank()) {
                return;
            }
            ObjectNode ack = mapper.createObjectNode();
            ack.put("ok", false);
            ack.put("reason", reason);
            String payload = mapper.writeValueAsString(ack);

            MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            msg.setQos(props.qos());
            client.publish(props.topicAck(), msg);
            log.debug("Published negative ACK to {}: {}", props.topicAck(), payload);
        } catch (Exception e) {
            log.warn("Failed to publish negative ACK: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // ClientId helper
    // -------------------------------------------------------------------------

    /**
     * Build a unique clientId derived from the configured base clientId.
     * This avoids "Session taken over" (142) when running multiple instances
     * with the same configuration.
     */
    private static String buildClientId(MqttProps props) {
        String base = props.clientId();
        if (base == null || base.isBlank()) {
            base = "mdt-client";
        }

        String host = "unknown";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
        }

        String pid = "unknown";
        try {
            pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        } catch (Exception ignored) {
        }

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        return base + "-" + host + "-" + pid + "-" + suffix;
    }
}
