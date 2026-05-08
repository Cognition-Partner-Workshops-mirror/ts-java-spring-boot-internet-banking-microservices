package com.javatodev.finance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Master Data Management (MDM) Service.
 * This service provides EBX MDM-like capabilities including dataspaces,
 * datasets, snapshots, user management, permissions, workflows, and
 * environment synchronization.
 */
@SpringBootApplication
public class MasterDataManagementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MasterDataManagementServiceApplication.class, args);
    }

}
