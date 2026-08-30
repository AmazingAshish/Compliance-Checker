import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getDocument } from "../api.js";
import StatusBadge from "./StatusBadge.jsx";

export default function DocumentDetail() {
  const { id } = useParams();
  const [doc, setDoc] = useState(null);
  const [error, setError] = useState(null);

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

  if (error) return <div className="error-banner">{error}</div>;
  if (!doc) return <div className="empty-state">Loading...</div>;

  return (
    <div>
      <p><Link className="back-link" to="/">&larr; Back to document list</Link></p>

      <div className="panel">
        <p className="section-title">Document</p>
        <h2 style={{ margin: "0 0 8px" }}>{doc.filename}</h2>
        <p>
          <StatusBadge status={doc.status} />{" "}
          {doc.finalVerdict && <StatusBadge status={doc.finalVerdict} />}
        </p>
        <p style={{ fontSize: 13, color: "#64748b" }}>
          {doc.contentType} &middot; {doc.fileSizeBytes} bytes &middot; uploaded {new Date(doc.uploadedAt).toLocaleString()}
        </p>
      </div>

      <div className="panel">
        <p className="section-title">Extracted Text</p>
        {doc.extractedText ? (
          <pre className="extracted-text">{doc.extractedText}</pre>
        ) : (
          <div className="empty-state">No text extracted yet.</div>
        )}
      </div>

      <div className="panel">
        <p className="section-title">Rule-by-Rule Results</p>
        {doc.ruleResults?.length ? (
          <table>
            <thead>
              <tr>
                <th>Rule</th>
                <th>Verdict</th>
                <th>Confidence</th>
                <th>Reason</th>
              </tr>
            </thead>
            <tbody>
              {doc.ruleResults.map((r) => (
                <tr key={r.id}>
                  <td>{r.ruleName}</td>
                  <td><StatusBadge status={r.verdict} /></td>
                  <td>{r.confidence.toFixed(2)}</td>
                  <td>{r.reason}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <div className="empty-state">No rule results yet.</div>
        )}
      </div>

      <div className="panel">
        <p className="section-title">LLM Review</p>
        {doc.llmReviewResults?.length ? (
          doc.llmReviewResults.map((r) => (
            <div key={r.id} style={{ marginBottom: 12 }}>
              <p><StatusBadge status={r.verdict} /> <span style={{ fontSize: 12, color: "#64748b" }}>via {r.modelUsed}, {new Date(r.reviewedAt).toLocaleString()}</span></p>
              <p style={{ fontSize: 14 }}>{r.reasoning}</p>
            </div>
          ))
        ) : (
          <div className="empty-state">No LLM review was needed for this document.</div>
        )}
      </div>

      <div className="panel">
        <p className="section-title">Audit Trail</p>
        {doc.auditTrail?.length ? (
          doc.auditTrail.map((entry) => (
            <div key={entry.id} className="audit-item">
              <span className="audit-time">{new Date(entry.timestamp).toLocaleString()}</span>
              <span><StatusBadge status={entry.toStatus} /> <strong>{entry.stage}</strong> - {entry.detail}</span>
            </div>
          ))
        ) : (
          <div className="empty-state">No audit entries yet.</div>
        )}
      </div>
    </div>
  );
}
