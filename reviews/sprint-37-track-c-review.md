# Sprint 37 Track C Review - OAuth Redirect Scheme Behind Reverse Proxy

Review-Target-Commit: `de147a0`  
Handoff: `docs/backlog/sprint-37-handoff.md`  
Verdict: ACCEPT

## Findings

No blocking source-level findings in Sprint 37.

## Verified Against Handoff

- **The fix is exactly the intended one-line change.** `forward-headers-strategy: framework` is now present under `server:` at [auth-server/src/main/resources/application.yml](C:/projects/pub-rec-opencode-deepseek/auth-server/src/main/resources/application.yml:1). Nothing else in the auth-server OAuth flow changed in this sprint, which matches the handoff's narrow scope.

- **The chosen setting matches Spring Boot's documented behavior for proxy header trust.** Spring Boot 4.1 defines `framework` as "use Spring's support for handling forwarded headers", which is the correct knob when the app is reconstructing absolute URLs behind a reverse proxy that already sends `X-Forwarded-*`. Source: [Spring Boot `ServerProperties.ForwardHeadersStrategy`](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/web/server/autoconfigure/ServerProperties.ForwardHeadersStrategy.html).

- **This sprint correctly fixes the observed symptom at the application layer, not by compensating in unrelated config.** The handoff states Nginx was already emitting `X-Forwarded-Proto`; this change makes the application consume it when building OAuth redirect URLs. That is the right ownership boundary for the reported `http://` vs `https://` bug.

## Residual Checks Not Reproduced Here

- I did not replay the live VPS Google authorization redirect from this review session.
- I did not inspect live `journalctl` output after redeploy from this review session.

Those are operational confirmation steps, not source-level blockers for this one-line fix.
