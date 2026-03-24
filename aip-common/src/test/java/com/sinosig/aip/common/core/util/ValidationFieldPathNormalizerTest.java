package com.sinosig.aip.common.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValidationFieldPathNormalizerTest {

    @Test
    void normalize_shouldTrimMethodAndRequestPrefix() {
        assertEquals("taskName", ValidationFieldPathNormalizer.normalize("createTask.request.taskName"));
        assertEquals("page", ValidationFieldPathNormalizer.normalize("listTasks.page"));
    }

    @Test
    void normalize_shouldKeepSimpleFieldAsIs() {
        assertEquals("size", ValidationFieldPathNormalizer.normalize("size"));
    }
}

