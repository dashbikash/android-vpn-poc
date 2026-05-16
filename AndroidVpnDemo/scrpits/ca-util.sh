#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

# Define the file names for the output keys
PRIVATE_KEY="ec_private.pem"
PUBLIC_KEY="ec_public.pem"
CURVE_NAME="prime256v1" # This is secp256r1

echo "Generating ECDSA Key Pair using curve: $CURVE_NAME"

# Step 1: Generate the Private Key
# -name specifies the curve
# -genkey tells it to generate the key
# -noout prevents the parameters from being printed to the screen
# -out specifies the output file
openssl ecparam -name "$CURVE_NAME" -genkey -noout -out "$PRIVATE_KEY"

echo "Private key generated and saved to: $PRIVATE_KEY"

# Step 2: Extract the Public Key from the Private Key
# -in specifies the private key file
# -pubout tells it to output the public key
# -out specifies the output file
openssl ec -in "$PRIVATE_KEY" -pubout -out "$PUBLIC_KEY"

echo "Public key extracted and saved to: $PUBLIC_KEY"
echo "-----------------------------------"
echo "Done! Make sure to keep your private key secure."