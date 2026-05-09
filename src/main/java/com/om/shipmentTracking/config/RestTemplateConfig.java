package com.om.shipmentTracking.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@Slf4j
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {

        HttpClient httpClient = HttpClients.custom()
                .setRedirectStrategy(new DefaultRedirectStrategy())
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(100)
                        .setMaxConnPerRoute(20)
                        .build())
                .build();

        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectionRequestTimeout(Duration.ofSeconds(30));  // replaces setConnectTimeout
        factory.setReadTimeout(Duration.ofSeconds(90));               // replaces setReadTimeout

        RestTemplate restTemplate = new RestTemplate(factory);

        restTemplate.getInterceptors().add((request, body, execution) -> {
            log.info("[RestTemplate] URI: {}", request.getURI());
            log.debug("[RestTemplate] Method: {}", request.getMethod());
            log.debug("[RestTemplate] Headers: {}", request.getHeaders());
            return execution.execute(request, body);
        });

        return restTemplate;
    }
}