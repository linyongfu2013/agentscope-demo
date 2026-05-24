package com.agentscope.demo.rag;

import java.util.List;

public interface DocumentParser {

    List<String> parse(byte[] content, String filename);
}
