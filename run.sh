#!/bin/bash
curl https://raw.githubusercontent.com/ryeash/full-steam-2/refs/heads/master/Dockerfile -o Dockerfile
docker build --build-arg BRANCH=wip5 -t full-steam --no-cache .
docker run -e APP_ALLOWED_ORIGINS="(https?|wss?)://(www\.)?full-steam\.io" --memory=2.8g -p 80:8080 -p 443:8443 --rm -d full-steam
