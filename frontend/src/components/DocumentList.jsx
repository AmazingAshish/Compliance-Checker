import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { listDocuments, uploadDocument, deleteDocument } from "../api.js";
import StatusBadge from "./StatusBadge.jsx";
import ConfirmDialog from "./ConfirmDialog.jsx";
import {
  AlertTriangle, ArrowUpDown, CheckCircle, ChevronLeft, ChevronRight, Clock,
  FileText, Inbox, Loader, Search, Trash, UploadCloud, XCircle, RefreshCw,
} from "./Icons.jsx";

const STATUS_OPTIONS = [
  "UPLOADED", "EXTRACTING", "EXTRACTED", "EXTRACTION_FAILED",
  "RULE_CHECKING", "RULE_CHECKED", "RULE_CHECK_FAILED",
  "NEEDS_LLM_REVIEW", "LLM_REVIEWING", "LLM_REVIEWED", "LLM_REVIEW_FAILED",
  "FINALIZED",
];

const IN_PROGRESS = new Set([
  "UPLOADED", "EXTRACTING", "RULE_CHECKING", "NEEDS_LLM_REVIEW", "LLM_REVIEWING",
]);
const FAILED = new Set(["EXTRACTION_FAILED", "RULE_CHECK_FAILED", "LLM_REVIEW_FAILED"]);

const PAGE_SIZE = 8;

function timeAgo(iso) {
  const diffMs = Date.now() - new Date(iso).getTime();
  const mins = Math.round(diffMs / 60000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.round(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  return `${days}d ago`;
}

function isFailedDoc(d) {
  return d.finalVerdict === "FAIL" || FAILED.has(d.status);
}
function isPassedDoc(d) {
  return d.finalVerdict === "PASS";
}
function isInProgressDoc(d) {
  return IN_PROGRESS.has(d.status);
}

export default function DocumentList() {
  const [documents, setDocuments] = useState([]);
  const [filters, setFilters] = useState({ status: "", filename: "" });
  const [error, setError] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [uploadedName, setUploadedName] = useState(null);
  const [dragActive, setDragActive] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [quickFilter, setQuickFilter] = useState("all");
  const [sortDir, setSortDir] = useState("desc");
  const [page, setPage] = useState(1);
  const [pendingDelete, setPendingDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const navigate = useNavigate();
  const filenameInputRef = useRef(null);
  const fileInputRef = useRef(null);

  const refresh = useCallback(() => {
    listDocuments(filters)
      .then((docs) => {
        setDocuments(docs);
        setLoaded(true);
      })
      .catch((e) => setError(e.message));
  }, [filters]);

  useEffect(() => {
    refresh();
    const interval = setInterval(refresh, 5000); // poll so pipeline progress shows up live
    return () => clearInterval(interval);
  }, [refresh]);

  // "/" focuses the filename search, like GitHub/Linear — a quick affordance
  // for a compliance reviewer scanning through many documents.
  useEffect(() => {
    function handleKey(e) {
      if (e.key === "/" && document.activeElement?.tagName !== "INPUT" && document.activeElement?.tagName !== "SELECT") {
        e.preventDefault();
        filenameInputRef.current?.focus();
      }
    }
    window.addEventListener("keydown", handleKey);
    return () => window.removeEventListener("keydown", handleKey);
  }, []);

  useEffect(() => {
    setPage(1);
  }, [filters, quickFilter, sortDir]);

  const stats = useMemo(() => {
    const s = { total: documents.length, pass: 0, fail: 0, progress: 0 };
    for (const d of documents) {
      if (isPassedDoc(d)) s.pass += 1;
      if (isFailedDoc(d)) s.fail += 1;
      if (isInProgressDoc(d)) s.progress += 1;
    }
    return s;
  }, [documents]);

  const visible = useMemo(() => {
    let list = documents;
    if (quickFilter === "pass") list = list.filter(isPassedDoc);
    else if (quickFilter === "fail") list = list.filter(isFailedDoc);
    else if (quickFilter === "progress") list = list.filter(isInProgressDoc);

    list = [...list].sort((a, b) => {
      const diff = new Date(b.uploadedAt) - new Date(a.uploadedAt);
      return sortDir === "desc" ? diff : -diff;
    });
    return list;
  }, [documents, quickFilter, sortDir]);

  const totalPages = Math.max(1, Math.ceil(visible.length / PAGE_SIZE));
  const pageItems = visible.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  async function doUpload(file) {
    if (!file) return;
    setUploading(true);
    setError(null);
    setUploadedName(null);
    try {
      await uploadDocument(file);
      setUploadedName(file.name);
      refresh();
      setTimeout(() => setUploadedName(null), 4000);
    } catch (e) {
      setError(e.message);
    } finally {
      setUploading(false);
    }
  }

  function handleFileInput(event) {
    const file = event.target.files?.[0];
    doUpload(file);
    event.target.value = "";
  }

  function handleDrop(event) {
    event.preventDefault();
    setDragActive(false);
    const file = event.dataTransfer.files?.[0];
    doUpload(file);
  }

  function toggleQuickFilter(key) {
    setQuickFilter((current) => (current === key ? "all" : key));
  }

  async function confirmDelete() {
    if (!pendingDelete) return;
    setDeleting(true);
    try {
      await deleteDocument(pendingDelete.id);
      setPendingDelete(null);
      refresh();
    } catch (e) {
      setError(e.message);
      setPendingDelete(null);
    } finally {
      setDeleting(false);
    }
  }

  const hasFilters = filters.status || filters.filename || filters.from;

  return (
    <div>
      {error && (
        <div className="error-banner">
          <AlertTriangle width={16} height={16} />
          {error}
        </div>
      )}

      <div className="stat-grid">
        <button
          type="button"
          className={`stat-card clickable${quickFilter === "all" ? " active" : ""}`}
          onClick={() => setQuickFilter("all")}
        >
          <div className="stat-value">{stats.total}</div>
          <div className="stat-label"><FileText width={13} height={13} /> Total documents</div>
        </button>
        <button
          type="button"
          className={`stat-card clickable accent-pass${quickFilter === "pass" ? " active" : ""}`}
          onClick={() => toggleQuickFilter("pass")}
        >
          <div className="stat-value">{stats.pass}</div>
          <div className="stat-label"><CheckCircle width={13} height={13} /> Passed</div>
        </button>
        <button
          type="button"
          className={`stat-card clickable accent-fail${quickFilter === "fail" ? " active" : ""}`}
          onClick={() => toggleQuickFilter("fail")}
        >
          <div className="stat-value">{stats.fail}</div>
          <div className="stat-label"><XCircle width={13} height={13} /> Failed</div>
        </button>
        <button
          type="button"
          className={`stat-card clickable accent-warn${quickFilter === "progress" ? " active" : ""}`}
          onClick={() => toggleQuickFilter("progress")}
        >
          <div className="stat-value">{stats.progress}</div>
          <div className="stat-label"><Loader width={13} height={13} /> In progress</div>
        </button>
      </div>

      <div className="panel">
        <p className="section-title"><UploadCloud width={13} height={13} /> Upload a document</p>
        <div
          className={`dropzone${dragActive ? " drag-active" : ""}`}
          onClick={() => fileInputRef.current?.click()}
          onDragOver={(e) => { e.preventDefault(); setDragActive(true); }}
          onDragLeave={() => setDragActive(false)}
          onDrop={handleDrop}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") fileInputRef.current?.click(); }}
        >
          <input
            ref={fileInputRef}
            className="visually-hidden"
            type="file"
            accept=".pdf,.png,.jpg,.jpeg"
            onChange={handleFileInput}
            disabled={uploading}
            tabIndex={-1}
          />
          <div className="dropzone-icon">
            <UploadCloud width={19} height={19} />
          </div>
          <p className="dropzone-title">
            <strong>Click to browse</strong> or drag a file here
          </p>
          <p className="dropzone-hint">PDF, PNG or JPG · scanned or native text</p>
        </div>

        {uploading && (
          <div className="upload-progress">
            <Loader width={14} height={14} className="spin" />
            Uploading &amp; queuing for OCR...
          </div>
        )}
        {uploadedName && !uploading && (
          <div className="upload-toast">
            <CheckCircle width={14} height={14} />
            "{uploadedName}" uploaded — now moving through the pipeline
          </div>
        )}
      </div>

      <div className="panel">
        <div className="panel-head doc-toolbar-head">
          <p className="section-title"><FileText width={13} height={13} /> Documents ({visible.length})</p>
          <div className="doc-toolbar">
            <div className="input-with-icon compact">
              <Search width={13} height={13} />
              <input
                ref={filenameInputRef}
                type="text"
                placeholder="Search filename… (/)"
                value={filters.filename}
                onChange={(e) => setFilters((f) => ({ ...f, filename: e.target.value }))}
              />
            </div>
            <select
              className="compact"
              value={filters.status}
              onChange={(e) => setFilters((f) => ({ ...f, status: e.target.value }))}
            >
              <option value="">All statuses</option>
              {STATUS_OPTIONS.map((s) => (
                <option key={s} value={s}>{s.replaceAll("_", " ")}</option>
              ))}
            </select>
            <input
              className="compact"
              type="date"
              onChange={(e) =>
                setFilters((f) => ({ ...f, from: e.target.value ? `${e.target.value}T00:00:00Z` : "" }))
              }
            />
            {hasFilters && (
              <button
                type="button"
                className="filter-clear"
                onClick={() => setFilters({ status: "", filename: "", from: "" })}
              >
                Clear
              </button>
            )}
            <button type="button" className="ghost icon-btn" onClick={refresh} title="Refresh now">
              <RefreshCw width={13} height={13} />
            </button>
          </div>
        </div>
        {!loaded ? (
          <div>
            <div className="skeleton skeleton-line" style={{ width: "100%" }} />
            <div className="skeleton skeleton-line" style={{ width: "90%" }} />
            <div className="skeleton skeleton-line" style={{ width: "95%" }} />
          </div>
        ) : visible.length === 0 ? (
          <div className="empty-state">
            <Inbox width={30} height={30} />
            <strong>No documents{quickFilter !== "all" ? " in this category" : ""}</strong>
            {hasFilters || quickFilter !== "all" ? "Try clearing filters." : "Upload a PDF or image above to run it through the pipeline."}
          </div>
        ) : (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Filename</th>
                    <th>Status</th>
                    <th>Final Verdict</th>
                    <th>
                      <button
                        type="button"
                        className={`th-sort active ${sortDir}`}
                        onClick={() => setSortDir((d) => (d === "desc" ? "asc" : "desc"))}
                        title="Toggle sort order"
                      >
                        Uploaded <ArrowUpDown width={11} height={11} />
                      </button>
                    </th>
                    <th aria-label="Actions"></th>
                  </tr>
                </thead>
                <tbody>
                  {pageItems.map((doc) => (
                    <tr key={doc.id} className="clickable" onClick={() => navigate(`/documents/${doc.id}`)}>
                      <td className="filename-cell">
                        <FileText width={15} height={15} />
                        {doc.filename}
                      </td>
                      <td><StatusBadge status={doc.status} /></td>
                      <td>{doc.finalVerdict ? <StatusBadge status={doc.finalVerdict} /> : <span className="muted-cell">—</span>}</td>
                      <td className="muted-cell" title={new Date(doc.uploadedAt).toLocaleString()}>
                        <Clock width={12} height={12} style={{ verticalAlign: -2, marginRight: 5 }} />
                        {timeAgo(doc.uploadedAt)}
                      </td>
                      <td>
                        <button
                          type="button"
                          className="row-delete-btn"
                          title="Delete document"
                          onClick={(e) => { e.stopPropagation(); setPendingDelete(doc); }}
                        >
                          <Trash width={14} height={14} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {totalPages > 1 && (
              <div className="pagination">
                <span className="pagination-info">
                  Page {page} of {totalPages} · {visible.length} documents
                </span>
                <div className="pagination-controls">
                  <button
                    type="button"
                    className="page-btn"
                    disabled={page === 1}
                    onClick={() => setPage((p) => Math.max(1, p - 1))}
                  >
                    <ChevronLeft width={13} height={13} />
                  </button>
                  {Array.from({ length: totalPages }, (_, i) => i + 1)
                    .filter((n) => n === 1 || n === totalPages || Math.abs(n - page) <= 1)
                    .reduce((acc, n, i, arr) => {
                      if (i > 0 && n - arr[i - 1] > 1) acc.push("...");
                      acc.push(n);
                      return acc;
                    }, [])
                    .map((n, i) =>
                      n === "..." ? (
                        <span key={`gap-${i}`} style={{ color: "var(--muted-2)", padding: "0 2px" }}>…</span>
                      ) : (
                        <button
                          key={n}
                          type="button"
                          className={`page-btn${n === page ? " active" : ""}`}
                          onClick={() => setPage(n)}
                        >
                          {n}
                        </button>
                      )
                    )}
                  <button
                    type="button"
                    className="page-btn"
                    disabled={page === totalPages}
                    onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                  >
                    <ChevronRight width={13} height={13} />
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      <ConfirmDialog
        open={!!pendingDelete}
        title="Delete this document?"
        message={
          pendingDelete
            ? `"${pendingDelete.filename}" and all its rule results, LLM review, and audit history will be permanently deleted. This can't be undone.`
            : ""
        }
        confirmLabel={deleting ? "Deleting..." : "Delete"}
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  );
}
