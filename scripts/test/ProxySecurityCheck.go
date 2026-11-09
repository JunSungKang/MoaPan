// Isolated proxy regression. Synthetic origin and a trusted fixture certificate;
// no host ports, live services, public ACME requests or insecure TLS clients.
package main

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/json"
	"encoding/pem"
	"fmt"
	"io"
	"math/big"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync/atomic"
	"time"
)

func main() {
	if len(os.Args) != 2 {
		panic("workspace required")
	}
	root, err := filepath.Abs(os.Args[1])
	must(err)
	work, err := os.MkdirTemp(filepath.Join(root, ".local/tmp"), "proxy-boundary-")
	must(err)
	defer os.RemoveAll(work)
	var hits atomic.Int64
	backend := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		hits.Add(1)
		if _, err := io.Copy(io.Discard, r.Body); err != nil {
			http.Error(w, "body rejected", http.StatusBadRequest)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		w.Header().Set("Content-Language", "en")
		http.SetCookie(w, &http.Cookie{Name: "SESSION", Value: "synthetic-only", Secure: true, HttpOnly: true, SameSite: http.SameSiteLaxMode, Path: "/"})
		_ = json.NewEncoder(w).Encode(map[string]string{"for": r.Header.Get("X-Forwarded-For"), "host": r.Header.Get("X-Forwarded-Host"), "proto": r.Header.Get("X-Forwarded-Proto")})
	}))
	defer backend.Close()
	cert, key, roots := certificate(work)
	checked := 0
	for _, mode := range []string{"local", "public", "platform"} {
        public := mode != "local"
		config := filepath.Join(root, "Caddyfile")
		if public {
			config = filepath.Join(root, "scripts/deploy/Caddyfile.public")
		}
		if mode == "platform" { config = filepath.Join(root, "scripts/platform/Caddyfile") }
        command := exec.Command("/caddy", "adapt", "--config", config, "--adapter", "caddyfile")
		command.Env = append(os.Environ(), "DAMGOX2_PUBLIC_HOST=proxy.test")
		raw, err := command.Output()
		must(err)
		if public {
			require(strings.Contains(string(raw), `"profile":"shortlived"`), "public ACME profile lost")
			require(strings.Contains(string(raw), "https://acme-v02.api.letsencrypt.org/directory"), "public ACME issuer changed")
			checked += 2
		}
		var c map[string]any
		must(json.Unmarshal(raw, &c))
		require(c["admin"].(map[string]any)["disabled"] == true, "admin API enabled")
		checked++
		replaceUpstream(c, backend.Listener.Addr().String())
		if public {
			apps := c["apps"].(map[string]any)
			apps["tls"] = map[string]any{"certificates": map[string]any{"load_files": []any{map[string]any{"certificate": cert, "key": key}}}}
			for _, server := range apps["http"].(map[string]any)["servers"].(map[string]any) {
				server.(map[string]any)["automatic_https"] = map[string]any{"disable": true}
			}
		}
		data, err := json.Marshal(c)
		must(err)
		file := filepath.Join(work, "config.json")
		must(os.WriteFile(file, data, 0600))
		logFile, err := os.Create(filepath.Join(work, "proxy.log"))
		must(err)
		process := exec.Command("/caddy", "run", "--config", file)
		process.Env = append(os.Environ(), "XDG_DATA_HOME="+work, "XDG_CONFIG_HOME="+work)
		process.Stdout = logFile
		process.Stderr = logFile
		must(process.Start())
		func() {
			defer func() { _ = process.Process.Signal(os.Interrupt); _ = process.Wait(); _ = logFile.Close() }()
			url := "http://127.0.0.1:8080"
			client := &http.Client{Timeout: 5 * time.Second}
			if public {
				url = "https://proxy.test:8443"
                if mode == "platform" { url = "https://proxy.test:443" }
				client.Transport = &http.Transport{TLSClientConfig: &tls.Config{RootCAs: roots, MinVersion: tls.VersionTLS12}, DialContext: func(ctx context.Context, network, address string) (net.Conn, error) {
					_, port, err := net.SplitHostPort(address)
					if err != nil {
						return nil, err
					}
					return (&net.Dialer{}).DialContext(ctx, network, net.JoinHostPort("127.0.0.1", port))
				}}
			}
			defer client.CloseIdleConnections()
			for i := 0; ; i++ {
				response, err := client.Get(url + "/about.html")
				if err == nil {
					_ = response.Body.Close()
					break
				}
				if i >= 49 {
					panic(err)
				}
				time.Sleep(100 * time.Millisecond)
			}
			request, err := http.NewRequest("GET", url+"/api/v1/auth/session", nil)
			must(err)
			for _, header := range []string{"X-Forwarded-For", "X-Forwarded-Host", "X-Forwarded-Proto"} {
				request.Header.Set(header, "spoofed.invalid")
			}
			response, err := client.Do(request)
			must(err)
			require(response.StatusCode == 200, "reverse proxy failed")
			var headers map[string]string
			must(json.NewDecoder(response.Body).Decode(&headers))
			must(response.Body.Close())
			require(headers["for"] == "" && headers["host"] == "" && headers["proto"] == "", "spoofed proxy headers reached origin")
			checked++
			require(response.Header.Get("Content-Language") == "", "incorrect forced response language")
			checked++
			cookie := response.Header.Get("Set-Cookie")
			require(strings.Contains(cookie, "Secure") && strings.Contains(cookie, "HttpOnly") && strings.Contains(cookie, "SameSite=Lax"), "cookie attributes changed")
			checked++
			if public {
				require(response.Header.Get("Strict-Transport-Security") == "max-age=31536000; includeSubDomains", "HSTS lost")
				checked++
			}
			for _, path := range []string{"/internal/health", "/actuator/env"} {
				before := hits.Load()
				response, err = client.Get(url + path)
				must(err)
				require(response.StatusCode == 404, "internal route exposed")
				must(response.Body.Close())
				require(hits.Load() == before, "blocked route reached origin")
				checked++
			}
			response, err = client.Post(url+"/api/v1/auth/login", "application/json", strings.NewReader(strings.Repeat("x", 1024*1024+1)))
			must(err)
			require(response.StatusCode == 413, fmt.Sprintf("oversized body status %d", response.StatusCode))
			must(response.Body.Close())
			checked++
		}()
	}
	fmt.Printf("Proxy boundary checks PASS: %d; trusted synthetic HTTPS; no public ACME or live service changes.\n", checked)
}
func replaceUpstream(value any, address string) {
	switch v := value.(type) {
	case map[string]any:
		for k, x := range v {
			if k == "dial" && (x == "web:8080" || x == "damgox2-web:8080") {
				v[k] = address
			} else {
				replaceUpstream(x, address)
			}
		}
	case []any:
		for _, x := range v {
			replaceUpstream(x, address)
		}
	}
}
func certificate(work string) (string, string, *x509.CertPool) {
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	must(err)
	template := &x509.Certificate{SerialNumber: big.NewInt(1), Subject: pkix.Name{CommonName: "proxy.test"}, DNSNames: []string{"proxy.test"}, NotBefore: time.Now().Add(-time.Hour), NotAfter: time.Now().Add(time.Hour), KeyUsage: x509.KeyUsageDigitalSignature | x509.KeyUsageCertSign, ExtKeyUsage: []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth}, IsCA: true, BasicConstraintsValid: true}
	der, err := x509.CreateCertificate(rand.Reader, template, template, &key.PublicKey, key)
	must(err)
	keyBytes, err := x509.MarshalECPrivateKey(key)
	must(err)
	certPath, keyPath := filepath.Join(work, "cert.pem"), filepath.Join(work, "key.pem")
	must(os.WriteFile(certPath, pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der}), 0600))
	must(os.WriteFile(keyPath, pem.EncodeToMemory(&pem.Block{Type: "EC PRIVATE KEY", Bytes: keyBytes}), 0600))
	roots := x509.NewCertPool()
	cert, err := x509.ParseCertificate(der)
	must(err)
	roots.AddCert(cert)
	return certPath, keyPath, roots
}
func require(ok bool, message string) {
	if !ok {
		panic(message)
	}
}
func must(err error) {
	if err != nil {
		panic(err)
	}
}
