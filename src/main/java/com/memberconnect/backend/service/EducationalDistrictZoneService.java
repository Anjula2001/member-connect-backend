package com.memberconnect.backend.service;

import com.memberconnect.backend.model.EducationalDistrictZone;
import com.memberconnect.backend.repository.EducationalDistrictZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class EducationalDistrictZoneService {

    private final EducationalDistrictZoneRepository repository;

    public EducationalDistrictZoneService(EducationalDistrictZoneRepository repository) {
        this.repository = repository;
    }

    public List<String> getDistricts() {
        seedDefaultsIfEmpty();
        return repository.findDistinctDistricts();
    }

    public List<String> getZonesByDistrict(String district) {
        seedDefaultsIfEmpty();
        return repository.findZonesByDistrict(district);
    }

    public boolean isValidDistrictZone(String district, String zone) {
        if (district == null || district.isBlank() || zone == null || zone.isBlank()) {
            return false;
        }
        return repository.existsByDistrictIgnoreCaseAndZoneIgnoreCase(district.trim(), zone.trim());
    }

    private void seedDefaultsIfEmpty() {
        if (repository.count() > 0) {
            return;
        }

        List<EducationalDistrictZone> defaults = new ArrayList<>();
        defaults.add(new EducationalDistrictZone("Colombo", "Colombo South"));
        defaults.add(new EducationalDistrictZone("Colombo", "Colombo North"));
        defaults.add(new EducationalDistrictZone("Colombo", "Homagama"));

        defaults.add(new EducationalDistrictZone("Kandy", "Kandy"));
        defaults.add(new EducationalDistrictZone("Kandy", "Gampola"));
        defaults.add(new EducationalDistrictZone("Kandy", "Katugastota"));

        defaults.add(new EducationalDistrictZone("Galle", "Galle"));
        defaults.add(new EducationalDistrictZone("Galle", "Elpitiya"));
        defaults.add(new EducationalDistrictZone("Galle", "Ambalangoda"));

        defaults.add(new EducationalDistrictZone("Matara", "Matara"));
        defaults.add(new EducationalDistrictZone("Matara", "Akuressa"));
        defaults.add(new EducationalDistrictZone("Matara", "Weligama"));

        defaults.add(new EducationalDistrictZone("Jaffna", "Jaffna"));
        defaults.add(new EducationalDistrictZone("Jaffna", "Nallur"));
        defaults.add(new EducationalDistrictZone("Jaffna", "Chavakachcheri"));

        defaults.add(new EducationalDistrictZone("Gampaha", "Gampaha"));
        defaults.add(new EducationalDistrictZone("Gampaha", "Negombo"));
        defaults.add(new EducationalDistrictZone("Gampaha", "Minuwangoda"));

        repository.saveAll(defaults);
    }
}
