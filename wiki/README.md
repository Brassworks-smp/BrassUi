# brassui - wiki

A developer wiki for the **Kotlin `brassui`** toolkit, built with **[BrassUiReact](https://github.com/salem-5/BrassUiReact)**
so it looks like what it documents. A page for every widget, the design language, and the layout rules -
written so an agent can follow them, with a screenshot slot per widget.

## Run

```bash
npm install          # BrassUiReact is a sibling: ../BrassUiReact
npm run dev          # http://localhost:5173
npm run build        # static site → dist/
```

> This app depends on `brassui-react` via `file:../BrassUiReact`, so keep the two repos as siblings.
> Once BrassUiReact is published to a registry, swap that line in `package.json` for a version range.

## What's here

- **Overview / Getting started / Design guide / Layout guide** - the narrative docs (`src/pages/`).
- **Widget catalog** - a page per widget (`/widgets/:name`), generated from the Kotlin sources.
- **Screenshots** - drop `public/screenshots/<Widget>.png` and it appears on that widget's page.

## Regenerating the widget catalog

The catalog (`src/data/widgets.ts`) is generated from the `brassui` Kotlin KDoc - class name, kind,
category, doc, and declaration signature. Re-run it after the toolkit changes:

```bash
npm run gen   # reads ../brassui/core, writes src/data/widgets.ts
```

The generator path to the Kotlin repo is set at the top of `scripts/gen-widgets.mjs`.

## Deploy

It's a static Vite site with `base: "./"` and hash routing, so it hosts anywhere - GitHub Pages,
Netlify, an S3 bucket. `npm run build` then serve `dist/`.
