# Health, readiness and metrics

Implementation: `com.zuunr.dcentb.actuator` (`DcentbActuatorAutoConfiguration`,
`MongoReadinessHealthIndicator`, `AsyncProcessingHealthIndicator`, `AsyncProcessingMetricsBinder`,
`AsyncMetricsSampler`, `DcentbMongoEnvironmentPostProcessor`). Tests: unit tests per class, plus
`DcentbActuatorIT` (a real embedded server over real HTTP, against a real MongoDB — see
`README.md`, "Start MongoDB") and `DcentbActuatorAutoConfigurationTest` (the enable/disable switch,
fast, no server).

Built on Spring Boot Actuator, a required dependency of dcentb (so health/readiness/metrics work
the moment you add dcentb — no extra dependency to remember), gated by one property if you want it
off entirely: `dcentb.actuator.enabled=false`.

## What's exposed, and why

- **Liveness** (`/actuator/health/liveness`) — Boot's own trivial liveness probe. dcentb adds
  nothing here. See "Why leadership and Mongo blips never gate liveness" below for why that's
  deliberate, not an oversight.
- **Readiness** (`/actuator/health/readiness`) — Boot's own `readinessState` plus dcentb's `mongo`
  indicator (`MongoReadinessHealthIndicator`): can this instance actually reach MongoDB right now.
  Registered only when `dcentb.mongodb.db` is configured as a property (true for every real
  deployment and both demo apps in this repo).
- **`asyncProcessing`** (`/actuator/health/asyncProcessing`, and inside the plain aggregate
  `/actuator/health` once `show-details` allows it) — informational leader/running state. Never in
  the readiness or liveness group. See "Why readiness is Mongo-reachability only" below.
- **Metrics** (`/actuator/metrics/dcentb.async.*`, once `management.endpoints.web.exposure.include`
  includes `metrics`) — `dcentb.async.leader`, `dcentb.async.changestream.lag.seconds`,
  `dcentb.async.errorqueue.depth`, `dcentb.async.errorqueue.deadletter.count`. Always registered
  (when a `MeterRegistry` is present); they simply stay at `0` when async processing is disabled.

## Why leadership and Mongo blips never gate liveness

Killing and restarting a pod fixes nothing about a database that's temporarily unreachable — it
just adds a restart-loop risk on top of an outage that was already recovering on its own. And
losing leadership is the **normal steady state** for every instance except one in a multi-instance
deployment — tying liveness to "is this instance the leader" would mean Kubernetes kills and
restarts every non-leader instance, forever. Liveness here means exactly one thing: is the JVM
process itself alive, nothing about Mongo or leadership.

## Why readiness is Mongo-reachability only, not leader state

A non-leader instance serves ordinary CRUD HTTP traffic exactly as well as the leader — only the
elected leader runs the change-stream listener (`docs/leader-election.md`). Gating readiness on
leadership would mean only one instance in your whole fleet is ever "ready," which is wrong: it
would stop Kubernetes from routing traffic to every other, perfectly healthy instance.

## Change-stream lag and error-queue depth are sampled, not computed inline

`AsyncProcessingHealthIndicator` deliberately never calls `CheckpointStore.load()` or
`ErrorQueueStore.count()` — both are real MongoDB round-trips, and doing them synchronously on
every `/actuator/health` hit would make health-check latency depend on Mongo, which is exactly the
coupling readiness-vs-liveness separation above is trying to avoid. Instead, `AsyncMetricsSampler`
samples them on a timer (`dcentb.actuator.async-metrics.sample-interval`, default `PT15S`) into
`AsyncProcessingMetricsBinder`'s Micrometer gauges.

The sampler also deliberately does **not** use Spring's `@Scheduled`/`@EnableScheduling` — it runs
its own daemon thread, started via `ApplicationReadyEvent` and stopped via `@PreDestroy`, the exact
same lifecycle shape `ChangeStreamListener`/`ErrorQueueProcessor` already use. Enabling Actuator
metrics should never silently turn on Spring's scheduling infrastructure in a consumer's own
application context.

Checkpoint and error-queue state are both shared, global Mongo collections (one per `streamId`,
not per-instance) — every instance samples the same values regardless of which one currently holds
leadership, so only the `dcentb.async.leader` gauge itself differs per instance.

## Security defaults

dcentb ships exactly three Actuator defaults, via `dcentb-actuator-defaults.properties`
(low priority — a consumer's own `application.properties` always wins):

```properties
management.endpoint.health.probes.enabled=true
management.endpoint.health.group.readiness.include=readinessState,mongo
management.endpoint.health.group.liveness.include=livenessState
```

**Deliberately absent, left at Spring Boot's own safe defaults, never overridden by dcentb:**

- `management.endpoints.web.exposure.include` — stays at Boot's default (`health` only). Nothing
  else (`/env`, `/metrics`, `/beans`, ...) is reachable until you explicitly opt in.
- `management.endpoint.health.show-details` — stays at Boot's default (`never`). An unauthenticated
  caller sees only `{"status":"UP"}`, never the detail breakdown, until you explicitly opt in.

**The Mongo connection string, and why `/env` needs a word of caution.** `dcentb.mongodb.connection`
is a cleartext connection string (containing a username/password), read via plain `@Value` rather
than `@ConfigurationProperties`. Spring Boot Actuator's `/env` endpoint sanitizes a property's
*value* only when its *key* matches a default regex list (`password|secret|key|token|.*credentials.*`)
— `"dcentb.mongodb.connection"` matches none of those. `/env` isn't exposed by default (see above),
so this is dormant, not a live hole — but `DcentbMongoEnvironmentPostProcessor` defends it anyway:
it appends `dcentb.mongodb.connection` to `management.endpoint.env.additional-keys-to-sanitize`
automatically (merging with, never overwriting, whatever you've already configured there), so the
value stays redacted even if you later turn `/env` on without knowing about this specific property.
See that class's own Javadoc for exactly how the merge works and its one known limitation (a
property source added with even higher priority *after* this runs, most commonly a `--management...`
command-line argument, would still win outright).

**Why the generic health indicators never leak the connection string either.**
`MongoReadinessHealthIndicator` never puts anything connection-string-derived into a `Health`
`details` map — the UP path has no details at all, and the DOWN path uses a fixed string
(`"mongo unreachable"`), never the raw exception message (which is still logged, in full, server-side,
at WARN). This is a stronger guarantee than relying on `show-details` staying at `never` — it holds
regardless of how a consumer configures that property.

**Boot's own generic Mongo health indicator is intentionally pre-empted.** `com.zuunr:mongodb`
depends on `spring-boot-starter-data-mongodb`, which brings Spring Boot's own auto-configured Mongo
health indicator along for the ride — one that uses *Spring Data MongoDB's* own connection
properties (which dcentb never sets; dcentb manages its own `MongoClient` independently via
`dcentb.mongodb.*`). Left alone, that indicator connects anonymously to `localhost:27017` and calls
`listDatabases`, which fails `Unauthorized` against a real, credentialed deployment and drags the
whole aggregate `/actuator/health` down to `503` — for a reason that has nothing to do with whether
dcentb's actual MongoDB is reachable. `DcentbActuatorAutoConfiguration` registers its own indicator
under the exact bean name Boot's own `MongoHealthContributorAutoConfiguration` checks for
(`@ConditionalOnMissingBean(name = "mongoHealthIndicator")`), so Boot skips its broken one in favor
of dcentb's correct one. If you ever see this behavior change after a Spring Boot upgrade, this is
the mechanism to re-verify first.

## Recommended production topology: a separate management port

Nothing above requires authentication to view — there is no Spring Security anywhere in dcentb
(see the codebase-wide absence of `spring-boot-starter-security` usage). The standard, recommended
mitigation is **not** application-level auth on these endpoints, but keeping them off the
internet-facing port entirely:

```properties
management.server.port=8081
```

Firewall/route that port so only your own infrastructure (load balancer health checks, a metrics
scraper, an internal dashboard) can reach it. This is a deployment decision, not something dcentb's
code enforces — it's not set as a shipped default because picking a port, and deciding who can
reach it, is inherently your infrastructure's call, not a library default.

## Pre-existing, separate issue: `/swagger` has no access control

`SwaggerController`'s `/swagger` and `/swagger/openapi.json` endpoints are fully unauthenticated
today, for reasons unrelated to and unfixed by this work — they sit outside the `Controller.execute()`
auth pipeline entirely, the same situation a hand-rolled `@RestController` is always in (see that
class's own code: neither endpoint calls into dcentb's request pipeline at all). This document does
not address that. Worth knowing about if you're reasoning about this codebase's overall exposure,
but out of scope here.
