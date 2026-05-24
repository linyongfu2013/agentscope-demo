package com.agentscope.demo.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IntentClassifierTest {

    private final IntentClassifier classifier = new IntentClassifier();

    @Test
    void classifiesShortDirectQuestionAsSimple() {
        assertThat(classifier.classify("什么是 pgvector?")).isEqualTo(Intent.SIMPLE);
    }

    @Test
    void classifiesResearchAndPlanningRequestAsComplex() {
        assertThat(classifier.classify("请调研 AgentScope Java 并拆解一个三阶段实施计划"))
                .isEqualTo(Intent.COMPLEX);
    }
}
