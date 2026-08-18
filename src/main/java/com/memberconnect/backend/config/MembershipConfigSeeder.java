package com.memberconnect.backend.config;

import com.memberconnect.backend.service.MembershipEligibilityService;
import com.memberconnect.backend.service.RemittanceMasterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the Member Registration configuration tables on first start:
 *  - Remittance Master (the four accounts collected on an application)
 *  - Membership eligibility age limits
 *
 * Both seeders are no-ops once rows exist, so restarts never overwrite whatever
 * Accounts/Super Admin have configured.
 */
@Component
public class MembershipConfigSeeder implements ApplicationRunner {

    @Autowired
    private RemittanceMasterService remittanceMasterService;

    @Autowired
    private MembershipEligibilityService membershipEligibilityService;

    @Override
    public void run(ApplicationArguments args) {
        remittanceMasterService.seedDefaultsIfEmpty();
        membershipEligibilityService.seedDefaultIfEmpty();
    }
}
