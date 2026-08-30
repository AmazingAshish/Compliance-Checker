# Compliance Checker

An AI-assisted document compliance checker: upload a PDF/image, extract its
text via OCR, run it through a deterministic rule engine, and escalate only
the genuinely ambiguous cases to an LLM grounded in a small RAG corpus of
compliance policy documents. Every stage is a separate Kafka consumer so the
pipeline stays decoupled and resilient to any one stage failing.

This project is a portfolio/interview reference piece. The explanations below
are written to be talking points, not boilerplate.

## Architecture

```
                         ┌─────────────────┐
   PDF / PNG / JPG  ───► │  UploadController │  (multipart POST /api/documents/upload)
                         └────────┬─────────┘
                                  │ saves file, persists Document(status=UPLOADED)
                                  │ audit: UPLOADED
                                  ▼
                       topic: document-uploaded
                                  │
                                  ▼
                    ┌─────────────────────────┐
                    │  ocr.DocumentUploaded-   │   Tika (text-layer PDFs)
                    │  Consumer                │   falls back to
                    │                          │   Tesseract OCR (tess4j)
                    └────────────┬─────────────┘   for images / scanned PDFs
                                 │ on success: ExtractionResult saved,
                                 │   status=EXTRACTED, audit: EXTRACTED
                                 │ on failure: status=EXTRACTION_FAILED,
                                 │   audit: EXTRACTION_FAILED (consumer thread survives)
                                 ▼
                     topic: document-extracted
                                 │
                                 ▼
                  ┌────────────────────────────┐
                  │ ruleengine.DocumentExtracted│   6 ComplianceRule impls run
                  │ Consumer -> RuleEngineService│  independently, aggregated
                  └───────────┬────────────────┘
                     confident?         ambiguous?
                    (all rules >=        (any rule UNCERTAIN
                  threshold, none         or below threshold)
                    UNCERTAIN)
                        │                     │
                        ▼                     ▼
              status=FINALIZED      topic: needs-llm-review
              audit: RULE_CHECKED,             │
                     FINALIZED                 ▼
                        │            ┌───────────────────────────┐
                        │            │ llmreview.NeedsLlmReview-  │
                        │            │ Consumer -> LlmReviewService│
                        │            │  1. similarity-search the  │
                        │            │     policy vector store    │
                        │            │  2. ChatClient.prompt()    │
                        │            │     (Spring AI, Gemini)    │
                        │            │  3. persist LlmReviewResult│
                        │            │     linked to the          │
                        │            │     triggering RuleResult  │
                        │            └────────────┬───────────────┘
                        │               status=FINALIZED
                        │               audit: LLM_REVIEWED, FINALIZED
                        ▼                          ▼
                        └──────────► topic: document-finalized
                                                    │
                                                    ▼
                                       audit_log table (every transition,
                                       every stage, full history)
                                                    │
                                                    ▼
                                     React dashboard (list + detail views)
                                     reads via GET /api/documents[/{id}]
```

Every box that touches the database also writes an `AuditLogEntry` -
`UPLOADED → EXTRACTED|EXTRACTION_FAILED → RULE_CHECKED → NEEDS_LLM_REVIEW? →
LLM_REVIEWED? → FINALIZED` - independent of the `documents.status` column, so
the full history survives even if the current status is later overwritten.

## Repository layout

```
backend/    Spring Boot 3 app, package base com.compliance.checker
  common/       entities, repositories, Kafka topic/producer config, RAG vector store config
  upload/       REST upload endpoint + DocumentService + DocumentUploadedProducer
  ocr/          OcrService (Tika + tess4j) + DocumentUploadedConsumer
  ruleengine/   ComplianceRule interface, 6 rule impls, RuleEngineService, DocumentExtractedConsumer
  llmreview/    ComplianceLlmClient (Spring AI wrapper), LlmReviewService, PolicyVectorStoreInitializer, NeedsLlmReviewConsumer
  audit/        AuditLogService
  dashboard/    read-only list/detail/filter REST endpoints for the React app
  resources/policies/  the 6-document synthetic RAG corpus
frontend/   React (Vite) dashboard - document list + detail views
k8s/        namespace, postgres, kafka, backend, frontend manifests
docker-compose.yml
```

## Running it

### Docker Compose (recommended for a quick look)

```bash
export GEMINI_API_KEY=AIza...        # optional - see "LLM review without a key" below
docker compose up --build
```

- Backend: http://localhost:8080
- Frontend: http://localhost:5173
- Postgres: localhost:5432 (compliance/compliance)
- Kafka: localhost:9092

### Setting GEMINI_API_KEY

Spring AI's Google GenAI starter reads `GEMINI_API_KEY` from the environment
(wired through `application.yml`'s `spring.ai.google.genai.api-key`). It is never
hardcoded anywhere in the codebase.

- **Local run:** `export GEMINI_API_KEY=AIza...` before `mvn spring-boot:run`.
- **Docker Compose:** export it in your shell before `docker compose up` (see above).
- **Kubernetes:** edit `k8s/backend.yaml`'s `backend-secret` (or `kubectl create secret generic backend-secret --from-literal=GEMINI_API_KEY=AIza... -n compliance-checker`) before applying.

**LLM review without a key:** the app still starts and the rule engine still
runs. Documents that need LLM escalation will fail that one step gracefully
(`LLM_REVIEW_FAILED`, with the audit log noting to check the key) rather than
crashing the consumer - this is the same resilience pattern used everywhere
else in the pipeline.

### Running tests

```bash
cd backend
mvn test
```

Covers: `RuleEngineServiceTest` (table-driven over all 6 rules + aggregation
logic), `TikaTesseractOcrServiceTest` (Tika text-layer extraction path),
`DocumentExtractedConsumerKafkaTest` (embedded-broker end-to-end test of the
rule-engine consumer), and `UploadControllerTest` (MockMvc).

### Kubernetes

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/postgres.yaml -f k8s/kafka.yaml
kubectl apply -f k8s/backend.yaml -f k8s/frontend.yaml
```

Build/push `compliance-checker-backend:latest` and `compliance-checker-frontend:latest`
first (or point the deployments at your registry), and fill in
`backend-secret`'s `GEMINI_API_KEY`.

## Design decisions worth talking about in an interview

### Why Kafka instead of direct service calls

Each pipeline stage (OCR, rule engine, LLM review) has wildly different
latency and failure characteristics: OCR on a large scanned PDF can take
seconds, an LLM call can take seconds-to-tens-of-seconds and depends on an
external provider's uptime, while the rule engine runs in single-digit
milliseconds. If `UploadController` called these synchronously in a chain, a
slow OCR run or an LLM provider outage would block the HTTP request (and
every other upload waiting on that thread pool). Kafka decouples them: upload
returns immediately after publishing `document-uploaded`, and each stage
scales and fails independently. If the LLM provider goes down entirely,
uploaded documents queue up on `needs-llm-review` and get processed the
moment the provider recovers - nothing is lost, and the rest of the pipeline
(upload, OCR, rule checks) keeps running unaffected. That's the concrete
payoff of decoupling here, not just "microservices best practice."

### Why RAG instead of plain prompting

The LLM is asked to make judgment calls that are explicitly grounded in this
organization's specific policies (see `backend/src/main/resources/policies/`)
- e.g. "a banned term appearing in a *warning* is different from one
appearing in a *facilitation*" is a distinction defined in `policy-02`, not
something a general-purpose model would reliably infer or apply consistently.
Plain prompting would either (a) rely on the model's fuzzy general knowledge
of "compliance," which drifts from this system's actual rules and can't be
audited against a source document, or (b) require stuffing the entire policy
corpus into every prompt regardless of relevance, which wastes tokens and
dilutes the model's attention on documents that don't need it. RAG retrieves
only the 2-3 passages most relevant to *this* document's ambiguous rule(s)
(`LlmReviewService.review()` does a similarity search against the flagged
rule descriptions + extracted text), so the prompt stays focused and every
verdict traces back to a specific, citable policy passage stored in
`LlmReviewResult.retrievedContext` - which is exactly what an auditor needs.

### How the confidence threshold drives escalation

`RuleEngineService.evaluate()` runs all 6 `ComplianceRule` implementations
and only treats the result as final if **every** rule's confidence is >= the
configured `compliance.confidence-threshold` (default `0.75`) **and** none of
them returned `UNCERTAIN`. Confidence isn't a formality here - each rule
picks it deliberately: `BannedTermsRule` always returns `UNCERTAIN` on a hit
(context genuinely can't be judged by regex), `MinimumContentQualityRule`
drops confidence on short/noisy text (garbled OCR looks different from a
clean fail), and `DateExpiryRule` treats a missing date as more suspicious
than a stale-but-present one, matching policy `DOC-330`. Raising the
threshold in `application.yml` makes the system escalate more aggressively
(more LLM calls, more cost, fewer false auto-approvals); lowering it trusts
the rule engine more. That single config value is the whole cost/accuracy
dial for the system, which is a natural thing to point at when discussing
tuning tradeoffs.

### Concurrency/failure-handling decision: consumer idempotency under at-least-once delivery

Spring Kafka's default delivery semantics here are at-least-once: a consumer
can be redelivered the same message (e.g. after a rebalance right after
processing but before the offset commit lands). Every consumer in this
pipeline (`DocumentUploadedConsumer`, `DocumentExtractedConsumer`,
`NeedsLlmReviewConsumer`) guards against double-processing the same way: **check
whether this stage's output already exists for the document before doing any
work** (`extractionResultRepository.existsByDocumentId(...)`,
`ruleResultRepository.existsByDocumentId(...)`,
`llmReviewResultRepository.existsByDocumentId(...)`). If it does, the
handler logs and returns immediately - no duplicate OCR run, no duplicate
(and possibly billed) LLM call, no duplicate audit entries. This was chosen
over deduplication via a message ID / dedup table because the natural
idempotency key already exists in the domain model (a document only needs
one extraction, one set of rule results, one LLM review), so a separate
dedup mechanism would be redundant machinery. Combined with the per-consumer
try/catch that marks the document `*_FAILED` instead of throwing (which
would otherwise get the message redelivered indefinitely and potentially
poison-pill the partition), this is the concrete resilience story for the
whole pipeline: no message can be silently dropped, permanently stuck, or
double-billed to the LLM provider.

## Bugs found and fixed while getting this running end-to-end

These are worth knowing for an interview because they're the kind of thing
that only shows up when you actually run the stack, not when you eyeball the
code - "I wrote it" and "I made it work" are different claims.

1. **Tika's PDF parser vs. tess4j's PDFBox, `NoSuchMethodError` at OCR time.**
   `tess4j` transitively pulls PDFBox 3.x for its own (unused, in this
   project) PDF-rasterization path; Tika's PDF module is built against
   PDFBox 2.x. Whichever version Maven happened to put first on the runtime
   classpath silently shadowed the other, and the first real PDF upload blew
   up with `NoSuchMethodError` on `PDDocument.load(...)` deep inside Tika -
   `mvn compile` and unit tests never caught it because nothing in the test
   suite exercises the real Tika PDF-parsing path against a real file. Fixed
   by excluding PDFBox from `tess4j` in `backend/pom.xml` so Tika's PDFBox
   2.x wins unambiguously. Lesson: `dependency:tree` mediation and the
   *packaged fat jar's actual contents* can disagree - trust
   `jar tf app.jar | grep <lib>` over the tree output when something behaves
   like two versions are on the classpath at once.

2. **Spring AI's Google GenAI auto-configuration crashes app startup without
   a key, breaking the "runs without an LLM key" resilience promise.** By
   default, Spring eagerly instantiates every non-lazy singleton bean at
   context refresh - including the auto-configured `ChatModel`/`EmbeddingModel`
   beans, which validate their connection config *at construction time*, before
   anything ever calls them. An empty `GEMINI_API_KEY` therefore failed the
   whole application context, not just the LLM-review stage. `@Lazy` on the
   injection points (see `LlmReviewService`'s constructor,
   `VectorStoreConfig.vectorStore(@Lazy EmbeddingModel ...)`) turned out to be
   necessary but not sufficient, since Spring Boot's auto-configured beans are
   still pre-instantiated during `preInstantiateSingletons` regardless of
   whether anything currently depends on them. The actual fix ended up being
   simpler than the plumbing: Spring AI's connection check only verifies the
   key is **non-blank locally** - it never calls the network at startup - so
   defaulting `GEMINI_API_KEY` to a non-empty placeholder (`unset`, see
   `application.yml`'s comment) satisfies that check and lets the app boot
   normally. A genuinely missing/invalid key then only fails the real API
   call inside `NeedsLlmReviewConsumer`'s try/catch, which was always the
   intended resilience boundary. Lesson: distinguish "eager local validation"
   from "eager network call" before reaching for `@Lazy` - the former is
   usually cheaper to just satisfy than to defer.

3. **`@Lob` on Postgres + Hibernate: "Large Objects may not be used in
   auto-commit mode."** `ExtractionResult.extractedText`, `RuleResult.reason`,
   `LlmReviewResult.reasoning`/`retrievedContext`, and `AuditLogEntry.detail`
   were all mapped `@Lob @Column(columnDefinition = "TEXT")`. On Postgres,
   `@Lob` on a `String` makes Hibernate treat the column as a JDBC CLOB and
   stream it via `pg_largeobject`, which Postgres only allows inside an
   explicit transaction - reading it from a plain `GET` request (no
   `@Transactional`, `spring.jpa.open-in-view: false`) threw a
   `JpaSystemException` the moment any row with real extracted text was
   fetched. `columnDefinition = "TEXT"` was already the correct DDL; `@Lob`
   was the redundant, actively harmful annotation forcing the wrong read
   strategy on top of it. Fixed by dropping `@Lob` from all four entities -
   Hibernate then just reads the `TEXT` column as a plain `String`, no
   streaming, no transaction requirement. Lesson: on Postgres, `@Lob` on a
   `String` is very rarely what you want; a plain `TEXT` column with no
   `@Lob` handles multi-KB text fine.

4. **Spring AI's Google GenAI property paths in the docs don't match the
   actual `@ConfigurationProperties` in 1.1.8.** Several tutorials and even
   the official reference docs show `spring.ai.google.genai.chat.model` and
   `spring.ai.google.genai.embedding.text.model`; the jar's own
   `spring-configuration-metadata.json` says the real paths have an extra
   `.options.` segment: `spring.ai.google.genai.chat.options.model` and
   `spring.ai.google.genai.embedding.text.options.model`. The wrong path
   doesn't error - Spring just silently falls back to Spring AI's internal
   default model constant, which was a retired model
   (`gemini-2.0-flash-001`), so the failure surfaced as a confusing 404 from
   Google ("this model is no longer available") that had nothing to do with
   the actual bug. Lesson: when a Spring Boot property "does nothing," check
   `META-INF/spring-configuration-metadata.json` inside the actual jar you
   depend on before trusting a doc page, since minor-version property
   renames don't always make it into every tutorial. Also: Google retires
   Gemini model names over time - `curl
   "https://generativelanguage.googleapis.com/v1beta/models?key=$GEMINI_API_KEY"`
   is the fastest way to check which ones your key currently has access to.
   See `samples/README.md` for the full working end-to-end run with a real
   key.

## Known gaps / TODOs for whoever picks this up

- **Set `GEMINI_API_KEY`** before expecting LLM review to actually produce a
  verdict (see above); without it, ambiguous documents will sit at
  `LLM_REVIEW_FAILED`.
- **Tesseract tuning**: `TESSDATA_PREFIX` / `compliance.ocr.tessdata-path`
  point at a default location; for real scanned documents you'll likely want
  to tune DPI/preprocessing (deskew, binarization) before OCR for better
  accuracy - the current `TikaTesseractOcrService` does no image
  preprocessing.
- **PDF-with-no-text-layer + OCR fallback**: the fallback path in
  `TikaTesseractOcrService` OCRs the raw PDF bytes directly via tess4j; for
  multi-page scanned PDFs you'd typically rasterize each page (e.g. via
  pdfbox) and OCR page-by-page for better results. Out of scope for this
  skeleton.
- **Auth**: none beyond what's noted as acceptable in the brief (no
  multi-tenant auth system). Don't expose this as-is on the public internet.
- **pgvector migration**: `VectorStoreConfig` intentionally uses Spring AI's
  in-memory `SimpleVectorStore` (see the comment there for why); swapping to
  pgvector for a persistent/larger corpus is a one-bean change.
- **Kafka topic partition count / consumer group scaling** hasn't been load
  tested; the manifests here are demo-sized, not production-sized.
#   C o m p l i a n c e - C h e c k e r  
 