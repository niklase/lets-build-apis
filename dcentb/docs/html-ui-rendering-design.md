# Generic HTML/HTMX rendering for API resources — design (v1: read-only)

Status: **design proposal, nothing implemented yet.** Scope agreed for v1: read-only list and
detail views for every collection, plus a dashboard page listing all collections. Create/update/
delete UI, and anything about forms, is explicitly deferred to a later version.

Everything here is driven by the OpenAPI document — no per-collection Java, matching how
`dbSchema`/`ItemDecorator`/`EffectiveOpenApiDoc` already work (see `README.md` and
`rest/controller/EffectiveOpenApiDoc.java`).

## 1. Where this hooks into the existing pipeline

Every request already flows through `RequestHandlerBase.processInternally`
(`rest/controller/RequestHandlerBase.java`): the operation's own `Processor[]` chain runs, then
`ResponseAccessController` filters the response body by the caller's permission, then the method
returns. The HTML renderer is one more processor, invoked **generically at that same point, for
every handler**, not per-collection:

```
... existing Processor[] chain ... → ResponseAccessController → [NEW] HtmlRenderingProcessor → return
```

It must run for both success and error responses (400/403/404), so a browser hitting a bad URL
gets a rendered page, not a raw JSON body with the wrong `Content-Type`.

### Trigger condition

Standard `Accept` header content negotiation (dcentb parses no request headers for negotiation
today — confirmed, nothing in the codebase reads `Accept`). Proposed rule, to avoid surprising any
existing JSON client:

- Render HTML only when `text/html` is present in `Accept` with a preference **at or above**
  `application/json`'s.
- `Accept: */*`, or no `Accept` header at all, **defaults to JSON** — the current, unchanged
  behavior. This matters because `curl`/Postman/existing integration tests all send `*/*` or
  nothing; none of them should silently start getting HTML back.
- A browser's own default navigation `Accept` header lists `text/html` first, so plain `<a href>`
  navigation and address-bar URLs work by default without the user doing anything.
- Per-operation opt-out: `x-dcentb.html.enabled: false`, for an endpoint that should never render
  as HTML even if asked.

## 2. The `/ui` dashboard page

### Path

Not `/index.html` or `/`. `HttpController` (`http/HttpController.java`) is a `@RestController`
with catch-all `**` mappings deliberately pinned at `@Order(Ordered.LOWEST_PRECEDENCE)` — i.e. it
runs **after** Spring Boot's own static-resource/welcome-page handling. Any app that ever has a
real file under `src/main/resources/static/`, or relies on Spring's default welcome-page behavior,
would have `/` or `/index.html` intercepted before dcentb's dispatcher ever saw the request —
fragile and app-dependent.

**Proposed: `/ui`.** Sibling to the existing `/swagger` convention (`http/SwaggerController.java`)
— `/swagger` is the machine/human API-docs page, `/ui` is the human data-browsing page. Short,
and not a path any real OpenAPI collection would plausibly declare.

### Wiring — a new, synthetic operation, not a bolted-on Spring controller

Unlike `/swagger` (a plain `@RestController` reading the raw spec file, entirely outside the
dcentb pipeline), `/ui` must go through the **same** `Controller` → `RequestHandlerProvider` →
`RequestHandlerHandle` → `RequestHandler` → `Processor[]` pipeline as every declared resource, so
it gets authentication, response filtering, and the HTML renderer for free, and so it respects
per-collection access control when deciding what to list.

1. `RequestHandlerProvider.setupPathConfigMapper` (`rest/controller/RequestHandlerProvider.java`)
   currently only registers paths found in the user's own `paths` object. It needs to synthesize
   one additional `GET /ui` entry (method/path/operation) before the document is handed to
   `EffectiveOpenApiDoc`/`EffectiveXUi` resolution, so it's indistinguishable from a declared path
   from that point on.
2. A new `RequestHandlerConfig` branch (`rest/controller/RequestHandlerConfig.java` currently
   dispatches purely on HTTP method + whether the path ends in `/{id}`) needs a marker on this
   synthetic operation to route it to a new `RequestHandler`, e.g. `CollectionsIndexRequestHandler`,
   instead of falling into the existing get-item/get-collection branches.
3. That handler's processor chain is small: `AuthenticationProcessor`, then one new processor that
   reads the already-resolved `x-dcentb.collections` map and builds `{"collections": [...]}`,
   **filtered to collections the caller's permission has a `GET`-collection `permissionSchemas`
   entry for** — reusing whatever `PermissionSchemasProvider`/`RequestAccessController` already
   does for per-collection permission checks, just invoked from a different spot, so the dashboard
   never links somewhere the same caller would get a 403 clicking through.
4. `ResponseAccessController` and `HtmlRenderingProcessor` are already generic (§1), so this
   handler gets both for free just by producing a normal `response` key.
5. A collection is **always listed**, even with no `x-dcentb.collections.<name>.ui.displayName`
   configured — falls back to the raw collection name. Label is cosmetic only, never gates
   visibility (visibility is the permission check in step 3).

## 3. The `x-ui` schema extension

Every **object-typed JSON Schema** reachable from a 2xx response body — the root, every `$ref`
target, and every inline nested object — carries its own `x-ui`, scoped strictly to that schema's
own direct `properties`:

```json
"Teachers_item_v1": {
  "x-ui": {
    "properties": [
      {"name": "name", "displayName": {"sv_SE": "Namn", "en_US": "Name"}},
      {"name": "status", "displayName": {"sv_SE": "Status", "en_US": "Status"}}
    ]
  },
  "properties": {
    "classSummary": { "type": "object", "properties": { "...": "..." } },
    "teacherId": { "type": "string" },
    "name": { "type": "string" },
    "status": { "type": "string", "enum": ["ACTIVE", "INACTIVE"] },
    "meta": { "$ref": "#/components/schemas/Meta_item_v1" }
  }
}
```

A nested object like `classSummary` declares its **own** `x-ui.properties`
(`studentCount`/`atRiskCount`/...) — it never reaches up into the parent schema or down past its
own direct children. Each level of nesting is independently labelled/ordered.

Rules:

- **Order**: entries listed in `x-ui.properties` render first, in the given order. Any remaining
  property in that schema not mentioned is appended afterward, sorted **alphabetically by key**.
- **`displayName`** is a locale-keyed map (`sv_SE`, `en_US`, ...), optional per entry and per
  locale. No entry, or no match for the requested/default locale, falls back to the raw property
  key.
- A property listed in `x-ui.properties` but **absent from the actual (role-filtered) response
  body** for a given request is silently skipped, not an error — different permission roles see
  different field subsets (`ResponseAccessController`), and `x-ui.properties` describes the
  schema's full universe, not any one role's view of it.

## 4. "Effective x-ui" resolution — a new pass, same idiom as `EffectiveOpenApiDoc`

Computed once at startup (restart required on spec changes — already true of every other
`x-dcentb` default per the demo READMEs), mirroring how `EffectiveOpenApiDoc` bakes in
`dbCollection`/`collectionSchemas`:

- For every operation, for every 2xx response's `content.application/json.schema`, resolve `$ref`s
  recursively through the **whole reachable schema graph** (so a shared schema like
  `Meta_item_v1`, and inline nested objects like `classSummary`, are both visited).
- For each object-typed node visited, write back a **complete** `x-ui.properties`: the author's
  declared entries unchanged, then every remaining property key appended alphabetically with no
  `displayName` (the raw-key fallback happens at render time, not baked in here).
- Scope is exactly "2xx response bodies reachable from `paths`" — request bodies, non-2xx schemas,
  and `components/schemas` entries nothing references are left untouched, matching read-only v1.
- Needs cycle detection: a schema that references itself (directly or through a chain) must not
  send this resolution into infinite recursion. Low-probability for today's demo schemas, but a
  real correctness requirement for a general-purpose mechanism.

## 5. Collection-level display name

`x-dcentb.collections.<name>.ui` = `{"displayName": {"sv_SE": "...", "en_US": "..."}}` — same
locale-map shape and same fallback-to-raw-key rule as property-level `displayName`. Used by `/ui`
and any nav/breadcrumbs.

## 6. List view pagination (open, resolved with an assumption)

`ReadCollectionRequestHandler` already supports `limit`/`offset` query params
(`todo-api.openapi.json`). Whether the response also carries a total count hasn't been checked
against `MongoToRestCollectionTranslator`'s actual output — the design assumes **no total count is
required**: "Next" shows whenever `items.length == limit` (there may be more), "Prev" is disabled
at `offset == 0`. This avoids any API/response-shape change just to support pagination UI. If a
total count does turn out to already be in the response, it's a free upgrade to an exact page
count later, not a blocker now.

## 7. HTMX wiring notes (read-only v1)

- No `HX-Request` header → full HTML document (shell + fragment). `HX-Request: true` (htmx sets
  this automatically on its own requests) → render just the fragment, for in-place swaps.
- Every htmx-issued request needs `Accept: text/html` explicitly, since htmx's own default is
  `*/*` — which, per §1's rule, would get **JSON** back and get dumped raw into the DOM on swap.
  Set `hx-headers='{"Accept":"text/html"}'` once on a top-level element (`<body>` or the page
  shell) — htmx inherits `hx-headers` down through descendants, so one declaration covers every
  nested `hx-get` on the page. This is an easy thing to get wrong in just one spot and silently
  ship broken; worth a lint/test rather than trusting every template author to remember it.

## Issues and risks with this design

**Authentication for plain browser navigation is unresolved, and is the biggest open gap.**
dcentb's access control is keyed off a custom `api-key` header (or JWT) — see
`AuthenticationProcessor`/`todo-api.openapi.json`'s `ApiKeyAuth` security scheme. A plain `<a
href>` click, typed URL, or bookmark is a browser navigation that **cannot carry a custom header**
— only `fetch`/`XMLHttpRequest` (and so htmx's own AJAX requests) can. That means:
- The *very first* load of `/ui`, or of any collection/item page reached by typing a URL or
  opening a bookmark rather than clicking an in-page htmx link, has no way to authenticate under
  the current access-control model.
- Only navigation that originates from *inside* an already-running htmx page (where JS can attach
  the header) works — which breaks the "bookmarkable page" property that's part of the appeal of
  server-rendered URLs in the first place.
- This needs an explicit decision before v1 is usable in a real browser: a cookie-based session
  (login page, sets a cookie, every subsequent request — including full-page navigations — carries
  it automatically), HTTP Basic Auth (browser's native credential prompt, still header-based but
  the *browser* attaches it after the first prompt), or an accepted-but-flagged-insecure
  api-key-as-query-param escape hatch for HTML mode specifically (ends up in server logs and
  browser history, so not a casual choice). This is independent of everything else in this design
  and should be settled first.

**A schema's `x-ui` is shared everywhere that schema is referenced.** Because `x-ui` lives on the
schema definition, not on the `$ref` call site, if `Meta_item_v1` is reused across multiple
collections, all of them get identical `meta` labels/ordering. Almost certainly what you want (one
source of truth) — but it means "same field, different label per collection" isn't expressible
without duplicating the schema under a different name. Worth knowing in advance rather than
discovering it as a limitation later.

**Array-valued properties are undesigned.** The worked example (`classSummary`) is an object, not
an array, so "every object-typed schema gets its own `x-ui`" never had to answer what happens for
`{"type": "array", "items": {"type": "object", ...}}`. The effective-x-ui walk needs to decide
whether it recurses into `items` the same way, and the renderer needs a default for an
array-of-objects value in a list/detail view (e.g. a nested table, a count + link, or a raw
fallback) rather than crashing or silently dropping it. Flagging now since some existing schema in
this repo likely already has one (worth checking `dcentb-students-demo`'s schema before this is
built, not after).

**Locale key format mismatch.** `Accept-Language` arrives hyphenated per RFC (`sv-SE`); the
`displayName` convention here is underscored, Java-`Locale`-style (`sv_SE`). The render-time
lookup needs to normalize one to the other — small, but easy to get backwards once and then have
every non-default locale silently fall back to raw keys.

**Restart-to-pick-up-spec-changes applies here too.** Not new — already true of every other
`x-dcentb` default — but worth restating since `x-ui`/effective-x-ui adds another thing that goes
stale until restart if someone expects to tweak a label and refresh the browser.
