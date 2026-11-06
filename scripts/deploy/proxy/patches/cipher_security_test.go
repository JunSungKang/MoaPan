package josecipher
import("crypto/aes";"testing")
func TestDamGoX2EmptyCBCPaddingRejected(t *testing.T){if _,err:=unpadBuffer(nil,16);err==nil{t.Fatal("empty CBC buffer accepted")}}
func TestDamGoX2InvalidCBCKeyRejected(t *testing.T){if _,err:=NewCBCHMAC(make([]byte,31),aes.NewCipher);err==nil{t.Fatal("invalid CBC key accepted")}}
