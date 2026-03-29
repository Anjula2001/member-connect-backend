package com.memberconnect.backend.service;
import com.memberconnect.backend.dto.MemberApplicationDTO;
import com.memberconnect.backend.model.Member_Application;
import com.memberconnect.backend.repository.MemberApplicationRepository;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    public List<MemberApplicationDTO>getAllMemberApplications(){
        List<Member_Application>memberApplications = memberApplicationRepository.findAll();
        return modelMapper.map(memberApplications, new TypeToken<List<MemberApplicationDTO>>() {}.getType());
    }

}