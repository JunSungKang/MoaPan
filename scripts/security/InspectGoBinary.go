// Reads the Go function table even when the native symbol table was stripped.
// Module-level findings alone do not prove an optional subpackage is linked.
package main

import (
	"crypto/sha256"
	"debug/elf"
	"debug/gosym"
	"debug/macho"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"strings"
)

func main() {
	if len(os.Args) < 3 {
		panic("binary and package prefixes required")
	}
	file := os.Args[1]
	raw, err := os.ReadFile(file)
	must(err)
	var pcln []byte
	var text uint64
	if f, err := elf.Open(file); err == nil {
		defer f.Close()
		p, t := f.Section(".gopclntab"), f.Section(".text")
		if p == nil || t == nil {
			panic("missing Go ELF sections")
		}
		pcln, err = p.Data()
		must(err)
		text = t.Addr
	} else if f, err := macho.Open(file); err == nil {
		defer f.Close()
		p, t := f.Section("__gopclntab"), f.Section("__text")
		if p == nil || t == nil {
			panic("missing Go Mach-O sections")
		}
		pcln, err = p.Data()
		must(err)
		text = t.Addr
	} else {
		panic("unsupported or unrecognized Go executable")
	}
	table, err := gosym.NewTable(nil, gosym.NewLineTable(pcln, text))
	must(err)
	if len(table.Funcs) < 10000 {
		panic("incomplete function inventory for reviewed proxy/scanner")
	}
	matches := map[string][]string{}
	for _, prefix := range os.Args[2:] {
		matches[prefix] = []string{}
		for _, function := range table.Funcs {
			if strings.HasPrefix(function.Name, prefix+".") || strings.HasPrefix(function.Name, prefix+"/") {
				matches[prefix] = append(matches[prefix], function.Name)
			}
		}
	}
	sum := sha256.Sum256(raw)
	result := struct {
		Binary    string
		SHA256    string
		Functions int
		Matches   map[string][]string
	}{file, hex.EncodeToString(sum[:]), len(table.Funcs), matches}
	data, err := json.MarshalIndent(result, "", "  ")
	must(err)
	fmt.Println(string(data))
}
func must(err error) {
	if err != nil {
		panic(err)
	}
}
