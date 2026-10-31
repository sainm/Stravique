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
let validationItemId = "";
let formItemId = "";
let jakartaItemId = "";
let dataItemId = "";
let transactionItemId = "";
let controllerItemId = "";
let messageItemId = "";
let jspItemId = "";
let listJspItemId = "";
let webXmlItemId = "";
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
  const validation = migrationItems.content?.find(
    (item) => item.itemType === "VALIDATION" && item.title === "OrderAction.java"
  );
  if (validation) {
    validationItemId = validation.id;
    const proposals = await fetch(
      `${apiUrl}/projects/${project.id}/migration-plans/${migrationPlanId}/items/${validation.id}/transformation-proposals`
    ).then((response) => (response.ok ? response.json() : []));
    if (proposals.length === 0) {
      await fetch(
        `${apiUrl}/projects/${project.id}/migration-plans/${migrationPlanId}/items/${validation.id}/transformation-proposals`,
        { method: "POST" }
      );
    }
  }
  const form = migrationItems.content?.find(
    (item) => item.itemType === "FORM_MODEL" && item.title === "OrderForm.java"
  );
  if (form) {
    formItemId = form.id;
    const proposals = await fetch(
      `${apiUrl}/projects/${project.id}/migration-plans/${migrationPlanId}/items/${form.id}/transformation-proposals`
    ).then((response) => (response.ok ? response.json() : []));
    if (proposals.length === 0) {
      await fetch(
        `${apiUrl}/projects/${project.id}/migration-plans/${migrationPlanId}/items/${form.id}/transformation-proposals`,
        { method: "POST" }
      );
    }
  }
  const data = migrationItems.content?.find(
    (item) =>
      item.itemType === "DATA_ACCESS" &&
      item.targetKey === "SPRING_JDBC" &&
      item.title === "OrderQueryDao.java"
  );
  if (data) {
    dataItemId = data.id;
    const proposals = await fetch(
      `${apiUrl}/projects/${project.id}/migration-plans/${migrationPlanId}/items/${data.id}/transformation-proposals`
    ).then((response) => (response.ok ? response.json() : []));
    if (proposals.length === 0) {
      await fetch(
        `${apiUrl}/projects/${project.id}/migration-plans/${migrationPlanId}/items/${data.id}/transformation-proposals`,
        { method: "POST" }
      );
    }
  }
  const transaction = migrationItems.content?.find(
    (item) => item.itemType === "TRANSACTION" && item.targetKey === "SPRING_JDBC"
  );
  if (transaction) {
    transactionItemId = transaction.id;
    const proposals = await fetch(
      `${apiUrl}/projects/${project.id}/migration-plans/${migrationPlanId}/items/${transaction.id}/transformation-proposals`
    ).then((response) => (response.ok ? response.json() : []));
    if (proposals.length === 0) {
      await fetch(
        `${apiUrl}/projects/${project.id}/migration-plans/${migrationPlanId}/items/${transaction.id}/transformation-proposals`,
        { method: "POST" }
      );
    }
  }
  const jakarta = migrationItems.content?.find(
    (item) =>
      item.itemType === "JAKARTA_NAMESPACE" &&
      item.targetKey === "JAKARTA_EE" &&
      item.title === "Order.java"
  );
  if (jakarta) {
    jakartaItemId = jakarta.id;
    const proposals = await fetch(
      `${apiUrl}/projects/${project.id}/migration-plans/${migrationPlanId}/items/${jakarta.id}/transformation-proposals`
    ).then((response) => (response.ok ? response.json() : []));
    if (proposals.length === 0) {
      await fetch(
        `${apiUrl}/projects/${project.id}/migration-plans/${migrationPlanId}/items/${jakarta.id}/transformation-proposals`,
        { method: "POST" }
      );
    }
  }
  const controller = migrationItems.content?.find(
    (item) =>
      item.itemType === "ACTION_CONTROLLER" &&
      item.targetKey === "SPRING_MVC" &&
      item.title === "OrderAction.java"
  );
  if (controller) {
    controllerItemId = controller.id;
    const proposals = await fetch(
      `${apiUrl}/projects/${project.id}/migration-plans/${migrationPlanId}/items/${controller.id}/transformation-proposals`
    ).then((response) => (response.ok ? response.json() : []));
    if (proposals.length === 0) {
      await fetch(
        `${apiUrl}/projects/${project.id}/migration-plans/${migrationPlanId}/items/${controller.id}/transformation-proposals`,
        { method: "POST" }
      );
    }
  }
  const messageResource = migrationItems.content?.find(
    (item) =>
      item.itemType === "MESSAGE_RESOURCE" &&
      item.targetKey === "SPRING_MESSAGES" &&
      item.title === "messages.properties"
  );
  if (messageResource) {
    messageItemId = messageResource.id;
    const proposals = await fetch(
      `${apiUrl}/projects/${project.id}/migration-plans/${migrationPlanId}/items/${messageResource.id}/transformation-proposals`
    ).then((response) => (response.ok ? response.json() : []));
    if (proposals.length === 0) {
      await fetch(
        `${apiUrl}/projects/${project.id}/migration-plans/${migrationPlanId}/items/${messageResource.id}/transformation-proposals`,
        { method: "POST" }
      );
    }
  }
  const jspView = migrationItems.content?.find(
    (item) =>
      item.itemType === "JSP_VIEW" &&
      item.targetKey === "KEEP_JSP" &&
      item.title === "edit.jsp"
  );
  if (jspView) {
    jspItemId = jspView.id;
    const proposals = await fetch(
      `${apiUrl}/projects/${project.id}/migration-plans/${migrationPlanId}/items/${jspView.id}/transformation-proposals`
    ).then((response) => (response.ok ? response.json() : []));
    if (proposals.length === 0) {
      await fetch(
        `${apiUrl}/projects/${project.id}/migration-plans/${migrationPlanId}/items/${jspView.id}/transformation-proposals`,
        { method: "POST" }
      );
    }
  }
  const listJspView = migrationItems.content?.find(
    (item) =>
      item.itemType === "JSP_VIEW" &&
      item.targetKey === "KEEP_JSP" &&
      item.title === "list.jsp"
  );
  if (listJspView) {
    listJspItemId = listJspView.id;
    const proposals = await fetch(
      `${apiUrl}/projects/${project.id}/migration-plans/${migrationPlanId}/items/${listJspView.id}/transformation-proposals`
    ).then((response) => (response.ok ? response.json() : []));
    if (proposals.length === 0) {
      await fetch(
        `${apiUrl}/projects/${project.id}/migration-plans/${migrationPlanId}/items/${listJspView.id}/transformation-proposals`,
        { method: "POST" }
      );
    }
  }
  const webXml = migrationItems.content?.find(
    (item) =>
      item.itemType === "CONFIGURATION" &&
      item.targetKey === "SPRING_BOOT_CONFIG" &&
      item.title === "web.xml"
  );
  if (webXml) {
    webXmlItemId = webXml.id;
    const proposals = await fetch(
      `${apiUrl}/projects/${project.id}/migration-plans/${migrationPlanId}/items/${webXml.id}/transformation-proposals`
    ).then((response) => (response.ok ? response.json() : []));
    if (proposals.length === 0) {
      await fetch(
        `${apiUrl}/projects/${project.id}/migration-plans/${migrationPlanId}/items/${webXml.id}/transformation-proposals`,
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
      if (name === "migration" && validationItemId) {
        const validationRow = page
          .locator(`[data-item-id="${validationItemId}"] .migration-row-open`)
          .first();
        if ((await validationRow.count()) > 0) {
          await validationRow.click();
          await page.locator(".modal-card").waitFor({ state: "visible" });
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
              route: "migration-validation-inspector",
              transformationDiff: 0
            });
          }
          await page.waitForTimeout(250);
          await page.screenshot({
            path: path.join(
              outputDirectory,
              `${language}-${viewport.name}-migration-validation-inspector.png`
            ),
            fullPage: false
          });
          await page.keyboard.press("Escape");
          await page.locator(".modal-card").waitFor({ state: "hidden" });
        }
      }
      if (name === "migration" && formItemId) {
        const formRow = page
          .locator(`[data-item-id="${formItemId}"] .migration-row-open`)
          .first();
        if ((await formRow.count()) > 0) {
          await formRow.click();
          await page.locator(".modal-card").waitFor({ state: "visible" });
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
              route: "migration-form-inspector",
              transformationDiff: 0
            });
          }
          await page.waitForTimeout(250);
          await page.screenshot({
            path: path.join(
              outputDirectory,
              `${language}-${viewport.name}-migration-form-inspector.png`
            ),
            fullPage: false
          });
          await page.keyboard.press("Escape");
          await page.locator(".modal-card").waitFor({ state: "hidden" });
        }
      }
      if (name === "migration" && dataItemId) {
        const dataRow = page
          .locator(`[data-item-id="${dataItemId}"] .migration-row-open`)
          .first();
        if ((await dataRow.count()) > 0) {
          await dataRow.click();
          await page.locator(".modal-card").waitFor({ state: "visible" });
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
              route: "migration-data-inspector",
              transformationDiff: 0
            });
          }
          await page.waitForTimeout(250);
          await page.screenshot({
            path: path.join(
              outputDirectory,
              `${language}-${viewport.name}-migration-data-inspector.png`
            ),
            fullPage: false
          });
          await page.keyboard.press("Escape");
          await page.locator(".modal-card").waitFor({ state: "hidden" });
        }
      }
      if (name === "migration" && transactionItemId) {
        const transactionRow = page
          .locator(`[data-item-id="${transactionItemId}"] .migration-row-open`)
          .first();
        if ((await transactionRow.count()) > 0) {
          await transactionRow.click();
          await page.locator(".modal-card").waitFor({ state: "visible" });
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
              route: "migration-transaction-inspector",
              transformationDiff: 0
            });
          }
          await page.waitForTimeout(250);
          await page.screenshot({
            path: path.join(
              outputDirectory,
              `${language}-${viewport.name}-migration-transaction-inspector.png`
            ),
            fullPage: false
          });
          await page.keyboard.press("Escape");
          await page.locator(".modal-card").waitFor({ state: "hidden" });
        }
      }
      if (name === "migration" && jakartaItemId) {
        const jakartaRow = page
          .locator(`[data-item-id="${jakartaItemId}"] .migration-row-open`)
          .first();
        if ((await jakartaRow.count()) > 0) {
          await jakartaRow.click();
          await page.locator(".modal-card").waitFor({ state: "visible" });
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
              route: "migration-jakarta-inspector",
              transformationDiff: 0
            });
          }
          await page.waitForTimeout(250);
          await page.screenshot({
            path: path.join(
              outputDirectory,
              `${language}-${viewport.name}-migration-jakarta-inspector.png`
            ),
            fullPage: false
          });
          await page.keyboard.press("Escape");
          await page.locator(".modal-card").waitFor({ state: "hidden" });
        }
      }
      if (name === "migration" && controllerItemId) {
        const controllerRow = page
          .locator(`[data-item-id="${controllerItemId}"] .migration-row-open`)
          .first();
        if ((await controllerRow.count()) > 0) {
          await controllerRow.click();
          await page.locator(".modal-card").waitFor({ state: "visible" });
          await page.locator(".migration-transformation").waitFor({ state: "visible" });
          await page
            .locator(".migration-diff")
            .first()
            .waitFor({ state: "visible", timeout: 5000 })
            .catch(() => {});
          const diffVisible = await page.locator(".migration-diff").count();
          const warningVisible = await page
            .locator(".migration-transformation-warning")
            .count();
          if (diffVisible === 0 || warningVisible === 0) {
            issues.push({
              language,
              viewport: viewport.name,
              route: "migration-controller-inspector",
              transformationDiff: diffVisible,
              createWarning: warningVisible
            });
          }
          await page.waitForTimeout(250);
          await page.screenshot({
            path: path.join(
              outputDirectory,
              `${language}-${viewport.name}-migration-controller-inspector.png`
            ),
            fullPage: false
          });
          await page.keyboard.press("Escape");
          await page.locator(".modal-card").waitFor({ state: "hidden" });
        }
      }

      if (name === "migration" && messageItemId) {
        const messageRow = page
          .locator(`[data-item-id="${messageItemId}"] .migration-row-open`)
          .first();
        if ((await messageRow.count()) > 0) {
          await messageRow.click();
          await page.locator(".modal-card").waitFor({ state: "visible" });
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
              route: "migration-messages-inspector",
              transformationDiff: 0
            });
          }
          await page.waitForTimeout(250);
          await page.screenshot({
            path: path.join(
              outputDirectory,
              `${language}-${viewport.name}-migration-messages-inspector.png`
            ),
            fullPage: false
          });
          await page.keyboard.press("Escape");
          await page.locator(".modal-card").waitFor({ state: "hidden" });
        }
      }

      if (name === "migration" && jspItemId) {
        const searchInput = page.locator(".migration-filters input").first();
        await searchInput.fill("edit.jsp");
        await page.waitForTimeout(300);
        const jspRow = page
          .locator(`[data-item-id="${jspItemId}"] .migration-row-open`)
          .first();
        await jspRow
          .waitFor({ state: "visible", timeout: 5000 })
          .catch(() => {});
        if ((await jspRow.count()) > 0) {
          await jspRow.click();
          await page.locator(".modal-card").waitFor({ state: "visible" });
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
              route: "migration-jsp-message-inspector",
              transformationDiff: 0
            });
          }
          await page.waitForTimeout(250);
          await page.screenshot({
            path: path.join(
              outputDirectory,
              `${language}-${viewport.name}-migration-jsp-message-inspector.png`
            ),
            fullPage: false
          });
          await page.keyboard.press("Escape");
          await page.locator(".modal-card").waitFor({ state: "hidden" });
        }
        await searchInput.fill("");
        await page.waitForTimeout(200);
      }

      if (name === "migration" && listJspItemId) {
        const searchInput = page.locator(".migration-filters input").first();
        await searchInput.fill("list.jsp");
        await page.waitForTimeout(300);
        const listJspRow = page
          .locator(`[data-item-id="${listJspItemId}"] .migration-row-open`)
          .first();
        await listJspRow
          .waitFor({ state: "visible", timeout: 5000 })
          .catch(() => {});
        if ((await listJspRow.count()) > 0) {
          await listJspRow.click();
          await page.locator(".modal-card").waitFor({ state: "visible" });
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
              route: "migration-jsp-jstl-inspector",
              transformationDiff: 0
            });
          }
          await page.waitForTimeout(250);
          await page
            .locator(".migration-diff")
            .first()
            .evaluate((element) => {
              element.scrollTop = element.scrollHeight;
            });
          await page.screenshot({
            path: path.join(
              outputDirectory,
              `${language}-${viewport.name}-migration-jsp-jstl-inspector.png`
            ),
            fullPage: false
          });
          await page.locator(".migration-diff").first().evaluate((element) => {
            element.scrollTop = element.scrollHeight;
            element.scrollIntoView({ block: "end" });
          });
          await page.waitForTimeout(150);
          await page.screenshot({
            path: path.join(
              outputDirectory,
              `${language}-${viewport.name}-migration-jsp-write-inspector.png`
            ),
            fullPage: false
          });
          await page.keyboard.press("Escape");
          await page.locator(".modal-card").waitFor({ state: "hidden" });
        }
        await searchInput.fill("");
        await page.waitForTimeout(200);
      }

      if (name === "migration" && webXmlItemId) {
        const searchInput = page.locator(".migration-filters input").first();
        await searchInput.fill("web.xml");
        await page.waitForTimeout(300);
        const webXmlRow = page
          .locator(`[data-item-id="${webXmlItemId}"] .migration-row-open`)
          .first();
        await webXmlRow
          .waitFor({ state: "visible", timeout: 5000 })
          .catch(() => {});
        if ((await webXmlRow.count()) > 0) {
          await webXmlRow.click();
          await page.locator(".modal-card").waitFor({ state: "visible" });
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
              route: "migration-webxml-inspector",
              transformationDiff: 0
            });
          }
          await page.waitForTimeout(250);
          await page.screenshot({
            path: path.join(
              outputDirectory,
              `${language}-${viewport.name}-migration-webxml-inspector.png`
            ),
            fullPage: false
          });
          await page.keyboard.press("Escape");
          await page.locator(".modal-card").waitFor({ state: "hidden" });
        }
        await searchInput.fill("");
        await page.waitForTimeout(200);
      }

      if (name === "migration" && validationItemId && controllerItemId) {
        const searchInput = page.locator(".migration-filters input").first();
        await searchInput.fill("OrderAction.java");
        await page.waitForTimeout(300);
        const validationCheck = page
          .locator(`[data-item-id="${validationItemId}"] .migration-batch-check input`)
          .first();
        const controllerCheck = page
          .locator(`[data-item-id="${controllerItemId}"] .migration-batch-check input`)
          .first();
        if ((await validationCheck.count()) > 0 && (await controllerCheck.count()) > 0) {
          await validationCheck.check();
          await controllerCheck.check();
          await searchInput.fill("");
          await page.waitForTimeout(300);
          const batchBar = page.locator(".migration-batch-bar").first();
          await batchBar.waitFor({ state: "visible", timeout: 5000 }).catch(() => {});
          if ((await batchBar.count()) > 0) {
            await batchBar.locator("button").nth(0).click();
            const previewModal = page.locator(".modal-card");
            const previewVisible = await previewModal
              .waitFor({ state: "visible", timeout: 10000 })
              .then(() => true)
              .catch(() => false);
            if (!previewVisible) {
              issues.push({
                language,
                viewport: viewport.name,
                route: "migration-batch-preview",
                batchPreview: 0
              });
            } else {
              await page.locator(".migration-batch-items").waitFor({ state: "visible" });
              await page.waitForTimeout(250);
              await page.screenshot({
                path: path.join(
                  outputDirectory,
                  `${language}-${viewport.name}-migration-batch-preview.png`
                ),
                fullPage: false
              });
              await page.keyboard.press("Escape");
              await previewModal.waitFor({ state: "hidden" });
              await batchBar.locator("button").nth(1).click();
              await page.locator(".modal-card").waitFor({ state: "visible" });
              await page.waitForTimeout(250);
              await page.screenshot({
                path: path.join(
                  outputDirectory,
                  `${language}-${viewport.name}-migration-batch-confirm.png`
                ),
                fullPage: false
              });
              await page.keyboard.press("Escape");
              await page.locator(".modal-card").waitFor({ state: "hidden" });
            }
            if ((await batchBar.count()) > 0) {
              await batchBar.locator("button").nth(2).click();
            }
          }
        }
        await searchInput.fill("");
        await page.waitForTimeout(200);
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
