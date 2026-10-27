import { Languages } from "lucide-react";
import { useTranslation } from "react-i18next";

const languages = [
  { code: "en-US", labelKey: "language.en" },
  { code: "zh-CN", labelKey: "language.zh" },
  { code: "ja-JP", labelKey: "language.ja" }
];

export default function LanguageSwitcher({ compact = false }: { compact?: boolean }) {
  const { i18n, t } = useTranslation();
  const changeLanguage = async (language: string) => {
    localStorage.setItem("stravique.language", language);
    await i18n.changeLanguage(language);
    document.documentElement.lang = language;
  };

  return (
    <label className={`language-switcher ${compact ? "language-compact" : ""}`}>
      <Languages size={15} strokeWidth={1.8} aria-hidden="true" />
      <span className="sr-only">{t("language.label")}</span>
      <select
        aria-label={t("language.label")}
        value={i18n.resolvedLanguage || i18n.language}
        onChange={(event) => changeLanguage(event.target.value)}
      >
        {languages.map((language) => (
          <option key={language.code} value={language.code}>
            {t(language.labelKey)}
          </option>
        ))}
      </select>
    </label>
  );
}
