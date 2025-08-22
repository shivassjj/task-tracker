package ru.panchenko.task_tracker;

import org.springframework.boot.SpringApplication;

public class TestTaskTrackerApplication {

	public static void main(String[] args) {
		SpringApplication.from(TaskTrackerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
