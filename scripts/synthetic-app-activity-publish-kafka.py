import json
import random
import time
from datetime import datetime, timedelta
from kafka import KafkaProducer
from faker import Faker

# Initialize Faker
fake = Faker()

# Define App Categories and Apps
APP_MARKETPLACE = {
    "Social Media": ["Instagram", "TikTok", "Facebook", "X (Twitter)", "Snapchat"],
    "Productivity & Tools": ["Slack", "Gmail", "Notion", "Google Calendar", "Microsoft Teams"],
    "Gaming": ["Roblox", "Subway Surfers", "Candy Crush", "PUBG Mobile", "Among Us"],
    "Entertainment & Streaming": ["Netflix", "YouTube", "Spotify", "Disney+", "Twitch"],
    "Finance & Shopping": ["Amazon", "PayPal", "Chime", "Robinhood", "Klarna"]
}

CATEGORIES = list(APP_MARKETPLACE.keys())

# Time interval constraints (in seconds)
MIN_INTERVAL = 5
MAX_INTERVAL_MULTIMEDIA = 3 * 60 * 60  # 3 hours
MAX_INTERVAL_STANDARD = 30 * 60       # 30 minutes

# Kafka Configuration
BOOTSTRAP_SERVERS = ['localhost:9094','localhost:9095','localhost:9096']  
TOPIC_NAME = 'android-app-activity'

# Dictionary to track the last event timestamp for each unique device
# Structure: { "device_id": datetime_object }
device_timelines = {}

# A pool of fixed device IDs to simulate a realistic group of active users
DEVICE_POOL = [f"and_dev_{fake.hexify(text='^^^^^^^^^^^^^^^^')}" for _ in range(50)]

def json_serializer(data):
    return json.dumps(data).encode('utf-8')

def generate_app_activity(device_id):
    """Generates an app foreground event with timestamps calculated from the previous event."""
    global device_timelines
    
    category = random.choice(CATEGORIES)
    app_name = random.choice(APP_MARKETPLACE[category])
    package_name = f"com.{category.lower().replace(' & ', '.').replace(' ', '')}.{app_name.lower().replace(' ', '')}"
    
    # 1. Determine the baseline (previous) timestamp
    if device_id in device_timelines:
        previous_timestamp = device_timelines[device_id]
    else:
        # If it's a brand new device, start its timeline from "now"
        previous_timestamp = datetime.utcnow()
    
    # 2. Determine interval rule based on the APP CATEGORY
    if category in ["Gaming", "Entertainment & Streaming"]:
        interval_seconds = random.randint(MIN_INTERVAL, MAX_INTERVAL_MULTIMEDIA)
        rule_desc = "Multimedia/Gaming (5s to 3h)"
    else:
        interval_seconds = random.randint(MIN_INTERVAL, MAX_INTERVAL_STANDARD)
        rule_desc = "Standard (5s to 30m)"
        
    # 3. Calculate new timestamp based precisely on the previous one
    new_timestamp = previous_timestamp + timedelta(seconds=interval_seconds)
    
    # Update the tracking cache for this device's next turn
    device_timelines[device_id] = new_timestamp

    activity_event = {
        "event_id": fake.uuid4(),
        "device_id": device_id,
        "user_id": f"usr_{random.randint(10000, 99999)}",
        "previous_timestamp": previous_timestamp.isoformat() + "Z",
        "timestamp": new_timestamp.isoformat() + "Z", # Calculated from previous
        "interval_added_seconds": interval_seconds,
        "event_type": "MOVE_TO_FOREGROUND", 
        "app_details": {
            "app_name": app_name,
            "package_name": package_name,
            "category": category,
            "activity_name": f"{package_name}.MainActivity"
        },
        "device_metadata": {
            "os_version": f"Android {random.choice([11, 12, 13, 14, 15])}",
            "network_type": random.choice(["Wi-Fi", "5G", "4G LTE"])
        }
    }
    return activity_event, rule_desc

def main():
    print(f"Initializing Kafka Producer connecting to {BOOTSTRAP_SERVERS}...")
    try:
        producer = KafkaProducer(
            bootstrap_servers=BOOTSTRAP_SERVERS,
            value_serializer=json_serializer,
            acks='all', 
            retries=3
        )
        print(f"Connected! Streaming synthetic data to '{TOPIC_NAME}'...")
        print("Press Ctrl+C to stop.\n")
        
        while True:
            # Pick a random device from our user pool
            selected_device = random.choice(DEVICE_POOL)
            
            # Generate the event data
            event_data, rule_used = generate_app_activity(selected_device)
            
            # Publish to Kafka (keyed by device ID)
            partition_key = event_data["device_id"].encode('utf-8')
            producer.send(TOPIC_NAME, key=partition_key, value=event_data)
            
            # Human-readable interval for log visibility
            hours, rem = divmod(event_data["interval_added_seconds"], 3600)
            mins, secs = divmod(rem, 60)
            
            print(f"Device: {event_data['device_id'][:12]}... | App: {event_data['app_details']['app_name']} ({event_data['app_details']['category']})")
            print(f" ├── Prev Time: {event_data['previous_timestamp']}")
            print(f" ├── Next Time: {event_data['timestamp']} (+{hours}h {mins}m {secs}s via {rule_used})")
            print("-" * 70)
            
            # Throttle the loop slightly just so the terminal doesn't crash from speed, 
            # while allowing data to flow uninterrupted.
            time.sleep(0.5)
            
    except KeyboardInterrupt:
        print("\nStreaming stopped by user.")
    except Exception as e:
        print(f"\nAn error occurred: {e}")
    finally:
        if 'producer' in locals():
            producer.flush()
            producer.close()
            print("Kafka producer connection closed cleanly.")

if __name__ == "__main__":
    main()