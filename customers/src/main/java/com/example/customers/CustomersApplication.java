package com.example.customers;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@SpringBootApplication
public class CustomersApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomersApplication.class, args);
    }

}

@Controller
@ResponseBody
class CustomersRestController {

    private final AtomicInteger id = new AtomicInteger();
    private final List<Customer> customers  = List.of(
            "Mario", "Zhouyue", "Zhen", "Mia", "Stéphane", "Valerie", "Mike", "Julia")
            .stream()
            .map( name -> new Customer( this.id.incrementAndGet(), name))
            .collect(Collectors.toList());

    @GetMapping("/customers")
    Collection<Customer> get() {
        return this.customers ;
    }
}

record Customer(@JsonProperty("id") Integer id, @JsonProperty("name") String name) {
}