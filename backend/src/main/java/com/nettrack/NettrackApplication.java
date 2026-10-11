package com.nettrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @SpringBootApplication = "scan this package for my classes, auto-configure everything, start the server"
@SpringBootApplication
public class NettrackApplication {
    public static void main(String[] args) {
        SpringApplication.run(NettrackApplication.class, args);
    }
}
