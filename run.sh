#!/bin/bash
curl https://raw.githubusercontent.com/ryeash/full-steam-2/refs/heads/master/Dockerfile -o Dockerfile
docker build --build-arg BRANCH=wip4 -t full-steam --no-cache .
docker run -e APP_ALLOWED_ORIGINS=full-steam.io,www.full-steam.io --memory=2g -p 80:8080  --rm -it full-steam