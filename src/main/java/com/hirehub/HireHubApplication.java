package com.hirehub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the HireHub Recruitment Management System.
 *
 * <p>{@code @SpringBootApplication} is a composed annotation:
 * {@code @Configuration} + {@code @EnableAutoConfiguration} + {@code @ComponentScan}.
 * Component scanning starts from this package ({@code com.hirehub}), which is why every
 * other package of the application lives underneath it.</p>
 */
@SpringBootApplication
public class HireHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(HireHubApplication.class, args);
    }
}
