package main

import (
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"runtime"
	"sort"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

type result struct {
	URL            string  `json:"url"`
	Workers        int     `json:"workers"`
	DurationSecs   float64 `json:"durationSeconds"`
	Requests       uint64  `json:"requests"`
	Failures       uint64  `json:"failures"`
	ErrorRate      float64 `json:"errorRate"`
	RPS            float64 `json:"rps"`
	P50Millis      float64 `json:"p50Millis"`
	P95Millis      float64 `json:"p95Millis"`
	P99Millis      float64 `json:"p99Millis"`
	ExpectedStatus int     `json:"expectedStatus"`
}

func main() {
	target := env("URL", "http://127.0.0.1:8040/api/v2/deck?limit=20")
	token := strings.TrimSpace(os.Getenv("AUTH_TOKEN"))
	if token == "" {
		fatal("AUTH_TOKEN is required")
	}
	duration := durationEnv("DURATION", 15*time.Second)
	workers := intEnv("WORKERS", runtime.NumCPU()*16)
	expectedStatus := intEnv("EXPECTED_STATUS", http.StatusOK)
	maxErrorRate := floatEnv("MAX_ERROR_RATE", 0.01)

	transport := &http.Transport{
		MaxIdleConns:        workers * 2,
		MaxIdleConnsPerHost: workers * 2,
		MaxConnsPerHost:     workers * 2,
		IdleConnTimeout:     30 * time.Second,
		DialContext: (&net.Dialer{
			Timeout:   2 * time.Second,
			KeepAlive: 30 * time.Second,
		}).DialContext,
	}
	client := &http.Client{Transport: transport, Timeout: 5 * time.Second}
	defer transport.CloseIdleConnections()

	deadline := time.Now().Add(duration)
	var attempts atomic.Uint64
	var failures atomic.Uint64
	latencies := make(chan []int64, workers)
	var group sync.WaitGroup
	group.Add(workers)
	start := time.Now()
	for worker := 0; worker < workers; worker++ {
		go func() {
			defer group.Done()
			local := make([]int64, 0, 4096)
			for time.Now().Before(deadline) {
				request, err := http.NewRequest(http.MethodGet, target, nil)
				if err != nil {
					fatal(err.Error())
				}
				request.Header.Set("Authorization", "Bearer "+token)
				request.Header.Set("Accept", "application/json")
				requestStart := time.Now()
				response, err := client.Do(request)
				elapsed := time.Since(requestStart)
				attempts.Add(1)
				local = append(local, elapsed.Microseconds())
				if err != nil {
					failures.Add(1)
					continue
				}
				_, _ = io.Copy(io.Discard, response.Body)
				response.Body.Close()
				if response.StatusCode != expectedStatus {
					failures.Add(1)
				}
			}
			latencies <- local
		}()
	}
	group.Wait()
	close(latencies)
	elapsed := time.Since(start)
	allLatencies := make([]int64, 0, int(attempts.Load()))
	for local := range latencies {
		allLatencies = append(allLatencies, local...)
	}
	sort.Slice(allLatencies, func(left, right int) bool { return allLatencies[left] < allLatencies[right] })

	requestCount := attempts.Load()
	failureCount := failures.Load()
	errorRate := 0.0
	if requestCount > 0 {
		errorRate = float64(failureCount) / float64(requestCount)
	}
	report := result{
		URL:            target,
		Workers:        workers,
		DurationSecs:   elapsed.Seconds(),
		Requests:       requestCount,
		Failures:       failureCount,
		ErrorRate:      errorRate,
		RPS:            float64(requestCount) / elapsed.Seconds(),
		P50Millis:      percentileMillis(allLatencies, 0.50),
		P95Millis:      percentileMillis(allLatencies, 0.95),
		P99Millis:      percentileMillis(allLatencies, 0.99),
		ExpectedStatus: expectedStatus,
	}
	encoded, err := json.Marshal(report)
	if err != nil {
		fatal(err.Error())
	}
	fmt.Println(string(encoded))

	if requestCount == 0 || errorRate >= maxErrorRate {
		os.Exit(2)
	}
	checkScalingGate(report)
}

func checkScalingGate(current result) {
	baselineRPS := floatEnv("BASELINE_RPS", 0)
	baselineP95 := floatEnv("BASELINE_P95_MS", 0)
	if baselineRPS > 0 && current.RPS < baselineRPS*1.7 {
		fatal(fmt.Sprintf("throughput %.2f is below the 1.7x gate %.2f", current.RPS, baselineRPS*1.7))
	}
	if baselineP95 > 0 && current.P95Millis > baselineP95*1.2 {
		fatal(fmt.Sprintf("p95 %.2fms exceeds the 20%% gate %.2fms", current.P95Millis, baselineP95*1.2))
	}
}

func percentileMillis(values []int64, percentile float64) float64 {
	if len(values) == 0 {
		return 0
	}
	index := int(float64(len(values)-1) * percentile)
	return float64(values[index]) / 1000
}

func env(name, fallback string) string {
	if value := strings.TrimSpace(os.Getenv(name)); value != "" {
		return value
	}
	return fallback
}

func intEnv(name string, fallback int) int {
	value, err := strconv.Atoi(env(name, strconv.Itoa(fallback)))
	if err != nil || value <= 0 {
		fatal(name + " must be a positive integer")
	}
	return value
}

func floatEnv(name string, fallback float64) float64 {
	value, err := strconv.ParseFloat(env(name, strconv.FormatFloat(fallback, 'f', -1, 64)), 64)
	if err != nil || value < 0 {
		fatal(name + " must be a non-negative number")
	}
	return value
}

func durationEnv(name string, fallback time.Duration) time.Duration {
	value, err := time.ParseDuration(env(name, fallback.String()))
	if err != nil || value <= 0 {
		fatal(name + " must be a positive duration")
	}
	return value
}

func fatal(message string) {
	fmt.Fprintln(os.Stderr, "error:", message)
	os.Exit(1)
}
