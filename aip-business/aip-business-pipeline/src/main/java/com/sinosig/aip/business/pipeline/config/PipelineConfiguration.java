package com.sinosig.aip.business.pipeline.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AipPipelineProperties.class)
public class PipelineConfiguration {
}
