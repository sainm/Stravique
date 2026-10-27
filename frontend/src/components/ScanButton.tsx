import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Play, RefreshCw } from "lucide-react";
import { useTranslation } from "react-i18next";
import { api } from "../lib/api";

interface ScanButtonProps {
  projectId: string;
  active?: boolean;
  compact?: boolean;
  label?: string;
}

export default function ScanButton({
  projectId,
  active = false,
  compact = false,
  label
}: ScanButtonProps) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: () => api.startScan(projectId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["project", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["overview", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["scans", projectId] })
      ]);
    }
  });
  const text = label || t(active ? "common.scanAgain" : "common.scanProject");
  return (
    <div className="scan-button-wrap">
      <button
        className={`button button-primary ${compact ? "button-small" : ""}`}
        disabled={active || mutation.isPending}
        onClick={() => mutation.mutate()}
      >
        {active ? <RefreshCw className="spin" size={16} /> : <Play size={15} />}
        {active ? t("common.scanning") : mutation.isPending ? t("common.starting") : text}
      </button>
      {mutation.error ? (
        <span className="inline-error">{mutation.error.message}</span>
      ) : null}
    </div>
  );
}
