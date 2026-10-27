import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import LanguageSwitcher from "../components/LanguageSwitcher";
import i18n from "../lib/i18n";

describe("LanguageSwitcher", () => {
  it("changes the active locale and persists the selection", async () => {
    const user = userEvent.setup();
    render(<LanguageSwitcher />);

    await user.selectOptions(screen.getByRole("combobox"), "ja-JP");

    expect(i18n.resolvedLanguage).toBe("ja-JP");
    expect(localStorage.getItem("stravique.language")).toBe("ja-JP");
    expect(document.documentElement.lang).toBe("ja-JP");
  });
});
