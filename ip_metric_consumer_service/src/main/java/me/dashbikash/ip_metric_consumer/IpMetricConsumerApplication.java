package me.dashbikash.ip_metric_consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;

@SpringBootApplication
public class IpMetricConsumerApplication {

	public static void main(String[] args) {
		SpringApplication.run(IpMetricConsumerApplication.class, args);
	}

	@Bean
	public JdbcTemplate postgresJdbcTemplate(DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}

}
