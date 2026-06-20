# Secrets and test data policy

Written for: anyone (human or agent — DeepSeek implementing a task, Codex reviewing one, a future Claude session) touching this repo. Applies to all services, not just Track A.

## There are no real secrets in this repo, by design

- `auth-server`'s only external credential, Google OAuth (`GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` in `application.yml`), defaults to the literal string `placeholder` if the env var isn't set. Nothing in the build, the test suite, or the Order/Inventory saga depends on OAuth working — email/password login is the path everything is built and tested against.
- The JWT signing key (`auth-server`'s `JwtConfig.java`) is a fresh 2048-bit RSA keypair generated in memory on every application start. It is never written to disk or committed. Don't change this to a fixed/persisted key for convenience — regenerating on each boot is the correct behavior for a demo identity provider.
- The H2 database is in-memory (`jdbc:h2:mem:authdb`) with no password, and is wiped on every restart (`ddl-auto: create-drop`). There is no persistent store anywhere in this repo to leak from.
- Seed/demo accounts (`DataSeeder.java`) use the `@example.test` domain and obviously-fake passwords (`admin123`, etc.) — `.test` is an IANA-reserved TLD that can never resolve to a real address. Keep using it for any new seed data; never seed accounts on `@example.com`, `@gmail.com`, or any real-looking domain.

**If you ever find yourself about to hardcode a real-looking credential, API key, or production-style URL anywhere in this repo — stop. There is no legitimate reason for one to exist here.**

## How to write code that agents can test without secrets

Follow the pattern already established in `kafka-demo`/`kafka-demo-2` (renamed to `order-service`/`inventory-service` per task A-1):

- A `src/test/resources/application-test.yml` activated via `@ActiveProfiles("test")`, which:
  - Excludes `SecurityAutoConfiguration` and `OAuth2ResourceServerAutoConfiguration` entirely — integration tests for Kafka producer/consumer logic don't need a real JWT or a running `auth-server`.
  - Points Kafka at a `@EmbeddedKafka` broker on an isolated port, not the `localhost:9092` broker from `docker-compose.yml`.
- Any new backend service should get the same shape of `application-test.yml`. If a service's test needs to exercise something `auth-server`-gated, mock the JWT (`@WithMockUser`/a manually-built `Jwt` test fixture), don't spin up real `auth-server` and don't disable security checks in production code to make tests easier.
- Frontend tests (when added) should mock `AuthService`/`HttpClient`, not hit a running backend.

## Dummy data conventions

- Product/order data: the fixed three-SKU catalog (`SKU-001` Widget, `SKU-002` Gadget, `SKU-003` Gizmo) defined in tasks A-4/A-5 is the canonical demo dataset. Reuse it rather than inventing parallel fake catalogs in different services — consistency here is what makes the cross-service saga demoable end to end.
- User/customer data: `@example.test` emails only, as above.
- If a future sprint needs richer fake data (e.g. more SKUs, more customers), prefer a small seeded/static fixture over a fake-data-generation library — predictable data makes the showcase's demo flow reliable and makes review (Codex) and acceptance criteria easier to verify deterministically.

## Running multiple coding agents against this repo

Since sprint 1 hands independent tasks to a workhorse agent (DeepSeek via opencode), each task should run in its own isolated workspace (e.g. a git worktree/branch per task) rather than several agents sharing one working tree — this also gives Codex a clean, isolated diff to review per task.

No task in this repo's backlog needs cloud credentials, SSH keys, or kube config — nothing here deploys anywhere. Don't mount or export any of those into an agent's shell environment; if an agent's environment has them available "by default," that's broader access than this project ever needs.
