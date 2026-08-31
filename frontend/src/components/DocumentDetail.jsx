import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { getDocument, deleteDocument } from "../api.js";
import StatusBadge from "./StatusBadge.jsx";
import PipelineStepper from "./PipelineStepper.jsx";
import ConfirmDialog from "./ConfirmDialog.jsx";
import { renderInline, parseReasoning } from "../formatting.jsx";
import {
  AlertTriangle, ArrowLeft, CheckCircle, ChevronDown, Clock, Copy,
  Download, FileText, ListChecks, Sparkles, Trash, XCircle,
} from "./Icons.jsx";

function verdictClass(verdict) {
  if (verdict === "PASS") return "pass";
  if (verdict === "FAIL") return "fail";
  return "uncertain";
}

function verdictIcon(verdict) {
  if (verdict === "PASS") return CheckCircle;
  if (verdict === "FAIL") return XCircle;
  return AlertTriangle;
}

function timelineTone(status) {
  if (status === "FINALIZED" || status.endsWith("_CHECKED") || status === "EXTRACTED" || status === "LLM_REVIEWED") return "pass";
  if (status.endsWith("_FAILED")) return "fail";
  if (status === "NEEDS_LLM_REVIEW") return "warn";
  return "info";
}

function DetailSkeleton() {
  return (
    <div className="panel loading-panel">
      <div className="skeleton skeleton-line" style={{ width: "40%", height: 22 }} />
      <div className="skeleton skeleton-line" style={{ width: "60%" }} />
      <div className="skeleton skeleton-line" style={{ width: "50%" }} />
    </div>
  );
}

function CopyButton({ text, label = "Copy" }) {
  const [copied, setCopied] = useState(false);
  return (
    <button
      type="button"
      className="ghost icon-btn"
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(text);
          setCopied(true);
          setTimeout(() => setCopied(false), 1500);
        } catch {
          // clipboard unavailable — silently ignore, non-critical affordance
        }
      }}
    >
      {copied ? <CheckCircle width={13} height={13} /> : <Copy width={13} height={13} />}
      {copied ? "Copied" : label}
    </button>
  );
}

function DownloadJsonButton({ doc }) {
  function handleDownload() {
    const blob = new Blob([JSON.stringify(doc, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${doc.filename.replace(/\.[^.]+$/, "")}-compliance-record.json`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  }
  return (
    <button type="button" className="ghost icon-btn" onClick={handleDownload}>
      <Download width={13} height={13} /> Export JSON
    </button>
  );
}

/** Renders reasoning text as a structured numbered list when the LLM used
 * that pattern, otherwise as a plain (still bold/policy-code-aware) paragraph. */
function Reasoning({ text }) {
  const parsed = parseReasoning(text);
  if (!parsed) {
    return <p className="llm-reasoning">{renderInline(text)}</p>;
  }
  return (
    <>
      {parsed.intro && <p className="llm-intro">{renderInline(parsed.intro, "intro")}</p>}
      <div className="llm-item-list">
        {parsed.items.map((item, i) => (
          <div className="llm-item" key={i}>
            <span className="llm-item-num">{item.number}</span>
            <div className="llm-item-body">
              <span className="llm-item-title">{renderInline(item.title, `title-${i}`)}</span>
              <p className="llm-item-text">{renderInline(item.body, `body-${i}`)}</p>
            </div>
          </div>
        ))}
      </div>
    </>
  );
}

function LlmReviewCard({ result }) {
  const [expanded, setExpanded] = useState(false);
  return (
    <div className="llm-card">
      <div className="llm-card-head">
        <span className="llm-badge-icon"><Sparkles width={13} height={13} /></span>
        <StatusBadge status={result.verdict} />
        <span className="llm-model-meta">
          via {result.modelUsed} · {new Date(result.reviewedAt).toLocaleString()}
        </span>
      </div>
      <Reasoning text={result.reasoning} />
      {result.retrievedContext && (
        <>
          <button
            type="button"
            className={`llm-context-toggle${expanded ? " open" : ""}`}
            onClick={() => setExpanded((v) => !v)}
          >
            <ChevronDown width={13} height={13} />
            {expanded ? "Hide" : "View"} retrieved policy passages
          </button>
          {expanded && <div className="llm-context">{result.retrievedContext}</div>}
        </>
      )}
    </div>
  );
}

export default function DocumentDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [doc, setDoc] = useState(null);
  const [error, setError] = useState(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);

  async function handleDelete() {
    setDeleting(true);
    try {
      await deleteDocument(id);
      navigate("/");
    } catch (e) {
      setError(e.message);
      setConfirmingDelete(false);
      setDeleting(false);
    }
  }

  useEffect(() => {
    let cancelled = false;
    function load() {
      getDocument(id)
        .then((d) => !cancelled && setDoc(d))
        .catch((e) => !cancelled && setError(e.message));
    }
    load();
    const interval = setInterval(load, 4000);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [id]);

  if (error) {
    return (
      <div>
        <Link className="back-link" to="/"><ArrowLeft width={14} height={14} /> Back to document list</Link>
        <div className="error-banner"><AlertTriangle width={16} height={16} /> {error}</div>
      </div>
    );
  }
  if (!doc) {
    return (
      <div>
        <Link className="back-link" to="/"><ArrowLeft width={14} height={14} /> Back to document list</Link>
        <DetailSkeleton />
      </div>
    );
  }

  return (
    <div>
      <Link className="back-link" to="/"><ArrowLeft width={14} height={14} /> Back to document list</Link>

      <div className="panel">
        <div className="doc-header-row">
          <div className="doc-title">
            <span className="doc-icon"><FileText width={20} height={20} /></span>
            <div>
              <h2>{doc.filename}</h2>
              <p className="doc-meta">
                {doc.contentType}
                <span className="dot">·</span>
                {(doc.fileSizeBytes / 1024).toFixed(1)} KB
                <span className="dot">·</span>
                <Clock width={12} height={12} style={{ verticalAlign: -2 }} />
                uploaded {new Date(doc.uploadedAt).toLocaleString()}
              </p>
            </div>
          </div>
          <div className="doc-badges">
            <StatusBadge status={doc.status} />
            {doc.finalVerdict && <StatusBadge status={doc.finalVerdict} />}
            <DownloadJsonButton doc={doc} />
            <button
              type="button"
              className="ghost icon-btn danger-ghost"
              onClick={() => setConfirmingDelete(true)}
            >
              <Trash width={13} height={13} /> Delete
            </button>
          </div>
        </div>
        <PipelineStepper document={doc} />
      </div>

      <div className="panel">
        <div className="text-panel-toolbar">
          <p className="section-title"><FileText width={13} height={13} /> Extracted Text</p>
          {doc.extractedText && <CopyButton text={doc.extractedText} />}
        </div>
        {doc.extractedText ? (
          <pre className="extracted-text">{doc.extractedText}</pre>
        ) : (
          <div className="empty-state">
            <Clock width={22} height={22} />
            Waiting on OCR extraction...
          </div>
        )}
      </div>

      <div className="panel">
        <p className="section-title"><ListChecks width={13} height={13} /> Rule-by-Rule Results</p>
        {doc.ruleResults?.length ? (
          <div className="rule-grid">
            {doc.ruleResults.map((r) => {
              const cls = verdictClass(r.verdict);
              const Icon = verdictIcon(r.verdict);
              const pct = Math.round(r.confidence * 100);
              return (
                <div key={r.id} className={`rule-card ${cls}`}>
                  <span className="rule-card-icon"><Icon width={17} height={17} /></span>
                  <div className="rule-card-body">
                    <div className="rule-card-head">
                      <span className="rule-name">{r.ruleName}</span>
                      <span className="confidence-wrap">
                        <span className="confidence-bar">
                          <span className="confidence-fill" style={{ width: `${pct}%` }} />
                        </span>
                        {pct}%
                      </span>
                    </div>
                    <p className="rule-reason">{renderInline(r.reason, r.id)}</p>
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          <div className="empty-state">
            <Clock width={22} height={22} />
            No rule results yet.
          </div>
        )}
      </div>

      <div className="panel">
        <p className="section-title"><Sparkles width={13} height={13} /> LLM Review</p>
        {doc.llmReviewResults?.length ? (
          doc.llmReviewResults.map((r) => <LlmReviewCard key={r.id} result={r} />)
        ) : (
          <div className="empty-state">
            <CheckCircle width={22} height={22} />
            No LLM review was needed — every rule was confident enough on its own.
          </div>
        )}
      </div>

      <div className="panel">
        <p className="section-title"><Clock width={13} height={13} /> Audit Trail</p>
        {doc.auditTrail?.length ? (
          <div className="timeline">
            {doc.auditTrail.map((entry) => {
              const tone = timelineTone(entry.toStatus);
              const Icon = tone === "pass" ? CheckCircle : tone === "fail" ? XCircle : tone === "warn" ? AlertTriangle : Clock;
              return (
                <div key={entry.id} className="timeline-item">
                  <span className={`timeline-dot ${tone}`}><Icon width={9} height={9} /></span>
                  <div className="timeline-head">
                    <StatusBadge status={entry.toStatus} />
                    <span className="timeline-stage">{entry.stage}</span>
                    <span className="timeline-time">{new Date(entry.timestamp).toLocaleString()}</span>
                  </div>
                  <p className="timeline-detail">{renderInline(entry.detail, entry.id)}</p>
                </div>
              );
            })}
          </div>
        ) : (
          <div className="empty-state">No audit entries yet.</div>
        )}
      </div>

      <ConfirmDialog
        open={confirmingDelete}
        title="Delete this document?"
        message={`"${doc.filename}" and all its rule results, LLM review, and audit history will be permanently deleted. This can't be undone.`}
        confirmLabel={deleting ? "Deleting..." : "Delete"}
        onConfirm={handleDelete}
        onCancel={() => setConfirmingDelete(false)}
      />
    </div>
  );
}
