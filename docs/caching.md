# Caching Architecture Analysis - Valtimo Platform

## Executive Summary

This document provides a comprehensive analysis of caching patterns in the Valtimo platform, focusing on multi-instance deployment challenges, cache invalidation strategies, and performance optimization opportunities. The analysis reveals critical gaps in the current caching implementation that pose risks for scaled deployments.

## Current State Analysis

### Existing Cache Implementation

#### Active Caching Modules
1. **ZGW Catalogi API** (`zgw/catalogi-api`)
   - Caches `Informatieobjecttype` entities
   - Cache key: `"zgw-catalogiapi-informatieobjecttype"`
   - Manual cache population via `prefillCache()`
   - Uses `@Cacheable` annotation with URL-based keys

2. **Keycloak IAM** (`keycloak-iam`)
   - User information caching
   - Cache keys: `"EMAIL_ManageableUser"`, `"USER_IDENTIFIER_ValtimoUser"`
   - Scheduled eviction every hour (`PT1H`)
   - Custom cache implementation with `CacheManagerUserCache`

3. **Dashboard Module** (`dashboard`)
   - Widget data caching
   - Cache key: `"dashboard.widgetData"`
   - Per-widget configuration caching
   - Uses proxy pattern for AOP cache support

4. **Milestones Module** (`milestones`)
   - Overdue milestone count caching
   - Cache key: `"milestones.overDueMilestoneCount"`
   - No explicit eviction strategy

#### Cache Configuration
- **Default Implementation**: Spring Boot auto-configuration
- **Cache Manager**: `ConcurrentMapCacheManager` (default)
- **Distribution**: **None** - Local caches only
- **Configuration**: Module-level `@EnableCaching` annotations
- **Hibernate L2 Cache**: Disabled in tests, enabled only for `ChoiceField` entities

### Configuration Analysis

```yaml
# No centralized cache configuration found
# Hibernate settings from test configs:
hibernate:
  cache:
    use_second_level_cache: false
    use_query_cache: false
    region.factory_class: org.hibernate.cache.ehcache.SingletonEhCacheRegionFactory
```

## Critical Issues for Multi-Instance Deployment

### 1. Cache Consistency Risks

**Problem**: Using `ConcurrentMapCacheManager` creates local caches that are not synchronized across instances.

**Impact**:
- User cache misses after load balancer routing
- Stale authorization decisions
- Inconsistent application state across instances

**Example Scenario**:
```java
// Instance 1: User admin updates form definition
formDefinitionService.modifyFormDefinition("form1", newDefinition);

// Instance 2: Still serves cached old definition
// No cache invalidation propagation
```

### 2. No Distributed Cache Infrastructure

**Current Implementation**:
```kotlin
@AutoConfiguration
@EnableCaching
class DashboardAutoConfiguration {
    // Uses default ConcurrentMapCacheManager
    // No distributed cache configuration
}
```

**Required for Scale**:
```kotlin
@Bean
fun cacheManager(): CacheManager {
    return RedisCacheManager.builder(connectionFactory)
        .cacheDefaults(
            RedisCacheConfiguration.defaultCacheConfig()
                .ttl(Duration.ofMinutes(30))
                .disableCachingNullValues()
        )
        .transactionAware()
        .build()
}
```

### 3. Security Implications

**Keycloak User Cache**:
- 1-hour TTL without cross-instance invalidation
- User permission changes not reflected across instances
- Potential authorization bypass scenarios

## Cache Invalidation Analysis

### Current Invalidation Patterns

#### 1. Time-Based Eviction (Keycloak)
```kotlin
@CacheEvict(allEntries = true, value = ["EMAIL_ManageableUser", "USER_IDENTIFIER_ValtimoUser"])
@Scheduled(fixedRateString = "\${valtimo.keycloak.cache.maxTtl:PT1H}")
fun logCacheClear() {
    logger.debug { "Clearing all user information cache" }
}
```

**Issues**:
- Fixed time interval regardless of data changes
- All-or-nothing eviction (inefficient)
- No cross-instance synchronization

#### 2. Manual Cache Population (Catalogi API)
```kotlin
private fun prefillInformatieobjecttypeCache(
    authenticationPluginConfiguration: CatalogiApiAuthentication,
    url: URI
) {
    Page.getAll { page ->
        getInformatieobjecttypes(/* ... */)
    }.forEach {
        cacheManager.getCache(INFORMATIEOBJECTTYPECACHE_KEY)?.put(it.url!!, it)
    }
}
```

**Issues**:
- No invalidation on updates
- Manual cache management
- Race conditions in multi-instance setup

#### 3. No Mutation-Based Invalidation

**Missing Pattern**:
```java
// SHOULD BE IMPLEMENTED
@CacheEvict(value = "formDefinitions", key = "#name")
public FormDefinition modifyFormDefinition(String name, JsonNode formDefinition) {
    return formDefinitionService.modifyFormDefinition(name, formDefinition);
}
```

### Event-Driven Architecture Opportunities

**Existing Event System**:
```kotlin
// Current events that could trigger cache invalidation
- DocumentCreated/Updated/Deleted
- ZaakCreated/Updated
- PluginConfigurationChanged
- FormDefinitionModified
```

**Recommended Cache Invalidation Events**:
```kotlin
@EventListener
@CacheEvict(value = ["documentDefinitions"], key = "#event.documentDefinitionName")
fun handleDocumentDefinitionChanged(event: DocumentDefinitionChangedEvent) {
    // Automatic cache invalidation
}
```

## High-Impact Caching Opportunities

### 1. Document & Form Definitions (Critical Priority)

**Access Pattern Analysis**:
```java
// JsonSchemaDocumentDefinitionService - Heavy read operations
public Optional<JsonSchemaDocumentDefinition> findBy(DocumentDefinition.Id id) {
    // Called on every document operation
    // Complex authorization checks
    // Stable data (rarely changes)
}

// FormIoFormDefinitionService - Frequent lookups
public Optional<FormIoFormDefinition> getFormDefinitionByName(String name) {
    // Called on every form render
    // Database queries with complex filtering
    // Low change frequency
}
```

**Recommended Implementation**:
```java
@Cacheable(value = "documentDefinitions", key = "#id", unless = "#result == null")
public Optional<JsonSchemaDocumentDefinition> findBy(DocumentDefinition.Id id) {
    // Implementation
}

@CacheEvict(value = "documentDefinitions", key = "#id")
public DocumentDefinition updateDocumentDefinition(DocumentDefinition.Id id, /* params */) {
    // Implementation with automatic invalidation
}
```

### 2. Authorization Service (Performance Critical)

**Current Usage Pattern**:
```java
// Called on EVERY request - major performance bottleneck
authorizationService.requirePermission(
    EntityAuthorizationRequest(
        JsonSchemaDocumentDefinition.class,
        VIEW_LIST
    )
);

// Complex specification building
authorizationService.getAuthorizationSpecification(/* ... */);
```

**Caching Challenges**:
- Context-dependent results (user, resource, action)
- Security-sensitive data
- Complex cache key generation

**Recommended Approach**:
```java
@Cacheable(
    value = "authorizationDecisions",
    key = "#request.resourceType.simpleName + ':' + #request.action + ':' + T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication().getName()",
    condition = "#request.action != T(com.ritense.authorization.Action).deny()"
)
public boolean hasPermission(AuthorizationRequest request) {
    // Cache permission decisions with user context
}
```

### 3. Plugin Configurations (Encryption Overhead)

**Performance Issue**:
```kotlin
class PluginConfiguration {
    // Expensive operations on every access
    fun decryptProperties() {
        // Encryption/decryption overhead
        // Complex property mapping
    }
}
```

**Current Access Pattern**:
```java
// Called frequently during plugin execution
pluginService.getPluginConfiguration(pluginDefinitionKey, title)
```

**Recommended Implementation**:
```java
@Cacheable(
    value = "pluginConfigurations",
    key = "#pluginDefinitionKey + ':' + #title"
)
public PluginConfiguration getPluginConfiguration(String pluginDefinitionKey, String title) {
    // Cache decrypted configurations
    // Implement cache-aside for encryption overhead
}

@CacheEvict(value = "pluginConfigurations", allEntries = true)
public void clearPluginConfigurationCache() {
    // Called when plugin configurations change
}
```

### 4. External API Caching (ZGW APIs)

**Current API Client Pattern**:
```kotlin
class ZakenApiClient {
    // 30+ RestClient implementations
    // No caching for stable reference data
    // Network overhead on every call

    fun getZaaktype(authentication: ZakenApiAuthentication, baseUrl: URI, zaaktypeUrl: URI): Zaaktype {
        // Should be cached - zaaktypen rarely change
    }

    fun getStatustypen(/* ... */): Page<Statustype> {
        // Reference data - perfect for caching
    }
}
```

**Recommended Implementation**:
```kotlin
@Cacheable(
    value = "zgw-zaaktypen",
    key = "#baseUrl.host + ':' + #zaaktypeUrl",
    unless = "#result == null"
)
fun getZaaktype(authentication: ZakenApiAuthentication, baseUrl: URI, zaaktypeUrl: URI): Zaaktype {
    // Cache with TTL for external API data
}
```

### 5. Choice Fields & Reference Data

**Current Implementation**:
```java
@Entity
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ChoiceField extends AbstractAuditingEntity {
    // Only entity with Hibernate L2 cache
    // Reference data with low change frequency
}
```

**Opportunity**:
```java
@Cacheable(value = "choiceFields", key = "#keyName")
public Optional<ChoiceField> findByKeyName(String keyName) {
    // Extend caching to service layer
    // Add choice field values caching
}
```

## Recommended Implementation Strategy

### Phase 1: Distributed Cache Infrastructure

#### 1. Redis Implementation
```yaml
# application.yml
spring:
  cache:
    type: redis
    redis:
      time-to-live: PT30M
      cache-null-values: false
      use-key-prefix: true
      key-prefix: "valtimo:"
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: ${REDIS_DATABASE:0}
      timeout: PT10S
      lettuce:
        pool:
          max-active: 8
          max-wait: PT5S
```

#### 2. Cache Configuration
```kotlin
@Configuration
@EnableCaching
class CacheConfiguration {

    @Bean
    fun cacheManager(connectionFactory: LettuceConnectionFactory): CacheManager {
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultCacheConfig())
            .withCacheConfiguration("documentDefinitions",
                documentDefinitionCacheConfig())
            .withCacheConfiguration("authorizationDecisions",
                authorizationCacheConfig())
            .withCacheConfiguration("pluginConfigurations",
                pluginConfigurationCacheConfig())
            .transactionAware()
            .build()
    }

    private fun defaultCacheConfig(): RedisCacheConfiguration {
        return RedisCacheConfiguration.defaultCacheConfig()
            .ttl(Duration.ofMinutes(30))
            .disableCachingNullValues()
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(GenericJackson2JsonRedisSerializer()))
    }

    private fun documentDefinitionCacheConfig(): RedisCacheConfiguration {
        return defaultCacheConfig()
            .ttl(Duration.ofHours(2)) // Long TTL for stable data
    }

    private fun authorizationCacheConfig(): RedisCacheConfiguration {
        return defaultCacheConfig()
            .ttl(Duration.ofMinutes(5)) // Short TTL for security
    }
}
```

### Phase 2: Cache Invalidation Framework

#### 1. Event-Driven Invalidation
```kotlin
@Component
class CacheInvalidationEventListener {

    @Autowired
    private lateinit var cacheManager: CacheManager

    @EventListener
    @CacheEvict(value = ["documentDefinitions"], key = "#event.documentDefinitionId")
    fun handleDocumentDefinitionChanged(event: DocumentDefinitionChangedEvent) {
        logger.info("Invalidating document definition cache for: ${event.documentDefinitionId}")
    }

    @EventListener
    @CacheEvict(value = ["formDefinitions"], key = "#event.formDefinitionName")
    fun handleFormDefinitionChanged(event: FormDefinitionChangedEvent) {
        logger.info("Invalidating form definition cache for: ${event.formDefinitionName}")
    }

    @EventListener
    @CacheEvict(value = ["authorizationDecisions"], allEntries = true)
    fun handleUserRoleChanged(event: UserRoleChangedEvent) {
        logger.info("Clearing all authorization caches due to role change")
    }
}
```

#### 2. Manual Invalidation API
```kotlin
@RestController
@RequestMapping("/management/cache")
class CacheManagementController {

    @Autowired
    private lateinit var cacheManager: CacheManager

    @PostMapping("/invalidate/{cacheName}")
    fun invalidateCache(@PathVariable cacheName: String) {
        cacheManager.getCache(cacheName)?.clear()
    }

    @PostMapping("/invalidate-all")
    fun invalidateAllCaches() {
        cacheManager.cacheNames.forEach { cacheName ->
            cacheManager.getCache(cacheName)?.clear()
        }
    }

    @GetMapping("/stats")
    fun getCacheStatistics(): Map<String, Any> {
        // Return cache hit/miss statistics
    }
}
```

### Phase 3: Strategic Cache Implementation

#### 1. Document Definition Caching
```java
@Service
@Transactional
public class CachedJsonSchemaDocumentDefinitionService implements DocumentDefinitionService {

    @Cacheable(
        value = "documentDefinitions",
        key = "#id",
        unless = "#result.isEmpty()"
    )
    @Override
    public Optional<JsonSchemaDocumentDefinition> findBy(DocumentDefinition.Id id) {
        // Delegate to original implementation
        return delegate.findBy(id);
    }

    @CacheEvict(value = "documentDefinitions", key = "#result.getId()")
    @Override
    public JsonSchemaDocumentDefinition deploy(DeployDocumentDefinitionRequest request) {
        return delegate.deploy(request);
    }

    @CacheEvict(value = "documentDefinitions", allEntries = true)
    @Override
    public void removeDocumentDefinition(String documentDefinitionName) {
        delegate.removeDocumentDefinition(documentDefinitionName);
    }
}
```

#### 2. Authorization Service Caching
```kotlin
@Component
class CachedAuthorizationService(
    private val delegate: AuthorizationService
) : AuthorizationService {

    @Cacheable(
        value = ["authorizationDecisions"],
        key = "#request.resourceType.simpleName + ':' + #request.action.actionKey + ':' + T(com.ritense.authorization.AuthorizationContext).getCurrentUserLogin()",
        condition = "#request.action.actionKey != 'DENY'"
    )
    override fun hasPermission(request: AuthorizationRequest<*>): Boolean {
        return delegate.hasPermission(request)
    }

    // Cache authorization specifications for common patterns
    @Cacheable(
        value = ["authorizationSpecs"],
        key = "#request.resourceType.simpleName + ':' + #request.action.actionKey"
    )
    override fun <T : Any> getAuthorizationSpecification(
        request: AuthorizationRequest<T>,
        permissions: List<Permission>?
    ): AuthorizationSpecification<T> {
        return delegate.getAuthorizationSpecification(request, permissions)
    }
}
```

#### 3. External API Caching with Circuit Breaker
```kotlin
@Component
class CachedZakenApiClient(
    private val delegate: ZakenApiClient
) {

    @Cacheable(
        value = ["zgw-zaaktypen"],
        key = "#baseUrl.host + ':' + #zaaktypeUrl",
        unless = "#result == null"
    )
    @CircuitBreaker(name = "zakenapi", fallbackMethod = "getCachedZaaktype")
    fun getZaaktype(
        authentication: ZakenApiAuthentication,
        baseUrl: URI,
        zaaktypeUrl: URI
    ): Zaaktype {
        return delegate.getZaaktype(authentication, baseUrl, zaaktypeUrl)
    }

    private fun getCachedZaaktype(
        authentication: ZakenApiAuthentication,
        baseUrl: URI,
        zaaktypeUrl: URI,
        exception: Exception
    ): Zaaktype? {
        // Return cached value even if expired during circuit breaker
        val cache = cacheManager.getCache("zgw-zaaktypen")
        val cacheKey = "${baseUrl.host}:$zaaktypeUrl"
        return cache?.get(cacheKey)?.get() as? Zaaktype
    }
}
```

## Security Considerations

### 1. Encrypted Data in Cache

**Plugin Configurations**:
```kotlin
@Component
class SecureCacheSerializer : RedisSerializer<Any> {

    override fun serialize(value: Any?): ByteArray? {
        return when (value) {
            is PluginConfiguration -> {
                // Ensure sensitive properties remain encrypted in cache
                val safeConfig = value.copy().apply {
                    encryptProperties() // Re-encrypt before caching
                }
                objectMapper.writeValueAsBytes(safeConfig)
            }
            else -> defaultSerializer.serialize(value)
        }
    }
}
```

### 2. User Permission Cache Security

**Short TTL for Security-Sensitive Data**:
```kotlin
@Cacheable(
    value = ["userPermissions"],
    key = "#username + ':' + #resourceType + ':' + #action",
    condition = "#username != 'anonymous'"
)
@CacheEvict(
    value = ["userPermissions"],
    allEntries = true,
    condition = "#username == T(com.ritense.authorization.AuthorizationContext).getCurrentUserLogin()"
)
fun getUserPermissions(username: String, resourceType: String, action: String): Set<Permission> {
    // Short TTL (5 minutes) for permission data
}
```

### 3. Cache Isolation by Tenant

```kotlin
@Component
class TenantAwareCacheKeyGenerator : KeyGenerator {

    override fun generate(target: Any, method: Method, vararg params: Any?): Any {
        val tenantId = TenantContext.getCurrentTenant()
        val baseKey = DefaultKeyGenerator().generate(target, method, *params)
        return "$tenantId:$baseKey"
    }
}
```

## Monitoring and Observability

### 1. Cache Metrics
```kotlin
@Component
class CacheMetricsConfiguration {

    @Bean
    fun cacheMetricsBinderCustomizer(): MeterBinderCustomizer<CacheMeterBinder> {
        return MeterBinderCustomizer { binder ->
            binder.tag("application", "valtimo")
        }
    }
}
```

### 2. Health Indicators
```kotlin
@Component
class CacheHealthIndicator(
    private val cacheManager: CacheManager
) : HealthIndicator {

    override fun health(): Health {
        return try {
            val cacheStats = cacheManager.cacheNames.associateWith { cacheName ->
                val cache = cacheManager.getCache(cacheName)
                mapOf(
                    "size" to getCacheSize(cache),
                    "hitRate" to getCacheHitRate(cache)
                )
            }

            Health.up()
                .withDetail("caches", cacheStats)
                .build()
        } catch (e: Exception) {
            Health.down(e).build()
        }
    }
}
```

### 3. Logging Configuration
```yaml
logging:
  level:
    org.springframework.cache: DEBUG
    com.ritense.cache: DEBUG
    ROOT: INFO
```

## Performance Impact Analysis

### Expected Improvements

#### 1. Document Definition Lookups
- **Current**: Database query + authorization check per request
- **With Caching**: ~95% cache hit rate for stable definitions
- **Expected Improvement**: 50-80% reduction in response time

#### 2. Authorization Decisions
- **Current**: Complex specification building + database queries
- **With Caching**: Common permission patterns cached
- **Expected Improvement**: 60-90% reduction in authorization overhead

#### 3. External API Calls
- **Current**: Network call for every ZGW API request
- **With Caching**: Cache stable reference data (zaaktypen, statustypen)
- **Expected Improvement**: 70-95% reduction in external API calls

### Cache Size Estimates

| Cache Type | Expected Size | TTL | Eviction Policy |
|------------|--------------|-----|-----------------|
| Document Definitions | 1-10 MB | 2 hours | LRU |
| Form Definitions | 5-50 MB | 1 hour | LRU |
| Authorization Decisions | 10-100 MB | 5 minutes | TTL |
| Plugin Configurations | 1-5 MB | 30 minutes | LRU |
| ZGW Reference Data | 10-100 MB | 1 hour | LRU |

## Migration Strategy

### Phase 1: Infrastructure (Week 1-2)
1. Deploy Redis cluster
2. Configure Spring Cache with Redis
3. Add cache monitoring and health checks
4. Test cache connectivity

### Phase 2: Core Services (Week 3-4)
1. Implement document definition caching
2. Add form definition caching
3. Add basic cache invalidation events
4. Performance testing

### Phase 3: Security & Authorization (Week 5-6)
1. Implement authorization service caching
2. Add user permission caching
3. Security testing and validation
4. Performance benchmarking

### Phase 4: External APIs (Week 7-8)
1. Add ZGW API caching
2. Implement circuit breaker patterns
3. Add plugin configuration caching
4. Full integration testing

### Phase 5: Optimization (Week 9-10)
1. Fine-tune TTL values
2. Optimize cache keys and serialization
3. Add advanced monitoring
4. Performance optimization

## Conclusion

The current caching implementation in Valtimo is insufficient for multi-instance production deployments. The analysis reveals critical gaps in cache distribution, invalidation strategies, and performance optimization.

**Key Recommendations**:

1. **Immediate Action Required**: Replace `ConcurrentMapCacheManager` with Redis for distributed caching
2. **High Impact**: Implement caching for document definitions, form definitions, and authorization decisions
3. **Security Critical**: Address user permission cache invalidation across instances
4. **Performance Optimization**: Cache external API calls and plugin configurations

**Expected Benefits**:
- 50-80% reduction in database queries
- 60-90% improvement in authorization performance
- 70-95% reduction in external API calls
- Improved system scalability and reliability

**Risk Mitigation**:
- Proper cache invalidation prevents data inconsistency
- Security-aware caching maintains authorization integrity
- Circuit breaker patterns ensure graceful degradation
- Comprehensive monitoring enables proactive management

Implementation of these recommendations will significantly improve Valtimo's performance, scalability, and reliability in multi-instance production environments.