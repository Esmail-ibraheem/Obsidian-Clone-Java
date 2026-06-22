# Dev image for the Vite dev server with HMR. Uses node:20-alpine (cached
# locally). node_modules lives in an anonymous/named volume (see
# docker-compose.dev.yml) so the host bind-mount doesn't clobber it; `npm
# install` runs on start, using the DNS servers set on the compose service.
FROM node:20-alpine

WORKDIR /app

EXPOSE 5173

CMD ["sh", "-c", "npm install --no-audit --no-fund && npm run dev -- --host --port 5173"]
