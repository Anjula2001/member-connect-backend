package com.memberconnect.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Configures the AWS S3 client.
 *
 * <p>Credentials are resolved automatically by {@link DefaultCredentialsProvider},
 * which walks the standard AWS credential chain in order:
 * <ol>
 *   <li>Java system properties</li>
 *   <li>Environment variables ({@code AWS_ACCESS_KEY_ID} / {@code AWS_SECRET_ACCESS_KEY})</li>
 *   <li>AWS shared credentials file (~/.aws/credentials)</li>
 *   <li>EC2 / ECS instance metadata (IAM instance role) — used in production</li>
 * </ol>
 *
 * <p><strong>Do not inject static access-key / secret-key values here.</strong>
 * On EC2 the {@code MemberConnectEC2Role} IAM instance role supplies short-lived,
 * automatically-rotated credentials via the instance metadata service (IMDS), so
 * no credentials need to appear anywhere in the codebase or environment files.
 */
@Configuration
public class S3Config {

    @Value("${aws.region}")
    private String region;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
