import { CheckCircle, XCircle, AlertTriangle, Clock, Loader } from "./Icons.jsx";

const PASS_LIKE = new Set(["PASS", "FINALIZED"]);
const FAIL_LIKE = new Set([
  "FAIL", "EXTRACTION_FAILED", "RULE_CHECK_FAILED", "LLM_REVIEW_FAILED",
]);
const WARN_LIKE = new Set(["UNCERTAIN", "NEEDS_LLM_REVIEW"]);
const PROGRESS_LIKE = new Set(["EXTRACTING", "RULE_CHECKING", "LLM_REVIEWING", "UPLOADED"]);

function iconFor(status) {
  if (PASS_LIKE.has(status)) return CheckCircle;
  if (FAIL_LIKE.has(status)) return XCircle;
  if (WARN_LIKE.has(status)) return AlertTriangle;
  if (PROGRESS_LIKE.has(status)) return status === "UPLOADED" ? Clock : Loader;
  return Clock;
}

export default function StatusBadge({ status }) {
  if (!status) return null;
  const className = `badge badge-${status.toLowerCase()}`;
  const Icon = iconFor(status);
  const spinning = status === "EXTRACTING" || status === "RULE_CHECKING" || status === "LLM_REVIEWING";
  return (
    <span className={className}>
      <Icon width={11} height={11} className={spinning ? "spin" : undefined} />
      {status.replaceAll("_", " ")}
    </span>
  );
}
