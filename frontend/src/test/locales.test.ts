import { describe, expect, it } from "vitest";
import enUS from "../locales/en-US.json";
import jaJP from "../locales/ja-JP.json";
import zhCN from "../locales/zh-CN.json";

function flattenKeys(value: unknown, prefix = ""): string[] {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    return [prefix];
  }
  return Object.entries(value).flatMap(([key, child]) =>
    flattenKeys(child, prefix ? `${prefix}.${key}` : key)
  );
}

describe("locale resources", () => {
  const englishKeys = flattenKeys(enUS).sort();

  it.each([
    ["zh-CN", zhCN],
    ["ja-JP", jaJP]
  ])("%s has the same translation key set", (_language, resources) => {
    expect(flattenKeys(resources).sort()).toEqual(englishKeys);
  });

  it("does not translate stable technical state values", () => {
    expect(Object.keys(enUS.status.certainty).sort()).toEqual(
      Object.keys(zhCN.status.certainty).sort()
    );
    expect(Object.keys(enUS.status.certainty).sort()).toEqual(
      Object.keys(jaJP.status.certainty).sort()
    );
    expect(Object.keys(enUS.fileType).sort()).toEqual(
      Object.keys(jaJP.fileType).sort()
    );
  });
});
