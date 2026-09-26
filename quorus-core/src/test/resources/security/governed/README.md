# Governed TLS test fixtures

Test-only certificates for the governed HTTPS boundary tests (plan item RT-03b). They are not
production PKI. Keystore password: `changeit`.

| File | Contents |
|---|---|
| `ca.pem` | "Quorus Governed Test CA" certificate. Tests trust it as the base trust anchor. Its private key was discarded after signing |
| `transfer.p12` | Key and chain (leaf plus CA) for `transfer.quorus.test`. SAN `DNS:transfer.quorus.test` only, with no IP address, so a pinned connection to 127.0.0.1 must pass real hostname verification |
| `other.p12` | Key and chain for `other.quorus.test`, signed by the same CA. Used to prove that a certificate for the wrong name is rejected |
| `unapproved-ca.pem` | A second, unrelated CA certificate. Its fingerprint is used as the only approved CA, to prove CA restriction |

The `.test` names are reserved (RFC 2606) and never resolve, so a test only reaches the server
through the approved-address pin.

Regenerate with JDK `keytool` (valid 10 years):

```sh
P=changeit
keytool -genkeypair -alias ca -keyalg EC -groupname secp256r1 -dname "CN=Quorus Governed Test CA" \
  -ext bc:c -validity 3650 -storetype PKCS12 -keystore ca.p12 -storepass $P
keytool -exportcert -rfc -alias ca -keystore ca.p12 -storepass $P -file ca.pem
for name in transfer other; do
  keytool -genkeypair -alias $name -keyalg EC -groupname secp256r1 -dname "CN=$name.quorus.test" \
    -validity 3650 -storetype PKCS12 -keystore $name.p12 -storepass $P
  keytool -certreq -alias $name -keystore $name.p12 -storepass $P -file $name.csr
  keytool -gencert -alias ca -keystore ca.p12 -storepass $P -infile $name.csr -outfile $name-signed.pem \
    -rfc -validity 3650 -ext "SAN=dns:$name.quorus.test" -ext ku:c=digitalSignature -ext eku=serverAuth
  keytool -importcert -noprompt -alias ca -file ca.pem -keystore $name.p12 -storepass $P
  keytool -importcert -alias $name -file $name-signed.pem -keystore $name.p12 -storepass $P
done
keytool -genkeypair -alias ca -keyalg EC -groupname secp256r1 -dname "CN=Quorus Unapproved Test CA" \
  -ext bc:c -validity 3650 -storetype PKCS12 -keystore unapproved-ca.p12 -storepass $P
keytool -exportcert -rfc -alias ca -keystore unapproved-ca.p12 -storepass $P -file unapproved-ca.pem
rm ca.p12 unapproved-ca.p12 *.csr *-signed.pem
```
