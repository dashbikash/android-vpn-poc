package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/confluentinc/confluent-kafka-go/v2/kafka"
	"github.com/jackc/pgx/v5/pgxpool"
)

// Metric matches the provided JSON data format
type Metric struct {
	IP        string `json:"ip"`
	User      string `json:"user"`
	Direction string `json:"direction"`
	Count     int    `json:"count"`
	Timestamp int64  `json:"timestamp"`
}

// HealthResponse defines the JSON structure for the health check API
type HealthResponse struct {
	Status   string `json:"status"`
	Postgres string `json:"postgres"`
	Kafka    string `json:"kafka"`
}

func main() {
	// Configuration
	const (
		kafkaBrokers  = "localhost:9094,localhost:9095,localhost:9096"
		kafkaTopic    = "ip-metrics"
		consumerGroup = "ip-metrics-sink-group"
		dbConnString  = "postgres://postgres:password@localhost:5432/context-db"
		httpPort      = ":8080"
	)

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	// 1. Initialize TimescaleDB Connection Pool
	dbPool, err := pgxpool.New(ctx, dbConnString)
	if err != nil {
		log.Fatalf("Unable to connect to database: %v\n", err)
	}
	defer dbPool.Close()
	log.Println("Connected to TimescaleDB")

	// 2. Initialize Kafka Consumer
	c, err := kafka.NewConsumer(&kafka.ConfigMap{
		"bootstrap.servers": kafkaBrokers,
		"group.id":          consumerGroup,
		"auto.offset.reset": "earliest",
	})
	if err != nil {
		log.Fatalf("Failed to create consumer: %s", err)
	}
	defer c.Close()

	if err = c.SubscribeTopics([]string{kafkaTopic}, nil); err != nil {
		log.Fatalf("Failed to subscribe to topic: %s", err)
	}

	// 3. Start Health Check API Server
	mux := http.NewServeMux()
	mux.HandleFunc("/health", healthCheckHandler(dbPool, c))

	httpServer := &http.Server{
		Addr:    httpPort,
		Handler: mux,
	}

	go func() {
		log.Printf("Starting Health Check API on http://localhost%s/health\n", httpPort)
		if err := httpServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("HTTP server error: %v", err)
		}
	}()

	log.Printf("Starting consumer for topic: %s...", kafkaTopic)

	// 4. Main Consumption Loop
	go func() {
		for {
			select {
			case <-ctx.Done():
				return // Exit goroutine on shutdown
			default:
				// Poll for messages (1 second timeout)
				ev := c.Poll(1000)
				if ev == nil {
					continue
				}

				switch e := ev.(type) {
				case *kafka.Message:
					if err := processAndInsert(ctx, dbPool, e.Value); err != nil {
						log.Printf("Error processing message: %v", err)
					}
				case kafka.Error:
					log.Printf("Kafka Error: %v (%v)", e.Code(), e)
				}
			}
		}
	}()

	// 5. Graceful Shutdown Waiter
	<-ctx.Done()
	log.Println("Shutdown signal received. Closing connections...")

	// Gracefully shutdown the HTTP server (max 5 seconds)
	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer shutdownCancel()
	if err := httpServer.Shutdown(shutdownCtx); err != nil {
		log.Printf("HTTP server shutdown error: %v", err)
	}

	log.Println("Service exited gracefully.")
}

// healthCheckHandler returns an HTTP handler that actively pings Postgres and Kafka
func healthCheckHandler(dbPool *pgxpool.Pool, c *kafka.Consumer) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		response := HealthResponse{
			Status:   "UP",
			Postgres: "UP",
			Kafka:    "UP",
		}

		// 1. Check PostgreSQL (Ping with a 2-second timeout)
		dbCtx, dbCancel := context.WithTimeout(r.Context(), 2*time.Second)
		defer dbCancel()
		if err := dbPool.Ping(dbCtx); err != nil {
			response.Postgres = fmt.Sprintf("DOWN - %v", err)
			response.Status = "DOWN"
		}

		// 2. Check Kafka Broker Connectivity (Fetch metadata with a 2000ms timeout)
		// Passing `nil` fetches metadata for all topics, `false` ignores cache.
		_, err := c.GetMetadata(nil, false, 2000)
		if err != nil {
			response.Kafka = fmt.Sprintf("DOWN - %v", err)
			response.Status = "DOWN"
		}

		// Set response headers and status code
		w.Header().Set("Content-Type", "application/json")
		if response.Status == "DOWN" {
			w.WriteHeader(http.StatusServiceUnavailable) // 503
		} else {
			w.WriteHeader(http.StatusOK) // 200
		}

		// Send JSON payload
		json.NewEncoder(w).Encode(response)
	}
}

// processAndInsert handles JSON decoding and DB insertion
func processAndInsert(ctx context.Context, db *pgxpool.Pool, payload []byte) error {
	var m Metric
	if err := json.Unmarshal(payload, &m); err != nil {
		return fmt.Errorf("json unmarshal failed: %w", err)
	}

	ts := time.UnixMilli(m.Timestamp)
	query := `
		INSERT INTO "public"."traffic_logs" ("time", "ip", "user", "direction", "count")
		VALUES ($1, $2, $3, $4, $5)
	`
	_, err := db.Exec(ctx, query, ts, m.IP, m.User, m.Direction, m.Count)
	if err != nil {
		return fmt.Errorf("db insert failed: %w", err)
	}

	return nil
}
