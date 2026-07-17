# Sprint 29 Demo Notes

Sprint 29 is a good example of a bug that looked like a deploy problem but was really configuration semantics.

- The important insight was that persistence required two separate fixes: move H2 out of in-memory mode and stop Hibernate from dropping the schema on shutdown.
- Good presentation angle: this is where reading the actual config mattered more than adding code. The deploy script was suspected by symptoms, but the real fault was in `application.yml`.
- It also shows a useful pattern for small production-hardening changes: keep dev/CI behavior unchanged in code, and express the VPS-only difference as an environment override.
