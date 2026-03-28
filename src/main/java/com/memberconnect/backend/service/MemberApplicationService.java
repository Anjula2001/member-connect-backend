package com.memberconnect.backend.service;
import com.memberconnect.backend.dto.MemberApplicationDTO;
import com.memberconnect.backend.model.Member_Application;
import com.memberconnect.backend.repository.MemberApplicationRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MemberApplicationService {
    @Autowired
    private MemberApplicationRepository memberApplicationRepository;

    @Autowired
    private ModelMapper modelMapper;

    public MemberApplicationDTO saveMemberApplication(MemberApplicationDTO memberApplicationDTO) {
        memberApplicationRepository.save(modelMapper.map(memberApplicationDTO, Member_Application.class));
        return memberApplicationDTO;
    }

}