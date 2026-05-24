package com.agentscope.demo.rag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

@Component
public class DeterministicEmbeddingClient implements EmbeddingClient {

    public static final int DIMENSIONS = 1536;

    @Override
    public float[] embed(String text) {
        byte[] seed = sha256(text == null ? "" : text);
        float[] vector = new float[DIMENSIONS];
        for (int i = 0; i < vector.length; i++) {
            int value = seed[i % seed.length] & 0xff;
            vector[i] = (value - 128) / 128.0f;
        }
        return vector;
    }

    private byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
