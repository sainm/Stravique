import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import StatusBadge from "../components/StatusBadge";
import i18n from "../lib/i18n";

describe("StatusBadge", () => {
  it.each([
    ["en-US", "Review required"],
    ["zh-CN", "需要确认"],
    ["ja-JP", "要確認"]
  ])("localizes the label while retaining the technical value in %s", async (language, label) => {
    await i18n.changeLanguage(language);
    const { container } = render(
      <StatusBadge value="REVIEW_REQUIRED" kind="certainty" />
    );
    expect(screen.getByText(label)).toBeInTheDocument();
    expect(container.querySelector("[data-value]")).toHaveAttribute(
      "data-value",
      "REVIEW_REQUIRED"
    );
  });
});
