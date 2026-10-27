import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import enUS from "../locales/en-US.json";
import jaJP from "../locales/ja-JP.json";
import zhCN from "../locales/zh-CN.json";

const supported = ["en-US", "zh-CN", "ja-JP"];
const configured = import.meta.env.VITE_DEFAULT_LOCALE || "en-US";
const stored = localStorage.getItem("stravique.language");
const initial = stored && supported.includes(stored)
  ? stored
  : supported.includes(configured)
    ? configured
    : "en-US";

i18n.use(initReactI18next).init({
  resources: {
    "en-US": { translation: enUS },
    "zh-CN": { translation: zhCN },
    "ja-JP": { translation: jaJP }
  },
  lng: initial,
  fallbackLng: "en-US",
  supportedLngs: supported,
  interpolation: {
    escapeValue: false
  },
  returnNull: false
});

document.documentElement.lang = initial;

export default i18n;
