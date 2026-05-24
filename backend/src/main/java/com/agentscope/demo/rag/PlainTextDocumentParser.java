package com.agentscope.demo.rag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PlainTextDocumentParser implements DocumentParser {

    @Override
    public List<String> parse(byte[] content, String filename) {
        String text = new String(content, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            return List.of();
        }
        String[] paragraphs = text.split("(\\r?\\n){2,}");
        List<String> chunks = new ArrayList<>();
        for (String paragraph : paragraphs) {
            String clean = paragraph.trim();
            if (!clean.isEmpty()) {
                chunks.add(clean);
            }
        }
        return chunks;
    }
}
