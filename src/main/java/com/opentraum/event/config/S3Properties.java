package com.opentraum.event.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "s3")
public class S3Properties {
    private String region = "ap-northeast-2";
    private String bucket;
    private int presignHours = 24;
}
