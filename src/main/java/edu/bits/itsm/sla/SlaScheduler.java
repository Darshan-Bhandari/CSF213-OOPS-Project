package edu.bits.itsm.sla;
import org.springframework.boot.context.event.ApplicationReadyEvent;import org.springframework.context.event.EventListener;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Component;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;
@Component @ConditionalOnProperty(name="app.sla.monitor-enabled",havingValue="true",matchIfMissing=true)
public class SlaScheduler {private final SlaMonitor monitor;private final Logger log=LoggerFactory.getLogger(getClass());public SlaScheduler(SlaMonitor monitor){this.monitor=monitor;}
 @EventListener(ApplicationReadyEvent.class) public void catchUp(){run();}
 @Scheduled(fixedDelayString="${app.sla.scan-delay-ms:30000}",initialDelayString="${app.sla.scan-delay-ms:30000}") public void run(){try{monitor.scan();}catch(org.springframework.dao.DataAccessException ex){log.error("SLA scan failed; will retry on next scheduled scan ({})",ex.getClass().getSimpleName());}}
}
