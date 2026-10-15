# E-commerce Order Agent Platform

An observable AI agent for e-commerce order scenarios. It uses Spring AI Alibaba StateGraph to orchestrate planning, memory, RAG, order tools, and human-in-the-loop confirmation. Every execution trace is written asynchronously to Elasticsearch for quality evaluation via AgentInsight.

> This is not a general-purpose chatbot. It is an explainable, auditable, human-interruptible order agent execution pipeline.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Demo Accounts & What to Ask](#demo-accounts--what-to-ask)
3. [Demo Orders](#demo-orders)
4. [Architecture](#architecture)
5. [Core Modules](#core-modules)
6. [Refund Eligibility Model](#refund-eligibility-model)
7. [Build & Test](#build--test)
8. [Security Scope](#security-scope)
9. [Trace Contract](#trace-contract)
10. [Known Limitations](#known-limitations)

---

## Quick Start

### Prerequisites

- JDK 17+
- Docker Desktop with Docker Compose
- Mistral API key (free tier works — set in `.env`)

### 1. Configure environment variables

```bash
cp .env.example .env
```

Open `.env` and set your `MISTRAL_API_KEY`. The default demo password is `DemoLogin@2026!`. For the admin dashboard, set `ADMIN_INITIAL_PASSWORD` (12+ characters). Service tokens already have loopback-only defaults; replace them for any non-local deployment. `.env` is git-ignored.

### 2. Start infrastructure

```bash
docker compose up -d
```

This starts MySQL, Redis, Milvus (with etcd/MinIO), RocketMQ, and Elasticsearch. MySQL auto-creates two schemas and seeds 3 demo orders on first run. Host ports use non-standard values (`13306`, `16379`, `29530`, `19876`, `19200`) to avoid collisions with common local dev services.

### 3. Start the four application services

Start them in this order, waiting for each to be ready before starting the next:

```bash
# 1 — Order service (business logic, refund eligibility, after-sales tickets)
java -jar mall-order/target/mall-order-0.0.1-SNAPSHOT.jar

# 2 — MCP server (exposes order operations as MCP Tools)
java -jar mall-order-cmp-server/target/mall-order-cmp-server-0.0.1.jar

# 3 — Observability service (consumes traces from RocketMQ → Elasticsearch)
java -jar mall-order-observability/target/mall-order-observability-0.0.1-SNAPSHOT-exec.jar

# 4 — Agent service (web UI, planner, LLM, RAG, memory)
java -jar mall-order-agent/target/mall-order-agent-0.0.1-SNAPSHOT.jar
```

Required environment variables for each process are in `.env`. On Windows, pass them as `-D` JVM flags or set them as environment variables before launching. See the `.run/` folder for pre-configured IntelliJ run configurations.

Once all four are running, open **http://127.0.0.1:8087**.

### 4. Import the demo knowledge base (RAG)

Log in as `admin`, navigate to the admin dashboard, and trigger the PDF import. This chunks the 8 demo PDFs in `mall-order-milvus-rag/src/main/resources/data`, vectorises them with Mistral embeddings, and writes them to Milvus. The import is idempotent — you can re-run it safely.

Alternatively, call the API directly after logging in as admin:

```
POST http://127.0.0.1:8087/vector/milvus/documents/import-local
```

### 5. Reset the demo (optional)

The admin dashboard has a **Reset Demo** button. It restores the 3 seed orders to their original state, deletes after-sales tickets, and clears all agent memory (Redis short-term, Milvus long-term, user profiles, and pending confirmations) for all 8 demo identities. Personas, knowledge scopes, and the imported knowledge base are not affected.

---

## Demo Accounts & What to Ask

All demo users share the same password: **`DemoLogin@2026!`**

The admin account uses the password you set in `ADMIN_INITIAL_PASSWORD` (e.g. `Jyy@12345678`).

> **How identity works:** The username you log in with determines your role, knowledge scope, and which orders you can see. Users cannot change their own identity or expand their own permissions.

---

### HR — Lin Yue (`hr.linyue`)
**Actor ID:** HR001 | **Role:** HRBP, Human Resources  
**Knowledge scope:** Public + HR documents  
**Order access:** None

Good questions to ask:
- How is annual leave calculated?
- What employee benefits does the company offer?
- What are the cross-department collaboration guidelines?
- What are the rules around sick leave?

---

### HR — Chen Chen (`hr.chenchen`)
**Actor ID:** HR002 | **Role:** Recruiter, Human Resources  
**Knowledge scope:** Public + HR documents  
**Order access:** None

Good questions to ask:
- What does a new employee need to prepare for onboarding?
- What are the probation period management requirements?
- What is the recruitment interview process?
- How is headcount approval handled?

---

### Engineering — Zhou Hang (`dev.zhouhang`)
**Actor ID:** DEV001 | **Role:** Backend Engineer  
**Knowledge scope:** Public + Developer documents  
**Order access:** None

Good questions to ask:
- What standards apply to order API development?
- What are the code review requirements?
- How should API idempotency be handled?
- What are the logging standards for services?

---

### Engineering — Zhao Ning (`dev.zhaoning`)
**Actor ID:** DEV002 | **Role:** Platform Engineer  
**Knowledge scope:** Public + Developer + Admin documents  
**Order access:** None

This is the most technically privileged non-admin identity — it can see platform ops and Agent internals documentation.

Good questions to ask:
- How do I troubleshoot Agent service errors?
- What should I do if knowledge base import fails?
- What should be checked before a service goes live?
- How does the RAG retrieval pipeline work?

---

### Sales — Wang Lei (`sales.wanglei`)
**Actor ID:** SALES001 | **Role:** East China Sales  
**Knowledge scope:** Public + Sales documents  
**Order access:** Can view orders for **Zhang Wei (USER1001)** only — phone and address are masked

Good questions to ask:
- View orders for my assigned customers
- What should I watch for when following up on orders?
- How do I coordinate a customer refund request?
- What is the current status of ORD20260810001?

> Note: Sales users can *view* orders but cannot cancel or create after-sales tickets.

---

### Sales — Liu Ting (`sales.liuting`)
**Actor ID:** SALES002 | **Role:** Key Account Sales  
**Knowledge scope:** Public + Sales documents  
**Order access:** Can view orders for **Li Na (USER1002)** only — phone and address are masked

Good questions to ask:
- View orders for my assigned customers
- How do I follow up on key account orders?
- What are the boundaries for sales pricing?
- What is the current status of ORD20260810003?

---

### Customer — Zhang Wei (`customer.zhangwei`)
**Actor ID:** USER1001 | **Role:** Individual Customer  
**Knowledge scope:** Public + Customer service documents  
**Order access:** Both own orders (ORD20260810001, ORD20260810002) — full detail, can cancel or request after-sales

This is the richest demo identity for showing the full agent loop.

Good questions to ask:
- Show me my orders
- Can I get a refund for ORD20260810001? *(triggers Human-in-the-Loop confirmation)*
- When will ORD20260810002 be shipped?
- I want to cancel order ORD20260810001 *(triggers Human-in-the-Loop)*
- What are the refund rules for electronics?

**After the agent asks for confirmation:**
- Type `confirm` to execute the operation (writes to DB via MCP)
- Type `cancel` to abort

---

### Customer — Li Na (`customer.lina`)
**Actor ID:** USER1002 | **Role:** Individual Customer  
**Knowledge scope:** Public + Customer service documents  
**Order access:** One completed order (ORD20260810003) — can view and request after-sales

Good questions to ask:
- Show me my orders
- Can I still request after-sales for a completed order?
- Explain the after-sales processing rules
- What is the return window for my order?

---

## Demo Orders

| Order ID | Customer | Product | Amount | Status |
|---|---|---|---|---|
| `ORD20260810001` | Zhang Wei (USER1001) | Demo Phone Pro — 16GB+512GB | ¥5,999 | **Paid** (not yet shipped) |
| `ORD20260810002` | Zhang Wei (USER1001) | Demo Tablet — 256GB | ¥3,299 | **Shipped** (in transit) |
| `ORD20260810003` | Li Na (USER1002) | Demo Laptop — 32GB+1TB | ¥8,999 | **Completed** (delivered) |

**Order status codes:**
- `0` — Pending Payment
- `1` — Paid (not shipped)
- `2` — Shipped (in transit)
- `3` — Completed (delivered)
- `4` — Cancelled

---

## Architecture

```
Authenticated Web UI  →  mall-order-agent :8087
                              │
                         StateGraph
                    ┌─────────┼──────────┐
                   RAG      Memory     Planner
                    │                    │
                 Milvus            Order Tools
                                   │        │
                             mall-order   mall-order-cmp-server
                               :8081           :8082
                                 │
                              MySQL
                         (orders, after_sales)

mall-order-agent → RocketMQ → mall-order-observability :8089 → Elasticsearch
```

The agent receives a user message, runs it through a deterministic Planner, then executes one of these strategies:

| Strategy | What happens |
|---|---|
| `ORDER_QUERY` | Directly calls the order service — no LLM needed |
| `RAG_QA` | Retrieves relevant PDF chunks from Milvus, passes to LLM |
| `DANGEROUS_ORDER_OP` | **Pauses the graph**, asks user to confirm, then executes via MCP |
| `MEMORY` | Reads/writes user profile and conversation memory |
| `GENERAL` | LLM answers directly |

---

## Core Modules

| Module | Responsibility | Port |
|---|---|---|
| `mall-order` | Order query, ownership check, refund eligibility, cancel, after-sales tickets | 8081 |
| `mall-order-cmp-server` | Wraps sensitive order operations as MCP Tools | 8082 |
| `mall-order-observability` | Publishes, consumes, and indexes execution traces to Elasticsearch | 8089 |
| `mall-order-milvus-rag` | PDF import, Milvus retrieval, rerank, RAG context | embedded in agent |
| `mall-order-memory` | Redis short-term memory, Milvus long-term memory, user profile | embedded in agent |
| `mall-order-agent` | Planner, StateGraph, LLM, Tools, Human-in-the-Loop, Web UI | 8087 |

---

## Refund Eligibility Model

`POST /orders/{orderId}/refund-eligibility` returns one of four decisions:

| Decision | Meaning |
|---|---|
| `ELIGIBLE` | Refund/return can proceed immediately |
| `INELIGIBLE` | Outside policy — cannot proceed |
| `NEED_MORE_INFO` | Missing required fields (e.g. condition status, customer used?) |
| `MANUAL_REVIEW` | Needs a human agent to review (e.g. logistics intercept scenario) |

Key rules:
- **Paid, not yet shipped** → eligible for refund directly, no questions asked
- **Delivered, within 7 days** → eligible for no-reason return if item is resalable
- **Delivered, over 7 days** → ineligible for no-reason return
- **Custom or digital goods** → excluded from no-reason returns; quality issues still accepted
- After-sales submission re-checks eligibility in the same transaction and is idempotent

---

## Build & Test

Build everything and run unit tests:

```bash
./mvnw verify
```

Build only the agent module and its dependencies:

```bash
./mvnw -pl mall-order-agent -am verify
```

Tests use only unit tests, MockMvc, and mock clients — no MySQL, Redis, Milvus, or live API calls required. Tests named `*LiveIT` or `*IntegrationTest` require a real environment and are excluded from the default Surefire run.

CI is defined in `.github/workflows/ci.yml` and runs the full reactor `verify`.

---

## Security Scope

- Spring Security with BCrypt-12, Redis server-side sessions, CSRF protection, session fixation protection, single active session per user, and login lockout (5 failures → 15 min lock)
- Session idle timeout: 30 minutes; absolute max: 8 hours
- `actorUserId`, RAG scope, capabilities, and order customer scope all derive from the authenticated session — clients cannot expand their own permissions
- Order, MCP, and observability services use separate service tokens; health endpoints are public; all ports bind to `127.0.0.1` by default
- AgentInsight uses a dedicated `EVALUATION_ACT_AS` token for `/internal/evaluation/ask`; the token is stored as SHA-256 only
- Login, account, password, token, impersonation, and denial events are written to a sanitised security audit log (retained 180 days)
- Traces never capture query text, response text, prompts, order numbers, or full user IDs

---

## Trace Contract

Every request produces a root trace (`agent.ask` or `agent.resume`) with a `TRACE_END` event containing these stable fields:

| Field | Meaning |
|---|---|
| `traceSchemaVersion` | Contract version (`1.0`) |
| `agentName` / `agentVersion` | Agent identifier and version |
| `conversationId` | Business conversation ID |
| `queryFingerprint` / `queryLength` | Input identifier without storing the raw text |
| `planStrategy` | Planner's final strategy |
| `grounded` / `interrupted` | Whether RAG evidence was used / whether human review was triggered |
| `responseLength` | Output length (text not captured) |
| `continuedFromTraceId` | Links a `resume` to its original interrupted trace |

Retrieval spans record `chunkId`, `source`, and `score`. Sensitive tool spans record `operation`, order/user fingerprint, and `executionStatus`.

---

## Known Limitations

- Graph checkpoint and pending-confirmation state are stored in single-process memory — no multi-instance or restart recovery
- Knowledge base import is an explicit ops step; re-importing after content changes requires a version cleanup strategy
- Docker Compose is for local development only — no production HA, TLS, backup, or capacity planning
- The `local-*-change-me` default service tokens are for loopback-only demos — replace them for any internet-accessible deployment
