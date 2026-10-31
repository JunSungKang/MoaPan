package jose

import (
 "crypto/rand"
 "crypto/rsa"
 "math/big"
 "strings"
 "testing"
 "time"
 "github.com/go-jose/go-jose/v3/json"
)

func TestMoapanOversizedRSARejectedBeforeValidation(t *testing.T) {
 n:=new(big.Int).Lsh(big.NewInt(1),8191)
 input,_:=json.Marshal(rawJSONWebKey{Kty:"RSA",N:newBuffer(n.Bytes()),E:newBufferFromInt(65537)})
 var key JSONWebKey
 if err:=json.Unmarshal(input,&key);err==nil||!strings.Contains(err.Error(),"modulus too large"){t.Fatal("oversized public modulus was not rejected")}
}
func TestMoapanDegeneratePrimesRejectedWithoutExpensiveValidation(t *testing.T) {
 n:=new(big.Int).Lsh(big.NewInt(1),2048);p:=new(big.Int).Lsh(big.NewInt(1),16383);p.SetBit(p,0,1)
 input,_:=json.Marshal(rawJSONWebKey{Kty:"RSA",N:newBuffer(n.Bytes()),E:newBufferFromInt(65537),D:newBufferFromInt(3),P:newBuffer(p.Bytes()),Q:newBufferFromInt(3)})
 start:=time.Now();var key JSONWebKey
 if err:=json.Unmarshal(input,&key);err==nil||!strings.Contains(err.Error(),"p * q != n"){t.Fatal("degenerate RSA primes not rejected")}
 if time.Since(start)>time.Second{t.Fatal("degenerate key performed expensive validation")}
}
func TestMoapanPrivateEPKAndJWKHeadersRejected(t *testing.T) {
 private:=json.RawMessage(`{"kty":"oct","k":"AQ"}`)
 if _,err:=(rawHeader{headerEPK:&private}).getEPK();err==nil||!strings.Contains(err.Error(),"private parameters"){t.Fatal("private EPK accepted")}
 if _,err:=(rawHeader{headerJWK:&private}).sanitized();err==nil||!strings.Contains(err.Error(),"private parameters"){t.Fatal("private JWK accepted")}
}
func TestMoapanValidRSAJWKRoundTrip(t *testing.T) {
 original,err:=rsa.GenerateKey(rand.Reader,2048);if err!=nil{t.Fatal(err)}
 data,err:=json.Marshal(JSONWebKey{Key:original});if err!=nil{t.Fatal(err)}
 var decoded JSONWebKey;if err=json.Unmarshal(data,&decoded);err!=nil{t.Fatal(err)}
 if !decoded.Valid(){t.Fatal("valid private key rejected")}
}
