package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.BoardMeetingDTO;
import com.memberconnect.backend.model.BoardMeeting;
import com.memberconnect.backend.repository.BoardmeetingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@SuppressWarnings("null")
public class BoardMeetingService {
    @Autowired
    private BoardmeetingRepository boardMeetingRepository;

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
        boardMeetingRepository.delete(existing);
        return "Board Meeting deleted successfully";
    }
  
}
