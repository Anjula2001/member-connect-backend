package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.BoardMeetingDTO;
import com.memberconnect.backend.model.BoardMeeting;
import com.memberconnect.backend.repository.BoardApprovalListRepository;
import com.memberconnect.backend.repository.BoardmeetingRepository;
import com.memberconnect.backend.repository.DormantApprovalListRepository;
import com.memberconnect.backend.repository.Grade5ScholarshipApprovalListRepository;
import com.memberconnect.backend.repository.TerminationApprovalListRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@SuppressWarnings("null")
public class BoardMeetingService {
    @Autowired
    private BoardmeetingRepository boardMeetingRepository;

    // A Board Meeting can have several kinds of approval list attached. Deleting the
    // meeting while any of them exist would orphan those records, so all are checked.
    @Autowired
    private BoardApprovalListRepository boardApprovalListRepository;

    @Autowired
    private TerminationApprovalListRepository terminationApprovalListRepository;

    @Autowired
    private Grade5ScholarshipApprovalListRepository grade5ScholarshipApprovalListRepository;

    @Autowired
    private DormantApprovalListRepository dormantApprovalListRepository;

    @Autowired
    private ModelMapper modelMapper;

    public BoardMeetingDTO saveBoardMeeting(BoardMeetingDTO boardMeetingDTO) {
        BoardMeeting boardMeeting = modelMapper.map(boardMeetingDTO, BoardMeeting.class);
        boardMeeting.setBoardMeetingId("BM-" + System.currentTimeMillis());
        BoardMeeting saved = boardMeetingRepository.save(boardMeeting);
        return modelMapper.map(saved, BoardMeetingDTO.class);
    }

    public List<BoardMeetingDTO> getAllBoardMeetings() {
        List<BoardMeeting> boardMeetings = boardMeetingRepository.findAll();
        return modelMapper.map(boardMeetings, new TypeToken<List<BoardMeetingDTO>>() {}.getType());
    }

    public BoardMeetingDTO getBoardMeetingByBoardMeetingId(String boardMeetingId) {
        BoardMeeting boardMeeting = boardMeetingRepository.findByBoardMeetingId(boardMeetingId)
                .orElseThrow(() -> new RuntimeException("Board Meeting not found"));
        return modelMapper.map(boardMeeting, BoardMeetingDTO.class);
    }

    public BoardMeetingDTO updateBoardMeeting(Long id, BoardMeetingDTO boardMeetingDTO) {
        BoardMeeting existing = boardMeetingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Board Meeting not found"));
        
        if (boardMeetingDTO.getScheduledDate() != null) {
            existing.setScheduledDate(boardMeetingDTO.getScheduledDate());
        }
        if (boardMeetingDTO.getActualDate() != null) {
            existing.setActualDate(boardMeetingDTO.getActualDate());
        }
        
        BoardMeeting updated = boardMeetingRepository.save(existing);
        return modelMapper.map(updated, BoardMeetingDTO.class);
    }

    public String deleteBoardMeeting(Long id) {
        BoardMeeting existing = boardMeetingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Board Meeting not found"));

        // Only meetings with nothing attached may be deleted — otherwise the approval
        // lists pointing at this meeting would be left dangling.
        List<String> attached = new ArrayList<>();
        if (boardApprovalListRepository.existsByBoardMeetingId(id)) {
            attached.add("New Member Application approvals");
        }
        if (terminationApprovalListRepository.existsByBoardMeetingId(id)) {
            attached.add("Member Termination approvals");
        }
        if (grade5ScholarshipApprovalListRepository.existsByBoardMeetingId(id)) {
            attached.add("Grade 5 Scholarship approvals");
        }
        if (dormantApprovalListRepository.existsByBoardMeetingId(id)) {
            attached.add("Dormant Membership approvals");
        }
        if (!attached.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This Board Meeting cannot be deleted because it still has "
                            + String.join(", ", attached) + " attached.");
        }

        boardMeetingRepository.delete(existing);
        return "Board Meeting deleted successfully";
    }
  
}
