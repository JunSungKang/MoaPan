package json
import("strings";"testing")
func TestMoapanRejectExcessiveJSONDepth(t *testing.T){var v interface{};if err:=Unmarshal([]byte(strings.Repeat("[",10001)+"0"+strings.Repeat("]",10001)),&v);err==nil{t.Fatal("excessive depth accepted")}}
