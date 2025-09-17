# HTTP Clients in Valtimo Backend Libraries

## Overview

This document provides a comprehensive analysis of HTTP client usage patterns in the Valtimo backend libraries project. The analysis covers the types of clients used, their configurations, potential issues, and recommended best practices.

## HTTP Client Types Found

### 1. Spring RestClient (Primary - Recommended)

The primary HTTP client used throughout the codebase, representing the modern Spring approach for HTTP communications.

**Key Files:**
- `contract/src/main/kotlin/com/ritense/valtimo/contract/client/RestClientAutoConfiguration.kt`
- `contract/src/main/kotlin/com/ritense/valtimo/contract/client/ApacheRequestFactoryCustomizer.kt`
- `zgw/zaken-api/src/main/kotlin/com/ritense/zakenapi/client/ZakenApiClient.kt`
- `document-generation/smartdocuments/src/main/kotlin/com/ritense/smartdocuments/client/SmartDocumentsClient.kt`

**Usage Pattern:**
```kotlin
class ZakenApiClient(
    private val restClientBuilder: RestClient.Builder,
    // other dependencies...
) {
    private fun buildRestClient(authentication: ZakenApiAuthentication): RestClient {
        return restClientBuilder
            .clone()
            .apply {
                authentication.applyAuth(it)
            }
            .build()
    }
}
```

### 2. Spring RestTemplate (Legacy)

Still used in some legacy components, particularly mail services.

**Key Files:**
- `mail/flowmailer/src/main/kotlin/com/ritense/mail/flowmailer/service/FlowmailerDispatcher.kt`

**Usage Pattern:**
```kotlin
class FlowmailerMailDispatcher(
    private val restTemplate: RestTemplate,
    // other dependencies...
) {
    private fun submitMessage(url: String, submitMessage: SubmitMessage): MailMessageStatus {
        val httpEntity = HttpEntity(objectMapper.writeValueAsString(submitMessage), getHttpHeaders(token))
        val response = restTemplate.exchange(url, HttpMethod.POST, httpEntity, String::class.java)
        // ...
    }
}
```

### 3. Spring WebClient (Deprecated)

Found in legacy code but marked as deprecated.

**Key Files:**
- `zgw/src/main/kotlin/com/ritense/zgw/ClientTools.kt:35` (marked as deprecated)
- `contract/src/main/kotlin/com/ritense/valtimo/contract/client/LoggingWebClientCustomizer.kt`

## Configuration and Customization

### 1. Timeout Configuration

**Configuration Properties:**
```kotlin
@ConfigurationProperties(prefix = "valtimo.http.rest-client")
data class ValtimoHttpRestClientConfigurationProperties(
    val connectTimeout: Long = 5, // seconds
    val connectionRequestTimeout: Long = 5 // seconds
)
```

**Apache HTTP Components Integration:**
```kotlin
class ApacheRequestFactoryCustomizer(
    private val valtimoHttpRestClientConfigurationProperties: ValtimoHttpRestClientConfigurationProperties
) : RestClientCustomizer {
    override fun customize(restClientBuilder: RestClient.Builder) {
        val apacheRequestFactory = HttpComponentsClientHttpRequestFactory()
        apacheRequestFactory.setConnectTimeout(Duration.ofSeconds(connectTimeout))
        apacheRequestFactory.setConnectionRequestTimeout(Duration.ofSeconds(connectionRequestTimeout))
        restClientBuilder.requestFactory(BufferingClientHttpRequestFactory(apacheRequestFactory))
    }
}
```

### 2. Docker Environment Support

**Host Resolution for Docker:**
```kotlin
@Component
class HostDockerInternalRestClientCustomizer(
    private val dockerPorts: List<Int>,
    private val rewriteRequestHost: Boolean,
    private val webServerPort: Int,
) : RestClientCustomizer, RestTemplateCustomizer, ClientHttpRequestInterceptor
```

This customizer automatically handles:
- `localhost` ↔ `host.docker.internal` conversion
- Port-based routing in Docker environments
- Request/response body modification for JSON content

### 3. Logging and Debugging

**Two logging implementations:**

1. **RestClient Logging:**
```kotlin
@Component
class LoggingRestClientCustomizer : RestClientCustomizer, ClientHttpRequestInterceptor {
    override fun intercept(request: HttpRequest, requestBody: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
        // Logs detailed request/response information
        // Throws enhanced HttpClientErrorException with full context
    }
}
```

2. **WebClient Logging (Netty-based):**
```kotlin
class LoggingWebClientCustomizer: WebClientCustomizer {
    override fun customize(webClientBuilder: WebClient.Builder?) {
        webClientBuilder?.clientConnector(
            ReactorClientHttpConnector(
                HttpClient.create().wiretap("reactor.netty.http.client.HttpClient", LogLevel.DEBUG)
            )
        )
    }
}
```

## Client Implementation Patterns

### 1. ZGW API Pattern (Recommended)

Used across Dutch government standard (ZGW) API integrations:

```kotlin
class ZakenApiClient(
    private val restClientBuilder: RestClient.Builder,
    private val outboxService: OutboxService,
    private val objectMapper: ObjectMapper,
    private val authorizationService: AuthorizationService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    fun createZaak(authentication: ZakenApiAuthentication, baseUrl: URI, request: CreateZaakRequest): ZaakResponse {
        val result = buildRestClient(authentication)
            .post()
            .uri {
                ClientTools.baseUrlToBuilder(it, baseUrl)
                    .path("zaken")
                    .build()
            }
            .headers(this::defaultHeaders)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body<ZaakResponse>()!!

        // Event publishing and outbox pattern
        val event = ZaakCreated(result.url.toString(), objectMapper.valueToTree(result))
        applicationEventPublisher.publishEvent(event)
        outboxService.send { event }
        return result
    }
}
```

**Benefits:**
- Authorization integration
- Event-driven architecture support
- Standardized error handling
- Outbox pattern for reliable messaging

### 2. Abstract Endpoint Pattern

Used in plugins like Exact:

```kotlin
abstract class ExactEndpoint<Response>(val type: Class<Response>) {
    open fun call(client: RestClient): Response {
        try {
            return create(client)
                .retrieve()
                .body(type)!!
        } catch (e: RestClientResponseException) {
            throw HttpClientErrorException(e.statusCode, e.responseBodyAsString)
        }
    }

    abstract fun create(client: RestClient): RestClient.RequestHeadersSpec<*>
}
```

**Benefits:**
- Type-safe response handling
- Consistent error handling
- Template method pattern for different endpoint implementations

## Issues Identified

### 1. **Critical Issues**

#### Deprecated WebClient Usage
- **Location:** `zgw/src/main/kotlin/com/ritense/zgw/ClientTools.kt:35`
- **Issue:** Contains deprecated WebClient utilities that should be migrated to RestClient
- **Impact:** Technical debt, potential future compatibility issues

#### Missing Read Timeouts
- **Issue:** Only connection timeouts are configured, no read timeouts
- **Impact:** Potential hanging connections on slow responses
- **Recommendation:** Add read timeout configuration

#### Inconsistent Error Handling
- **Issue:** Different error handling patterns across clients
- **Impact:** Unpredictable error behavior, difficult debugging

### 2. **Performance Concerns**

#### Connection Pool Management
- **Issue:** No explicit connection pool configuration visible
- **Impact:** Potential resource exhaustion under load
- **Recommendation:** Configure Apache HTTP Client connection pools

#### Request/Response Buffering
- **Location:** `ApacheRequestFactoryCustomizer.kt:37`
- **Issue:** Uses `BufferingClientHttpRequestFactory` for all requests
- **Impact:** Memory overhead for large payloads
- **Recommendation:** Consider streaming for large responses

### 3. **Security Considerations**

#### Authentication Cloning
- **Pattern:** `restClientBuilder.clone().apply { authentication.applyAuth(it) }`
- **Risk:** Potential credential leakage if builder is reused
- **Mitigation:** Current pattern is safe due to cloning

#### Host Validation
- **Location:** `ZakenApiClient.kt:599-605`
- **Good Practice:** Validates URLs against expected hosts
- **Recommendation:** Apply this pattern consistently across all clients

## Recommendations

### 1. **Immediate Actions**

1. **Remove Deprecated Code**
   ```kotlin
   // Remove from ClientTools.kt
   @Deprecated("Use of WebClient is deprecated, this was used before")
   fun <T> getTypedPage(responseClass: Class<out T>): ParameterizedTypeReference<Page<T>>
   ```

2. **Add Read Timeout Configuration**
   ```kotlin
   @ConfigurationProperties(prefix = "valtimo.http.rest-client")
   data class ValtimoHttpRestClientConfigurationProperties(
       val connectTimeout: Long = 5,
       val connectionRequestTimeout: Long = 5,
       val readTimeout: Long = 30 // Add this
   )
   ```

3. **Standardize Error Handling**
   Create a common error handler interface:
   ```kotlin
   interface HttpClientErrorHandler {
       fun handleClientError(response: ClientHttpResponse): Exception
       fun handleServerError(response: ClientHttpResponse): Exception
   }
   ```

### 2. **Configuration Improvements**

1. **Connection Pool Configuration**
   ```kotlin
   class ApacheRequestFactoryCustomizer {
       override fun customize(restClientBuilder: RestClient.Builder) {
           val connectionManager = PoolingHttpClientConnectionManager().apply {
               maxTotal = 100
               defaultMaxPerRoute = 20
           }

           val httpClient = HttpClients.custom()
               .setConnectionManager(connectionManager)
               .build()

           val requestFactory = HttpComponentsClientHttpRequestFactory(httpClient)
           // Configure timeouts...
       }
   }
   ```

2. **Circuit Breaker Integration**
   Consider adding Resilience4j circuit breakers for external service calls.

3. **Metrics and Monitoring**
   Add Micrometer metrics for HTTP client operations:
   ```kotlin
   @Component
   class MetricsRestClientCustomizer(
       private val meterRegistry: MeterRegistry
   ) : RestClientCustomizer {
       override fun customize(restClientBuilder: RestClient.Builder) {
           restClientBuilder.requestInterceptor(MetricsClientHttpRequestInterceptor(meterRegistry))
       }
   }
   ```

### 3. **Migration Strategy**

1. **Phase 1:** Remove deprecated WebClient utilities
2. **Phase 2:** Migrate RestTemplate usage to RestClient
3. **Phase 3:** Standardize error handling across all clients
4. **Phase 4:** Add comprehensive monitoring and metrics

### 4. **Best Practices for Production HTTP Clients**

#### Connection Pool Management

**Configure Proper Pool Sizes:**
```kotlin
@Component
class ProductionHttpClientCustomizer : RestClientCustomizer {
    override fun customize(restClientBuilder: RestClient.Builder) {
        val connectionManager = PoolingHttpClientConnectionManager().apply {
            // Total connections across all routes
            maxTotal = 200
            // Default max connections per route (host:port)
            defaultMaxPerRoute = 50
            // Specific route limits for high-traffic endpoints
            setMaxPerRoute(HttpRoute(HttpHost("api.external-service.com", 443, "https")), 100)
        }

        val httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setConnectionTimeToLive(5, TimeUnit.MINUTES) // TTL for persistent connections
            .evictIdleConnections(2, TimeUnit.MINUTES) // Close idle connections
            .setDefaultRequestConfig(
                RequestConfig.custom()
                    .setConnectTimeout(5000) // Connection timeout
                    .setSocketTimeout(30000) // Read timeout
                    .setConnectionRequestTimeout(3000) // Pool timeout
                    .build()
            )
            .build()

        restClientBuilder.requestFactory(HttpComponentsClientHttpRequestFactory(httpClient))
    }
}
```

#### Comprehensive Timeout Strategy

**Multi-layered Timeout Configuration:**
```kotlin
@ConfigurationProperties(prefix = "valtimo.http.rest-client")
data class HttpClientConfigurationProperties(
    // Connection establishment timeout
    val connectTimeout: Duration = Duration.ofSeconds(5),

    // Time to wait for data (socket timeout)
    val readTimeout: Duration = Duration.ofSeconds(30),

    // Time to wait for connection from pool
    val connectionRequestTimeout: Duration = Duration.ofSeconds(3),

    // Keep-alive duration for persistent connections
    val keepAliveDuration: Duration = Duration.ofMinutes(5),

    // Idle connection eviction timeout
    val idleConnectionTimeout: Duration = Duration.ofMinutes(2),

    // Per-endpoint specific timeouts
    val endpointTimeouts: Map<String, EndpointTimeouts> = emptyMap()
)

data class EndpointTimeouts(
    val connectTimeout: Duration,
    val readTimeout: Duration,
    val retryAttempts: Int = 3
)
```

#### Backpressure and Rate Limiting

**Circuit Breaker Integration:**
```kotlin
@Component
class ResilientHttpClientCustomizer(
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val retryRegistry: RetryRegistry,
    private val bulkheadRegistry: BulkheadRegistry
) : RestClientCustomizer {

    override fun customize(restClientBuilder: RestClient.Builder) {
        restClientBuilder.requestInterceptor { request, body, execution ->
            val serviceName = extractServiceName(request.uri)

            // Apply circuit breaker
            val circuitBreaker = circuitBreakerRegistry.circuitBreaker(serviceName)

            // Apply retry logic
            val retry = retryRegistry.retry(serviceName)

            // Apply bulkhead (concurrency limiting)
            val bulkhead = bulkheadRegistry.bulkhead(serviceName)

            val decoratedExecution = Decorators.ofSupplier {
                execution.execute(request, body)
            }
                .withCircuitBreaker(circuitBreaker)
                .withRetry(retry)
                .withBulkhead(bulkhead)
                .decorate()

            decoratedExecution.get()
        }
    }
}
```

**Rate Limiting Implementation:**
```kotlin
@Component
class RateLimitingInterceptor(
    private val rateLimiterRegistry: RateLimiterRegistry
) : ClientHttpRequestInterceptor {

    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution
    ): ClientHttpResponse {
        val serviceName = extractServiceName(request.uri)
        val rateLimiter = rateLimiterRegistry.rateLimiter(serviceName)

        return rateLimiter.executeSupplier {
            execution.execute(request, body)
        }
    }
}
```

#### Streaming and Memory Management

**Large Response Handling:**
```kotlin
class StreamingApiClient(private val restClientBuilder: RestClient.Builder) {

    fun downloadLargeFile(url: URI): Resource {
        return restClientBuilder
            .clone()
            .messageConverters { converters ->
                // Use streaming converter for large files
                converters.add(ResourceHttpMessageConverter(true))
            }
            .build()
            .get()
            .uri(url)
            .retrieve()
            .body<Resource>()!!
    }

    fun processStreamingData(url: URI, processor: (InputStream) -> Unit) {
        restClientBuilder
            .clone()
            .build()
            .get()
            .uri(url)
            .retrieve()
            .body<Resource>()!!
            .inputStream
            .use(processor)
    }
}
```

#### Monitoring and Observability

**Comprehensive Metrics Collection:**
```kotlin
@Component
class ObservabilityHttpClientCustomizer(
    private val meterRegistry: MeterRegistry,
    private val tracingCustomizer: HttpClientTracingCustomizer
) : RestClientCustomizer {

    override fun customize(restClientBuilder: RestClient.Builder) {
        restClientBuilder
            .requestInterceptor(MetricsInterceptor(meterRegistry))
            .requestInterceptor(TracingInterceptor())
            .requestInterceptor(LoggingInterceptor())
    }

    private class MetricsInterceptor(
        private val meterRegistry: MeterRegistry
    ) : ClientHttpRequestInterceptor {

        override fun intercept(
            request: HttpRequest,
            body: ByteArray,
            execution: ClientHttpRequestExecution
        ): ClientHttpResponse {
            val timer = Timer.start(meterRegistry)

            return try {
                val response = execution.execute(request, body)

                timer.stop(Timer.builder("http.client.requests")
                    .tag("method", request.method.name())
                    .tag("uri", request.uri.path)
                    .tag("status", response.statusCode.value().toString())
                    .tag("outcome", if (response.statusCode.is2xxSuccessful) "SUCCESS" else "ERROR")
                    .register(meterRegistry))

                // Track response size
                meterRegistry.counter("http.client.response.size",
                    "uri", request.uri.path).increment(response.headers.contentLength.toDouble())

                response
            } catch (ex: Exception) {
                timer.stop(Timer.builder("http.client.requests")
                    .tag("method", request.method.name())
                    .tag("uri", request.uri.path)
                    .tag("status", "UNKNOWN")
                    .tag("outcome", "ERROR")
                    .tag("exception", ex.javaClass.simpleName)
                    .register(meterRegistry))
                throw ex
            }
        }
    }
}
```

#### Security Best Practices

**Secure Configuration:**
```kotlin
@Component
class SecurityAwareHttpClientCustomizer : RestClientCustomizer {

    override fun customize(restClientBuilder: RestClient.Builder) {
        val sslContext = SSLContextBuilder.create()
            .loadTrustMaterial(null) { chain, authType ->
                // Implement custom certificate validation
                validateCertificateChain(chain, authType)
            }
            .build()

        val httpClient = HttpClients.custom()
            .setSSLContext(sslContext)
            .setSSLHostnameVerifier(DefaultHostnameVerifier()) // Strict hostname verification
            .setDefaultHeaders(listOf(
                BasicHeader("User-Agent", "Valtimo-Backend/1.0"),
                BasicHeader("X-Forwarded-Proto", "https")
            ))
            .build()

        restClientBuilder.requestFactory(HttpComponentsClientHttpRequestFactory(httpClient))
    }
}
```

#### Error Handling and Resilience

**Advanced Error Handling:**
```kotlin
class ResilientApiClient(private val restClientBuilder: RestClient.Builder) {

    fun <T> executeWithResilience(
        operation: (RestClient) -> T,
        fallback: () -> T? = { null }
    ): T? {
        return try {
            val client = restClientBuilder
                .clone()
                .defaultStatusHandler(HttpStatusCode::is4xxClientError) { request, response ->
                    val errorBody = response.bodyTo(String::class.java)
                    when (response.statusCode) {
                        HttpStatus.TOO_MANY_REQUESTS -> throw RateLimitExceededException(errorBody)
                        HttpStatus.UNAUTHORIZED -> throw AuthenticationException(errorBody)
                        HttpStatus.FORBIDDEN -> throw AuthorizationException(errorBody)
                        HttpStatus.NOT_FOUND -> throw ResourceNotFoundException(errorBody)
                        else -> throw ClientException(response.statusCode, errorBody)
                    }
                }
                .defaultStatusHandler(HttpStatusCode::is5xxServerError) { request, response ->
                    val errorBody = response.bodyTo(String::class.java)
                    throw ServerException(response.statusCode, errorBody)
                }
                .build()

            operation(client)
        } catch (ex: Exception) {
            logger.error("HTTP operation failed, attempting fallback", ex)
            fallback() ?: throw ex
        }
    }
}
```

#### Performance Optimization

**Request/Response Optimization:**
```kotlin
@Component
class PerformanceOptimizedHttpClientCustomizer : RestClientCustomizer {

    override fun customize(restClientBuilder: RestClient.Builder) {
        restClientBuilder
            .requestInterceptor(CompressionInterceptor())
            .requestInterceptor(CachingInterceptor())
            .messageConverters { converters ->
                // Optimize JSON processing
                converters.removeIf { it is MappingJackson2HttpMessageConverter }
                converters.add(FastJsonHttpMessageConverter())
            }
    }

    private class CompressionInterceptor : ClientHttpRequestInterceptor {
        override fun intercept(
            request: HttpRequest,
            body: ByteArray,
            execution: ClientHttpRequestExecution
        ): ClientHttpResponse {
            // Add compression headers
            request.headers.set("Accept-Encoding", "gzip, deflate")
            if (body.size > 1024) { // Compress large request bodies
                request.headers.set("Content-Encoding", "gzip")
                val compressedBody = gzipCompress(body)
                return execution.execute(request, compressedBody)
            }
            return execution.execute(request, body)
        }
    }
}
```

#### Configuration Examples

**Production-Ready Configuration:**
```yaml
valtimo:
  http:
    rest-client:
      connect-timeout: 5s
      read-timeout: 30s
      connection-request-timeout: 3s
      keep-alive-duration: 5m
      idle-connection-timeout: 2m
      endpoint-timeouts:
        zaken-api:
          connect-timeout: 3s
          read-timeout: 15s
          retry-attempts: 3
        documents-api:
          connect-timeout: 5s
          read-timeout: 60s # Longer for file operations
          retry-attempts: 2

resilience4j:
  circuitbreaker:
    instances:
      zaken-api:
        failure-rate-threshold: 50
        slow-call-rate-threshold: 50
        slow-call-duration-threshold: 5s
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        sliding-window-size: 10

  retry:
    instances:
      zaken-api:
        max-attempts: 3
        wait-duration: 1s
        retry-exceptions:
          - java.net.ConnectException
          - java.net.SocketTimeoutException

  bulkhead:
    instances:
      zaken-api:
        max-concurrent-calls: 50
        max-wait-duration: 10s

  ratelimiter:
    instances:
      zaken-api:
        limit-for-period: 100
        limit-refresh-period: 1s
        timeout-duration: 0s
```

### 5. **Testing Best Practices**

**Comprehensive HTTP Client Testing:**
```kotlin
@ExtendWith(WireMockExtension::class)
class HttpClientIntegrationTest {

    @Test
    fun `should handle connection timeout gracefully`(wireMock: WireMockServer) {
        wireMock.stubFor(get(urlEqualTo("/api/test"))
            .willReturn(aResponse().withFixedDelay(10000))) // Longer than timeout

        assertThrows<ResourceAccessException> {
            apiClient.getData()
        }
    }

    @Test
    fun `should retry on transient failures`(wireMock: WireMockServer) {
        wireMock.stubFor(get(urlEqualTo("/api/test"))
            .inScenario("retry")
            .whenScenarioStateIs(STARTED)
            .willSetStateTo("second-call")
            .willReturn(serverError()))

        wireMock.stubFor(get(urlEqualTo("/api/test"))
            .inScenario("retry")
            .whenScenarioStateIs("second-call")
            .willReturn(ok().withBody("success")))

        val result = apiClient.getData()
        assertEquals("success", result)
    }

    @Test
    fun `should respect rate limits`() {
        // Test rate limiting behavior
        val futures = (1..150).map {
            CompletableFuture.supplyAsync { apiClient.getData() }
        }

        val results = futures.map { it.join() }
        // Verify some requests were rate limited
    }
}

## Testing Recommendations

1. **Mock HTTP clients in tests**
2. **Use WireMock for integration testing**
3. **Test timeout scenarios**
4. **Verify error handling behavior**
5. **Test authentication mechanisms**

## Conclusion

The Valtimo project demonstrates a well-structured approach to HTTP client management with good separation of concerns and configuration. The primary recommendation is to complete the migration from deprecated WebClient usage to RestClient and implement consistent error handling patterns across all clients.

The current architecture supports:
- ✅ Configurable timeouts
- ✅ Docker environment adaptation
- ✅ Comprehensive logging
- ✅ Authorization integration
- ✅ Event-driven patterns

Areas for improvement:
- ⚠️ Remove deprecated code
- ⚠️ Add read timeout configuration
- ⚠️ Standardize error handling
- ⚠️ Add connection pool management
- ⚠️ Implement circuit breaker patterns