# Sprint 28 Demo Notes

Sprint 28 is useful because it mixes a real security fix, a real authorization boundary, and a subtle frontend race.

- The backend-side work is solid: password hashes stop leaking from the admin API, and role enforcement moved to the real security boundary in the services.
- The rejected detail is a classic SPA auth-state race: the new admin page exists, but the guard checks synchronous user state before the async `/me` call finishes, so a legitimate admin can be redirected away on refresh.
- Good presentation angle: this is the kind of bug that hides inside a "working demo" unless someone tests the actual navigation lifecycle, not just the happy path after a fresh login.
