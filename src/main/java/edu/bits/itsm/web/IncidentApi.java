package edu.bits.itsm.web;

import edu.bits.itsm.identity.AccountPrincipal;
import edu.bits.itsm.incident.*;
import edu.bits.itsm.reporting.IncidentQueryService;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class IncidentApi {
  private final IncidentService incidents;
  private final IncidentQueryService queries;

  public IncidentApi(IncidentService incidents, IncidentQueryService queries) {
    this.incidents = incidents;
    this.queries = queries;
  }

  @GetMapping("/incidents")
  public Map<String, Object> list(
      @AuthenticationPrincipal AccountPrincipal actor, @ModelAttribute IncidentFilter filter) {
    return Map.of(
        "items",
        queries.list(actor, filter),
        "total",
        queries.count(actor, filter),
        "page",
        filter.getPage(),
        "pageSize",
        20);
  }

  @GetMapping("/incidents/{id}")
  public Map<String, Object> detail(
      @PathVariable long id, @AuthenticationPrincipal AccountPrincipal actor) {
    var i = incidents.view(id, actor);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", i.getId());
    result.put("version", i.getVersion());
    result.put("title", i.getTitle());
    result.put("description", i.getDescription());
    result.put("status", i.getStatus());
    result.put("priority", i.getAssessment().getPriority());
    result.put("score", i.getAssessment().getScore());
    result.put("createdAt", i.getCreatedAt());
    result.put("responseDue", i.getResponseDue());
    result.put("resolutionDue", i.getResolutionDue());
    return result;
  }

  @GetMapping("/metrics")
  public Map<String, Object> metrics(
      @AuthenticationPrincipal AccountPrincipal actor, @ModelAttribute IncidentFilter filter) {
    return queries.metrics(actor, filter);
  }
}
