package router

import (
	"bytes"
	"context"
	"io"
	"log"
	"net/http"
	"testing"

	"github.com/valyala/fasthttp"
	"tinder-clone/services/swipes-go/internal/metrics"
	"tinder-clone/services/swipes-go/internal/model"
	"tinder-clone/services/swipes-go/internal/security"
)

type fakeService struct {
	called bool
	err    error
}

func (service *fakeService) Send(_ context.Context, _ model.SwipeDTO, principal security.Principal, _ bool) error {
	service.called = principal.Benchmark
	return service.err
}

type fakeQueue struct{}

func (fakeQueue) QueueStats() (int, int) { return 2, 10 }

func TestRouterAcceptsBenchmarkSwipe(t *testing.T) {
	service := &fakeService{}
	handler := New(service, security.NewAuthenticator("secret", nil), &metrics.Metrics{}, fakeQueue{}, testLogger()).Handler
	ctx := requestContext(http.MethodPost, "/api/v1/swipes", []byte(`{"profile1Id":"a","profile2Id":"b"}`))
	ctx.Request.Header.Set("X-Internal-Auth", "secret")
	handler(ctx)
	if ctx.Response.StatusCode() != http.StatusAccepted || !service.called {
		t.Fatalf("status=%d called=%v body=%s", ctx.Response.StatusCode(), service.called, ctx.Response.Body())
	}
}

func TestRouterRejectsOverscopedMethod(t *testing.T) {
	handler := New(&fakeService{}, security.NewAuthenticator("secret", nil), &metrics.Metrics{}, fakeQueue{}, testLogger()).Handler
	ctx := requestContext(http.MethodGet, "/api/v1/swipes", nil)
	handler(ctx)
	if ctx.Response.StatusCode() != http.StatusMethodNotAllowed {
		t.Fatalf("unexpected status: %d", ctx.Response.StatusCode())
	}
}

func TestRouterMetrics(t *testing.T) {
	serviceMetrics := &metrics.Metrics{}
	serviceMetrics.Accepted.Store(3)
	handler := New(&fakeService{}, security.NewAuthenticator("secret", nil), serviceMetrics, fakeQueue{}, testLogger()).Handler
	ctx := requestContext(http.MethodGet, "/actuator/metrics", nil)
	handler(ctx)
	if ctx.Response.StatusCode() != http.StatusOK || !bytes.Contains(ctx.Response.Body(), []byte("swipes_accepted_total 3")) {
		t.Fatalf("unexpected metrics response: %d %s", ctx.Response.StatusCode(), ctx.Response.Body())
	}
}

func BenchmarkRouterBenchmarkSwipe(b *testing.B) {
	handler := New(&fakeService{}, security.NewAuthenticator("secret", nil), &metrics.Metrics{}, fakeQueue{}, testLogger()).Handler
	body := []byte(`{"profile1Id":"a","profile2Id":"b","decision":true,"isSuper":false}`)
	ctx := &fasthttp.RequestCtx{}
	b.ReportAllocs()
	for i := 0; i < b.N; i++ {
		ctx.Request.Reset()
		ctx.Response.Reset()
		ctx.Request.Header.SetMethod(http.MethodPost)
		ctx.Request.SetRequestURI("/api/v1/swipes")
		ctx.Request.Header.Set("X-Internal-Auth", "secret")
		ctx.Request.SetBodyRaw(body)
		handler(ctx)
	}
}

func requestContext(method, path string, body []byte) *fasthttp.RequestCtx {
	ctx := &fasthttp.RequestCtx{}
	ctx.Request.Header.SetMethod(method)
	ctx.Request.SetRequestURI(path)
	ctx.Request.SetBody(body)
	return ctx
}

func testLogger() *log.Logger { return log.New(io.Discard, "", 0) }
