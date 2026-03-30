package com.memberconnect.backend;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.modelmapper.ModelMapper;

@SpringBootApplication
public class MemberConnectBackendApplication {

	public static void main(String[] args) {

        SpringApplication.run(MemberConnectBackendApplication.class, args);
	}

    @Bean
    public ModelMapper modelmapper(){
        return new ModelMapper();
    }


}
