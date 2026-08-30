# Sample test document

`kyc-onboarding-review-case.pdf` is a small synthetic PDF built specifically to
exercise the **escalation path** - it deliberately trips exactly one rule into
`UNCERTAIN` so you can see `needs-llm-review` fire, rather than the boring
all-PASS case.

## Input text (as extracted by Tika)

```
Customer onboarding file. Ref No: REF7788321A. Account number 4400112233990071.
Document dated 2026-02-10. Note: applicant declined to open accounts via any
shell company structure, per due diligence review policy.
```

It's built to hit every rule deliberately:

| Element | Why it's there |
|---|---|
| `REF7788321A` | Satisfies `reference-number-presence` cleanly (KYC-101 format) |
| `4400112233990071` (16 digits, not repeated) | Satisfies `account-number-format` (KYC-115) |
| `2026-02-10` | Valid, parseable, ~7 months old - within `date-validity` and `date-expiry`'s 18-month window |
| 214 characters, low punctuation noise | Clears `minimum-content-quality`'s 50-char floor |
| **"shell company"** | On the `compliance.banned-terms` watchlist (ECC-220) - this is the one deliberate trip wire |

The "shell company" mention is phrased as the applicant *declining* to use one
- i.e. exactly the ambiguous, context-dependent case `BannedTermsRule`'s own
Javadoc describes: a hit is never an automatic FAIL, because "a due-diligence
memo warning against a shell company is legitimate." A dumb keyword-ban would
flag this as a violation; the point of routing it to the LLM (grounded in the
RAG policy corpus) is to actually understand this is a **compliant** mention,
not a violation.

## Expected output

**Rule engine (6/6 evaluated, confidentEnough = false):**

| Rule | Verdict | Confidence | Reason |
|---|---|---|---|
| `account-number-format` | PASS | 0.90 | Account number '4400112233990071' matches expected numeric format. |
| `banned-watchlist-terms` | **UNCERTAIN** | 0.50 | Document contains watchlist term 'shell company'. Context must be reviewed... (policy ECC-220). |
| `date-expiry` | PASS | 0.90 | Document date '2026-02-10' is within the valid window. |
| `date-validity` | PASS | 0.90 | Found valid, parseable date '2026-02-10'. |
| `minimum-content-quality` | PASS | 0.85 | Extracted text length and character composition look reasonable. |
| `reference-number-presence` | PASS | 0.95 | Found reference/account identifier 'REF7788321A'. |

Because `banned-watchlist-terms` came back `UNCERTAIN` (this rule *always*
returns UNCERTAIN on a hit, by design - see its Javadoc), the document is
**not** confident enough to finalize on rules alone, regardless of the
`compliance.confidence-threshold` setting. It escalates:

**Audit trail:**

```
UPLOADED            upload      Document 'kyc-onboarding-review-case.pdf' uploaded (668 bytes).
EXTRACTED           ocr         Extracted 214 characters via TIKA.
RULE_CHECKED        ruleengine  Evaluated 6 rule(s); confidentEnough=false
NEEDS_LLM_REVIEW    ruleengine  1 rule result(s) below confidence threshold or UNCERTAIN.
```

**From here, one of two things happens, depending on `GEMINI_API_KEY`:**

- **No valid key set** (the default - see root `README.md`'s "LLM review
  without a key"): the LLM call fails, and the pipeline lands at
  `LLM_REVIEW_FAILED` with an audit entry explaining why - the app and the
  rest of the pipeline keep running normally, this document just doesn't get
  a final verdict yet.
  ```
  LLM_REVIEW_FAILED   llmreview   LLM review failed: ClientException: 400 . API key not valid.
                                   Check GEMINI_API_KEY is set correctly.
  ```
- **Valid `GEMINI_API_KEY` set**: this is what actually happened when tested
  against the live Gemini API (`gemini-3.6-flash` chat model,
  `gemini-embedding-001` for RAG retrieval). `LlmReviewService` retrieved the
  top-3 policy passages most relevant to "shell company" + the rule's
  reasoning (`ECC-220`, `KYC-101`, `KYC-115` all came back), Gemini read them
  alongside the extracted text, and returned:

  > **Verdict: PASS.** Policy REF: ECC-220 permits the use of high-risk terms
  > like "shell company" provided the document is describing or warning
  > against a prohibited structure, rather than facilitating or recommending
  > one. The sentence containing the flagged term reads: "Note: applicant
  > declined to open accounts via any shell company structure, per due
  > diligence review policy." Because this context clearly indicates
  > compliance with due diligence policies by rejecting a prohibited entity
  > structure, the document passes review under ECC-220. Furthermore, the
  > document contains valid identification numbers (Ref No: REF7788321A and
  > Account number 4400112233990071) satisfying Policy REF: KYC-101 and
  > Policy REF: KYC-115.

  Persisted as an `LlmReviewResult` linked back to the triggering
  `banned-watchlist-terms` `RuleResult`, and the document finalizes at
  `FINALIZED` / `PASS`:
  ```
  LLM_REVIEWED   llmreview   LLM verdict=PASS. Policy REF: ECC-220 permits the use of...
  FINALIZED      llmreview   Finalized with verdict PASS (LLM-assisted).
  ```

  This is exactly the outcome the test document was designed to prove: a
  naive keyword ban would have flagged or blocked this document, but the
  RAG-grounded LLM correctly read the surrounding sentence and passed it,
  citing the specific policy clause that makes the distinction.

Note: getting the real Gemini call working required two config fixes beyond
what's in the "Bugs found and fixed" section of the root `README.md` - Spring
AI 1.1.8's actual property paths are `spring.ai.google.genai.chat.options.model`
and `spring.ai.google.genai.embedding.text.options.model` (an extra
`.options.` segment vs. what several docs/tutorials show), and the model
names need to be current ones your API key actually has access to - `curl
"https://generativelanguage.googleapis.com/v1beta/models?key=$GEMINI_API_KEY"`
is the fastest way to check, since Google retires model names over time and
a stale name 404s with a message that (usefully) names its replacement.

This was verified end-to-end against a running `docker compose` stack,
including a real Gemini API call: every rule verdict and the LLM reasoning
above are the actual observed output, not a prediction.

## Try it yourself

```bash
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@samples/kyc-onboarding-review-case.pdf;type=application/pdf"

# then poll (swap in the returned id):
curl http://localhost:8080/api/documents/<id>
```

Or via the dashboard: upload it at http://localhost:5173, open the detail
view, and watch the rule-by-rule table plus the audit log fill in.
