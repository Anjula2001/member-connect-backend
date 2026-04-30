package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.BoardMeeting;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BoardmeetingRepository extends JpaRepository<BoardMeeting, Long> {

    Optional<BoardMeeting> findByBoardMeetingId(String boardMeetingId);

}
