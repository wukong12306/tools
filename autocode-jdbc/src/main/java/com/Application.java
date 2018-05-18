package com;

import com.jdbc.AutoCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class Application {

	@Autowired
	private AutoCode autoCode;

	public static void main(String[] args) {
		ConfigurableApplicationContext run = SpringApplication.run(Application.class, args);
		AutoCode autoCode = run.getBean(AutoCode.class);
		autoCode.auto();
		run.close();
	}
}
