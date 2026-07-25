package com.example.collab_sync_engine;

import com.example.collab_sync_engine.engine.OperationalTransformationEngine;
import org.springframework.boot.SpringApplication;

public class TestCollabSyncEngineApplication {

	public static void main(String[] args) {
		SpringApplication.from(CollabSyncEngineApplication::main).with(OperationalTransformationEngine.class).run(args);
	}

}
