import { AlertTriangle, RotateCcw } from "lucide-react";
import { useTranslation } from "react-i18next";

interface ErrorPanelProps {
  error: unknown;
  onRetry?: () => void;
}

export default function ErrorPanel({ error, onRetry }: ErrorPanelProps) {
  const { t } = useTranslation();
  const message =
    error instanceof Error ? error.message : t("common.unexpectedError");
  return (
    <div className="error-panel" role="alert">
      <AlertTriangle size={19} />
      <div>
        <strong>{t("common.requestFailed")}</strong>
        <p>{message}</p>
      </div>
      {onRetry ? (
        <button className="button button-ghost button-small" onClick={onRetry}>
          <RotateCcw size={14} />
          {t("common.retry")}
        </button>
      ) : null}
    </div>
  );
}
