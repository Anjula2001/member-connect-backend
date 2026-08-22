package com.memberconnect.backend.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.memberconnect.backend.dto.UniversityMasterDto;
import com.memberconnect.backend.model.Program;
import com.memberconnect.backend.model.University;
import com.memberconnect.backend.model.UniversityProgram;
import com.memberconnect.backend.repository.ProgramRepository;
import com.memberconnect.backend.repository.UniversityProgramRepository;
import com.memberconnect.backend.repository.UniversityRepository;

@Service
public class UniversityMasterService {

    private final UniversityRepository universityRepository;
    private final ProgramRepository programRepository;
    private final UniversityProgramRepository universityProgramRepository;

    public UniversityMasterService(UniversityRepository universityRepository,
                                   ProgramRepository programRepository,
                                   UniversityProgramRepository universityProgramRepository) {
        this.universityRepository = universityRepository;
        this.programRepository = programRepository;
        this.universityProgramRepository = universityProgramRepository;
    }

    // ---- Universities -------------------------------------------------------
    public List<UniversityMasterDto> getUniversities() {
        return universityRepository.findAll().stream()
                .sorted((a, b) -> safe(a.getName()).compareToIgnoreCase(safe(b.getName())))
                .map(this::toDto)
                .toList();
    }

    public UniversityMasterDto createUniversity(UniversityMasterDto request) {
        String name = requireName(request.getName(), "University name");
        if (universityRepository.existsByNameIgnoreCase(name)) {
            throw badRequest("A University with this name already exists");
        }
        University university = new University();
        university.setName(name);
        return toDto(universityRepository.save(university));
    }

    public UniversityMasterDto updateUniversity(Long id, UniversityMasterDto request) {
        University university = universityRepository.findById(id)
                .orElseThrow(() -> notFound("University not found"));
        String name = requireName(request.getName(), "University name");
        if (universityRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw badRequest("A University with this name already exists");
        }
        university.setName(name);
        return toDto(universityRepository.save(university));
    }

    // ---- Programmes ---------------------------------------------------------

    public List<UniversityMasterDto> getPrograms() {
        return programRepository.findAll().stream()
                .sorted((a, b) -> safe(a.getName()).compareToIgnoreCase(safe(b.getName())))
                .map(this::toDto)
                .toList();
    }

    public UniversityMasterDto createProgram(UniversityMasterDto request) {
        String name = requireName(request.getName(), "Programme name");
        if (programRepository.existsByNameIgnoreCase(name)) {
            throw badRequest("A Programme with this name already exists");
        }
        Program program = new Program();
        program.setName(name);
        return toDto(programRepository.save(program));
    }

    public UniversityMasterDto updateProgram(Long id, UniversityMasterDto request) {
        Program program = programRepository.findById(id)
                .orElseThrow(() -> notFound("Programme not found"));
        String name = requireName(request.getName(), "Programme name");
        if (programRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw badRequest("A Programme with this name already exists");
        }
        program.setName(name);
        return toDto(programRepository.save(program));
    }

    // ---- University / Programme pairings ------------------------------------

    public List<UniversityMasterDto> getUniversityPrograms() {
        return universityProgramRepository.findAll().stream()
                .map(this::toDto)
                .sorted((a, b) -> {
                    int byUniversity = safe(a.getUniversityName())
                            .compareToIgnoreCase(safe(b.getUniversityName()));
                    return byUniversity != 0
                            ? byUniversity
                            : safe(a.getProgramName()).compareToIgnoreCase(safe(b.getProgramName()));
                })
                .toList();
    }

    public UniversityMasterDto createUniversityProgram(UniversityMasterDto request) {
        University university = requireUniversity(request.getUniversityId());
        Program program = requireProgram(request.getProgramId());

        if (universityProgramRepository
                .findByUniversityIdAndProgramId(university.getId(), program.getId())
                .isPresent()) {
            throw badRequest("This Programme is already set up for the selected University");
        }

        UniversityProgram row = new UniversityProgram();
        row.setUniversity(university);
        row.setProgram(program);
        row.setDuration(requireDuration(request.getDuration()));
        row.setScholarshipAmount(requireAmount(request.getScholarshipAmount()));
        return toDto(universityProgramRepository.save(row));
    }

    
    public UniversityMasterDto updateUniversityProgram(Long id, UniversityMasterDto request) {
        UniversityProgram row = universityProgramRepository.findById(id)
                .orElseThrow(() -> notFound("University Programme not found"));
        row.setDuration(requireDuration(request.getDuration()));
        row.setScholarshipAmount(requireAmount(request.getScholarshipAmount()));
        return toDto(universityProgramRepository.save(row));
    }

    private University requireUniversity(Long id) {
        if (id == null) {
            throw badRequest("University is required");
        }
        return universityRepository.findById(id)
                .orElseThrow(() -> notFound("University not found"));
    }

    private Program requireProgram(Long id) {
        if (id == null) {
            throw badRequest("Programme is required");
        }
        return programRepository.findById(id)
                .orElseThrow(() -> notFound("Programme not found"));
    }

    private String requireName(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw badRequest(label + " is required");
        }
        return value.trim();
    }

    private Integer requireDuration(Integer duration) {
        if (duration == null || duration < 1) {
            throw badRequest("Duration must be at least 1 year");
        }
        return duration;
    }

    private Double requireAmount(Double amount) {
        if (amount == null || amount < 0) {
            throw badRequest("Scholarship amount cannot be negative");
        }
        return amount;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private UniversityMasterDto toDto(University university) {
        UniversityMasterDto dto = new UniversityMasterDto();
        dto.setId(university.getId());
        dto.setName(university.getName());
        return dto;
    }

    private UniversityMasterDto toDto(Program program) {
        UniversityMasterDto dto = new UniversityMasterDto();
        dto.setId(program.getId());
        dto.setName(program.getName());
        return dto;
    }

    private UniversityMasterDto toDto(UniversityProgram row) {
        UniversityMasterDto dto = new UniversityMasterDto();
        dto.setId(row.getId());
        dto.setUniversityId(row.getUniversity() != null ? row.getUniversity().getId() : null);
        dto.setUniversityName(row.getUniversity() != null ? row.getUniversity().getName() : null);
        dto.setProgramId(row.getProgram() != null ? row.getProgram().getId() : null);
        dto.setProgramName(row.getProgram() != null ? row.getProgram().getName() : null);
        dto.setDuration(row.getDuration());
        dto.setScholarshipAmount(row.getScholarshipAmount());
        return dto;
    }
}
