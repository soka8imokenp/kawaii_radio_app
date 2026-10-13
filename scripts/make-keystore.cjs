// One-off: creates android/app/kr01-release.p12, the signing key every release APK is signed with.
// Keeping one key is what lets a new APK install over the old one (in-app updates keep user data).
// Usage: node scripts/make-keystore.cjs
const forge = require('node-forge');
const fs = require('node:fs');
const path = require('node:path');

const out = path.join(__dirname, '..', 'android', 'app', 'kr01-release.p12');
if (fs.existsSync(out)) {
  console.log('keystore already exists, not overwriting:', out);
  process.exit(0);
}

const keys = forge.pki.rsa.generateKeyPair(2048);
const cert = forge.pki.createCertificate();
cert.publicKey = keys.publicKey;
cert.serialNumber = '01' + forge.util.bytesToHex(forge.random.getBytesSync(8));
cert.validity.notBefore = new Date();
cert.validity.notAfter = new Date();
cert.validity.notAfter.setFullYear(cert.validity.notBefore.getFullYear() + 30);
const attrs = [{ name: 'commonName', value: 'Kawaii Radio KR-01' }, { name: 'organizationName', value: 'Kawaii.uz' }, { name: 'countryName', value: 'UZ' }];
cert.setSubject(attrs);
cert.setIssuer(attrs);
cert.sign(keys.privateKey, forge.md.sha256.create());

const p12 = forge.pkcs12.toPkcs12Asn1(keys.privateKey, [cert], 'kawaii-kr01', { algorithm: '3des', friendlyName: 'kr01' });
fs.writeFileSync(out, Buffer.from(forge.asn1.toDer(p12).getBytes(), 'binary'));
console.log('written', out);
