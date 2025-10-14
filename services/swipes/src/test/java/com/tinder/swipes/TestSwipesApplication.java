package com.tinder.swipes;

import org.springframework.boot.SpringApplication;

public class TestSwipesApplication {

	public static void main(String[] args) {
		SpringApplication.from(SwipesApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
