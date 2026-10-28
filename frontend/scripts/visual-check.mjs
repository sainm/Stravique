import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright-core";

const baseUrl = process.env.STRAVIQUE_WEB_URL || "http://127.0.0.1:5173";
const apiUrl = process.env.STRAVIQUE_API_URL || "http://127.0.0.1:8080/api/v1";
const chromePath =
  process.env.CHROME_PATH ||
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const outputDirectory = path.resolve(".artifacts/visual");

const projects = await fetch(`${apiUrl}/projects`).then((response) => {
  if (!response.ok) {
    throw new Error(`Project API failed with ${response.status}`);
  }
  return response.json();
});
const project = projects[0];
if (!project) {
  throw new Error("Register and scan a project before running visual checks.");
}

const scanId = project.latestScan?.id;
let firstFileId = "";
if (scanId) {
  const files = await fetch(
    `${apiUrl}/projects/${project.id}/files?scanId=${scanId}&size=200`
  ).then((response) => response.json());
  firstFileId =
    files.content?.find((file) => file.relativePath.endsWith("OrderAction.java"))?.id ||
    files.content?.[0]?.id ||
    "";
}

const createMigrationPlan = async () => {
  const generated = await fetch(
    `${apiUrl}/projects/${project.id}/migration-plans`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ targetProfile: "SPRING_BOOT_MVC" })
    }
  );
  if (!generated.ok) {
    return "";
  }
  const body = await generated.json();
  return body.summary?.id || "";
};

let migrationPlanId = "";
const existingPlans = await fetch(
  `${apiUrl}/projects/${project.id}/migration-plans`
).then((response) => (response.ok ? response.json() : []));
if (existingPlans.length > 0) {
  migrationPlanId = existingPlans[0].id;
  if (
    project.latestScan?.status === "COMPLETED" &&
    existingPlans[0].scanId !== project.latestScan.id
  ) {
    migrationPlanId = (await createMigrationPlan()) || migrationPlanId;
  }
} else if (project.latestScan?.status === "COMPLETED") {
  migrationPlanId = await createMigrationPlan();
}
let transformationTitle = "";
if (migrationPlanId) {
  const migrationItems = await fetch(
    `${apiUrl}/projects/${project.id}/migration-plans/${migrationPlanId}/items?size=200`
  ).then((response) => (response.ok ? response.json() : { content: [] }));
  const component =
    migrationItems.content?.find(
      (item) =>
        item.itemType === "COMPONENT_BEAN" &&
        item.targetKey === "SPRING_DI" &&
        item.title === "OrderService.java"
    ) ||
    migrationItems.content?.find(
      (item) => item.itemType === "COMPONENT_BEAN" && item.targetKey === "SPRING_DI"
    );
  if (component) {
    transformationTitle = component.title;
    const proposals = await fetch(
      `${apiUrl}/projects/${project.id}/migration-plans/${migrationPlanId}/items/${component.id}/transformation-proposals`
    ).then((response) => (response.ok ? response.json() : []));
    if (proposals.length === 0) {
      await fetch(
        `${apiUrl}/projects/${project.id}/migration-plans/${migrationPlanId}/items/${component.id}/transformation-proposals`,
        { method: "POST" }
      );
    }
  }
}
const migrationQuery = migrationPlanId ? `?plan=${migrationPlanId}` : "";
const migrationView = (view) =>
  `/projects/${project.id}/migration${
    migrationQuery ? `${migrationQuery}&view=${view}` : `?view=${view}`
  }`;

const routes = [
  ["projects", "/projects"],
  ["overview", `/projects/${project.id}/overview`],
  [
    "inventory",
    `/projects/${project.id}/inventory${firstFileId ? `?fileId=${firstFileId}` : ""}`
  ],
  ["frameworks", `/projects/${project.id}/frameworks?key=SASTRUTS`],
  ["relations", `/projects/${project.id}/relations`],
  ["relations-map", `/projects/${project.id}/relations?view=map`],
  ["migration", `/projects/${project.id}/migration${migrationQuery}`],
  ["migration-dependencies", migrationView("dependencies")],
  ["migration-review", migrationView("review")],
  ["reports", `/projects/${project.id}/reports`],
  ["settings", `/projects/${project.id}/settings`]
];

const languages = ["en-US", "zh-CN", "ja-JP"];
const viewports = [
  { name: "desktop", width: 1440, height: 960 },
  { name: "mobile", width: 390, height: 844 }
];

await fs.mkdir(outputDirectory, { recursive: true });
const browser = await chromium.launch({
  headless: true,
  executablePath: chromePath,
  args: ["--no-sandbox"]
});
const issues = [];
const consoleErrors = [];

for (const language of languages) {
  for (const viewport of viewports) {
    const context = await browser.newContext({
      viewport: { width: viewport.width, height: viewport.height },
      deviceScaleFactor: 1
    });
    await context.addInitScript((locale) => {
      localStorage.setItem("stravique.language", locale);
    }, language);
    const page = await context.newPage();
    page.on("console", (message) => {
      if (message.type() === "error") {
        consoleErrors.push({
          language,
          viewport: viewport.name,
          message: message.text()
        });
      }
    });
    page.on("pageerror", (error) => {
      consoleErrors.push({
        language,
        viewport: viewport.name,
        message: error.message
      });
    });

    const routesForViewport =
      viewport.name === "desktop"
        ? routes
        : routes.filter(([name]) =>
            [
              "projects",
              "overview",
              "inventory",
              "relations",
              "relations-map",
              "migration",
              "settings"
            ].includes(name)
          );

    for (const [name, route] of routesForViewport) {
      await page.goto(`${baseUrl}${route}`, { waitUntil: "networkidle" });
      await page.locator("h1").first().waitFor({ state: "visible" });
      await page.waitForTimeout(120);

      const metrics = await page.evaluate(() => {
        const viewportWidth = document.documentElement.clientWidth;
        const overflow = document.documentElement.scrollWidth - viewportWidth;
        const clippedButtons = Array.from(
          document.querySelectorAll(".button, .mode-switch button, .format-button")
        )
          .filter((element) => {
            const rect = element.getBoundingClientRect();
            return (
              rect.left < -1 ||
              rect.right > viewportWidth + 1 ||
              element.scrollWidth > element.clientWidth + 2
            );
          })
          .map((element) => ({
            text: element.textContent?.trim(),
            className: element.className
          }));
        const h1 = document.querySelector("h1")?.getBoundingClientRect();
        return {
          overflow,
          clippedButtons,
          heroWithinViewport: !h1 || (h1.left >= 0 && h1.right <= viewportWidth + 1)
        };
      });
      if (metrics.overflow > 1 || metrics.clippedButtons.length > 0 || !metrics.heroWithinViewport) {
        issues.push({ language, viewport: viewport.name, route: name, ...metrics });
      }
      if (name === "inventory" && firstFileId) {
        const relationCount = await page.locator(".relation-card").count();
        if (relationCount === 0) {
          issues.push({
            language,
            viewport: viewport.name,
            route: name,
            relationCards: 0
          });
        }
      }
      if (name === "relations") {
        const relationRows = await page.locator(".relation-table-row").count();
        if (relationRows === 0) {
          issues.push({
            language,
            viewport: viewport.name,
            route: name,
            relationRows: 0
          });
        }
      }
      if (name === "relations-map") {
        const painted = await page.evaluate(() => {
          const canvas = document.querySelector(".relation-canvas-surface");
          if (!(canvas instanceof HTMLCanvasElement)) {
            return { found: false, painted: 0 };
          }
          const context = canvas.getContext("2d");
          if (!context || canvas.width === 0 || canvas.height === 0) {
            return { found: true, painted: 0 };
          }
          const data = context.getImageData(0, 0, canvas.width, canvas.height).data;
          let count = 0;
          for (let index = 3; index < data.length; index += 4) {
            if (data[index] !== 0) {
              count += 1;
            }
          }
          return { found: true, painted: count };
        });
        if (!painted.found || painted.painted < 200) {
          issues.push({
            language,
            viewport: viewport.name,
            route: name,
            graphCanvas: painted.found,
            paintedPixels: painted.painted
          });
        }
      }
      if (name === "migration" && migrationPlanId) {
        const migrationRows = await page.locator(".migration-row").count();
        if (migrationRows === 0) {
          issues.push({
            language,
            viewport: viewport.name,
            route: name,
            migrationRows: 0
          });
        }
      }
      if (name === "migration-review" && migrationPlanId) {
        const reviewRows = await page.locator(".migration-row").count();
        if (reviewRows === 0) {
          issues.push({
            language,
            viewport: viewport.name,
            route: name,
            reviewRows: 0
          });
        }
      }
      if (name === "migration-dependencies") {
        const painted = await page.evaluate(() => {
          const canvas = document.querySelector(".migration-map canvas");
          if (!(canvas instanceof HTMLCanvasElement)) {
            return { found: false, painted: 0 };
          }
          const context = canvas.getContext("2d");
          if (!context || canvas.width === 0 || canvas.height === 0) {
            return { found: true, painted: 0 };
          }
          const data = context.getImageData(0, 0, canvas.width, canvas.height).data;
          let count = 0;
          for (let index = 3; index < data.length; index += 4) {
            if (data[index] !== 0) {
              count += 1;
            }
          }
          return { found: true, painted: count };
        });
        if (!painted.found || painted.painted < 200) {
          issues.push({
            language,
            viewport: viewport.name,
            route: name,
            migrationGraph: painted.found,
            paintedPixels: painted.painted
          });
        }
      }

      if (name === "migration" && transformationTitle) {
        const transformationRow = page
          .locator(".migration-row-open")
          .filter({ hasText: transformationTitle })
          .first();
        if ((await transformationRow.count()) > 0) {
          await transformationRow.click();
          await page.locator(".modal-card").waitFor({ state: "visible" });
          await page.locator(".migration-transformation").waitFor({ state: "visible" });
          await page
            .locator(".migration-diff")
            .first()
            .waitFor({ state: "visible", timeout: 5000 })
            .catch(() => {});
          const diffVisible = await page.locator(".migration-diff").count();
          if (diffVisible === 0) {
            issues.push({
              language,
              viewport: viewport.name,
              route: "migration-inspector",
              transformationDiff: 0
            });
          }
          await page.waitForTimeout(250);
          await page.screenshot({
            path: path.join(
              outputDirectory,
              `${language}-${viewport.name}-migration-inspector.png`
            ),
            fullPage: false
          });
          await page.keyboard.press("Escape");
          await page.locator(".modal-card").waitFor({ state: "hidden" });
        }
      }

      const filename = `${language}-${viewport.name}-${name}.png`;
      await page.screenshot({
        path: path.join(outputDirectory, filename),
        fullPage: true
      });
    }
    await context.close();
  }
}

await browser.close();
await fs.writeFile(
  path.join(outputDirectory, "report.json"),
  JSON.stringify({ issues, consoleErrors }, null, 2)
);

console.log(
  JSON.stringify(
    {
      screenshots: (await fs.readdir(outputDirectory)).filter((file) =>
        file.endsWith(".png")
      ).length,
      issues,
      consoleErrors
    },
    null,
    2
  )
);

if (issues.length > 0 || consoleErrors.length > 0) {
  process.exitCode = 1;
}
