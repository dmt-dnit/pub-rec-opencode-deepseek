# Sprint 23 Demo Notes

Sprint 23 is a good example of the review loop catching a missing infrastructure assumption, not a bad implementation.

- The real issue was planning drift: the services were live on the VPS, but the always-on Kafka decision had never been turned into actual deploy artifacts.
- The artifact itself is intentionally conservative: same Kafka/ZooKeeper config as local dev, with the one security-critical change being loopback-only port binding for an unauthenticated broker.
- Good story angle: this is the kind of gap a live-apply session exposes even when earlier artifact reviews were correct within their scope. The process then feeds that discovery back into a narrow follow-up sprint instead of improvising on the server.
