package com.pydlove.workcove.identity.infrastructure.mail;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

final class WorkCoveMailTls {

    private WorkCoveMailTls() {
    }

    static SSLSocketFactory socketFactory(String trustStorePath, String password) {
        Path path = Path.of(trustStorePath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("SMTP 信任库不存在：" + path);
        }

        try (var input = Files.newInputStream(path)) {
            KeyStore customStore = KeyStore.getInstance("PKCS12");
            customStore.load(input, password.toCharArray());

            TrustManagerFactory systemFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            systemFactory.init((KeyStore) null);

            TrustManagerFactory customFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            customFactory.init(customStore);

            X509ExtendedTrustManager system = firstManager(systemFactory);
            X509ExtendedTrustManager custom = firstManager(customFactory);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new CombinedTrustManager(system, custom)}, null);
            return context.getSocketFactory();
        } catch (Exception exception) {
            throw new IllegalStateException("无法加载 SMTP 信任库：" + path, exception);
        }
    }

    private static X509ExtendedTrustManager firstManager(TrustManagerFactory factory) {
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509ExtendedTrustManager extended) return extended;
        }
        throw new IllegalStateException("当前 JDK 没有可用的 X509 信任管理器");
    }

    private static final class CombinedTrustManager extends X509ExtendedTrustManager {

        private final X509ExtendedTrustManager system;
        private final X509ExtendedTrustManager custom;

        private CombinedTrustManager(X509ExtendedTrustManager system,
                                     X509ExtendedTrustManager custom) {
            this.system = system;
            this.custom = custom;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType,
                                       Socket socket) throws CertificateException {
            checkClient(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType,
                                       Socket socket) throws CertificateException {
            checkServer(chain, authType);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType,
                                       SSLEngine engine) throws CertificateException {
            try {
                system.checkClientTrusted(chain, authType, engine);
            } catch (CertificateException first) {
                custom.checkClientTrusted(chain, authType, engine);
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType,
                                       SSLEngine engine) throws CertificateException {
            try {
                system.checkServerTrusted(chain, authType, engine);
            } catch (CertificateException first) {
                custom.checkServerTrusted(chain, authType, engine);
            }
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            checkClient(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            checkServer(chain, authType);
        }

        private void checkClient(X509Certificate[] chain, String authType)
                throws CertificateException {
            try {
                system.checkClientTrusted(chain, authType);
            } catch (CertificateException first) {
                custom.checkClientTrusted(chain, authType);
            }
        }

        private void checkServer(X509Certificate[] chain, String authType)
                throws CertificateException {
            try {
                system.checkServerTrusted(chain, authType);
            } catch (CertificateException first) {
                custom.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] systemIssuers = system.getAcceptedIssuers();
            X509Certificate[] customIssuers = custom.getAcceptedIssuers();
            X509Certificate[] combined =
                    new X509Certificate[systemIssuers.length + customIssuers.length];
            System.arraycopy(systemIssuers, 0, combined, 0, systemIssuers.length);
            System.arraycopy(
                    customIssuers,
                    0,
                    combined,
                    systemIssuers.length,
                    customIssuers.length);
            return combined;
        }
    }
}
