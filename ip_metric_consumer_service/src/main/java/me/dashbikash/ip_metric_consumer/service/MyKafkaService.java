package me.dashbikash.ip_metric_consumer.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import me.dashbikash.ip_metric_consumer.model.TrafficLog;
import me.dashbikash.ip_metric_consumer.model.TrafficLogKafkaSchema;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class MyKafkaService {

    private static final Logger log = LoggerFactory.getLogger(MyKafkaService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JdbcTemplate postgresJdbcTemplate;

    public MyKafkaService(JdbcTemplate postgresJdbcTemplate) {
        this.postgresJdbcTemplate = postgresJdbcTemplate;
    }

    @KafkaListener(topics = "ip-metrics")
    public void consume(
            @Payload String networkLogStr,
            @Header(KafkaHeaders.RECEIVED_KEY) String messageKey,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        try {
            TrafficLogKafkaSchema schema = objectMapper.readValue(networkLogStr, TrafficLogKafkaSchema.class);
            TrafficLog trafficLog = new TrafficLog(
                    OffsetDateTime.ofInstant(Instant.ofEpochMilli(schema.getTimestamp()), ZoneOffset.UTC),
                    schema.getIp(),
                    schema.getUser(),
                    schema.getDirection(),
                    schema.getCount());

            String sql = "INSERT INTO \"traffic_logs\" (\"time\", \"ip\", \"user\", \"direction\", \"count\") VALUES (?, CAST(? AS inet), ?, ?, ?)";
            postgresJdbcTemplate.update(
                    sql,
                    trafficLog.getTime(),
                    trafficLog.getIp(),
                    trafficLog.getUser(),
                    trafficLog.getDirection(),
                    trafficLog.getCount());

        } catch (Exception e) {
            log.error("Error parsing or saving Kafka message: {}", networkLogStr, e);
        }
    }
}