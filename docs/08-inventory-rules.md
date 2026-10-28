# Phase 1 Inventory Rules

## Discovery

Supported extensions and special names:

- Java, JSP/JSPF, HTML, JavaScript, CSS.
- XML, properties, YAML/YML, dicon, SQL, TLD.
- `pom.xml`, `build.gradle`, `build.gradle.kts`, `settings.gradle`, `settings.gradle.kts`.
- Test source files under conventional test roots.

Default excluded directories:

`.git`, `.svn`, `.hg`, `.idea`, `.settings`, `.gradle`, `node_modules`, `target`, `build`, `out`, `dist`, `coverage`, `.next`, `.cache`.

User exclusions extend the default. Files beyond the configured byte limit are recorded as skipped. Symbolic links are not traversed. Duplicate canonical files are rejected.

Analysis scope is applied after discovery:

- `FULL` analyzes all supported source and configuration families.
- `SOURCE_ONLY` analyzes Java, test, JSP, HTML, JavaScript, CSS, and SQL while inventorying other files as skipped.
- `CONFIGURATION_ONLY` analyzes XML, properties, YAML, dicon, TLD, POM, and Gradle while inventorying other files as skipped.

Scope-skipped files remain visible in the file inventory and explicitly state why analysis was not performed.

## Directory roles

Recognized roles:

- Java source root.
- Test source root.
- Resource root.
- Web root.
- JSP root.
- Static resource root.
- Configuration root.
- SQL root.

Conventions cover Maven/Gradle source layouts, common Eclipse web projects, and traditional `web`, `WebContent`, `WEB-INF`, `config`, and `sql` folders. User overrides take precedence for unexplained directories.

## Module detection

- Maven `<modules>` entries create child modules.
- Gradle `include(...)` entries in settings files create child modules.
- The project itself is always represented as a root module.
- Nested roots are assigned to the longest matching module path.
- Direct Maven dependencies matching another module's group and artifact create a `MAVEN_MODULE` relation.
- Gradle `project(':module')` declarations matching a settings module create a `GRADLE_PROJECT` relation.
- Module dependencies persist declaration path, line, rule ID, analyzer version, and certainty.
- Observed build files are evidence; a declaration is not treated as a successful build.

## File analysis

### Java

JavaParser is the required parser. Rules inspect imports, annotations, class inheritance, methods, and selected calls.

Example evidence keys:

- `SASTRUTS_ACTION`
- `SASTRUTS_EXECUTE`
- `SEASAR_COMPONENT`
- `SEASAR_BINDING`
- `S2JDBC_ENTITY`
- `S2JDBC_DAO`
- `S2JDBC_QUERY`
- `STRUTS_ACTION`
- `STRUTS_VALIDATION`
- `SERVLET_API`
- `JUNIT_TEST`

SAStruts validation annotations and `@ActionForm` fields produce separate evidence (`JAVA-SAST-VALIDATION-001`, `JAVA-SAST-FORM-001`). Struts 1 `validate(...ActionMapping...)` and `findForward("...")` calls produce `STRUTS1` evidence. Seasar container startup calls and S2 transaction API imports are recorded without claiming that the container or a transaction ran.

AST parse failure produces `PARSE_FAILED`; text is never promoted to an AST result.

### JSP and TLD

- Parse taglib declarations and tag URI/prefix usage with a markup scanner that understands quoted attributes, multi-line tags, JSP comments, scriptlets, directives, and expressions; commented-out markup is not evidence.
- Locate plain `<form>` submissions and Struts tag submissions such as `<html:form>` or `<s:form>`; every submission remains `REVIEW_REQUIRED` because runtime routing is not resolved.
- Locate plain field tags (`input`, `select`, `textarea`) and Struts field tags (`html:text`, `html:select`, `html:checkbox`, and related `property`-based tags).
- Locate message and error display tags: JSTL `fmt:message`, Struts `bean:message`, `html:errors`, and `html:messages`.
- Locate JavaScript and static references from `script`, `link`, `img`, and `source` elements.
- Treat page-to-action linkage as `INFERRED` only when a form action resolves exactly to one configured Struts action path; unresolved or dynamic targets produce no relationship.
- Parse TLD XML securely.

### Build files

- Parse Maven POM structure securely.
- Read compiler source/target values and dependency coordinates.
- Read Gradle source compatibility and dependency declaration lines.
- Dependency presence yields `IDENTIFIED`, not confirmed runtime use.

### XML and configuration

- Parse well-formed XML with DTDs and external entities disabled.
- Obtain components, classes, filters, servlets, mappings, and references from supported elements.
- Preserve parse failure evidence.
- dicon findings map to Seasar2: components with `instance`/`autoBinding` attributes, property and constructor injection, lifecycle methods, includes, aspects, and interceptors.
- `struts-config.xml` findings map to Struts 1: message resources, form beans, action mappings with path, type, form name, and validation flag, forwards, and plug-ins.
- `web.xml` declarations cover servlets, filters, and listeners with class references.
- Line evidence comes from literal source lookup of the matched tag, class name, or coordinate; a lookup that cannot locate the token reports line 1 rather than inventing a position.

### Properties, YAML, SQL, and text-backed config

- Locate known keys and class/file references.
- Preserve line numbers.
- Mark convention-only associations as inferred or review-required.

## Technology detection

| Key | Positive evidence |
| --- | --- |
| `JAVA` | Java files or compiler metadata |
| `MAVEN` | POM/build declaration |
| `GRADLE` | Gradle build/settings files |
| `SASTRUTS` | `org.seasar.struts` API or SAStruts dependency |
| `STRUTS1` | `org.apache.struts` API or Struts 1 dependency |
| `SEASAR2` | Seasar annotations, imports, dicon, or dependencies |
| `S2JDBC` | S2JDBC APIs, annotations, configuration, or dependencies |
| `SERVLET` | Servlet API imports or web.xml servlet declarations |
| `JSP` | JSP/JSPF files or JSP dependency |
| `JSTL` | JSTL namespace, imports, or dependencies |
| `CUSTOM_TAGS` | TLD declarations or custom tag URI usage |
| `CUSTOM_FRAMEWORK` | Project-owned framework/foundation/platform package imports, retained as review-required |
| `JUNIT` | JUnit API or dependencies |
| `DB_ACCESS` | JDBC/ORM dependency or static API evidence |

SAStruts and Struts 1 must never be collapsed into one key.

Custom business-framework detection is a review signal, not a confirmed framework identity. Project-owned imports in packages named `framework`, `foundation`, or `platform` are recorded with source evidence and remain `REVIEW_REQUIRED`.

## Determinism and diff

- Paths use forward slashes.
- Discovery order is lexicographic by relative path.
- Findings have deterministic business content; UUID identity may differ between scans.
- File SHA-256 drives `ADDED`, `MODIFIED`, `UNCHANGED`, and `DELETED`.
- A scan does not update prior scan rows.

## Phase 2 cross-file relations

The initial Phase 2 slice creates only relations with a concrete source locator:

- `JAVA_IMPORT` when an import resolves to a Java type declared in another scanned project file.
- `JAVA_INHERITANCE` when an extends or implements type resolves to another scanned Java type.
- `SEASAR_BINDING` when a `@Binding` field type resolves to another scanned Java component.
- `SEASAR_DICON_INJECTION` when dicon component/property declarations identify a project class target.
- `CONFIG_CLASS_REFERENCE` when a properties/YAML/XML configuration value contains a scanned project class name; this is `INFERRED`.
- `STRUTS_ACTION_TO_JSP` when `struts-config.xml` maps an action class and forward path to scanned files.
- `JSP_FORM_TO_ACTION` when a JSP form action matches a configured Struts action path; this remains `INFERRED`.
- `SASTRUTS_ACTION_TO_JSP` when a SAStruts action returns a JSP literal that resolves to one scanned JSP; this remains `INFERRED`.
- `DAO_SQL_REFERENCE` when an S2JDBC SQL literal and a scanned SQL file reference the same table; this remains `INFERRED`.
- `MESSAGE_PRODUCTION` when a literal `ActionMessage` key resolves to one project properties file.
- `MESSAGE_DISPLAY` when a JSP message tag key resolves to one project properties file.

Relations are bidirectional when viewing a file, but retain their original source and target direction, evidence path, line, rule ID, analyzer version, and certainty.

## Safety

- XML uses secure processing features.
- Text evidence is clipped before persistence.
- Scanner errors contain exception class and message, not full source.
- Cancellation is checked between files and during safe parsing checkpoints.
- Scanner APIs contain no source-write operations.
