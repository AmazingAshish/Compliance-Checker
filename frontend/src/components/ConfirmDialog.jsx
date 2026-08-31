import { useEffect } from "react";
import { AlertTriangle } from "./Icons.jsx";

export default function ConfirmDialog({ open, title, message, confirmLabel = "Delete", onConfirm, onCancel, danger = true }) {
  useEffect(() => {
    if (!open) return;
    function handleKey(e) {
      if (e.key === "Escape") onCancel();
    }
    window.addEventListener("keydown", handleKey);
    return () => window.removeEventListener("keydown", handleKey);
  }, [open, onCancel]);

  if (!open) return null;

  return (
    <div className="dialog-overlay" onClick={onCancel}>
      <div className="dialog" role="alertdialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
        <div className="dialog-icon"><AlertTriangle width={18} height={18} /></div>
        <h3 className="dialog-title">{title}</h3>
        <p className="dialog-message">{message}</p>
        <div className="dialog-actions">
          <button type="button" className="ghost" onClick={onCancel}>Cancel</button>
          <button type="button" className={danger ? "danger" : ""} onClick={onConfirm}>{confirmLabel}</button>
        </div>
      </div>
    </div>
  );
}
