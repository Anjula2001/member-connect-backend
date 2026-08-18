package com.memberconnect.backend;

import com.memberconnect.backend.service.MemberDeathRecordService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MemberConnectBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(MemberConnectBackendApplication.class, args);
	}

	@Bean
	public CommandLineRunner commandLineRunner(
			JdbcTemplate jdbcTemplate,
			MemberDeathRecordService memberDeathRecordService
	) {
		return args -> {
			try {
				jdbcTemplate.execute("ALTER TABLE member DROP CONSTRAINT IF EXISTS member_status_check;");
				System.out.println("SUCCESSFULLY DROPPED member_status_check CONSTRAINT!");
			} catch (Exception e) {
				System.err.println("FAILED TO DROP member_status_check CONSTRAINT: " + e.getMessage());
			}

			try {
				jdbcTemplate.execute("ALTER TABLE death_donation_request DROP CONSTRAINT IF EXISTS death_donation_request_status_check;");
				System.out.println("SUCCESSFULLY DROPPED death_donation_request_status_check CONSTRAINT!");
			} catch (Exception e) {
				System.err.println("FAILED TO DROP death_donation_request_status_check CONSTRAINT: " + e.getMessage());
			}

			try {
				jdbcTemplate.execute("ALTER TABLE member_death_record DROP CONSTRAINT IF EXISTS member_death_record_status_check;");
				System.out.println("SUCCESSFULLY DROPPED member_death_record_status_check CONSTRAINT!");
			} catch (Exception e) {
				System.err.println("FAILED TO DROP member_death_record_status_check CONSTRAINT: " + e.getMessage());
			}

			memberDeathRecordService.seedCauseOfDeathIfEmpty();
		};
	}
}