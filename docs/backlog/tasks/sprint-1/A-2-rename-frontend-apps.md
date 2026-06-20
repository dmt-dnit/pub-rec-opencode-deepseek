# Task A-2: Rename the frontend Angular apps

## Context
Same showcase repo, same domain pivot as A-1 (article-mirror demo → Order/Inventory saga). Two Angular 18 apps each pair 1:1 with one backend service. This task is a pure rename — **no component logic changes**. The actual UI rework (forms, live feeds) happens in A-6 and A-7.

## Current state
- `kafka-ui1/` — npm package name `kafka-ui`, Angular project name `kafka-ui` (in `angular.json`), serves on port 4200, proxies to the service on port 8080. Will pair with the **Order Service** → becomes **Order UI**.
- `kafka-ui2/` — npm package name `kafka-ui-2`, Angular project name `kafka-ui-2`, serves on port 4201, proxies to the service on port 8081. Will pair with the **Inventory Service** → becomes **Inventory UI**.

## Task

### 1. `kafka-ui1` → `order-ui`
- Rename directory `kafka-ui1/` to `order-ui/`.
- `package.json`: `"name"` → `"order-ui"`.
- `angular.json`: rename the project key from `"kafka-ui"` to `"order-ui"` (keep `architect.serve.options.port: 4200` and `proxyConfig: proxy.conf.json` unchanged).
- `src/index.html`: update `<title>` to something like "Order UI".
- Leave `proxy.conf.json` untouched in this task (still points at port 8080 — still correct, since Order Service will run there).

### 2. `kafka-ui2` → `inventory-ui`
- Rename directory `kafka-ui2/` to `inventory-ui/`.
- `package.json`: `"name"` → `"inventory-ui"`.
- `angular.json`: rename the project key from `"kafka-ui-2"` to `"inventory-ui"` (keep port `4201` and `proxyConfig` unchanged).
- `src/index.html`: update `<title>` to something like "Inventory UI".
- Leave `proxy.conf.json` untouched (still points at port 8081 — correct for Inventory Service).

## Out of scope
- Do not change any component, service, model, route, guard, or interceptor file in this task — only directory names, `package.json`, `angular.json` project name, and `index.html` title.
- Do not run `npm install` changes to dependencies.

## Acceptance criteria
- `cd order-ui && npm install && npm start` serves successfully on `http://localhost:4200` with no build errors.
- `cd inventory-ui && npm install && npm start` serves successfully on `http://localhost:4201` with no build errors.
- `grep -r "kafka-ui-2\|kafka-ui\b" order-ui inventory-ui` returns no matches in `package.json`/`angular.json` (component code still says "kafka" in places — that's fine, A-6/A-7 will rewrite it).
