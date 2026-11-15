package edu.bits.itsm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CampusItsmApplication {
  public static void main(String[] args) {
    SpringApplication.run(CampusItsmApplication.class, args);
  }
}
