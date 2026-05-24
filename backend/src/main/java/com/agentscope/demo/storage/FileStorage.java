package com.agentscope.demo.storage;

import java.io.IOException;
import java.io.InputStream;

public interface FileStorage {

    String put(String key, InputStream inputStream) throws IOException;

    InputStream get(String key) throws IOException;
}
