// Caddy's standard distribution, rebuilt with the reviewed security updates.
package main

import (
 caddycmd "github.com/caddyserver/caddy/v2/cmd"
 jose "github.com/go-jose/go-jose/v3"
 _ "github.com/caddyserver/caddy/v2/modules/standard"
)

func main() {
 // An accidental plain go build against unpatched upstream v3 must fail compilation.
 if jose.MoapanSecurityBackport != "jose-v3-20261031.1" { panic("unverified JOSE backport") }
 caddycmd.Main()
}
