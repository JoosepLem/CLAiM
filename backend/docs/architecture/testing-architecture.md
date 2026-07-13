# Testing Architecture

## Overview

The test suite uses three tiers — unit, integration, and end-to-end — with a per-class schema isolation strategy that enables parallel execution. Tests share a single ephemeral PostgreSQL container (Testcontainers) but never share data within it.

## Test Levels

### Unit Tests

`src/test/kotlin/ee/claimai/unit/` and `src/test/kotlin/ee/claimai/security/`

- Pure JUnit 5 with **MockK** for mocking and **AssertJ** for assertions
- No Spring context, no database, no HTTP server
- Use `MockMvcBuilders.standaloneSetup()` for controller tests
- Example: `JwtServiceTest`, `HealthControllerTest`, `LoginRedirectTest`, `JwtAuthenticationFilterTest`

```kotlin
class LoginRedirectTest {
    private val userRepository: UserRepository = mockk()
    private val jwtService: JwtService = mockk()
    private val mockMvc = MockMvcBuilders
        .standaloneSetup(AuthController(userRepository, jwtService, ...))
        .setViewResolvers(viewResolver)
        .build()

    @Test
    fun `GET dashboard without cookie redirects to login`() {
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/login"))
    }
}
```

### Integration Tests

`src/test/kotlin/ee/claimai/integration/`

- Full `@SpringBootTest` with a real PostgreSQL (Testcontainers) and `RANDOM_PORT` Tomcat
- HTTP requests via `RestTemplate` against the running server
- Each test class owns its own tenant schema — zero cross-class data sharing
- Use `JdbcTemplate` for direct SQL (setup, assertions, cleanup)

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminAuthTest : PostgresTestBase() {
    @LocalServerPort private var port: Int = 0

    @Test
    fun `admin can access admin dashboard`() {
        val jwt = loginAs(adminUser)
        val response = restTemplate.exchange(
            "http://localhost:$port/admin", HttpMethod.GET,
            HttpEntity<Any>(HttpHeaders().apply { add("Cookie", "jwt=$jwt") }),
            String::class.java
        )
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("Admin Dashboard")
    }
}
```

### End-to-End Test

`src/test/kotlin/ee/claimai/e2e/`

- Single test class (`HealthEndpointTest`) verifying that the Actuator health endpoint returns `UP` with a real PostgreSQL

## Per-Class Schema Isolation

The central design principle: each integration test class operates in a private tenant schema, eliminating shared state and enabling parallel execution.

### How It Works

```
Test class AdminAuthTest
        │
        │  @DynamicPropertySource
        │  test.tenant.suffix = "tA1B2C3D4"
        ▼
  Unique Spring ApplicationContext      # Different context per class
        │
        ▼
  MigrationRunner (@Order 1)            # Runs first — migrateShared()
        │  Creates public.tenants, public.users, role column
        ▼
  TestTenantBootstrapper (@Order 2)     # Runs second — creates private tenant
        │  INSERT INTO tenants ... ON CONFLICT DO NOTHING
        │  CREATE ROLE app_user (if not exists)
        │  migrateTenant("tA1B2C3D4")   # Creates tA1B2C3D4.treatment_invoices etc.
        ▼
  @BeforeEach (AtomicBoolean guard)     # One-time per class — cheap INSERTs
        │  INSERT INTO users (admin, employee)
        ▼
  Test methods run against "tA1B2C3D4" schema
```

### Key Components

**`PostgresTestBase`** (`src/test/kotlin/ee/claimai/support/PostgresTestBase.kt`)

Abstract base class for all DB-dependent tests. Provides:
- A single shared `PostgreSQLContainer` started once per JVM (companion object)
- `@DynamicPropertySource` that overrides datasource configuration to point at the container
- `ensureTenantSchema(tenantId, name)` helper for tests that need additional tenants
- PG tuned for test speed: `fsync=off`, `synchronous_commit=off`, `full_page_writes=off`, tmpfs data directory

**`TestTenantBootstrapper`** (`src/main/kotlin/ee/claimai/config/TestTenantBootstrapper.kt`)

An `ApplicationRunner` that fires during Spring context startup for any test class that sets `test.tenant.suffix`:
1. Creates the `app_user` role (used by `AppUserPrivilegesTest`)
2. Inserts a tenant row into `public.tenants` (`ON CONFLICT DO NOTHING`)
3. Runs Flyway tenant migrations on the new schema

**`@DynamicPropertySource` in each test class**

Each integration test class declares a companion object with a unique UUID-based suffix:

```kotlin
companion object {
    private val suffix = "t" + UUID.randomUUID().toString().replace("-", "").take(11)

    @JvmStatic @DynamicPropertySource
    fun registerTenantSuffix(registry: DynamicPropertyRegistry) {
        registry.add("test.tenant.suffix") { suffix }
    }
}
```

This has two effects:
1. The `TestTenantBootstrapper` reads it and creates a private tenant schema
2. Spring treats two test classes with different property values as different context cache keys — so each class gets its own `ApplicationContext`

**`@BeforeEach` with `AtomicBoolean` guard**

Flyway migrations are expensive but idempotent. User INSERTs are cheap. The pattern:

```kotlin
companion object {
    private val setupDone = AtomicBoolean(false)
}

@BeforeEach
fun setUp() {
    if (setupDone.compareAndSet(false, true)) {
        jdbcTemplate.update("INSERT INTO users ... ON CONFLICT DO NOTHING", ...)
    }
}
```

The `AtomicBoolean` ensures the setup runs only once per test class (first `@BeforeEach`), but `ON CONFLICT DO NOTHING` makes it safe even if called concurrently across forks.

### Adding a New Integration Test

1. Extend `PostgresTestBase`
2. Add `@SpringBootTest(webEnvironment = RANDOM_PORT)` (or `NONE` if no HTTP needed)
3. Add the companion object with `@DynamicPropertySource` + UUID suffix
4. Derive class-local tenant and user names from the suffix
5. Add `@BeforeEach` with AtomicBoolean guard for user setup
6. Write tests referencing those local names

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MyFeatureTest : PostgresTestBase() {

    @LocalServerPort private var port: Int = 0
    private val restTemplate = RestTemplate()

    companion object {
        private val suffix = "t" + UUID.randomUUID().toString().replace("-", "").take(11)
        private val setupDone = AtomicBoolean(false)

        @JvmStatic @DynamicPropertySource
        fun registerTenantSuffix(registry: DynamicPropertyRegistry) {
            registry.add("test.tenant.suffix") { suffix }
        }
    }

    private val adminUser = "adm_$suffix"
    private val employeeUser = "emp_$suffix"

    @BeforeEach
    fun setUp() {
        if (setupDone.compareAndSet(false, true)) {
            jdbcTemplate.update("INSERT INTO users ... ON CONFLICT DO NOTHING", adminUser, null, "ADMIN")
            jdbcTemplate.update("INSERT INTO users ... ON CONFLICT DO NOTHING", employeeUser, suffix, "CLINIC_EMPLOYEE")
        }
    }

    @Test
    fun `my test`() {
        // test logic against adminUser / employeeUser / suffix
    }
}
```

### Tests That Need Multiple Tenants

Use the `ensureTenantSchema()` helper from PostgresTestBase to create additional tenants:

```kotlin
@BeforeEach
fun setUp() {
    if (setupDone.compareAndSet(false, true)) {
        createTestTenant(tenantA, ...)                                 // primary (from bootstrapper)
        jdbcTemplate.update("INSERT INTO users ...", userA, suffix, ...)
        ensureTenantSchema(tenantBId, "Tenant B")                      // additional tenant
        jdbcTemplate.update("INSERT INTO users ...", userB, tenantBId, ...)
    }
}
```

`ensureTenantSchema` is a lightweight wrapper that INSERTs the tenant row and runs the tenant Flyway migration — same logic as the bootstrapper but for secondary tenants.

## Parallelization Model

### Intra-JVM

JUnit 5 parallel test execution is **disabled** (`junit.jupiter.execution.parallel.enabled=false`). Tests within a single JVM run sequentially. This is deliberate — HikariCP connection pools are shared across contexts and sequential execution avoids pool exhaustion.

### Inter-JVM (Gradle Forks)

`maxParallelForks` controls how many JVM processes Gradle forks. Each fork has:
- Its own PostgreSQL container (Testcontainers starts one per JVM)
- Its own Spring ApplicationContext instances
- Its own tenant schemas (UUID-based, guaranteed unique)

At 55 tests the fixed cost per fork (container startup ~2s + JVM warmup ~3s) outweighs the benefit. At scale (500+ tests), raising `maxParallelForks` to `availableProcessors()` will yield near-linear speedup since each fork is fully isolated.

### Idempotence Guarantees

All shared-state operations use patterns that are safe to execute concurrently without locks:

| Operation | Safe Mechanism |
|-----------|---------------|
| Tenant INSERT | `ON CONFLICT (tenant_id) DO NOTHING` |
| User INSERT | `ON CONFLICT (username) DO NOTHING` |
| Flyway `migrateShared()` | Flyway's internal `flyway_schema_history` table locking |
| Flyway `migrateTenant()` | Same — per-schema history table, concurrent-safe |
| `CREATE ROLE app_user` | `DROP ROLE IF EXISTS` + `CREATE ROLE` + try/catch |
| `@BeforeEach` guard | `AtomicBoolean.compareAndSet(false, true)` |

### No Cleanup Required

The PostgreSQL container is ephemeral — created at test start and destroyed by Testcontainers after the JVM exits. Tenant data never persists between test runs, so `@AfterEach`/`@AfterAll` cleanup is unnecessary. Tests that create inline tenants (like `AdminTenantTest`) clean up within the test method itself using `DROP SCHEMA IF EXISTS ... CASCADE`.

## Framework Configuration

| Concern | Tool |
|---------|------|
| Test engine | JUnit 5 Jupiter |
| Spring integration | `spring-boot-starter-test`, `@SpringBootTest` |
| Mocking | MockK 1.13 (`io.mockk:mockk`) |
| Assertions | AssertJ (`org.assertj:assertj-core`) |
| Database | Testcontainers PostgreSQL 16-alpine |
| Build parallelism | Gradle `maxParallelForks` (`build.gradle.kts:46`) |
| Static analysis | Ruff (Python back-end), not applied to Kotlin POC |

### PostgreSQL Container Tuning

The container is configured for test speed rather than durability:

```
fsync = off              # No disk flush on commit
synchronous_commit = off # No wait for WAL write
full_page_writes = off   # Skip full-page images in WAL
max_connections = 50     # Enough for all pooled contexts
tmpfs = /var/lib/postgresql/data  # Data in memory, not disk
```

HikariCP pool sizes are capped at 2 per datasource (runtime + migration) to avoid exhausting PostgreSQL connections across multiple cached Spring contexts.
