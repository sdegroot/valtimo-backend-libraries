# Valtimo Application Startup Time Optimization

Valtimo applications typically take 30+ seconds to start due to comprehensive auto-deployment processes, Liquibase migrations, and resource scanning. This document provides specific optimization strategies for Valtimo's startup sequence.

## Current Startup Bottlenecks

### 1. Auto-Deployment System (Primary Bottleneck)

Valtimo runs multiple auto-deployment listeners on `ApplicationReadyEvent` in sequence:

- **PluginAutoDeploymentEventListener** (`@Order(LOWEST_PRECEDENCE-1)`)
  - Scans `classpath*:**/*.pluginconfig.json`
  - Sequential JSON parsing and plugin deployment
  - Database transactions for each plugin configuration

- **CaseDefinitionDeploymentService** (`@Order(LOWEST_PRECEDENCE)`)
  - Scans `classpath*:config/case/*/*/**/*.*` (recursive, expensive)
  - Complex file grouping and path manipulation
  - Heavy database operations: `findAllByFinalTrue()`, `findAll()`
  - Calls `changelogDeployer.deployAll()` (additional expensive operation)

- **ProcessLinkDeploymentApplicationReadyEventListener** (`@Order(LOWEST_PRECEDENCE)`)
  - Property resolution with regex-based placeholder replacement
  - Recursive JSON tree traversal

- **SearchListColumnDefinitionDeploymentService** (`@Order(LOWEST_PRECEDENCE)`)
- **ObjectManagementDefinitionDeploymentService** (`@Order(LOWEST_PRECEDENCE-2)`)

### 2. Resource Scanning Operations

Multiple classpath scans with expensive glob patterns:
```
classpath*:**/*.pluginconfig.json
classpath*:config/case/*/*/**/*.*
classpath*:config/global/**/*.*
classpath*:/config/global/process-link/**/*.process-link.json
classpath*:config/search-list-column/*.json
classpath*:config/objectmanagement/*.json
```

### 3. Database Operations

- **Liquibase Migrations**: `OutboxLiquibaseRunner` and module-specific migrations
- **Deployment Validation**: Complex queries to check existing configurations
- **Sequential Database Writes**: Each deployment involves individual database transactions

## Optimization Strategies

### Quick Wins (Easy Implementation)

#### 1. Environment-Specific Auto-Deployment

Add conditional deployment based on environment:

```yaml
# application-dev.yml
valtimo:
  auto-deployment:
    enabled: true
    skip-unchanged: true  # Skip if checksum matches

# application-prod.yml
valtimo:
  auto-deployment:
    enabled: false  # Deploy manually in production
```

Implementation in each deployment service:
```kotlin
@ConditionalOnProperty(
    prefix = "valtimo.auto-deployment",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class CaseDefinitionDeploymentService {
    // existing implementation
}
```

#### 2. Parallel Auto-Deployment

Modify deployment services to run in parallel instead of sequential:

```kotlin
@EventListener(ApplicationReadyEvent::class)
@Async("deploymentTaskExecutor")
fun handleApplicationReadyEvent(event: ApplicationReadyEvent) {
    // existing deployment logic
}
```

Configure async executor:
```kotlin
@Configuration
class AsyncConfig {
    @Bean("deploymentTaskExecutor")
    fun deploymentTaskExecutor(): TaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 4
        executor.maxPoolSize = 8
        executor.queueCapacity = 0
        executor.setThreadNamePrefix("deployment-")
        executor.initialize()
        return executor
    }
}
```

#### 3. Resource Scanning Cache

Implement classpath scanning cache to avoid repeated scans:

```kotlin
@Component
@ConditionalOnProperty("valtimo.auto-deployment.cache-resources", havingValue = "true")
class ResourceScanCache {
    private val cache = ConcurrentHashMap<String, List<Resource>>()

    fun getResources(locationPattern: String): List<Resource> {
        return cache.computeIfAbsent(locationPattern) { pattern ->
            ResourcePatternUtils.getResourcePatternResolver()
                .getResources(pattern).toList()
        }
    }
}
```

#### 4. Development Profile Optimizations

```yaml
# application-dev.yml
spring:
  liquibase:
    contexts: dev  # Skip production-only migrations

valtimo:
  auto-deployment:
    skip-validation: true  # Skip expensive validation checks
    parallel: true
    cache-resources: true
```

### Medium Impact Optimizations

#### 1. Lazy Bean Initialization

Enable lazy initialization for non-critical beans:

```yaml
spring:
  main:
    lazy-initialization: true
```

Exclude critical beans:
```kotlin
@Component
@Lazy(false)  // Force eager initialization for critical components
class SecurityConfiguration
```

#### 2. Conditional Auto-Configuration

Disable unused auto-configurations:

```kotlin
@SpringBootApplication(exclude = [
    // Disable if not using specific features
    SearchAutoConfiguration::class,
    DashboardAutoConfiguration::class,
    MailAutoConfiguration::class
])
class ValtimoApplication
```

#### 3. Database Operation Batching

Optimize database operations in deployment services:

```kotlin
// Instead of individual saves
configurations.forEach { config ->
    pluginConfigurationRepository.save(config)
}

// Use batch operations
pluginConfigurationRepository.saveAll(configurations)
```

#### 4. Startup Profiling

Add timing metrics to identify bottlenecks:

```kotlin
@Component
class StartupProfiler {
    private val logger = LoggerFactory.getLogger(StartupProfiler::class.java)

    @EventListener
    fun onApplicationStarting(event: ApplicationStartingEvent) {
        logger.info("Application starting at: ${System.currentTimeMillis()}")
    }

    @EventListener
    fun onApplicationReady(event: ApplicationReadyEvent) {
        val startTime = event.springApplication.startTime
        val duration = System.currentTimeMillis() - startTime
        logger.info("Application ready in: ${duration}ms")
    }
}
```

### Advanced Optimizations (High Impact)

#### 1. Custom Deployment Ordering

Optimize the deployment sequence based on dependencies:

```kotlin
@Component
class OptimizedDeploymentOrchestrator {

    @EventListener(ApplicationReadyEvent::class)
    fun orchestrateDeployment(event: ApplicationReadyEvent) {
        val startTime = System.currentTimeMillis()

        // Phase 1: Independent deployments (parallel)
        CompletableFuture.allOf(
            runAsync { deployPlugins() },
            runAsync { deploySearchConfigurations() },
            runAsync { deployDashboards() }
        ).join()

        // Phase 2: Dependent deployments (sequential but optimized)
        deployCaseDefinitions()  // Needs plugins
        deployProcessLinks()     // Needs case definitions

        logger.info("Total deployment time: ${System.currentTimeMillis() - startTime}ms")
    }
}
```

#### 2. Background Initialization

Move non-critical initializations to background threads:

```kotlin
@Component
class BackgroundInitializer {

    @EventListener(ApplicationReadyEvent::class)
    @Async
    fun initializeNonCriticalComponents() {
        // Initialize dashboard widgets
        // Pre-warm caches
        // Load optional configurations
    }
}
```

#### 3. Smart Change Detection

Implement change detection to skip unchanged deployments:

```kotlin
@Service
class ChangeDetectionService {

    fun hasChanged(resource: Resource, lastChecksum: String?): Boolean {
        val currentChecksum = DigestUtils.md5Hex(resource.inputStream)
        return currentChecksum != lastChecksum
    }

    fun deployIfChanged(resource: Resource, deployer: () -> Unit) {
        val lastChecksum = getLastChecksum(resource.filename)
        if (hasChanged(resource, lastChecksum)) {
            deployer()
            saveChecksum(resource.filename, getCurrentChecksum(resource))
        }
    }
}
```

#### 4. Selective Module Loading

Allow disabling entire modules for faster development:

```yaml
# application-dev.yml
valtimo:
  modules:
    zgw: false          # Skip ZGW integrations in development
    dashboard: false    # Skip dashboard module
    search: false       # Skip search functionality
```

## JVM Tuning for Startup

### Recommended JVM Options

```bash
# Faster startup options
-XX:+UseG1GC
-XX:+UseStringDeduplication
-XX:+OptimizeStringConcat
-XX:+UseCompressedOops
-XX:+UseCompressedClassPointers

# Memory tuning
-Xms512m
-Xmx2g
-XX:MetaspaceSize=256m

# Development-specific
-XX:+UnlockExperimentalVMOptions
-XX:+EnableJVMCI
-XX:+UseJVMCICompiler  # If using Graal compiler
```

### Docker Development Optimization

```dockerfile
# Multi-stage build for development
FROM openjdk:21-jdk-slim as dev
COPY --from=gradle /app/build/libs/app.jar app.jar

# JVM options for development
ENV JAVA_OPTS="-XX:+UseG1GC -Xms512m -Xmx1g -XX:+TieredCompilation -XX:TieredStopAtLevel=1"

ENTRYPOINT ["java", "$JAVA_OPTS", "-jar", "/app.jar"]
```

## Implementation Priority

### Phase 1: Immediate Wins (1-2 days)
1. Add environment-specific auto-deployment flags
2. Enable resource scanning cache for development
3. Configure JVM options for faster startup
4. Add startup timing metrics

**Expected improvement: 20-30% (6-9 seconds)**

### Phase 2: Parallel Processing (1 week)
1. Implement async auto-deployment
2. Batch database operations
3. Optimize Liquibase execution
4. Add change detection for deployments

**Expected improvement: 40-50% (12-15 seconds)**

### Phase 3: Advanced Optimizations (2-3 weeks)
1. Custom deployment orchestrator
2. Background initialization for non-critical components
3. Selective module loading
4. Smart classpath scanning

**Expected improvement: 60-70% (18-21 seconds)**

## Monitoring and Measurement

### Startup Metrics

Add these metrics to track optimization progress:

```kotlin
@Component
class StartupMetrics {

    @EventListener
    fun measureDeploymentPhase(event: ApplicationReadyEvent) {
        Metrics.timer("valtimo.startup.total").record(getTotalStartupTime())
        Metrics.timer("valtimo.startup.deployment").record(getDeploymentTime())
        Metrics.counter("valtimo.startup.plugins.deployed").increment(pluginCount.toDouble())
        Metrics.counter("valtimo.startup.cases.deployed").increment(caseCount.toDouble())
    }
}
```

### Development Profile

Create a development profile optimized for fast startup:

```yaml
# application-dev-fast.yml
spring:
  main:
    lazy-initialization: true
  liquibase:
    enabled: false  # Use pre-migrated database

valtimo:
  auto-deployment:
    enabled: false     # Disable all auto-deployment
  modules:
    zgw: false         # Disable heavy modules
    dashboard: false
    search: false
```

## Troubleshooting Slow Startup

### Diagnostic Commands

```bash
# Profile startup with JFR
java -XX:+FlightRecorder -XX:StartFlightRecording=duration=60s,filename=startup.jfr -jar app.jar

# Enable detailed timing logs
java -Dlogging.level.com.ritense.valtimo=DEBUG -jar app.jar

# Memory analysis
java -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -jar app.jar
```

### Common Issues

1. **Database Connection Pool**: Ensure HikariCP is properly configured
2. **Resource Scanning**: Check for overly broad classpath patterns
3. **Large Datasets**: Review database queries in deployment services
4. **Docker Performance**: Ensure adequate memory allocation

## Configuration Summary

### Recommended Development Configuration

```yaml
# application-dev-optimized.yml
spring:
  main:
    lazy-initialization: true
  liquibase:
    contexts: dev

valtimo:
  auto-deployment:
    enabled: true
    parallel: true
    cache-resources: true
    skip-unchanged: true

  startup:
    profiling: true
    background-init: true
```

This configuration should reduce startup time from 30+ seconds to approximately 10-15 seconds in development environments while maintaining full functionality in production.