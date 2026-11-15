package edu.bits.itsm.config;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
@Configuration @EnableScheduling
public class WorkflowConfiguration {@Bean public Clock applicationClock(){return Clock.systemUTC();}}
