package main

import (
	"fmt"
	"net"
)

func main() {
	// The IP address you want to look up
	ip := "192.178.211.188"

	// net.LookupAddr returns a slice of hostnames and an error
	names, err := net.LookupAddr(ip)
	if err != nil {
		fmt.Printf("Lookup failed: %v\n", err)
		return
	}

	for _, name := range names {
		fmt.Println(name) // Output: dns.google.
	}
}
