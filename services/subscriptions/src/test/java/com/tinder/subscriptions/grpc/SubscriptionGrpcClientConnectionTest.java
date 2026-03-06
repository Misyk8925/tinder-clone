package com.tinder.subscriptions.grpc;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionGrpcClientConnectionTest {

    private static Server server;
    private static ManagedChannel channel;
    private static CircuitBreaker circuitBreaker;

    @BeforeAll
    static void beforeAll() throws Exception {
        server = ServerBuilder.forPort(19010)
                .addService(new SubscriptionsServiceGrpc.SubscriptionsServiceImplBase() {
                    @Override
                    public void updatePremiumUser(UpdatePremiumUserRequest request,
                                                  StreamObserver<UpdatePremiumUserResponse> responseObserver) {
                        UpdatePremiumUserResponse response = UpdatePremiumUserResponse.newBuilder()
                                .setSuccess(!request.getUserId().isBlank())
                                .build();
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                    }
                })
                .build()
                .start();

        channel = io.grpc.ManagedChannelBuilder.forAddress("localhost", 19010)
                .usePlaintext()
                .build();

        circuitBreaker = CircuitBreakerRegistry.ofDefaults().circuitBreaker("connection-test");
    }

    @AfterAll
    static void afterAll() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    void shouldCallGrpcServerThroughClient() throws Exception {
        SubscriptionGrpcClient client = new SubscriptionGrpcClient(circuitBreaker);
        SubscriptionsServiceGrpc.SubscriptionsServiceBlockingStub stub =
                SubscriptionsServiceGrpc.newBlockingStub(channel);

        Field field = SubscriptionGrpcClient.class.getDeclaredField("subscriptionsServiceStub");
        field.setAccessible(true);
        field.set(client, stub);

        UpdatePremiumUserResponse response = client.updatePremiumUser("user-123");

        assertTrue(response.getSuccess());
    }
}
