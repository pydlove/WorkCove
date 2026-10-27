package com.pydlove.workcove.boot.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/** Decrypts all ENC(v1:...) properties before Spring creates application beans. */
public class EncryptedPropertyEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        Map<String, Object> encrypted = new LinkedHashMap<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                Object value = enumerable.getProperty(name);
                if (value instanceof String text && ConfigEncryption.isEncrypted(text)) {
                    encrypted.put(name, text);
                }
            }
        }
        if (encrypted.isEmpty()) return;

        String masterKey = environment.getProperty("WORKCOVE_CONFIG_KEY");
        if (masterKey == null || masterKey.isBlank()) {
            masterKey = ConfigEncryption.resolveMasterKey();
        }
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException(
                    "检测到 ENC(...) 配置，但没有找到 WORKCOVE_CONFIG_KEY 或 ~/.workcove/config.key");
        }

        Map<String, Object> plain = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : encrypted.entrySet()) {
            plain.put(
                    entry.getKey(),
                    ConfigEncryption.decrypt((String) entry.getValue(), masterKey));
        }
        environment.getPropertySources().addFirst(
                new MapPropertySource("workcoveDecryptedProperties", plain));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
