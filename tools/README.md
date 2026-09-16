# Development and documentation aids

Nothing in this folder is part of the product. These scripts support front-end
development and the production of the user guide.

## ui-preview-server.js

Serves the compiled Angular bundle together with a fixed set of records, so the
interface can be opened, reviewed and screenshotted without the Java service
running. It performs no verification, contacts no registry and stores nothing.

Its fixed records were produced by running the platform's own scoring logic over
the bundled sample documents; see `../docs/scenario-results.txt`.

```bash
cd ..            # the project root
node tools/ui-preview-server.js       # serves on http://localhost:8080
```

## capture-screenshots.mjs

Drives a headless browser over the running preview server and writes the user
guide figures to `../docs/screenshots/`. Requires Playwright
(`npm install playwright`).

```bash
node tools/capture-screenshots.mjs
```

To capture screenshots from the real platform instead, start the Spring Boot
service on port 8080 and run the same script; it only needs something answering
on that port.
