# **Spring Cloud API Gateway & Circuit Breaker – Complete Notes**

## **1. Overview & Purpose**

* In a microservices architecture, multiple services exist (e.g., Order Service, Product Service, Payment Service).
* Direct calls from clients to individual services expose internal architecture and pose security risks.
* **API Gateway** acts as a single entry point for all client requests:

  * Handles routing to appropriate microservices
  * Implements security, authentication, and authorization
  * Provides monitoring, logging, and metrics
  * Can integrate cross-cutting concerns like rate limiting and circuit breaking

**Flow Diagram Conceptually:**

```
Client (Web/Mobile) --> API Gateway --> Microservices (Order, Product, Payment)
```

---

## **2. Creating the API Gateway with Spring Cloud Gateway**

### **2.1 Project Setup**

* Use **Spring Initializr** with:

  * Java 21 (Spring Boot 3.5.6)
  * Spring Boot
  * Artifact: `CloudGateWay`
* Dependencies:

  * **Spring Cloud Gateway** (`spring-cloud-starter-gateway-server-webflux`)
  * **Spring WebFlux** (Reactive programming)
  * **Spring Cloud Config Client** (`spring-cloud-starter-config`)
  * **Eureka Client** (`spring-cloud-starter-netflix-eureka-client`)
  * **Zipkin + Sleuth** (`opentelemetry-exporter-zipkin`) - distributed tracing
  * **Actuator** (`spring-boot-starter-actuator`) - metrics & health checks
  * **Lombok** (`lombok`) - boilerplate reduction
  * **Resilience4j** (`spring-cloud-starter-circuitbreaker-reactor-resilience4j`) - circuit breaker

---

### **2.2 Application Configuration (`application.properties`)**

```properties
# ===========================
# Spring Cloud Gateway
# ===========================
spring.application.name=cloud-gateway
server.port=9090

# ===========================
# Config Server
# ===========================
spring.config.import=configserver:http://localhost:9296

# ===========================
# Eureka Client
# ===========================
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/

# ===========================
# Gateway Routes
# ===========================
spring.cloud.gateway.server.webflux.routes[0].id=PAYMENT-SERVICE
spring.cloud.gateway.server.webflux.routes[0].uri=lb://PAYMENT-SERVICE
spring.cloud.gateway.server.webflux.routes[0].predicates[0].name=Path
spring.cloud.gateway.server.webflux.routes[0].predicates[0].args.pattern=/payment/**
spring.cloud.gateway.server.webflux.routes[0].filters[0].name=CircuitBreaker
spring.cloud.gateway.server.webflux.routes[0].filters[0].args.name=PAYMENT-SERVICE-CB
spring.cloud.gateway.server.webflux.routes[0].filters[0].args.fallbackUri=forward:/paymentFallback

spring.cloud.gateway.server.webflux.routes[1].id=ORDER-SERVICE
spring.cloud.gateway.server.webflux.routes[1].uri=lb://ORDER-SERVICE
spring.cloud.gateway.server.webflux.routes[1].predicates[0].name=Path
spring.cloud.gateway.server.webflux.routes[1].predicates[0].args.pattern=/order/**
spring.cloud.gateway.server.webflux.routes[1].filters[0].name=CircuitBreaker
spring.cloud.gateway.server.webflux.routes[1].filters[0].args.name=ORDER-SERVICE-CB
spring.cloud.gateway.server.webflux.routes[1].filters[0].args.fallbackUri=forward:/orderFallback

spring.cloud.gateway.server.webflux.routes[2].id=PRODUCT-SERVICE
spring.cloud.gateway.server.webflux.routes[2].uri=lb://PRODUCT-SERVICE
spring.cloud.gateway.server.webflux.routes[2].predicates[0].name=Path
spring.cloud.gateway.server.webflux.routes[2].predicates[0].args.pattern=/product/**
spring.cloud.gateway.server.webflux.routes[2].filters[0].name=CircuitBreaker
spring.cloud.gateway.server.webflux.routes[2].filters[0].args.name=PRODUCT-SERVICE-CB
spring.cloud.gateway.server.webflux.routes[2].filters[0].args.fallbackUri=forward:/productFallback
```

---

### **2.3 Routing Configuration**

* Define routes in `application.properties` for each microservice:

**Current Implementation:**
- **Payment Service**: `/payment/**` → `lb://PAYMENT-SERVICE`
- **Order Service**: `/order/**` → `lb://ORDER-SERVICE`  
- **Product Service**: `/product/**` → `lb://PRODUCT-SERVICE`

* **Routing logic:** All requests come to the API Gateway → routed to respective service based on path.
* **Load Balancing:** Uses `lb://` prefix for service discovery with Eureka

---

### **2.4 Testing the Gateway**

* Example: Replace direct service URL with gateway URL:

```
http://localhost:9090/payment/** --> API Gateway forwards to Payment Service
http://localhost:9090/order/** --> API Gateway forwards to Order Service
http://localhost:9090/product/** --> API Gateway forwards to Product Service
```

---

## **3. Circuit Breaker Concept**

* Prevents system overload and provides resilience when a service is down.
* Inspired by **electrical circuits**: stops sending requests when a service is failing.
* **Resilience4j** is used in modern Spring projects (Hystrix is deprecated).
* **Problem solved:** Avoids sending all requests to a downed service → saves resources and response time.

### **3.1 States of Circuit Breaker**

| State         | Description                                                                   |
| ------------- | ----------------------------------------------------------------------------- |
| **Closed**    | All requests pass normally; service is healthy.                               |
| **Open**      | Requests are blocked; service is down.                                        |
| **Half-Open** | Some requests allowed; success rate is checked to determine state transition. |

**State Transition Logic:**

1. Closed → Open if consecutive failures exceed threshold.
2. Open → Half-Open after a wait interval.
3. Half-Open → Closed if success rate above threshold, otherwise back to Open.

---

## **4. Implementing Circuit Breaker in API Gateway**

### **4.1 Add Dependency**

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-reactor-resilience4j</artifactId>
</dependency>
```

* Use **reactor variant** because the gateway is reactive (`WebFlux`).

---

### **4.2 Define Circuit Breaker Routes in `application.properties`**

**Current Implementation:**

```properties
# Payment Service Circuit Breaker
spring.cloud.gateway.server.webflux.routes[0].filters[0].name=CircuitBreaker
spring.cloud.gateway.server.webflux.routes[0].filters[0].args.name=PAYMENT-SERVICE-CB
spring.cloud.gateway.server.webflux.routes[0].filters[0].args.fallbackUri=forward:/paymentFallback

# Order Service Circuit Breaker
spring.cloud.gateway.server.webflux.routes[1].filters[0].name=CircuitBreaker
spring.cloud.gateway.server.webflux.routes[1].filters[0].args.name=ORDER-SERVICE-CB
spring.cloud.gateway.server.webflux.routes[1].filters[0].args.fallbackUri=forward:/orderFallback

# Product Service Circuit Breaker
spring.cloud.gateway.server.webflux.routes[2].filters[0].name=CircuitBreaker
spring.cloud.gateway.server.webflux.routes[2].filters[0].args.name=PRODUCT-SERVICE-CB
spring.cloud.gateway.server.webflux.routes[2].filters[0].args.fallbackUri=forward:/productFallback
```

---

### **4.3 Fallback Controller**

**Current Implementation (`FallBackController.java`):**

```java
@RestController
public class FallBackController {

    @GetMapping("/paymentFallBack")
    public String paymentFallBackMethod() {
        return "Payment Service is Down.";
    }

    @GetMapping("/orderFallback")
    public String orderFallBackMethod() {
        return "Order Service is Down.";
    }

    @GetMapping("/productFallback")
    public String fallBackMethod() {
        return "Product Service is Down.";
    }
}
```

---

### **4.4 Customizing Circuit Breaker Configuration**

**Current Implementation (`CloudGateWayApplication.java`):**

```java
@Bean
public Customizer<Resilience4JCircuitBreakerFactory> customizer() {
    return factory -> factory.configureDefault(
            id -> new Resilience4JConfigBuilder(id)
                    .circuitBreakerConfig(
                            CircuitBreakerConfig.ofDefaults()
                    ).build()
    );
}
```

* Ensures default configurations for the circuit breaker are loaded in Spring context.
* Uses Resilience4j default settings (can be customized further)

---

### **4.5 Testing Fallback**

1. Stop a microservice (e.g., Payment Service).
2. Call API Gateway endpoint:

```
http://localhost:9090/payment/...
```

3. Response from fallback:

```
"Payment Service is Down."
```

---

## **5. Key Points & Best Practices**

* **API Gateway Responsibilities:**

  * Routing
  * Authentication & Authorization
  * Rate limiting & throttling
  * Metrics & logging
  * Circuit breaking / fallback mechanisms

* **Circuit Breaker Tips:**

  * Define thresholds carefully (failure count, wait duration)
  * Use fallback methods for graceful degradation
  * Monitor success rates and adapt configuration dynamically
  * Combine with caching for data availability when services fail

* **Testing:**

  * Use **Postman** or **Swagger UI** for verifying routes and fallbacks
  * Simulate service downtime to ensure circuit breaker works as expected

---

## **6. Project Structure**

```
CloudGateWay/
├── src/main/java/com/Tulip_Tech/CloudGateWay/
│   ├── CloudGateWayApplication.java          # Main application class
│   └── controller/
│       └── FallBackController.java           # Fallback endpoints
├── src/main/resources/
│   └── application.properties                # Gateway configuration
└── pom.xml                                   # Maven dependencies
```

---

## **7. Service Dependencies**

**Required Services Running:**
1. **Eureka Server** (port 8761) - Service Discovery
2. **Config Server** (port 9296) - Configuration Management
3. **Payment Service** - Microservice
4. **Order Service** - Microservice  
5. **Product Service** - Microservice

---

### ✅ **Cheat Sheet / Quick Commands**

* **Start API Gateway:** `mvn spring-boot:run` or IDE run
* **Gateway URL:** `http://localhost:9090/<service-path>`
* **Fallback URLs:** 
  - `/paymentFallBack` - Payment service fallback
  - `/orderFallback` - Order service fallback
  - `/productFallback` - Product service fallback
* **Dependencies:** Spring Cloud Gateway, WebFlux, Resilience4j, Eureka Client, Config Client
* **Port:** 9090
* **Service Discovery:** Eureka (localhost:8761)
* **Config Server:** localhost:9296

---

## **8. Architecture Diagram**

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Client App    │───▶│   API Gateway    │───▶│  Microservices  │
│  (Web/Mobile)   │    │   (Port: 9090)   │    │                 │
└─────────────────┘    └──────────────────┘    │  ┌─────────────┐ │
                                               │  │   Payment   │ │
                                               │  │   Service   │ │
                                               │  └─────────────┘ │
                                               │  ┌─────────────┐ │
                                               │  │    Order    │ │
                                               │  │   Service   │ │
                                               │  └─────────────┘ │
                                               │  ┌─────────────┐ │
                                               │  │   Product   │ │
                                               │  │   Service   │ │
                                               │  └─────────────┘ │
                                               └─────────────────┘
                                                       │
                                                       ▼
                                               ┌─────────────────┐
                                               │ Circuit Breaker │
                                               │   (Resilience4j)│
                                               └─────────────────┘
```

---

**Perfect! ✅ Now you have a complete, structured note based on your actual CloudGateway implementation, organized for clarity and quick reference.**
