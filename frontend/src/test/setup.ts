import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";
import i18n from "../lib/i18n";

afterEach(() => {
  cleanup();
});

beforeEach(async () => {
  await i18n.changeLanguage("en-US");
});
