/**
 * dx-signer
 *
 * Copyright 2022 北京顶象技术有限公司
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dx.channel;

import com.android.apksig.ApkSigner;
import com.android.apksig.DefaultApkSignerEngine;
import com.android.apksig.util.DataSources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;

import dx.zip.AxmlFastZipOut;
import dx.zip.FastZipEntry;
import dx.zip.FastZipIn;

public class ApkSigns {
    private static final Logger log = LoggerFactory.getLogger(ApkSigns.class);
    private static final Pattern stripPattern = Pattern.compile("^META-INF/(.*)[.](SF|RSA|DSA|EC)$");

    public static void zipAlign(Path inputApk, Path outApk, boolean deleteSignature) throws IOException {
        try (FastZipIn in = new FastZipIn(inputApk.toFile())) {
            Path parent = outApk.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            ByteBuffer manifest = null;
            for (FastZipEntry entry : in.entries()) {
                if (entry.utf8Name().equals("AndroidManifest.xml")) {
                    manifest = in.getUncompressed(entry);
                    break;
                }
            }

            try (AxmlFastZipOut out = new AxmlFastZipOut(outApk.toFile())) {
                if (manifest != null) {
                    out.initByAndroidManifestContent(manifest);
                }
                List<FastZipEntry> entries = in.entries();
                List<FastZipEntry> zipEntryList = cleanup(entries, deleteSignature);
                out.copy(in, zipEntryList);
            }
        } catch (DataFormatException e) {
            throw new IOException(e);
        }
    }

    public static List<FastZipEntry> cleanup(List<FastZipEntry> entries, boolean deleteSignature) {
        List<FastZipEntry> zipEntryList = new ArrayList<>();
        for (FastZipEntry e : entries) {
            String name = e.utf8Name();
            if (name.endsWith("/")) {
                // skip dir
                continue;
            }
            if (deleteSignature) {
                if (name.equals(JarFile.MANIFEST_NAME) || stripPattern.matcher(name).matches()) {
                    continue;
                }
            }
            zipEntryList.add(e);
        }
        return zipEntryList;
    }

    public static KeyStore.PrivateKeyEntry loadKey(byte[] ksContent, String ksPass, String keyAlias, String keyPass) throws IOException {
        return loadKey0(ksContent, ksPass, keyAlias, keyPass);
    }

    public static KeyStore.PrivateKeyEntry loadKey(Path ks, String ksPass, String keyAlias, String keyPass) throws IOException {
        return loadKey0(ks, ksPass, keyAlias, keyPass);
    }

    private static KeyStore.PrivateKeyEntry loadKey0(Object ks, String ksPass, String keyAlias, String keyPass) throws IOException {
        KeyStore.PrivateKeyEntry privateKeyEntry = null;

        Set<String> passwordList = new TreeSet<>();
        if (ksPass != null) {
            passwordList.add(ksPass);
        }
        if (keyPass != null) {
            passwordList.add(keyPass);
        }
        passwordList.add("android");
        KeyStore keyStore = loadKeyStore0(ks, passwordList);

        List<String> aliasesList = new ArrayList<>();
        try {
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (keyStore.isKeyEntry(alias)) {
                    aliasesList.add(alias);
                }
            }
            if (keyAlias != null) {
                aliasesList.remove(keyAlias);
                aliasesList.add(0, keyAlias);
            }
        } catch (
                KeyStoreException e) {
            throw new RuntimeException(e);
        }


        for (String alias : aliasesList) {
            for (String pass : passwordList) {
                if (pass == null){
                    continue;
                }
                try {
                    KeyStore.ProtectionParameter param = new KeyStore.PasswordProtection(pass.toCharArray());
                    privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(alias, param);
                    break;
                } catch (Exception ignore) {
                }
            }
            if (privateKeyEntry != null) {
                break;
            }
        }
        if (privateKeyEntry == null) {
            throw new IOException("fail load key from keystore");
        }
        X509Certificate certificate = (X509Certificate) privateKeyEntry.getCertificate();

        log.info("loaded certificate {}", certificate.getSubjectDN());

        try {
            byte[] encoded = certificate.getEncoded();
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            String md5hex = toHexString(md5.digest(encoded));

            log.info("cert md5: {}", md5hex);

            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            String sha256hex = toHexString(sha256.digest(encoded));

            log.info("cert sha256: {}", sha256hex);
        } catch (Exception ignore) {

        }

        return privateKeyEntry;
    }

    private static String toHexString(byte[] digest) {
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    private static KeyStore loadKeyStore0(Object ks, Set<String> passwordList) throws IOException {
        if (ks instanceof byte[]) {
            return loadKeyStore((byte[]) ks, passwordList);
        } else {
            return loadKeyStore(Files.readAllBytes((Path) ks), passwordList);
        }
    }

    public static KeyStore loadKeyStore(Path ks, Set<String> passwordList) throws IOException {
        return loadKeyStore(Files.readAllBytes(ks), passwordList);
    }
    public static KeyStore loadKeyStore(byte[] ksContent, Set<String> passwordList0) throws IOException {
        List<String> storeTypes = Arrays.asList("PKCS12", "JKS", KeyStore.getDefaultType());

        Set<String> passwordList = new HashSet<>();
        if (passwordList0 != null) {
            passwordList.addAll(passwordList0);
        }
        passwordList.add("android");

        for (String type : storeTypes) {
            for (String password : passwordList) {
                if (password == null) {
                    continue;
                }
                try {
                    KeyStore keyStore = KeyStore.getInstance(type);
                    keyStore.load(new ByteArrayInputStream(ksContent), password.toCharArray());
                    log.info("loaded keystore with type {}", type);
                    return keyStore;
                } catch (Exception ignore) {
                }
            }
        }

        log.warn("fail load keystore with type {}, bad type or bad password", storeTypes);
        throw new IOException("fail to open keystore");
    }


    public static void sign(Path in, Path out,
                            Path ks, String ksPass, String keyAlias, String keyPass,
                            boolean isAAB
    ) throws IOException {
        Path apkUnsigned = Files.createTempFile(out.getParent(), "unsigned", ".apk");
        try {
            zipAlign(in, apkUnsigned, true);
            KeyStore.PrivateKeyEntry privateKeyEntry1 = loadKey(ks, ksPass, keyAlias, keyPass);
            sign(apkUnsigned, out, privateKeyEntry1, isAAB);
        } finally {
            Files.deleteIfExists(apkUnsigned);
        }
    }

    public static void sign(Path in, Path out, KeyStore.PrivateKeyEntry key, boolean isAAB) throws IOException {
        sign(in, out, key, isAAB, SigningOptions.DEFAULT);
    }

    public static void sign(Path in, Path out, KeyStore.PrivateKeyEntry key, boolean isAAB,
                            SigningOptions options) throws IOException {
        options.validate(isAAB, false);
        // 覆盖 APK 时清除旧的伴随签名，避免留下与新 APK 不匹配的 idsig。
        Files.deleteIfExists(v4SignaturePath(out));
        List<X509Certificate> x509Certificates = new ArrayList<>();
        for (Certificate c : key.getCertificateChain()) {
            x509Certificates.add((X509Certificate) c);
        }
        ApkSigner.SignerConfig signerConfig =
                new ApkSigner.SignerConfig.Builder(
                        "cert", key.getPrivateKey(), x509Certificates)
                        .build();
        ApkSigner.Builder apkSignerBuilder =
                new ApkSigner.Builder(Collections.singletonList(signerConfig))
                        .setOtherSignersSignaturesPreserved(false)
                        .setV3SigningEnabled(!isAAB && options.isV3Enabled())
                        .setV4SigningEnabled(false)
                        .setInputApk(in.toFile())
                        .setOutputApk(out.toFile());

        apkSignerBuilder.setV1SigningEnabled(options.isV1Enabled());
        apkSignerBuilder.setV2SigningEnabled(options.isV2Enabled());
        int minSdkVersion = isAAB ? 26 : 0;
        if (minSdkVersion > 0) {
            apkSignerBuilder.setMinSdkVersion(minSdkVersion);
        }
        ApkSigner signer = apkSignerBuilder.build();
        try {
            signer.sign();
            if (options.isV4Enabled()) {
                writeV4Signature(out, key, options);
            }
        } catch (RuntimeException | IOException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Path v4SignaturePath(Path apk) {
        return apk.resolveSibling(apk.getFileName().toString() + ".idsig");
    }

    /**
     * 基于最终 APK 字节生成 V4；多渠道必须在 Walle 写入之后调用。
     */
    public static void writeV4Signature(Path apk, KeyStore.PrivateKeyEntry key,
                                        SigningOptions options) throws IOException {
        options.validate(false, false);
        Path signature = v4SignaturePath(apk).toAbsolutePath();
        Path temporary = Files.createTempFile(signature.getParent(), "v4-signature-", ".idsig");
        try {
            List<X509Certificate> certificates = new ArrayList<>();
            for (Certificate certificate : key.getCertificateChain()) {
                certificates.add((X509Certificate) certificate);
            }
            DefaultApkSignerEngine.SignerConfig config = new DefaultApkSignerEngine.SignerConfig.Builder(
                    "cert", key.getPrivateKey(), certificates).build();
            // V4 面向 Android 11 及以上，独立生成签名不会更改已有 APK 签名或渠道信息。
            try (DefaultApkSignerEngine engine = new DefaultApkSignerEngine.Builder(
                    Collections.singletonList(config), 30)
                    .setV1SigningEnabled(false)
                    .setV2SigningEnabled(options.isV2Enabled())
                    .setV3SigningEnabled(options.isV3Enabled()).build();
                 RandomAccessFile input = new RandomAccessFile(apk.toFile(), "r")) {
                engine.signV4(DataSources.asDataSource(input), temporary.toFile(), false);
            }
            Files.move(temporary, signature, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception exception) {
            throw new IOException("生成 V4 签名失败: " + exception.getMessage(), exception);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
