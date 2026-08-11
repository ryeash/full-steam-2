#!/bin/bash
curl https://raw.githubusercontent.com/ryeash/full-steam/refs/heads/master/Dockerfile -o Dockerfile
docker build --build-arg BRANCH=master -t full-steam --no-cache .
docker run -e APP_ALLOWED_ORIGINS=full-steam.io,www.full-steam.io --memory=2g -p 80:8080  --rm -it full-steam