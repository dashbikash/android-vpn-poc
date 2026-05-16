from dns import reversename, resolver
# Example querying a TXT record for more info
addr = reversename.from_address("8.8.8.8")
answers = resolver.resolve(addr, "PTR") 
for val in answers:
    print(val.to_text())
