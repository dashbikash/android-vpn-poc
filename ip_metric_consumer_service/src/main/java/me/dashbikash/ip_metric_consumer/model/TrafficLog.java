package me.dashbikash.ip_metric_consumer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "traffic_logs")
public class TrafficLog {

    public TrafficLog(OffsetDateTime time, String ip, String user, String direction, Integer count) {
        this.time = time;
        this.ip = ip;
        this.user = user;
        this.direction = direction;
        this.count = count;
    }

    /*
     * As noted previously, JPA requires an @Id.
     * If multiple logs can share the exact same nanosecond, you will need
     * to implement an @IdClass composite key (e.g., time + ip + user).
     */
    @Id
    @Column(name = "time", nullable = false, updatable = false)
    private OffsetDateTime time;

    @Column(name = "ip", columnDefinition = "inet", nullable = false)
    private String ip;

    // IMPORTANT: "user" is a reserved keyword in PostgreSQL.
    // The escaped quotes (\"user\") force Hibernate to generate safe SQL.
    @Column(name = "\"user\"", columnDefinition = "text", nullable = false)
    private String user;

    @Column(name = "direction", length = 10, nullable = false)
    private String direction;

    @Column(name = "count", nullable = false)
    private Integer count;

    // --- Constructors ---

    public TrafficLog() {
    }

    // --- Getters and Setters ---

    public OffsetDateTime getTime() {
        return time;
    }

    public void setTime(OffsetDateTime time) {
        this.time = time;
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
}