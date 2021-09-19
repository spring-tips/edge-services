package bootiful.edge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.List;

@SpringBootApplication
public class EdgeApplication {

    @Bean
    WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }

    @Bean
    RSocketRequester rSocketRequester(RSocketRequester.Builder builder) {
        return builder.tcp("localhost", 8181);
    }

    @Bean
    RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(5, 2);
    }

    @Bean
    SecurityWebFilterChain authorization(ServerHttpSecurity httpSecurity) {
        return httpSecurity
                .authorizeExchange(ae -> ae
                        .pathMatchers("/proxy").authenticated()
                        .anyExchange().permitAll()
                )
                .httpBasic(Customizer.withDefaults())
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
    }

    @Bean
    MapReactiveUserDetailsService authentication() {
        return new MapReactiveUserDetailsService(User.withDefaultPasswordEncoder().username("jlong").password("pw").roles("USER").build());
    }

    @Bean
    RouteLocator gateway(RouteLocatorBuilder routeLocatorBuilder) {
        return routeLocatorBuilder
                .routes()
                .route(rs -> rs.path("/proxy").and().host("*.spring.io")
                        .filters(fs -> fs
                                .setPath("/customers")
                                .requestRateLimiter(c -> c
                                        .setRateLimiter(redisRateLimiter())
                                        .setKeyResolver(exchange -> exchange.getPrincipal().map(Principal::getName))
                                )
                        )
                        .uri("http://localhost:8080/"))
                .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(EdgeApplication.class, args);
    }

}


@Controller
@ResponseBody
class CrmRestController {

    private final CrmClient crmClient;

    CrmRestController(CrmClient crmClient) {
        this.crmClient = crmClient;
    }

    @GetMapping("/cos")
    Flux<CustomerOrders> getCustomerOrders() {
        return crmClient.getCustomerOrders();
    }

}

@Component
class CrmClient {

    private final WebClient http;
    private final RSocketRequester rSocket;

    CrmClient(WebClient http, RSocketRequester rSocket) {
        this.http = http;
        this.rSocket = rSocket;
    }

    Flux<Customer> getCustomers() {
        return this.http.get().uri("http://localhost:8080/customers").retrieve().bodyToFlux(Customer.class);
    }

    Flux<Order> getOrdersFor(Integer customerId) {
        return this.rSocket.route("orders.{cid}", customerId)
                .retrieveFlux(Order.class);
    }

    Flux<CustomerOrders> getCustomerOrders() {
        return this.getCustomers()
                .flatMap(c -> Mono.zip(
                        Mono.just(c),
                        getOrdersFor(c.id()).collectList()
                ))
                .map(tuple2 -> new CustomerOrders(tuple2.getT1(), tuple2.getT2()));
    }

}

record CustomerOrders(Customer customer, List<Order> orders) {
}

record Order(Integer customerId, Integer id) {
}

record Customer(String name, Integer id) {
}

@Controller
class CrmGraphqlController {

    private final CrmClient crmClient;

    CrmGraphqlController(CrmClient crmClient) {
        this.crmClient = crmClient;
    }

    @QueryMapping
    Flux<Customer> customers() {
        return this.crmClient.getCustomers();
    }

    @SchemaMapping(typeName = "Customer")
    Flux<Order> orders(Customer customer) {
        return this.crmClient.getOrdersFor(customer.id());
    }
}