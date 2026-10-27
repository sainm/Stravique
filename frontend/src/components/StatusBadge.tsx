import { useTranslation } from "react-i18next";

interface StatusBadgeProps {
  value?: string;
  kind?: "scan" | "certainty" | "file" | "parse" | "change";
  label?: string;
}

export default function StatusBadge({
  value,
  kind = "scan",
  label
}: StatusBadgeProps) {
  const { t } = useTranslation();
  if (!value) {
    return null;
  }
  const tone = toneFor(value);
  const translated = label || t(`status.${kind}.${value}`, { defaultValue: value });
  return (
    <span className={`status-badge status-${tone}`} data-value={value}>
      <span className="status-dot" aria-hidden="true" />
      {translated}
    </span>
  );
}

function toneFor(value: string): string {
  if (
    ["COMPLETED", "ANALYZED", "PARSED", "UNCHANGED"].includes(value)
  ) {
    return "success";
  }
  if (
    ["FAILED", "PARSE_FAILED", "VALIDATION_FAILED"].includes(value)
  ) {
    return "danger";
  }
  if (
    ["REVIEW_REQUIRED", "INFERRED", "UNRESOLVED", "CANCEL_REQUESTED", "PARTIAL"].includes(
      value
    )
  ) {
    return "warning";
  }
  if (
    ["RUNNING", "QUEUED", "ADDED", "MODIFIED", "IDENTIFIED"].includes(value)
  ) {
    return "accent";
  }
  return "neutral";
}
