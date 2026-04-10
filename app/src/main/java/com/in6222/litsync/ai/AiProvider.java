package com.in6222.litsync.ai;

import java.io.IOException;
import java.util.List;

public interface AiProvider {
    String complete(List<AiMessage> messages, int maxTokens, double temperature) throws IOException;
}
