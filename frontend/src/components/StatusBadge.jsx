export default function StatusBadge({ status }) {
  if (!status) return null;
  const className = `badge badge-${status.toLowerCase()}`;
  return <span className={className}>{status.replaceAll("_", " ")}</span>;
}
