# Legacy Web Demo

This source tree is a deterministic inventory fixture for Stravique. It models a multi-module Java web system that mixes SAStruts, Struts 1 tag libraries, Seasar2, S2JDBC, JSP, dicon, properties, SQL, YAML, and static assets.

The fixture is intentionally not expected to compile because its legacy dependencies are not packaged. `legacy-broken.xml` is intentionally malformed so parse-failure and review paths can be exercised. Generated paths and headers are also included to verify exclusions.

Inventory operations must not modify any file in this directory.
