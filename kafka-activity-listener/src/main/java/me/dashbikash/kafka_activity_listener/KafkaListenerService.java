package main.java.me.dashbikash.kafka_activity_listener;

import org.springframework.stereotype.Service;

@Service
public class KafkaListenerService {

    @KafkaListener(topic = "android-app-activity")
    public void ListenActivities(String message) {
    }

}