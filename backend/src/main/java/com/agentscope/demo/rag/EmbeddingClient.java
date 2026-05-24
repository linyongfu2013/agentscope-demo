package com.agentscope.demo.rag;

public interface EmbeddingClient {

    float[] embed(String text);
}
