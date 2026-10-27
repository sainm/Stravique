import { LoaderCircle } from "lucide-react";
import { useTranslation } from "react-i18next";

export default function LoadingBlock() {
  const { t } = useTranslation();
  return (
    <div className="loading-block" aria-live="polite">
      <LoaderCircle className="spin" size={20} />
      <span>{t("common.loading")}</span>
    </div>
  );
}
