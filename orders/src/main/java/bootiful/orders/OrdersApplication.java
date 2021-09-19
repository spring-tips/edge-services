package bootiful.orders;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
public class OrdersApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrdersApplication.class, args);
    }

}

@Controller
class OrderRSocketController {

    private final Map<Integer, Collection<Order>> db = new ConcurrentHashMap<>();

    OrderRSocketController() {
        for (var customerId = 1; customerId <= 8; customerId++) {
            var orders = new ArrayList<Order>();
            for (var orderId = 1; orderId <= (Math.random() * 100); orderId++)
                orders.add(new Order(orderId, customerId));
            this.db.put( customerId ,orders);
        }
    }

    @MessageMapping ("orders.{cid}")
    Flux<Order> ordersFor (@DestinationVariable Integer cid) {
    	return Flux.fromIterable( this.db.get( cid));
	}
}

record Order(@JsonProperty("id") Integer id,
			 @JsonProperty("customerId") Integer customerId) {
}