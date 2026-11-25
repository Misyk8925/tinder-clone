package com.tinder.profiles.config.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DatagramDnsQueryDecoder;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DatagramDnsResponseEncoder;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.dns.DnsAddressResolverGroup;
import io.netty.resolver.dns.SingletonDnsServerAddressStreamProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.NameResolverProvider.NameResolverSpec;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Reproduces a compose redeploy: the DNS server answers like Docker's embedded DNS (TTL 600 s) and then
 * re-points the service name to a new container IP.
 */
class InternalServiceHttpClientTest {

    private static final int DOCKER_DNS_TTL_SECONDS = 600;
    private static final InetAddress OLD_CONTAINER_IP = ipv4(172, 21, 0, 11);
    private static final InetAddress NEW_CONTAINER_IP = ipv4(172, 21, 0, 19);
    private static final Duration AFTER_CAP = InternalServiceHttpClient.DNS_CACHE_MAX_TTL.plusSeconds(1);

    private final AtomicReference<InetAddress> photosIp = new AtomicReference<>(OLD_CONTAINER_IP);
    private EventLoopGroup eventLoops;
    private Channel dnsServer;

    @BeforeEach
    void startDockerLikeDns() throws InterruptedException {
        eventLoops = new NioEventLoopGroup(1);
        dnsServer = new Bootstrap()
                .group(eventLoops)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<DatagramChannel>() {
                    @Override
                    protected void initChannel(DatagramChannel channel) {
                        channel.pipeline().addLast(
                                new DatagramDnsQueryDecoder(),
                                new DatagramDnsResponseEncoder(),
                                new AnswerEveryNameWith(photosIp));
                    }
                })
                .bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .sync()
                .channel();
    }

    @AfterEach
    void stopDns() {
        dnsServer.close().syncUninterruptibly();
        eventLoops.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
    }

    @Test
    void defaultReactorNettyResolverKeepsDialingTheRecreatedContainersOldIp() throws Exception {
        // Given: a client with Reactor Netty's default resolver has resolved photos once
        AddressResolverGroup<InetSocketAddress> resolver = resolverWith(spec -> { });
        assertThat(resolvePhotos(resolver)).isEqualTo(OLD_CONTAINER_IP);

        // When: photos is recreated and DNS now returns the new container IP
        photosIp.set(NEW_CONTAINER_IP);

        // Then: the client keeps the old IP from its cache, because it honours the 600 s TTL
        await().during(AFTER_CAP).atMost(AFTER_CAP.plusSeconds(2))
                .until(() -> resolvePhotos(resolver).equals(OLD_CONTAINER_IP));
    }

    @Test
    void internalServiceClientFollowsTheRecreatedContainersNewIpWithinTheCacheCap() throws Exception {
        // Given: the internal service client has resolved photos once
        AddressResolverGroup<InetSocketAddress> resolver = resolverWith(InternalServiceHttpClient::boundDnsCache);
        assertThat(resolvePhotos(resolver)).isEqualTo(OLD_CONTAINER_IP);

        // When: photos is recreated and DNS now returns the new container IP
        photosIp.set(NEW_CONTAINER_IP);

        // Then: the client resolves the new IP once the capped cache entry expires
        await().atMost(AFTER_CAP).pollInterval(Duration.ofMillis(250))
                .until(() -> resolvePhotos(resolver).equals(NEW_CONTAINER_IP));
    }

    @Test
    void createdClientCarriesTheCappedDnsCache() {
        // Given / When: the client every compose-service WebClient in profiles is built from
        HttpClient client = InternalServiceHttpClient.create();

        // Then: its name resolver caps the DNS cache instead of using Reactor Netty's unbounded default
        assertThat(client.configuration().getNameResolverProvider().cacheMaxTimeToLive())
                .isEqualTo(InternalServiceHttpClient.DNS_CACHE_MAX_TTL);
    }

    /** Builds the client's resolver as configured by {@code settings}, pointed at the test DNS server. */
    @SuppressWarnings("unchecked")
    private AddressResolverGroup<InetSocketAddress> resolverWith(Consumer<NameResolverSpec> settings) {
        InetSocketAddress dnsAddress = (InetSocketAddress) dnsServer.localAddress();
        HttpClient client = HttpClient.create().resolver(spec -> {
            settings.accept(spec);
            spec.dnsAddressResolverGroupProvider(builder -> new DnsAddressResolverGroup(
                    builder.nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsAddress))));
        });
        return (AddressResolverGroup<InetSocketAddress>) client.configuration().resolver();
    }

    private InetAddress resolvePhotos(AddressResolverGroup<InetSocketAddress> resolver) throws Exception {
        SocketAddress unresolved = InetSocketAddress.createUnresolved("photos", 8070);
        return resolver.getResolver(eventLoops.next())
                .resolve(unresolved)
                .get(5, TimeUnit.SECONDS)
                .getAddress();
    }

    private static InetAddress ipv4(int a, int b, int c, int d) {
        try {
            return InetAddress.getByAddress(new byte[]{(byte) a, (byte) b, (byte) c, (byte) d});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Answers every A query (including search-domain variants of "photos") with the current IP and Docker's TTL. */
    private static final class AnswerEveryNameWith extends SimpleChannelInboundHandler<DatagramDnsQuery> {

        private final AtomicReference<InetAddress> ip;

        private AnswerEveryNameWith(AtomicReference<InetAddress> ip) {
            this.ip = ip;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsQuery query) {
            DnsQuestion question = query.recordAt(DnsSection.QUESTION);
            DatagramDnsResponse response = new DatagramDnsResponse(query.recipient(), query.sender(), query.id());
            response.setRecursionAvailable(true);
            response.addRecord(DnsSection.QUESTION, question);
            if (question.type() == DnsRecordType.A) {
                response.addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord(
                        question.name(), DnsRecordType.A, DOCKER_DNS_TTL_SECONDS,
                        Unpooled.wrappedBuffer(ip.get().getAddress())));
            }
            ctx.writeAndFlush(response);
        }
    }
}
