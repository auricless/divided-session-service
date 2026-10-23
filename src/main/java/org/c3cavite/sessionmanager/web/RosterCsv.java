package org.c3cavite.sessionmanager.web;

import java.util.ArrayList;
import java.util.List;
import org.c3cavite.sessionmanager.events.EventAssignmentResponse;
import org.springframework.stereotype.Component;

@Component
class RosterCsv {
  String render(EventAssignmentResponse snapshot) {
    var result = snapshot.result();
    List<List<String>> rows = new ArrayList<>();
    rows.add(
        List.of(
            "Event",
            "State",
            "Row type",
            "Group",
            "Tier",
            "Gender",
            "Life stage",
            "Participant",
            "Age",
            "Leader",
            "Assistant",
            "Detail"));
    rows.add(
        row(
            snapshot,
            "SUMMARY",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            result.totalGroups()
                + " groups; "
                + result.totalAssignedParticipants()
                + " assigned participants"));
    for (int i = 0; i < result.groups().size(); i++) {
      var group = result.groups().get(i);
      for (var person : group.participants()) {
        rows.add(
            row(
                snapshot,
                "PARTICIPANT",
                Integer.toString(i + 1),
                value(group.ageTierTarget()),
                value(group.genderTarget()),
                value(group.lifeStageTarget()),
                person.name(),
                Integer.toString(person.age()),
                group.leader() == null ? "" : group.leader().name(),
                group.assistant() == null ? "" : group.assistant().name(),
                ""));
      }
    }
    result
        .blockers()
        .forEach(
            b ->
                rows.add(
                    row(
                        snapshot,
                        "BLOCKER",
                        Integer.toString(b.groupNumber()),
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        b.message())));
    result
        .warnings()
        .forEach(w -> rows.add(row(snapshot, "WARNING", "", "", "", "", "", "", "", "", w)));
    result
        .unassignedParticipants()
        .forEach(
            u ->
                rows.add(
                    row(
                        snapshot,
                        "UNASSIGNED",
                        "",
                        value(u.participant().ageTier()),
                        value(u.participant().gender()),
                        value(u.participant().lifeStage()),
                        u.participant().name(),
                        Integer.toString(u.participant().age()),
                        "",
                        "",
                        u.reason())));
    return rows.stream()
        .map(r -> r.stream().map(this::cell).collect(java.util.stream.Collectors.joining(",")))
        .collect(java.util.stream.Collectors.joining("\r\n", "", "\r\n"));
  }

  private List<String> row(
      EventAssignmentResponse snapshot,
      String type,
      String group,
      String tier,
      String gender,
      String lifeStage,
      String participant,
      String age,
      String leader,
      String assistant,
      String detail) {
    return List.of(
        snapshot.eventName(),
        snapshot.result().state().name(),
        type,
        group,
        tier,
        gender,
        lifeStage,
        participant,
        age,
        leader,
        assistant,
        detail);
  }

  private String value(Object value) {
    return value == null ? "" : value.toString();
  }

  private String cell(String value) {
    String trimmed = value.stripLeading();
    if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0
        || value.startsWith("\t")
        || value.startsWith("\r")) {
      value = "'" + value;
    }
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }
}
