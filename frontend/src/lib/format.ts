export function formatDate(value: string | undefined, language: string): string {
  if (!value) {
    return "—";
  }
  return new Intl.DateTimeFormat(language, {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}

export function formatNumber(value: number, language: string): string {
  return new Intl.NumberFormat(language).format(value);
}

export function formatBytes(value: number, language: string): string {
  if (value < 1024) {
    return `${formatNumber(value, language)} B`;
  }
  const units = ["KB", "MB", "GB"];
  let size = value / 1024;
  let unitIndex = 0;
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }
  return `${new Intl.NumberFormat(language, {
    maximumFractionDigits: 1
  }).format(size)} ${units[unitIndex]}`;
}

export function formatDuration(start: string | undefined, end: string | undefined): string {
  if (!start || !end) {
    return "—";
  }
  const milliseconds = Math.max(0, new Date(end).getTime() - new Date(start).getTime());
  if (milliseconds < 1000) {
    return `${milliseconds} ms`;
  }
  return `${(milliseconds / 1000).toFixed(1)} s`;
}
