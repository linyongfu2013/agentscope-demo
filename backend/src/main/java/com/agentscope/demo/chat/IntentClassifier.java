package com.agentscope.demo.chat;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class IntentClassifier {

    private static final List<String> COMPLEX_MARKERS = List.of(
            "调研", "研究", "拆解", "计划", "多步骤", "执行", "工具", "检索", "分析", "compare", "research", "plan"
    );

    public Intent classify(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return Intent.SIMPLE;
        }
        String normalized = prompt.toLowerCase();
        boolean markerMatched = COMPLEX_MARKERS.stream().anyMatch(normalized::contains);
        if (markerMatched || normalized.length() > 180) {
            return Intent.COMPLEX;
        }
        return Intent.SIMPLE;
    }
}
