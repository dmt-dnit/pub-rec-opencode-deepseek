# Sprint 24 Demo Notes

Sprint 24 is a strong example of a bug that was real in production, invisible in existing tests, and still fixed with a small, disciplined patch.

- The failure was not in the business logic inside `processPending()`. It was in how Spring proxy-based transactions are entered: the scheduled path called the method through `this`, so `@Transactional` never activated there.
- The important testing lesson is better than the fix itself: the existing suite had good coverage of the relay body, but not of the scheduled entrypoint. The new regression test closes exactly that gap by calling `scheduledRelay()`.
- Good presentation angle: this is the kind of issue teams often dismiss as "works in tests, fails in prod". Here the loop turned it into a precise explanation, a narrow patch, and a targeted regression guard.
