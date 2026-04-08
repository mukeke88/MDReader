package com.tinkertank.mdreader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MdReaderApplication {

    public static void main(String[] args) {
        SpringApplication.run(MdReaderApplication.class, args);
    }
}
