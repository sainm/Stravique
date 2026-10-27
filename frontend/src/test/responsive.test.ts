import { describe, expect, it } from "vitest";
import fs from "node:fs";
import path from "node:path";

describe("responsive design contract", () => {
  it("defines mobile layout rules for navigation, explorer, settings, and reports", () => {
    const css = fs.readFileSync(
      path.resolve(process.cwd(), "src/styles/app.css"),
      "utf8"
    );
    const tokens = fs.readFileSync(
      path.resolve(process.cwd(), "src/styles/tokens.css"),
      "utf8"
    );

    expect(css).toContain("@media (max-width: 820px)");
    expect(css).toContain(".app-rail");
    expect(css).toContain(".inspector-open");
    expect(css).toContain(".report-row");
    expect(css).toContain(".settings-grid");
    expect(tokens).toContain("prefers-reduced-motion");
  });
});
