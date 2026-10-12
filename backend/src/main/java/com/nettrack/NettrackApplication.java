package com.nettrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @SpringBootApplication = "scan this package for my classes, auto-configure everything, start the server"
@SpringBootApplication
@EnableScheduling // turns on @Scheduled jobs (the outbox relay)
public class NettrackApplication {
    public static void main(String[] args) {
        SpringApplication.run(NettrackApplication.class, args);
    }
}
