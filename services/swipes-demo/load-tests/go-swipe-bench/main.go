package main

import (
	"crypto/tls"
	"fmt"
	"net"
	"net/url"
	"os"
	"runtime"
	"strconv"
	"strings"
	"time"

	"github.com/valyala/fasthttp"
)

type config struct {
	URL               string
	AuthSecret        string
	Duration          time.Duration
	Workers           int
	MaxConns          int
	PayloadsPerWorker int
	ExpectedStatus    int
	Decision          bool
	IsSuper           bool
}

type workerResult struct {
	Requests   uint64
	Failures   uint64
	BytesSent  uint64
	Status     map[int]uint64
	FirstError string
}

func main() {
	cfg, err := loadConfig()
	if err != nil {
		fatal(err)
	}

	parsedURL, err := url.Parse(cfg.URL)
	if err != nil {
		fatal(fmt.Errorf("invalid URL: %w", err))
	}
	if parsedURL.Scheme != "http" && parsedURL.Scheme != "https" {
		fatal(fmt.Errorf("URL scheme must be http or https"))
	}
	if parsedURL.Path == "" {
		parsedURL.Path = "/"
	}

	client := &fasthttp.HostClient{
		Addr:                          hostAddr(parsedURL),
		IsTLS:                         parsedURL.Scheme == "https",
		TLSConfig:                     &tls.Config{MinVersion: tls.VersionTLS12},
		MaxConns:                      cfg.MaxConns,
		MaxIdleConnDuration:           30 * time.Second,
		ReadTimeout:                   5 * time.Second,
		WriteTimeout:                  5 * time.Second,
		NoDefaultUserAgentHeader:      true,
		DisableHeaderNamesNormalizing: true,
		Dial: (&fasthttp.TCPDialer{
			Concurrency:      cfg.MaxConns,
			DNSCacheDuration: time.Minute,
		}).Dial,
	}

	fmt.Printf("go-swipe-bench url=%s duration=%s workers=%d maxConns=%d payloadsPerWorker=%d expectedStatus=%d gomaxprocs=%d\n",
		cfg.URL, cfg.Duration, cfg.Workers, cfg.MaxConns, cfg.PayloadsPerWorker, cfg.ExpectedStatus, runtime.GOMAXPROCS(0))

	done := make(chan struct{})
	results := make(chan workerResult, cfg.Workers)
	requestURI := requestURI(parsedURL)
	hostHeader := parsedURL.Host
	for workerID := 0; workerID < cfg.Workers; workerID++ {
		payloads := buildPayloads(workerID, cfg.PayloadsPerWorker, cfg.Decision, cfg.IsSuper)
		go runWorker(workerID, client, requestURI, hostHeader, cfg, payloads, done, results)
	}

	start := time.Now()
	time.Sleep(cfg.Duration)
	close(done)

	total := workerResult{Status: make(map[int]uint64)}
	for i := 0; i < cfg.Workers; i++ {
		result := <-results
		total.Requests += result.Requests
		total.Failures += result.Failures
		total.BytesSent += result.BytesSent
		for status, count := range result.Status {
			total.Status[status] += count
		}
		if total.FirstError == "" && result.FirstError != "" {
			total.FirstError = result.FirstError
		}
	}
	elapsed := time.Since(start).Seconds()

	fmt.Printf("requests=%d\n", total.Requests)
	fmt.Printf("rps=%.2f\n", float64(total.Requests)/elapsed)
	fmt.Printf("failures=%d\n", total.Failures)
	fmt.Printf("bytes_sent=%d\n", total.BytesSent)
	fmt.Printf("elapsed_seconds=%.3f\n", elapsed)
	fmt.Printf("status_counts=%s\n", formatStatusCounts(total.Status))
	if total.FirstError != "" {
		fmt.Printf("first_error=%s\n", total.FirstError)
	}
}

func runWorker(workerID int, client *fasthttp.HostClient, requestURI, hostHeader string, cfg config, payloads [][]byte, done <-chan struct{}, results chan<- workerResult) {
	req := fasthttp.AcquireRequest()
	resp := fasthttp.AcquireResponse()
	defer fasthttp.ReleaseRequest(req)
	defer fasthttp.ReleaseResponse(resp)

	req.Header.SetMethod(fasthttp.MethodPost)
	req.SetRequestURI(requestURI)
	req.Header.SetHost(hostHeader)
	req.Header.SetContentType("application/json")
	req.Header.Set("X-Internal-Auth", cfg.AuthSecret)
	req.Header.Set("Connection", "keep-alive")

	result := workerResult{Status: make(map[int]uint64, 4)}
	payloadIndex := workerID % len(payloads)

	for {
		for i := 0; i < 64; i++ {
			payload := payloads[payloadIndex]
			payloadIndex++
			if payloadIndex == len(payloads) {
				payloadIndex = 0
			}

			req.SetBodyRaw(payload)
			resp.Reset()
			if err := client.Do(req, resp); err != nil {
				result.Failures++
				if result.FirstError == "" {
					result.FirstError = err.Error()
				}
				continue
			}
			status := resp.StatusCode()
			result.Status[status]++
			if status != cfg.ExpectedStatus {
				result.Failures++
			}
			result.Requests++
			result.BytesSent += uint64(len(payload))
		}

		select {
		case <-done:
			results <- result
			return
		default:
		}
	}
}

func loadConfig() (config, error) {
	duration, err := time.ParseDuration(envString("DURATION", "10s"))
	if err != nil {
		return config{}, fmt.Errorf("invalid DURATION: %w", err)
	}
	cfg := config{
		URL:               envString("URL", "http://127.0.0.1:8040/api/v1/swipes"),
		AuthSecret:        strings.TrimSpace(os.Getenv("INTERNAL_SWIPES_AUTH_SECRET")),
		Duration:          duration,
		Workers:           envInt("WORKERS", runtime.NumCPU()*128),
		MaxConns:          envInt("MAX_CONNS", runtime.NumCPU()*512),
		PayloadsPerWorker: envInt("PAYLOADS_PER_WORKER", 1),
		ExpectedStatus:    envInt("EXPECTED_STATUS", 202),
		Decision:          envBool("SWIPE_DECISION", true),
		IsSuper:           envBool("SWIPE_IS_SUPER", false),
	}
	if cfg.AuthSecret == "" {
		return config{}, fmt.Errorf("INTERNAL_SWIPES_AUTH_SECRET is required")
	}
	if cfg.Duration <= 0 {
		return config{}, fmt.Errorf("DURATION must be > 0")
	}
	if cfg.Workers <= 0 {
		return config{}, fmt.Errorf("WORKERS must be > 0")
	}
	if cfg.MaxConns <= 0 {
		return config{}, fmt.Errorf("MAX_CONNS must be > 0")
	}
	if cfg.PayloadsPerWorker <= 0 {
		return config{}, fmt.Errorf("PAYLOADS_PER_WORKER must be > 0")
	}
	return cfg, nil
}

func buildPayloads(workerID, count int, decision, isSuper bool) [][]byte {
	payloads := make([][]byte, count)
	for i := 0; i < count; i++ {
		base := uint32((workerID << 16) + (i * 2))
		profile1ID := incrementalUUID(base)
		profile2ID := incrementalUUID(base + 1)
		payloads[i] = []byte(fmt.Sprintf(`{"profile1Id":"%s","profile2Id":"%s","decision":%t,"isSuper":%t}`,
			profile1ID, profile2ID, decision, isSuper))
	}
	return payloads
}

func incrementalUUID(value uint32) string {
	part1 := fmt.Sprintf("%08x", value)
	part2 := fmt.Sprintf("%04x", (value>>16)&0xffff)
	part3 := fmt.Sprintf("4%03x", (value>>4)&0x0fff)
	part4 := fmt.Sprintf("8%03x", value&0x0fff)
	part5 := fmt.Sprintf("%012x", value)
	return fmt.Sprintf("%s-%s-%s-%s-%s", part1, part2, part3, part4, part5)
}

func hostAddr(parsedURL *url.URL) string {
	if _, _, err := net.SplitHostPort(parsedURL.Host); err == nil {
		return parsedURL.Host
	}
	if parsedURL.Scheme == "https" {
		return net.JoinHostPort(parsedURL.Host, "443")
	}
	return net.JoinHostPort(parsedURL.Host, "80")
}

func requestURI(parsedURL *url.URL) string {
	uri := parsedURL.EscapedPath()
	if uri == "" {
		uri = "/"
	}
	if parsedURL.RawQuery != "" {
		uri += "?" + parsedURL.RawQuery
	}
	return uri
}

func envString(name, fallback string) string {
	value := strings.TrimSpace(os.Getenv(name))
	if value == "" {
		return fallback
	}
	return value
}

func envInt(name string, fallback int) int {
	raw := strings.TrimSpace(os.Getenv(name))
	if raw == "" {
		return fallback
	}
	value, err := strconv.Atoi(raw)
	if err != nil {
		fatal(fmt.Errorf("%s must be an integer", name))
	}
	return value
}

func envBool(name string, fallback bool) bool {
	raw := strings.TrimSpace(os.Getenv(name))
	if raw == "" {
		return fallback
	}
	switch strings.ToLower(raw) {
	case "1", "true", "yes":
		return true
	case "0", "false", "no":
		return false
	default:
		fatal(fmt.Errorf("%s must be boolean", name))
		return fallback
	}
}

func formatStatusCounts(counts map[int]uint64) string {
	if len(counts) == 0 {
		return "{}"
	}
	parts := make([]string, 0, len(counts))
	for status := range counts {
		parts = append(parts, fmt.Sprintf("%d:%d", status, counts[status]))
	}
	return "{" + strings.Join(parts, ",") + "}"
}

func fatal(err error) {
	fmt.Fprintln(os.Stderr, "error:", err)
	os.Exit(1)
}
