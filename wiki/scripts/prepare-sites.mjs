import { mkdir, readdir, rename, writeFile } from "node:fs/promises";
import { join } from "node:path";

const dist = new URL("../dist/", import.meta.url);
const client = new URL("../dist/client/", import.meta.url);
const server = new URL("../dist/server/", import.meta.url);

await mkdir(client, { recursive: true });
for (const entry of await readdir(dist)) {
  if (entry === "client" || entry === "server") continue;
  await rename(new URL(entry, dist), new URL(entry, client));
}

await mkdir(server, { recursive: true });
await writeFile(
  join(server.pathname, "index.js"),
  `export default {
  async fetch(request, env) {
    const response = await env.ASSETS.fetch(request);
    if (response.status !== 404) return response;

    const url = new URL(request.url);
    url.pathname = "/index.html";
    return env.ASSETS.fetch(new Request(url, request));
  },
};
`,
);
