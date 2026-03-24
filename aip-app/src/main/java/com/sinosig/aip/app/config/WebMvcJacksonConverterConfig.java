package com.sinosig.aip.app.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.AbstractJacksonHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Slf4j
@Configuration
public class WebMvcJacksonConverterConfig implements WebMvcConfigurer {

    @Value("${aip.http.jackson.max-string-length:60000000}")
    private int maxStringLength;

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        JsonFactory jsonFactory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxStringLength(maxStringLength)
                        .build())
                .build();
        JsonMapper mapper = JsonMapper.builder(jsonFactory).build();
        JacksonJsonHttpMessageConverter converter = new JacksonJsonHttpMessageConverter(mapper);
        log.info("[WebMvcJackson] 已注册 tools.jackson JSON 转换器，maxStringLength={}", maxStringLength);

        int replaceIndex = -1;
        for (int i = 0; i < converters.size(); i++) {
            if (converters.get(i) instanceof AbstractJacksonHttpMessageConverter) {
                replaceIndex = i;
                break;
            }
        }
        if (replaceIndex >= 0) {
            converters.set(replaceIndex, converter);
        } else {
            converters.add(0, converter);
        }
    }
}
