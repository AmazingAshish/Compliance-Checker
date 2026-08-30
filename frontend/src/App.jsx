import { Routes, Route, Link } from "react-router-dom";
import DocumentList from "./components/DocumentList.jsx";
import DocumentDetail from "./components/DocumentDetail.jsx";

export default function App() {
  return (
    <div className="app-shell">
      <div className="app-header">
        <h1>
          <Link to="/" style={{ color: "inherit", textDecoration: "none" }}>
            Compliance Checker
          </Link>
        </h1>
        <span style={{ fontSize: 12, color: "#64748b" }}>
          AI-Assisted Document Compliance Dashboard
        </span>
      </div>
      <Routes>
        <Route path="/" element={<DocumentList />} />
        <Route path="/documents/:id" element={<DocumentDetail />} />
      </Routes>
    </div>
  );
}
