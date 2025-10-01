![Alfresco 25.x](https://img.shields.io/badge/Alfresco-25.x-blue)
![License](https://img.shields.io/badge/license-Apache%202.0-green)

# Markdown Rendition for Alfresco

Creates and stores a **Markdown rendition** for every document in Alfresco Repository

## What it does

- Generates `cm:markdown` (`text/markdown`) from the PDF version of a document using either the newly created `cm:pdf` rendition or the original when it’s already PDF. So Markdown is produced for any source mimetype
- Works in two cases:
  1) When Alfresco generates a `cm:pdf` rendition from another format (DOCX, ODT, etc.)
  2) When the original upload is already a PDF
- The Markdown file is stored as a proper rendition:
  - Association: `rn:rendition`
  - Association name (rendition id): `cm:markdown`
  - Child node has aspect: `rn:rendition`
  - Mimetype: `text/markdown`
- Runs *asynchronously after commit* (does not block user transactions)
- Idempotent: won’t create duplicates if `cm:markdown` already exists

## Installation

1. Build the JAR File using `mvn clean package`
2. Place it in `<ALFRESCO_HOME>/modules/platform/` or in Docker/k8s deployment folder
3. Restart Alfresco Repository
4. Ensure Transform Service is configured (both Community and Enterprise versions are accepted)

For instance, for a **Docker Deployment** with [https://github.com/Alfresco/alfresco-docker-installer/](https://github.com/Alfresco/alfresco-docker-installer/):

1. Add the Markdown TEngine reference to Alfresco Configuration

```yaml
    alfresco:
        environment:
            JAVA_OPTS : '
                -DlocalTransform.core-aio.url=http://transform-core-aio:8090/
                -DlocalTransform.md.url=http://transform-md:8090/
            '
```

2. Add the Markdown TEngine service

```yaml
    # Requires local Ollama running with "llava" model pulled
    transform-md:
        image: docker.io/angelborroy/alf-tengine-convert2md
        environment:
          SPRING_AI_OLLAMA_BASE_URL: http://host.docker.internal:11434
```

3. Copy the `markdown-rendition-0.8.0.jar` to `alfresco/modules/jars` deployment folder

## How it works in the Repository

- Triggers on either:
  - Rendition path: fires when a child association `rn:rendition/cm:pdf` is created
  - Content path: fires on content create/update when the **original node** has mimetype `application/pdf`

- Transform
  - Uses the Alfresco Transform Service to convert PDF to Markdown
  - Requires a Transform Engine capable of `application/pdf → text/markdown` (like `alf-tengine-convert2md`)

- Execution model
  - Work is queued *within the current transaction* and executed *post-commit* on a background thread
  - Each job runs in a *fresh Repository transaction* with *system privileges*

- Storage semantics
  - The Markdown output is persisted as a *child* of the original node under `rn:rendition` with name `cm:markdown`
  - Surfaces in Share/ADF/REST like native renditions (`cm:doclib`, `cm:webpreview`, `cm:pdf` and `cm:markdown`)

## Compatibility

- Repository: Alfresco Content Services **25.x** (Community & Enterprise)
- Transform*: Any TEngine advertising `application/pdf → text/markdown`
- Clients: Share / ADF / Public REST will list and retrieve the `cm:markdown` rendition

## Verifying

- Fetch Markdown content
  - `GET http://localhost:8080/alfresco/api/-default-/public/alfresco/versions/1/nodes/{nodeId}/renditions/markdown/content`
  - `Accept: text/markdown`

## Troubleshooting

- No `cm:markdown` shown
  - Confirm the Transform Engine supports **PDF to Markdown**
  - Ensure the source is a PDF (either via `cm:pdf` rendition or original mimetype)
  - Check Repository logs for transform failures

- Unexpected delays
  - Post-commit execution means the rendition appears shortly after the original transaction completes

## Contributing

Issues and PRs welcome!