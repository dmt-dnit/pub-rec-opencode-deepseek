# Sprint 29 Track C Review - Auth H2 Persistence

Review-Target-Commit: `cabc84c`  
Handoff: `docs/backlog/sprint-29-handoff.md`  
Verdict: ACCEPT

## Findings

No blocking source-level findings in Sprint 29.

## Verified Against Handoff

- **The two required persistence conditions are both addressed.** `auth-server` no longer destroys its schema on shutdown because `ddl-auto` is now `update` at [auth-server/src/main/resources/application.yml](C:/projects/pub-rec-opencode-deepseek/auth-server/src/main/resources/application.yml:18), while the production env example now documents the file-backed override at [deploy/systemd/env-examples/auth.env.example](C:/projects/pub-rec-opencode-deepseek/deploy/systemd/env-examples/auth.env.example:6). Fixing only one of those would have been insufficient; this commit covers both.

- **Local dev / CI behavior stays intentionally unchanged.** The code-side datasource URL remains `jdbc:h2:mem:authdb` at [auth-server/src/main/resources/application.yml](C:/projects/pub-rec-opencode-deepseek/auth-server/src/main/resources/application.yml:9), so the VPS-specific file path is not baked into normal developer or CI runs. The production switch is correctly expressed as an environment override instead of a hardcoded path.

- **The existing seeding behavior is compatible with persisted storage.** `DataSeeder` still exits early once any users exist at [auth-server/src/main/java/be/dnit/authserver/DataSeeder.java](C:/projects/pub-rec-opencode-deepseek/auth-server/src/main/java/be/dnit/authserver/DataSeeder.java:22), so persisted accounts are not silently overwritten on restart.

- **The deploy script is not the source of the wipe.** The reviewed deploy script only stops the service, swaps the jar, and restarts it at [deploy/scripts/deploy-backend.sh](C:/projects/pub-rec-opencode-deepseek/deploy/scripts/deploy-backend.sh:13), [deploy/scripts/deploy-backend.sh](C:/projects/pub-rec-opencode-deepseek/deploy/scripts/deploy-backend.sh:14), [deploy/scripts/deploy-backend.sh](C:/projects/pub-rec-opencode-deepseek/deploy/scripts/deploy-backend.sh:15), and [deploy/scripts/deploy-backend.sh](C:/projects/pub-rec-opencode-deepseek/deploy/scripts/deploy-backend.sh:16). That supports the handoff’s root-cause analysis.

## Independent Verification

- I re-ran a Java 21 Maven check in `auth-server` from integrated `main`; it completed successfully. The first attempt correctly failed under Java 25 because the project’s Enforcer rule restricts the build to Java 21, which is expected behavior rather than a project defect.

## Residual Checks Not Reproduced Here

- I did not reproduce the coordinator’s two-process file-backed restart test from this review session.
- I did not perform the one-time live VPS env update or the post-change redeploy.

Those are useful operational follow-ups, but they are not blockers to accepting the committed fix.
