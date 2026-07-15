# Sprint 22 Demo Notes

Sprint 22 is a good example of why "artifact-only" infrastructure work still needs an independent reviewer.

- The coordinator and implementer got most of the derivation work right: ports, jar names, service names, H2 override wiring, and the shared deploy pattern all trace back to source correctly.
- The reviewer still caught two issues before any live box was touched: CI deploy trust-on-first-use via `ssh-keyscan`, and Nginx WebSocket headers that were scoped by host but not by path.
- That is a useful story beat for Track C: the loop is not just for application bugs. It also catches deployment mistakes while they are still cheap text files instead of pager-duty incidents.
