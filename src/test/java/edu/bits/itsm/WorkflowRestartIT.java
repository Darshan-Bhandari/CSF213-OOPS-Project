package edu.bits.itsm;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

/** Runs the real packaged app and real HTTP sessions, with an isolated persistent PostgreSQL directory. */
class WorkflowRestartIT {
  @TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.ON_SUCCESS) Path directory;
  final ObjectMapper json = new ObjectMapper();
  String password = "Acceptance-" + UUID.randomUUID();
  Path databaseDirectory;
  Process application;
  URI base;
  boolean monitorEnabled;

  class Session {
    final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager())
        .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(5)).build();
    HttpResponse<String> get(String path) throws Exception {
      return client.send(HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10)).GET().build(),
          HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> post(String path, Map<String,String> values, String source) throws Exception {
      var data = new LinkedHashMap<>(values);
      if (source != null) data.put("_csrf", csrf(source));
      String encoded = data.entrySet().stream().map(e -> enc(e.getKey())+"="+enc(e.getValue()))
          .collect(java.util.stream.Collectors.joining("&"));
      return client.send(HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10))
          .header("Content-Type","application/x-www-form-urlencoded")
          .POST(HttpRequest.BodyPublishers.ofString(encoded)).build(), HttpResponse.BodyHandlers.ofString());
    }
    void login(String email) throws Exception {
      var result = post("/login",Map.of("email",email,"password",password),get("/login").body());
      assertEquals(200,result.statusCode());
      assertTrue(result.uri().getPath().endsWith("/dashboard"),"Credential login must reach the role dashboard");
    }
  }
  static String enc(String s) { return URLEncoder.encode(s,StandardCharsets.UTF_8); }
  static String csrf(String html) {
    var m=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
    assertTrue(m.find(),"CSRF token must be present");return m.group(1);
  }
  static String option(String html,String label) {
    var m=Pattern.compile("<option[^>]*value=\"(\\d+)\"[^>]*>"+Pattern.quote(label)+"</option>").matcher(html);
    assertTrue(m.find(),"Missing catalog option: "+label);return m.group(1);
  }
  JsonNode detail(Session s,long id) throws Exception {
    var response=s.get("/api/incidents/"+id);assertEquals(200,response.statusCode());
    return json.readTree(response.body());
  }
  void action(Session s,long id,String action,String details) throws Exception {
    var r=s.post("/incidents/"+id+"/action",Map.of("version",detail(s,id).get("version").asText(),
        "action",action,"details",details),s.get("/incidents/"+id).body());
    assertEquals(200,r.statusCode());assertEquals("/incidents/"+id,r.uri().getPath());
  }
  void message(Session s,long id,String kind,String body,String reply) throws Exception {
    var data=new LinkedHashMap<>(Map.of("version",detail(s,id).get("version").asText(),"kind",kind,"body",body));
    if(reply!=null)data.put("responseTo",reply);
    var r=s.post("/incidents/"+id+"/messages",data,s.get("/incidents/"+id).body());
    assertEquals(200,r.statusCode());
  }
  EmbeddedPostgres database() throws Exception {
    return EmbeddedPostgres.builder().setServerConfig("unix_socket_directories", "").setPort(0).setDataDirectory(databaseDirectory.toFile())
        .setCleanDataDirectory(false).setServerConfig("listen_addresses","127.0.0.1").start();
  }
  void startApplication(EmbeddedPostgres pg,String suppliedPassword,int iteration) throws Exception {
    int port;
    try(var socket=new ServerSocket(0,0,InetAddress.getLoopbackAddress())) { port=socket.getLocalPort(); }
    base=URI.create("http://127.0.0.1:"+port);
    var jar=Path.of(System.getProperty("acceptance.jar")).toAbsolutePath();assertTrue(Files.isRegularFile(jar));
    var command=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-jar",jar.toString());
    var env=command.environment();
    env.put("SPRING_DATASOURCE_URL",pg.getJdbcUrl("postgres","postgres"));
    env.put("SPRING_DATASOURCE_USERNAME","postgres");env.put("SPRING_DATASOURCE_PASSWORD","");
    env.put("SPRING_PROFILES_ACTIVE","demo");env.put("DEMO_PASSWORD",suppliedPassword);
    env.put("OPENROUTER_API_KEY","");env.put("OPENROUTER_MODEL","");env.put("SERVER_ADDRESS","127.0.0.1");
    env.put("SERVER_PORT",""+port);env.put("APP_SLA_MONITOR_ENABLED",Boolean.toString(monitorEnabled));
    // A long periodic delay isolates ApplicationReadyEvent catch-up in the restart test.
    env.put("APP_SLA_SCAN_DELAY_MS","3600000");
    Path log=directory.resolve("application-"+iteration+".log");
    application=command.redirectErrorStream(true).redirectOutput(log.toFile()).start();
    var guest=new Session();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(45);
    while(System.nanoTime()<deadline) {
      if(!application.isAlive()) fail("Packaged app stopped; inspect "+log);
      try {
        if(guest.get("/login").statusCode()==200 && new JdbcTemplate(pg.getPostgresDatabase()).queryForObject(
            "SELECT count(*) FROM demo_seed_markers WHERE name='initial-technician-skills'",Integer.class)==1) return;
      }
      catch(IOException e) { /* Startup has not bound the socket yet. */ }
      Thread.sleep(100);
    }
    fail("Packaged app startup timed out; inspect "+log);
  }
  void stopApplication() throws Exception {
    if(application!=null) {
      application.destroy();
      if(!application.waitFor(15,TimeUnit.SECONDS)) { application.destroyForcibly();assertTrue(application.waitFor(5,TimeUnit.SECONDS)); }
      application=null;
    }
  }
  Map<String,List<Map<String,Object>>> snapshot(JdbcTemplate jdbc) {
    var result=new LinkedHashMap<String,List<Map<String,Object>>>();
    for(String table:List.of("incidents","incident_messages","resolutions","audit_entries","notifications","ai_suggestions"))
      result.put(table,jdbc.queryForList("SELECT * FROM "+table+" ORDER BY id"));
    result.put("resolution_cycles",jdbc.queryForList("SELECT * FROM resolution_cycles ORDER BY incident_id,cycle"));
    result.put("feedback",jdbc.queryForList("SELECT * FROM feedback ORDER BY incident_id"));
    result.put("users",jdbc.queryForList("SELECT id,email,role,active FROM user_accounts ORDER BY id"));
    result.put("markers",jdbc.queryForList("SELECT * FROM demo_seed_markers ORDER BY name"));
    result.put("skills",jdbc.queryForList("SELECT * FROM technician_skills ORDER BY user_id,category_id"));
    result.put("migrations",jdbc.queryForList("SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank"));
    return result;
  }

  @Test @Timeout(value=3,unit=TimeUnit.MINUTES)
  void packagedWorkflowAndAllRelatedRecordsSurviveAppAndDatabaseRestarts() throws Exception {
    databaseDirectory=directory.resolve("postgres");
    Map<String,List<Map<String,Object>>> saved;
    long id;
    try(var pg=database()) {
      try {
        startApplication(pg,password,1);
        var employee=new Session();employee.login("employee@campus.test");
        var technician=new Session();technician.login("technician@campus.test");
        var admin=new Session();admin.login("admin@campus.test");
        for(String page:List.of("/admin/users","/admin/catalog","/admin/audit","/admin/email"))
          assertEquals(200,admin.get(page).statusCode(),page);
        assertEquals(403,employee.get("/admin/users").statusCode());
        assertEquals(403,employee.post("/admin/users",Map.of("displayName","Forbidden"),employee.get("/employee/dashboard").body()).statusCode());
        assertEquals(403,technician.get("/employee/incidents/new").statusCode());
        var form=employee.get("/employee/incidents/new").body();
        var report=employee.post("/employee/incidents",Map.of("title","Packaged acceptance Wi-Fi incident",
            "description","Acceptance fixture: library network failure","service",option(form,"Campus Wi-Fi"),
            "category",option(form,"Network"),"impact","2","urgency","2","affected","10"),form);
        assertEquals(200,report.statusCode());id=Long.parseLong(report.uri().getPath().replace("/incidents/",""));
        assertEquals("ASSIGNED",detail(employee,id).get("status").asText());
        assertEquals("P2",detail(employee,id).get("priority").asText());assertEquals(12,detail(employee,id).get("score").asInt());
        action(technician,id,"ACKNOWLEDGE","");action(technician,id,"START","");
        message(technician,id,"INTERNAL","Private acceptance diagnostic",null);
        message(technician,id,"REQUEST","Which library floor?",null);
        var jdbc=new JdbcTemplate(pg.getPostgresDatabase());
        String request=jdbc.queryForObject("SELECT id::text FROM incident_messages WHERE incident_id=? AND kind='REQUEST'",String.class,id);
        message(employee,id,"PUBLIC","First floor",request);
        assertFalse(employee.get("/incidents/"+id).body().contains("Private acceptance diagnostic"));
        assertTrue(technician.get("/incidents/"+id).body().contains("Private acceptance diagnostic"));
        action(technician,id,"RESOLVE","First acceptance fix");action(employee,id,"REJECT","Still cannot reconnect");
        assertEquals("ASSIGNED",detail(employee,id).get("status").asText());
        action(technician,id,"ACKNOWLEDGE","");action(technician,id,"START","");
        action(technician,id,"RESOLVE","Second acceptance fix restored access");
        action(employee,id,"VERIFY_CLOSE","");
        var feedback=employee.post("/incidents/"+id+"/feedback",Map.of("rating","5","comment","Accepted after second fix"),employee.get("/incidents/"+id).body());
        assertEquals(200,feedback.statusCode());assertEquals("CLOSED",detail(employee,id).get("status").asText());
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM resolutions WHERE incident_id=?",Integer.class,id));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM resolution_cycles WHERE incident_id=?",Integer.class,id));
        assertEquals(5,jdbc.queryForObject("SELECT rating FROM feedback WHERE incident_id=?",Integer.class,id));
        var outsider=new Session();var registration=outsider.get("/register").body();
        assertEquals(200,outsider.post("/register",Map.of("displayName","Acceptance outsider","email","outsider@campus.test", "password",password,"role","ADMIN"),registration).statusCode());
        outsider.login("outsider@campus.test");assertEquals(403,outsider.get("/api/incidents/"+id).statusCode());
        assertEquals("EMPLOYEE",jdbc.queryForObject("SELECT role FROM user_accounts WHERE email='outsider@campus.test'",String.class));
        long otherId=jdbc.queryForObject("SELECT id FROM user_accounts WHERE email='outsider@campus.test'",Long.class);
        assertEquals(200,admin.post("/admin/users/"+otherId+"/active",Map.of("active","false"),admin.get("/admin/users").body()).statusCode());
        assertEquals("/login",outsider.get("/employee/dashboard").uri().getPath());
        saved=snapshot(jdbc);
      } finally { stopApplication(); }
    }
    assertTrue(Files.exists(databaseDirectory.resolve("PG_VERSION")),"Persistent database files must survive stop");
    try(var pg=database()) {
      try {
        // Changing the demo seed password must not reset existing accounts or duplicate seed data.
        startApplication(pg,"Different-seed-"+UUID.randomUUID(),2);
        var employee=new Session();employee.login("employee@campus.test");
        var technician=new Session();technician.login("technician@campus.test");
        var admin=new Session();admin.login("admin@campus.test");
        assertEquals("CLOSED",detail(employee,id).get("status").asText());
        String page=employee.get("/incidents/"+id).body();
        assertTrue(page.contains("First acceptance fix"));assertTrue(page.contains("Second acceptance fix restored access"));
        assertTrue(page.contains("Accepted after second fix"));assertFalse(page.contains("Private acceptance diagnostic"));
        assertEquals(saved,snapshot(new JdbcTemplate(pg.getPostgresDatabase())),"Committed records must be unchanged after both restarts");
        assertEquals(403,employee.post("/incidents/"+id+"/feedback",Map.of("rating","1"),null).statusCode());
        var logout=employee.post("/logout",Map.of(),employee.get("/incidents/"+id).body());
        assertEquals("/login",logout.uri().getPath());assertEquals("/login",employee.get("/employee/dashboard").uri().getPath());
      } finally { stopApplication(); }
    }
  }

  @Test @Timeout(value=3,unit=TimeUnit.MINUTES)
  void startupCatchesUpOverdueTargetsAndNextRestartDoesNotDuplicateDeliveries() throws Exception {
    databaseDirectory=directory.resolve("postgres");
    long id, sentinel;
    try(var pg=database()) {
      try {
        startApplication(pg,password,1);
        var employee=new Session();employee.login("employee@campus.test");
        var form=employee.get("/employee/incidents/new").body();
        var report=employee.post("/employee/incidents",Map.of("title","Startup SLA catch-up fixture",
            "description","Test-owned ticket aged while the application is stopped",
            "service",option(form,"Campus Wi-Fi"),"category",option(form,"Network"),
            "impact","3","urgency","3","affected","10"),form);
        assertEquals(200,report.statusCode());id=Long.parseLong(report.uri().getPath().replace("/incidents/",""));
        stopApplication();
        var jdbc=new JdbcTemplate(pg.getPostgresDatabase());
        // Simulate downtime without waiting 30 minutes or changing a real policy.
        jdbc.update("UPDATE incidents SET created_at=now()-interval '2 hours',cycle_started_at=now()-interval '2 hours',"
            +"response_due=now()-interval '115 minutes',resolution_due=now()-interval '90 minutes' WHERE id=?",id);
        jdbc.update("UPDATE resolution_cycles SET started_at=now()-interval '2 hours',deadline=now()-interval '90 minutes' WHERE incident_id=?",id);
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM sla_events",Integer.class));
      } finally { stopApplication(); }
    }
    monitorEnabled=true;
    List<Map<String,Object>> events,deliveries,audits;
    try(var pg=database()) {
      try {
        startApplication(pg,password,2);
        var jdbc=new JdbcTemplate(pg.getPostgresDatabase());
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(jdbc.queryForObject("SELECT count(*) FROM sla_events",Integer.class)<2 && System.nanoTime()<deadline) Thread.sleep(100);
        events=jdbc.queryForList("SELECT * FROM sla_events ORDER BY id");
        assertEquals(Set.of("RESPONSE_BREACH","RESOLUTION_BREACH"),events.stream().map(r->r.get("kind")).collect(java.util.stream.Collectors.toSet()));
        assertEquals(2,events.size(),"No backfilled warnings on the first already-overdue scan");
        deliveries=jdbc.queryForList("SELECT * FROM notifications WHERE subject IN ('RESPONSE BREACH','RESOLUTION BREACH') ORDER BY id");
        assertEquals(12,deliveries.size(),"Two targets, three relevant recipients and two channels");
        audits=jdbc.queryForList("SELECT * FROM audit_entries WHERE action LIKE '%BREACH' ORDER BY id");
        assertEquals(2,audits.size());
        var employee=new Session();employee.login("employee@campus.test");
        assertTrue(employee.get("/incidents/"+id).body().contains("RESPONSE_BREACH"));
        var admin=new Session();admin.login("admin@campus.test");
        assertTrue(admin.get("/admin/email").body().contains("SIMULATED"));
        var form=employee.get("/employee/incidents/new").body();
        var report=employee.post("/employee/incidents",Map.of("title","Next-startup sentinel fixture",
            "description","Proves the third startup scan really completed",
            "service",option(form,"Campus Wi-Fi"),"category",option(form,"Network"),
            "impact","3","urgency","3","affected","10"),form);
        assertEquals(200,report.statusCode());sentinel=Long.parseLong(report.uri().getPath().replace("/incidents/",""));
        stopApplication();
        jdbc.update("UPDATE incidents SET created_at=now()-interval '2 hours',cycle_started_at=now()-interval '2 hours',"
            +"response_due=now()-interval '115 minutes',resolution_due=now()-interval '90 minutes' WHERE id=?",sentinel);
        jdbc.update("UPDATE resolution_cycles SET started_at=now()-interval '2 hours',deadline=now()-interval '90 minutes' WHERE incident_id=?",sentinel);
      } finally { stopApplication(); }
    }
    try(var pg=database()) {
      try {
        startApplication(pg,password,3);
        var employee=new Session();employee.login("employee@campus.test");
        var jdbc=new JdbcTemplate(pg.getPostgresDatabase());
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(jdbc.queryForObject("SELECT count(*) FROM sla_events WHERE incident_id=?",Integer.class,sentinel)<2
            && System.nanoTime()<deadline) Thread.sleep(100);
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM sla_events WHERE incident_id=?",Integer.class,sentinel));
        assertEquals(events,jdbc.queryForList("SELECT * FROM sla_events WHERE incident_id=? ORDER BY id",id));
        assertEquals(deliveries,jdbc.queryForList("SELECT * FROM notifications WHERE incident_id=? AND subject IN ('RESPONSE BREACH','RESOLUTION BREACH') ORDER BY id",id));
        assertEquals(audits,jdbc.queryForList("SELECT * FROM audit_entries WHERE incident_id=? AND action LIKE '%BREACH' ORDER BY id",id));
      } finally { stopApplication(); }
    }
  }
}
