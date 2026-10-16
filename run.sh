#!/bin/bash
curl https://raw.githubusercontent.com/ryeash/full-steam-2/refs/heads/master/Dockerfile -o Dockerfile
docker build --build-arg BRANCH=wip5 -t full-steam --no-cache .
docker run -e APP_ALLOWED_ORIGINS=http://full-steam.io,http://www.full-steam.io,https://full-steam.io,https://www.full-steam.io,wss://www.full-steam.io,ws://www.full-steam.io --memory=2.8g -p 80:8080 -p 443:8443 --rm -d full-steam