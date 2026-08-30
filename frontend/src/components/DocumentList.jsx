import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { listDocuments, uploadDocument } from "../api.js";
import StatusBadge from "./StatusBadge.jsx";

const STATUS_OPTIONS = [
  "UPLOADED", "EXTRACTING", "EXTRACTED", "EXTRACTION_FAILED",
  "RULE_CHECKING", "RULE_CHECKED", "RULE_CHECK_FAILED",
  "NEEDS_LLM_REVIEW", "LLM_REVIEWING", "LLM_REVIEWED", "LLM_REVIEW_FAILED",
  "FINALIZED",
];

export default function DocumentList() {
  const [documents, setDocuments] = useState([]);
  const [filters, setFilters] = useState({ status: "", filename: "" });
  const [error, setError] = useState(null);
  const [uploading, setUploading] = useState(false);
  const navigate = useNavigate();

  const refresh = useCallback(() => {
    listDocuments(filters)
      .then(setDocuments)
      .catch((e) => setError(e.message));
  }, [filters]);

  useEffect(() => {
    refresh();
    const interval = setInterval(refresh, 5000); // poll so pipeline progress shows up live
    return () => clearInterval(interval);
  }, [refresh]);

  async function handleUpload(event) {
    const file = event.target.files?.[0];
    if (!file) return;
    setUploading(true);
    setError(null);
    try {
      await uploadDocument(file);
      refresh();
    } catch (e) {
      setError(e.message);
    } finally {
      setUploading(false);
      event.target.value = "";
    }
  }

  return (
    <div>
      {error && <div className="error-banner">{error}</div>}

      <div className="panel">
        <p className="section-title">Upload a document</p>
        <div className="upload-form">
          <input type="file" accept=".pdf,.png,.jpg,.jpeg" onChange={handleUpload} disabled={uploading} />
          {uploading && <span>Uploading...</span>}
        </div>
      </div>

      <div className="panel">
        <p className="section-title">Filters</p>
        <div className="filters">
          <input
            type="text"
            placeholder="Filter by filename"
            value={filters.filename}
            onChange={(e) => setFilters((f) => ({ ...f, filename: e.target.value }))}
          />
          <select
            value={filters.status}
            onChange={(e) => setFilters((f) => ({ ...f, status: e.target.value }))}
          >
            <option value="">All statuses</option>
            {STATUS_OPTIONS.map((s) => (
              <option key={s} value={s}>{s.replaceAll("_", " ")}</option>
            ))}
          </select>
          <input
            type="date"
            onChange={(e) =>
              setFilters((f) => ({ ...f, from: e.target.value ? `${e.target.value}T00:00:00Z` : "" }))
            }
          />
        </div>
      </div>

      <div className="panel">
        <p className="section-title">Documents ({documents.length})</p>
        {documents.length === 0 ? (
          <div className="empty-state">No documents match the current filters.</div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Filename</th>
                <th>Status</th>
                <th>Final Verdict</th>
                <th>Uploaded</th>
              </tr>
            </thead>
            <tbody>
              {documents.map((doc) => (
                <tr key={doc.id} className="clickable" onClick={() => navigate(`/documents/${doc.id}`)}>
                  <td>{doc.filename}</td>
                  <td><StatusBadge status={doc.status} /></td>
                  <td>{doc.finalVerdict ? <StatusBadge status={doc.finalVerdict} /> : "-"}</td>
                  <td>{new Date(doc.uploadedAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
