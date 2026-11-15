package edu.bits.itsm.assignment;

import edu.bits.itsm.audit.AuditService;
import edu.bits.itsm.catalog.CatalogService;
import edu.bits.itsm.shared.BusinessException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignmentService {
  private final JdbcTemplate jdbc;
  private final Map<String, AssignmentStrategy> strategies = new HashMap<>();
  private final CatalogService catalog;
  private final AuditService audit;

  public AssignmentService(
      JdbcTemplate jdbc,
      List<AssignmentStrategy> implementations,
      CatalogService catalog,
      AuditService audit) {
    this.jdbc = jdbc;
    implementations.forEach(s -> strategies.put(s.key(), s));
    this.catalog = catalog;
    this.audit = audit;
  }

  public String selected() {
    return jdbc.queryForObject(
        "SELECT strategy FROM assignment_configuration WHERE id=1", String.class);
  }

  public List<AssignmentStrategy.Candidate> candidates(long category) {
    return jdbc
        .query(
            "SELECT u.id,s.proficiency,t.capacity,(SELECT count(*) FROM incidents i WHERE"
                + " i.assignee_id=u.id AND i.status NOT IN ('RESOLVED','VERIFIED','CLOSED'))"
                + " workload FROM user_accounts u JOIN technician_profiles t ON t.user_id=u.id JOIN"
                + " technician_skills s ON s.user_id=u.id JOIN categories c ON c.id=s.category_id"
                + " WHERE u.role='TECHNICIAN' AND u.active AND t.available AND c.active AND"
                + " s.category_id=?",
            (r, n) ->
                new AssignmentStrategy.Candidate(
                    r.getLong("id"),
                    r.getInt("proficiency"),
                    r.getInt("workload"),
                    r.getInt("capacity")),
            category)
        .stream()
        .filter(c -> c.workload() < c.capacity())
        .toList();
  }

  public Optional<AssignmentStrategy.Candidate> choose(long category) {
    return strategies.get(selected()).select(candidates(category));
  }

  public boolean hasSkill(long user, long category) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM technician_skills WHERE user_id=? AND category_id=?)",
            Boolean.class,
            user,
            category));
  }

  public void requireEligible(long user, long category) {
    if (candidates(category).stream().noneMatch(c -> c.userId() == user))
      throw new BusinessException(
          "Technician is unavailable, lacks the required skill, or is at capacity.");
  }

  public List<Map<String, Object>> technicians() {
    return jdbc.queryForList(
        "SELECT u.id,u.display_name,u.active,t.available,t.capacity,(SELECT count(*) FROM incidents"
            + " i WHERE i.assignee_id=u.id AND i.status NOT IN ('RESOLVED','VERIFIED','CLOSED'))"
            + " workload FROM user_accounts u JOIN technician_profiles t ON t.user_id=u.id ORDER BY"
            + " u.display_name");
  }

  public List<Map<String, Object>> skills() {
    return jdbc.queryForList(
        "SELECT s.*,c.name category_name,u.display_name FROM technician_skills s JOIN categories c"
            + " ON c.id=s.category_id JOIN user_accounts u ON u.id=s.user_id ORDER BY"
            + " u.display_name,c.name");
  }

  @Transactional
  public void configure(String key, long actor) {
    requireAdmin(actor);
    catalog.lock();
    if (!strategies.containsKey(key)) throw new BusinessException("Unknown assignment strategy.");
    jdbc.update("UPDATE assignment_configuration SET strategy=? WHERE id=1", key);
    audit.record(actor, "ASSIGNMENT_STRATEGY_CHANGED", "CONFIG", key);
  }

  @Transactional
  public void technician(long id, boolean available, int capacity, long actor) {
    requireAdmin(actor);
    catalog.lock();
    if (capacity < 1 || capacity > 100) throw new BusinessException("Capacity must be 1–100.");
    if (jdbc.update(
            "UPDATE technician_profiles SET available=?,capacity=? WHERE user_id=?",
            available,
            capacity,
            id)
        != 1) throw new BusinessException("Technician not found.");
    audit.record(
        actor,
        "TECHNICIAN_CONFIGURED",
        "USER-" + id,
        "Availability=" + available + ", capacity=" + capacity);
  }

  @Transactional
  public void skill(long user, long category, int proficiency, long actor) {
    requireAdmin(actor);
    catalog.lock();
    catalog.requireCategory(category);
    if (proficiency < 0 || proficiency > 3)
      throw new BusinessException("Proficiency must be 0–3 (0 removes a skill).");
    if (proficiency == 0) {
      if (Boolean.TRUE.equals(
          jdbc.queryForObject(
              "SELECT EXISTS(SELECT 1 FROM incidents WHERE assignee_id=? AND category_id=? AND"
                  + " status NOT IN ('RESOLVED','VERIFIED','CLOSED'))",
              Boolean.class,
              user,
              category)))
        throw new BusinessException("Reassign active incidents before removing this skill.");
      jdbc.update(
          "DELETE FROM technician_skills WHERE user_id=? AND category_id=?", user, category);
    } else
      jdbc.update(
          "INSERT INTO technician_skills(user_id,category_id,proficiency) VALUES (?,?,?) ON"
              + " CONFLICT(user_id,category_id) DO UPDATE SET proficiency=excluded.proficiency",
          user,
          category,
          proficiency);
    audit.record(
        actor,
        "TECHNICIAN_SKILL_CHANGED",
        "USER-" + user,
        "Category=" + category + ", proficiency=" + proficiency);
  }
  private void requireAdmin(long actor) {
    if (!Boolean.TRUE.equals(jdbc.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM user_accounts WHERE id=? AND active AND role='ADMIN')",
        Boolean.class, actor)))
      throw new org.springframework.security.access.AccessDeniedException("Administrator access required.");
  }

}
