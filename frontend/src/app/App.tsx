import { Navigate, Route, Routes } from "react-router-dom";
import AppShell from "./AppShell";
import ProjectsPage from "../features/projects/ProjectsPage";
import OverviewPage from "../features/overview/OverviewPage";
import InventoryPage from "../features/inventory/InventoryPage";
import FrameworksPage from "../features/frameworks/FrameworksPage";
import ReportsPage from "../features/reports/ReportsPage";
import SettingsPage from "../features/settings/SettingsPage";
import RelationsPage from "../features/relations/RelationsPage";

export default function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/" element={<Navigate to="/projects" replace />} />
        <Route path="/projects" element={<ProjectsPage />} />
        <Route path="/projects/:projectId" element={<Navigate to="overview" replace />} />
        <Route path="/projects/:projectId/overview" element={<OverviewPage />} />
        <Route path="/projects/:projectId/inventory" element={<InventoryPage />} />
        <Route path="/projects/:projectId/frameworks" element={<FrameworksPage />} />
        <Route path="/projects/:projectId/relations" element={<RelationsPage />} />
        <Route path="/projects/:projectId/reports" element={<ReportsPage />} />
        <Route path="/projects/:projectId/settings" element={<SettingsPage />} />
        <Route path="*" element={<Navigate to="/projects" replace />} />
      </Route>
    </Routes>
  );
}
