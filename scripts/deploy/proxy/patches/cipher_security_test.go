package josecipher
import("crypto/aes";"testing")
func TestMoapanEmptyCBCPaddingRejected(t *testing.T){if _,err:=unpadBuffer(nil,16);err==nil{t.Fatal("empty CBC buffer accepted")}}
func TestMoapanInvalidCBCKeyRejected(t *testing.T){if _,err:=NewCBCHMAC(make([]byte,31),aes.NewCipher);err==nil{t.Fatal("invalid CBC key accepted")}}
