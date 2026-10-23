package org.c3cavite.sessionmanager.web;

import java.util.UUID;
import org.c3cavite.sessionmanager.events.EventAssignmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class RosterExportController {
  private final EventAssignmentService assignments;
  private final RosterCsv csv;

  RosterExportController(EventAssignmentService assignments, RosterCsv csv) {
    this.assignments = assignments;
    this.csv = csv;
  }

  @GetMapping("/api/v1/events/{id}/assignment/export.csv")
  ResponseEntity<String> export(@PathVariable("id") UUID id) {
    var snapshot =
        assignments
            .latest(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.CONFLICT, "Generate a current assignment before exporting"));
    return ResponseEntity.ok()
        .header("Content-Type", "text/csv; charset=UTF-8")
        .header("Content-Disposition", "attachment; filename=\"event-" + id + "-roster.csv\"")
        .body(csv.render(snapshot));
  }
}
