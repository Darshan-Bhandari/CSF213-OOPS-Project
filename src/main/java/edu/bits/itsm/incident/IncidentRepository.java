package edu.bits.itsm.incident;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
  List<Incident> findAllByStatusNot(IncidentStatus status);
}
