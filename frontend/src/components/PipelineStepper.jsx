import { CheckCircle, XCircle } from "./Icons.jsx";

const STEP_INDEX = {
  UPLOADED: 0,
  EXTRACTING: 1,
  EXTRACTED: 1,
  EXTRACTION_FAILED: 1,
  RULE_CHECKING: 2,
  RULE_CHECKED: 2,
  RULE_CHECK_FAILED: 2,
  NEEDS_LLM_REVIEW: 3,
  LLM_REVIEWING: 3,
  LLM_REVIEWED: 3,
  LLM_REVIEW_FAILED: 3,
  FINALIZED: 4,
};

const LLM_STATUSES = new Set(["NEEDS_LLM_REVIEW", "LLM_REVIEWING", "LLM_REVIEWED", "LLM_REVIEW_FAILED"]);

const BASE_STEPS = [
  { index: 0, label: "Uploaded" },
  { index: 1, label: "Extracted" },
  { index: 2, label: "Rule Checked" },
];
const LLM_STEP = { index: 3, label: "LLM Review" };
const FINAL_STEP = { index: 4, label: "Finalized" };

export default function PipelineStepper({ document: doc }) {
  const status = doc.status;
  const currentIndex = STEP_INDEX[status] ?? 0;
  const isFailed = status.endsWith("_FAILED");

  const usesLlm =
    (doc.llmReviewResults && doc.llmReviewResults.length > 0) ||
    LLM_STATUSES.has(status) ||
    (doc.auditTrail || []).some((e) => LLM_STATUSES.has(e.toStatus));

  const steps = usesLlm ? [...BASE_STEPS, LLM_STEP, FINAL_STEP] : [...BASE_STEPS, FINAL_STEP];

  return (
    <div className="stepper" role="list" aria-label="Pipeline progress">
      {steps.map((step) => {
        let state = "pending";
        if (step.index < currentIndex) state = "done";
        else if (step.index === currentIndex) state = isFailed ? "error" : "current";

        return (
          <div className="step" role="listitem" key={step.label}>
            <div className={`step-connector ${state === "done" || state === "current" || state === "error" ? "done" : ""}`} />
            <div className={`step-dot ${state}`}>
              {state === "done" && <CheckCircle width={15} height={15} />}
              {state === "error" && <XCircle width={15} height={15} />}
              {state !== "done" && state !== "error" && step.index + 1}
            </div>
            <div className={`step-label ${state}`}>{step.label}</div>
          </div>
        );
      })}
    </div>
  );
}
