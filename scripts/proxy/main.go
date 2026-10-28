// Caddy's standard distribution, rebuilt with the reviewed security updates.
package main

import (
 caddycmd "github.com/caddyserver/caddy/v2/cmd"
 _ "github.com/caddyserver/caddy/v2/modules/standard"
)

func main() { caddycmd.Main() }
