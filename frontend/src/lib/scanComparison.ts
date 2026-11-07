import type { Scan, ScanComparisonRow } from "./types";

export const SCAN_COMPARISON_STATUSES = [
  "ADDED",
  "REMOVED",
  "CHANGED",
  "UNKNOWN"
] as const;

export type ScanComparisonStatus = (typeof SCAN_COMPARISON_STATUSES)[number];

export interface ScanComparisonGroup {
  status: ScanComparisonStatus;
  rows: ScanComparisonRow[];
}

export function groupScanComparisonRows(
  rows: ScanComparisonRow[]
): ScanComparisonGroup[] {
  return SCAN_COMPARISON_STATUSES.map((status) => ({
    status,
    rows: rows.filter((row) => row.status === status)
  })).filter((group) => group.rows.length > 0);
}

export function completedScans(scans: Scan[]): Scan[] {
  return scans
    .filter((scan) => scan.status === "COMPLETED")
    .sort((left, right) => right.sequence - left.sequence);
}

export function scanComparisonBaseOptions(
  scans: Scan[],
  currentScanId: string
): Scan[] {
  return completedScans(scans).filter((scan) => scan.id !== currentScanId);
}

export function defaultScanComparisonCurrent(
  scans: Scan[],
  preferredScanId?: string
): string {
  const completed = completedScans(scans);
  if (preferredScanId && completed.some((scan) => scan.id === preferredScanId)) {
    return preferredScanId;
  }
  return completed[0]?.id ?? "";
}

export function defaultScanComparisonBase(
  scans: Scan[],
  currentScanId: string
): string {
  const current = scans.find((scan) => scan.id === currentScanId);
  if (!current) {
    return "";
  }
  const options = scanComparisonBaseOptions(scans, currentScanId);
  const previous = options.find((scan) => scan.sequence < current.sequence);
  return previous?.id ?? options[0]?.id ?? "";
}

export function shortHash(value?: string): string {
  if (!value) {
    return "";
  }
  return value.slice(0, 12);
}
