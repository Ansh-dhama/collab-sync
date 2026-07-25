package com.example.collab_sync_engine;

import org.springframework.boot.SpringApplication;

public class TestCollabSyncEngineApplication {

	public static void main(String[] args) {
		SpringApplication.from(CollabSyncEngineApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
