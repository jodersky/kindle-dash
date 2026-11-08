#!/bin/sh
# Name: Dashboard
# Author: App
# Icon: data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAIAAAACACAAAAADmVT4XAAABYklEQVR42u2bWxKCMAxFSYc9swZXHT8cbaGVh9Ck1MOXOjr3mBdJNaKD7xUGAJyvcfFcqisugk7UVD1nCA76M6FoATF1vWYWsNWPcsFHPwqOG0Fa1f9JDIiRfK71AhA7+aVcU6VYzXPwDSA+X14WFjDsDLThu2HNsqR7AaSaz/UeHZFUjnt6QgAAAAAAAAAAAIBNgIrzQYtN6VjkdJ4LTKc0sgAAAAAoF6LH9genqyZ2/YztekD+LEJUbDEGdhpg9/tuGYRXX9JwQwIAAAAAAAAAV98ND/8IjQvC783m1IcFSm35mbmgHITZq1GxDHB1FqwAkAU9zwX7iiIu6O58IHF8+SHnA33WgTP/xaMOAAAAAAAAAAAAHFD0cUCxOQwYHlAcamNbG0zE2gARwGnpMVtycdk1ayMN/RedEquYrXrpl+FUnNJQXVIgDUL10Z/tnIq9fAO7ZqLWMbi29ktL9pcATwnVVvsdE0l+AAAAAElFTkSuQmCC
# DontUseFBInk

# the scriptlet's working directory is unspecified, so run from the script dir
cd "$(dirname "$0")" || exit 1

# Fully detach run.sh, since it runs forever and stops the framework that
# launched this scriptlet:
# - `&` lets the scriptlet return immediately instead of blocking the launcher
# - `setsid` puts run.sh in its own session/process group, so signals sent to
#   the framework's process group (e.g. SIGTERM on stop) don't reach it
# - `nohup` ignores the SIGHUP sent when the parent goes away
setsid nohup sh ./run.sh >/dev/null 2>&1 &
