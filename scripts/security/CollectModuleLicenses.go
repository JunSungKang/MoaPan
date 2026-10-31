// Collects notices from the checksummed Go module cache; never copies project source or credentials.
package main

import (
 "crypto/sha256"
 "encoding/hex"
 "encoding/json"
 "fmt"
 "io"
 "os"
 "os/exec"
 "path/filepath"
 "sort"
 "strings"
)

type module struct { Path, Version, Dir string; Main bool; Replace *module }
type notice struct { Name, SHA256 string }
type component struct { Module, Version string; Notices []notice }

func main(){
 if len(os.Args)!=2 { panic("notice output directory required") }
 target,err:=filepath.Abs(os.Args[1]);must(err);must(os.MkdirAll(target,0755))
 raw,err:=exec.Command("go","list","-m","-json","all").Output();must(err)
 decoder:=json.NewDecoder(strings.NewReader(string(raw)));var entries []component
 for {var m module;err=decoder.Decode(&m);if err==io.EOF{break};must(err);if m.Main{continue};if m.Replace!=nil{
  // Exactly one source-verified local backport is allowed; other replacements fail closed.
  if m.Path!="github.com/go-jose/go-jose/v3"||m.Version!="v3.0.5"||m.Replace.Path!="./patched-jose"||m.Replace.Dir!="/proxy/patched-jose"{panic("unreviewed module replacement")}
  verify:=exec.Command("sha256sum","-c","/proxy/patches/modified.sha256");verify.Dir=m.Replace.Dir;if output,err:=verify.CombinedOutput();err!=nil{panic(fmt.Sprintf("backport source mismatch: %v %s",err,output))}
  m.Dir=m.Replace.Dir;m.Version+="+moapan.security.1"
 };if m.Dir==""{continue};entries=append(entries,collect(target,m.Path,m.Version,m.Dir))}
 goroot,err:=exec.Command("go","env","GOROOT").Output();must(err);entries=append(entries,collect(target,"Go standard library","1.27.3",strings.TrimSpace(string(goroot))))
 sort.Slice(entries,func(i,j int)bool{return entries[i].Module<entries[j].Module})
 data,err:=json.MarshalIndent(entries,"","  ");must(err);must(os.WriteFile(filepath.Join(target,"modules.json"),data,0644));fmt.Println("Module notice inventory:",len(entries))
}
func must(err error){if err!=nil{panic(err)}}
func collect(target,name,version,dir string)component{
 result:=component{Module:name,Version:version,Notices:[]notice{}};files,err:=os.ReadDir(dir);must(err)
 id:=sha256.Sum256([]byte(name+"@"+version));out:=filepath.Join(target,hex.EncodeToString(id[:])[:24]);must(os.MkdirAll(out,0755))
 for _,f:=range files {upper:=strings.ToUpper(f.Name());if !(strings.HasPrefix(upper,"LICENSE")||strings.HasPrefix(upper,"COPYING")||strings.HasPrefix(upper,"NOTICE")||upper=="PATENTS"||upper=="COPYRIGHT"||upper=="AUTHORS"){continue};info,err:=f.Info();must(err);if !info.Mode().IsRegular(){continue};if info.Size()>2*1024*1024{panic("oversized notice")};data,err:=os.ReadFile(filepath.Join(dir,f.Name()));must(err);sum:=sha256.Sum256(data);must(os.WriteFile(filepath.Join(out,f.Name()),data,0644));result.Notices=append(result.Notices,notice{Name:filepath.Join(filepath.Base(out),f.Name()),SHA256:hex.EncodeToString(sum[:])})}
 return result
}
