package edu.bits.itsm.sla;
import edu.bits.itsm.incident.*;import edu.bits.itsm.catalog.CatalogService;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service public class SlaMonitor {
 private final IncidentRepository incidents;private final CatalogService catalog;private final SlaService sla;
 public SlaMonitor(IncidentRepository incidents,CatalogService catalog,SlaService sla){this.incidents=incidents;this.catalog=catalog;this.sla=sla;}
 @Transactional public void scan(){catalog.lock();for(var incident:incidents.findAll())sla.check(incident);}
}
