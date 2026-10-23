package org.c3cavite.sessionmanager.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@ConditionalOnResource(resources = "classpath:/static/index.html")
class PackagedSpaController {
  @GetMapping({
    "/",
    "/events",
    "/events/",
    "/events/{id}",
    "/events/{id}/",
    "/events/{id}/groups",
    "/events/{id}/groups/"
  })
  String index() {
    return "forward:/index.html";
  }
}
