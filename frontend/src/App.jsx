import { Routes, Route, Link } from "react-router-dom";
import DocumentList from "./components/DocumentList.jsx";
import DocumentDetail from "./components/DocumentDetail.jsx";
import { ScanLine } from "./components/Icons.jsx";

export default function App() {
  return (
    <div className="app-shell">
      <header className="app-header">
        <Link to="/" className="brand">
          <span className="brand-mark">
            <ScanLine width={17} height={17} />
          </span>
          <span className="brand-text">
            <h1>Compliance Checker</h1>
            <span>AI-Assisted Document Compliance Dashboard</span>
          </span>
        </Link>
        <span className="header-status">
          <span className="pulse-dot" />
          Live
        </span>
      </header>
      <main className="app-main">
        <Routes>
          <Route path="/" element={<DocumentList />} />
          <Route path="/documents/:id" element={<DocumentDetail />} />
        </Routes>
      </main>
    </div>
  );
}
