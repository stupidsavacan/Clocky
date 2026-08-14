import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.Base64;
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

public final class GenerateJks {
    private static String randomPassword(int bytes) {
        byte[] value = new byte[bytes];
        new SecureRandom().nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: GenerateJks <output.jks> <output-dir>");
        }
        Path jks = Path.of(args[0]);
        Path outDir = Path.of(args[1]);
        Files.createDirectories(jks.toAbsolutePath().getParent());
        Files.createDirectories(outDir);

        String alias = "clocky";
        String storePass = randomPassword(24);
        String keyPass = randomPassword(24);

        CertAndKeyGen keyGen = new CertAndKeyGen("RSA", "SHA256withRSA");
        keyGen.generate(3072);
        X500Name subject = new X500Name("CN=Clocky MVP, OU=Development, O=Clocky, C=JP");
        long validitySeconds = 25L * 365L * 24L * 60L * 60L;
        Certificate cert = keyGen.getSelfCertificate(subject, validitySeconds);

        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, storePass.toCharArray());
        ks.setKeyEntry(alias, keyGen.getPrivateKey(), keyPass.toCharArray(), new Certificate[]{cert});
        try (FileOutputStream fos = new FileOutputStream(jks.toFile())) {
            ks.store(fos, storePass.toCharArray());
        }

        Files.writeString(outDir.resolve("storepass.txt"), storePass, StandardCharsets.UTF_8);
        Files.writeString(outDir.resolve("keypass.txt"), keyPass, StandardCharsets.UTF_8);
        String info = "alias=" + alias + "\n" +
                "storeType=JKS\n" +
                "keyAlgorithm=RSA-3072\n" +
                "signatureAlgorithm=SHA256withRSA\n" +
                "subject=CN=Clocky MVP, OU=Development, O=Clocky, C=JP\n" +
                "validityYears=25\n" +
                "storePassword=" + storePass + "\n" +
                "keyPassword=" + keyPass + "\n";
        Files.writeString(outDir.resolve("Clocky-signing-info.txt"), info, StandardCharsets.UTF_8);
        System.out.println("JKS_CREATED=" + jks.toAbsolutePath());
        System.out.println("ALIAS=" + alias);
    }
}
