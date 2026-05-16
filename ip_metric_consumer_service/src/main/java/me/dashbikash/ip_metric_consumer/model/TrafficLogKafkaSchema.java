package me.dashbikash.ip_metric_consumer.model;

import java.io.Serializable;

/**
 * Represents a network log entry stored in Redis.
 * The @RedisHash annotation determines the Redis keyspace (e.g.,
 * "NetworkLog:123").
 */

public class TrafficLogKafkaSchema implements Serializable {

    public TrafficLogKafkaSchema() {
    }

    public TrafficLogKafkaSchema(String ip, String user, String direction, Integer count, Long timestamp) {
        this.ip = ip;
        this.user = user;
        this.direction = direction;
        this.count = count;
        this.timestamp = timestamp;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    private String ip;
    private String user;
    private String direction;
    private Integer count;

    // Using Long for epoch timestamp (1778436087028)
    private Long timestamp;
}