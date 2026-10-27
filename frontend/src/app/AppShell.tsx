import { useQuery } from "@tanstack/react-query";
import {
  Boxes,
  ChevronDown,
  FileSearch2,
  FolderKanban,
  Gauge,
  Menu,
  PanelLeft,
  ScrollText,
  Settings2,
  ShieldCheck,
  X
} from "lucide-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { NavLink, Outlet, useLocation, useNavigate, useParams } from "react-router-dom";
import { api } from "../lib/api";
import LanguageSwitcher from "../components/LanguageSwitcher";

export default function AppShell() {
  const { t } = useTranslation();
  const { projectId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);
  const projects = useQuery({
    queryKey: ["projects"],
    queryFn: api.projects
  });
  const project = projects.data?.find((item) => item.id === projectId);

  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname]);

  const nav = [
    { suffix: "overview", label: t("nav.overview"), icon: Gauge },
    { suffix: "inventory", label: t("nav.inventory"), icon: FileSearch2 },
    { suffix: "frameworks", label: t("nav.frameworks"), icon: Boxes },
    { suffix: "reports", label: t("nav.reports"), icon: ScrollText },
    { suffix: "settings", label: t("nav.settings"), icon: Settings2 }
  ];

  const sidebar = (
    <aside className={`app-rail ${mobileOpen ? "rail-open" : ""}`}>
      <div className="brand-row">
        <NavLink to="/projects" className="brand">
          <span className="brand-mark" aria-hidden="true">
            <span>S</span>
          </span>
          <span className="brand-copy">
            <strong>Stravique</strong>
            <small>{t("brand.tagline")}</small>
          </span>
        </NavLink>
        <button
          className="rail-close"
          onClick={() => setMobileOpen(false)}
          aria-label={t("common.close")}
        >
          <X size={18} />
        </button>
      </div>

      {project ? (
        <div className="project-switcher">
          <span className="rail-label">{t("project.current")}</span>
          <label className="project-select">
            <select
              value={project.id}
              onChange={(event) =>
                navigate(`/projects/${event.target.value}/overview`)
              }
              aria-label={t("project.switch")}
            >
              {projects.data?.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
            <ChevronDown size={14} aria-hidden="true" />
          </label>
        </div>
      ) : null}

      <nav className="rail-nav" aria-label={t("nav.primary")}>
        {projectId ? (
          <>
            <span className="rail-label">{t("nav.workspace")}</span>
            {nav.map((item) => {
              const Icon = item.icon;
              return (
                <NavLink
                  key={item.suffix}
                  to={`/projects/${projectId}/${item.suffix}`}
                  className={({ isActive }) =>
                    `rail-link ${isActive ? "rail-link-active" : ""}`
                  }
                >
                  <Icon size={17} strokeWidth={1.8} />
                  <span>{item.label}</span>
                </NavLink>
              );
            })}
          </>
        ) : null}
        <NavLink
          to="/projects"
          end
          className={({ isActive }) =>
            `rail-link rail-link-secondary ${isActive ? "rail-link-active" : ""}`
          }
        >
          <FolderKanban size={17} strokeWidth={1.8} />
          <span>{t("nav.projects")}</span>
        </NavLink>
      </nav>

      <div className="rail-footer">
        <div className="safety-note">
          <ShieldCheck size={16} />
          <span>{t("brand.readOnly")}</span>
        </div>
        <LanguageSwitcher />
      </div>
    </aside>
  );

  return (
    <div className="app-shell">
      <div className="mobile-bar">
        <button
          className="icon-button"
          onClick={() => setMobileOpen(true)}
          aria-label={t("nav.openMenu")}
        >
          <Menu size={19} />
        </button>
        <NavLink to="/projects" className="mobile-brand">
          <span className="brand-mark brand-mark-small">
            <span>S</span>
          </span>
          Stravique
        </NavLink>
        <LanguageSwitcher compact />
      </div>
      {mobileOpen ? (
        <button
          className="rail-scrim"
          aria-label={t("common.close")}
          onClick={() => setMobileOpen(false)}
        />
      ) : null}
      {sidebar}
      <main className="app-main">
        {project ? (
          <div className="context-bar">
            <div className="context-path">
              <PanelLeft size={14} />
              <span>{project.name}</span>
              <span className="context-separator">/</span>
              <code>{project.sourcePath}</code>
            </div>
            <span className="read-only-pill">
              <ShieldCheck size={13} />
              {t("brand.readOnly")}
            </span>
          </div>
        ) : null}
        <div className="page-shell">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
