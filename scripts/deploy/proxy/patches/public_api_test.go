/*-
 * Copyright 2014 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Modified by MOAPAN: official a0111f8 and 25b55fe regressions adapted to v3 parsing API.
package jose
import("encoding/base64";"strings";"testing";"github.com/go-jose/go-jose/v3/json")
func TestEmbeddedPrivateJWKRejected(t *testing.T) {
	privateJWK, err := fromRsaPrivateKey(rsaTestKey)
	if err != nil {
		t.Fatal(err)
	}
	publicJWK := fromRsaPublicKey(&rsaTestKey.PublicKey)
	strayJWK := fromRsaPublicKey(&rsaTestKey.PublicKey)
	strayJWK.P, strayJWK.Q = privateJWK.P, privateJWK.Q

	testCases := []struct {
		name        string
		jwk         *rawJSONWebKey
		unprotected bool // place the jwk in the unprotected header instead
		wantErr     bool
	}{
		{name: "public key", jwk: publicJWK},
		{name: "private key", jwk: privateJWK, wantErr: true},
		{name: "private key in unprotected header", jwk: privateJWK, unprotected: true, wantErr: true},
		{name: "symmetric key", jwk: &rawJSONWebKey{Kty: "oct", K: newBuffer([]byte("111"))}, wantErr: true},
		{name: "public key with stray private members", jwk: strayJWK, wantErr: true},
	}

	payload := newBuffer([]byte("Lorem ipsum dolor sit amet"))
	signature := newBuffer([]byte("not a real signature"))

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			marshal := func(v interface{}) []byte {
				t.Helper()
				b, err := json.Marshal(v)
				if err != nil {
					t.Fatal(err)
				}
				return b
			}

			alg := json.RawMessage(`"RS256"`)
			jwk := json.RawMessage(marshal(tc.jwk))
			protected := rawHeader{headerAlgorithm: &alg}
			var unprotected *rawHeader
			if tc.unprotected {
				unprotected = &rawHeader{headerJWK: &jwk}
			} else {
				protected[headerJWK] = &jwk
			}
			encProtected := newBuffer(marshal(protected))

			messages := map[string]string{
				"flattened": string(marshal(rawJSONWebSignature{
					Payload:   payload,
					Protected: encProtected,
					Header:    unprotected,
					Signature: signature,
				})),
				"general": string(marshal(rawJSONWebSignature{
					Payload: payload,
					Signatures: []rawSignatureInfo{{
						Protected: encProtected,
						Header:    unprotected,
						Signature: signature,
					}},
				})),
			}
			if !tc.unprotected {
				messages["compact"] = encProtected.base64() + "." + payload.base64() + "." + signature.base64()
			}

			for name, msg := range messages {
				_, err := ParseSigned(msg)
				if tc.wantErr {
					if err == nil || !strings.Contains(err.Error(), "JWK contains private parameters") {
						t.Errorf("%s: expected private parameters error, got %v", name, err)
					}
				} else if err != nil {
					t.Errorf("%s: unexpected error: %v", name, err)
				}
			}
		})
	}
}

func TestGeneralSerializationUnprotectedHeader(t *testing.T) {
	// The unprotected header of each signature must be reflected in the parsed
	// Header and Unprotected fields for both serializations.
	protected := base64.RawURLEncoding.EncodeToString([]byte(`{"alg":"RS256"}`))
	payload := base64.RawURLEncoding.EncodeToString([]byte("Lorem ipsum dolor sit amet"))
	signature := base64.RawURLEncoding.EncodeToString([]byte("not a real signature"))
	unprotected := `"header":{"kid":"from-unprotected"}`

	messages := map[string]string{
		"flattened": `{"payload":"` + payload + `","protected":"` + protected + `",` + unprotected + `,"signature":"` + signature + `"}`,
		"general":   `{"payload":"` + payload + `","signatures":[{"protected":"` + protected + `",` + unprotected + `,"signature":"` + signature + `"}]}`,
	}
	for name, msg := range messages {
		t.Run(name, func(t *testing.T) {
			obj, err := ParseSigned(msg)
			if err != nil {
				t.Fatal(err)
			}
			sig := obj.Signatures[0]
			if sig.Header.KeyID != "from-unprotected" {
				t.Errorf("Header.KeyID = %q, want %q", sig.Header.KeyID, "from-unprotected")
			}
			if sig.Unprotected.KeyID != "from-unprotected" {
				t.Errorf("Unprotected.KeyID = %q, want %q", sig.Unprotected.KeyID, "from-unprotected")
			}
		})
	}
}


func TestJWERejectsShortGCMCEKForDeclaredContentEncryption(t *testing.T) {
	tests := []struct {
		name     string
		key      []byte
		declared ContentEncryption
		actual   ContentEncryption
	}{
		{
			name:     "A256GCM with A128GCM key",
			key:      []byte("0123456789abcdef"),
			declared: A256GCM,
			actual:   A128GCM,
		},
		{
			name:     "A192GCM with A128GCM key",
			key:      []byte("0123456789abcdef"),
			declared: A192GCM,
			actual:   A128GCM,
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			plaintext := []byte("content encrypted under a shorter CEK")
			token := jweJSONWithMismatchedCEK(
				t,
				test.key,
				plaintext,
				test.declared,
				test.actual,
			)

			parsed, err := ParseEncrypted(token)
			if err != nil {
				t.Fatalf("ParseEncryptedJSON: %v", err)
			}

			_, err = parsed.Decrypt(test.key)
			if err == nil {
				t.Fatal("Decrypt accepted content encrypted under a shorter CEK")
			}

			_, _, _, err = parsed.DecryptMulti(test.key)
			if err == nil {
				t.Fatal("DecryptMulti accepted content encrypted under a shorter CEK")
			}
		})
	}
}

func TestJWERejectsShortCBCHMACCEKForDeclaredContentEncryption(t *testing.T) {
	tests := []struct {
		name     string
		key      []byte
		declared ContentEncryption
	}{
		{
			name:     "A256CBC-HS512 with A128CBC-HS256 key",
			key:      []byte("0123456789abcdef0123456789abcdef"),
			declared: A256CBC_HS512,
		},
		{
			name:     "A192CBC-HS384 with A128CBC-HS256 key",
			key:      []byte("0123456789abcdef0123456789abcdef"),
			declared: A192CBC_HS384,
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			plaintext := []byte("content encrypted under a shorter CEK")
			token := jweJSONWithMismatchedCBCHMACCEK(t, test.key, plaintext, test.declared)

			parsed, err := ParseEncrypted(token)
			if err != nil {
				t.Fatalf("ParseEncryptedJSON: %v", err)
			}

			_, err = parsed.Decrypt(test.key)
			if err == nil {
				t.Fatal("Decrypt accepted content encrypted under a shorter CEK")
			}

			_, _, _, err = parsed.DecryptMulti(test.key)
			if err == nil {
				t.Fatal("DecryptMulti accepted content encrypted under a shorter CEK")
			}
		})
	}
}

func jweJSONWithMismatchedCEK(
	t *testing.T,
	key []byte,
	plaintext []byte,
	declared ContentEncryption,
	actual ContentEncryption,
) string {
	t.Helper()

	protected := rawHeader{}
	err := protected.set(headerAlgorithm, DIRECT)
	if err != nil {
		t.Fatalf("set alg: %v", err)
	}
	err = protected.set(headerEncryption, declared)
	if err != nil {
		t.Fatalf("set enc: %v", err)
	}

	protectedJSON := mustSerializeJSON(protected)
	protectedB64 := newBuffer(protectedJSON).base64()
	cipher := getContentCipher(actual)
	parts, err := cipher.encrypt(key, []byte(protectedB64), plaintext)
	if err != nil {
		t.Fatalf("encrypt content: %v", err)
	}

	raw := rawJSONWebEncryption{
		Protected:  newBuffer(protectedJSON),
		Iv:         newBuffer(parts.iv),
		Ciphertext: newBuffer(parts.ciphertext),
		Tag:        newBuffer(parts.tag),
	}
	return string(mustSerializeJSON(raw))
}

func jweJSONWithMismatchedCBCHMACCEK(
	t *testing.T,
	key []byte,
	plaintext []byte,
	declared ContentEncryption,
) string {
	t.Helper()

	protected := rawHeader{}
	err := protected.set(headerAlgorithm, DIRECT)
	if err != nil {
		t.Fatalf("set alg: %v", err)
	}
	err = protected.set(headerEncryption, declared)
	if err != nil {
		t.Fatalf("set enc: %v", err)
	}

	protectedJSON := mustSerializeJSON(protected)
	protectedB64 := newBuffer(protectedJSON).base64()
	cipher := getContentCipher(A128CBC_HS256)
	parts, err := cipher.encrypt(key, []byte(protectedB64), plaintext)
	if err != nil {
		t.Fatalf("encrypt content: %v", err)
	}

	actualTagLen := len(parts.tag)
	declaredTagLen := cbcTagLength(t, declared)
	if declaredTagLen < actualTagLen {
		t.Fatalf("declared tag length %d is shorter than actual tag length %d",
			declaredTagLen, actualTagLen)
	}

	prefixLen := declaredTagLen - actualTagLen
	if len(parts.ciphertext) < prefixLen {
		t.Fatalf("ciphertext length %d is shorter than tag prefix length %d",
			len(parts.ciphertext), prefixLen)
	}

	// Move ciphertext bytes into the tag field so the outer declared-tag length check
	// passes while the inner A128CBC-HS256 AEAD still sees its original tag.
	ciphertextPart := parts.ciphertext[:len(parts.ciphertext)-prefixLen]
	tagPart := append([]byte{}, parts.ciphertext[len(parts.ciphertext)-prefixLen:]...)
	tagPart = append(tagPart, parts.tag...)

	raw := rawJSONWebEncryption{
		Protected:  newBuffer(protectedJSON),
		Iv:         newBuffer(parts.iv),
		Ciphertext: newBuffer(ciphertextPart),
		Tag:        newBuffer(tagPart),
	}
	return string(mustSerializeJSON(raw))
}

func cbcTagLength(t *testing.T, enc ContentEncryption) int {
	t.Helper()

	switch enc {
	case A128CBC_HS256:
		return 16
	case A192CBC_HS384:
		return 24
	case A256CBC_HS512:
		return 32
	default:
		t.Fatalf("unsupported CBC enc %q", enc)
		return 0
	}
}

