const API_BASE = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api";

async function handle(response) {
  if (!response.ok) {
    const body = await response.text().catch(() => "");
    throw new Error(`Request failed (${response.status}): ${body}`);
  }
  return response.status === 204 ? null : response.json();
}

export function listDocuments(filters = {}) {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value) params.set(key, value);
  });
  const query = params.toString();
  return fetch(`${API_BASE}/documents${query ? `?${query}` : ""}`).then(handle);
}

export function getDocument(id) {
  return fetch(`${API_BASE}/documents/${id}`).then(handle);
}

export function uploadDocument(file) {
  const formData = new FormData();
  formData.append("file", file);
  return fetch(`${API_BASE}/documents/upload`, {
    method: "POST",
    body: formData,
  }).then(handle);
}

export function deleteDocument(id) {
  return fetch(`${API_BASE}/documents/${id}`, { method: "DELETE" }).then(handle);
}
