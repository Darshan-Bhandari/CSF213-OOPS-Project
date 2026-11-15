package edu.bits.itsm.catalog;
import edu.bits.itsm.audit.AuditService;
import edu.bits.itsm.shared.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class CatalogService {
 private final JdbcTemplate jdbc;private final AuditService audit;
 public CatalogService(JdbcTemplate jdbc,AuditService audit){this.jdbc=jdbc;this.audit=audit;}
 public List<Map<String,Object>> categories(){return jdbc.queryForList("SELECT * FROM categories ORDER BY name");}
 public List<Map<String,Object>> services(){return jdbc.queryForList("SELECT * FROM it_services ORDER BY name");}
 public Map<String,Object> service(long id){return one("SELECT * FROM it_services WHERE id=? AND active",id);}
 public void requireCategory(long id){one("SELECT * FROM categories WHERE id=? AND active",id);}
 public Policy policy(long service,String priority){var rows=jdbc.query("SELECT response_seconds,resolution_seconds FROM sla_policies WHERE priority=? AND (service_id=? OR service_id IS NULL) ORDER BY service_id NULLS LAST LIMIT 1",(r,n)->new Policy(r.getInt(1),r.getInt(2)),priority,service);if(rows.isEmpty())throw new BusinessException("No SLA policy is configured for this service and priority.");return rows.getFirst();}
 private Map<String,Object> one(String sql,Object...args){var rows=jdbc.queryForList(sql,args);if(rows.isEmpty())throw new BusinessException("Active service/category was not found.");return rows.getFirst();}
 @Transactional public void lock(){jdbc.queryForObject("SELECT id FROM assignment_configuration WHERE id=1 FOR UPDATE",Integer.class);}
 @Transactional public void saveCategory(Long id,String name,boolean active,long actor){lock();name=validName(name,80);if(id==null)jdbc.update("INSERT INTO categories(name,active) VALUES (?,?)",name,active);else jdbc.update("UPDATE categories SET name=?,active=? WHERE id=?",name,active,id);audit.record(actor,"CATEGORY_SAVED","CATEGORY-"+id,"Catalog configuration updated");}
 @Transactional public void saveService(Long id,String name,int criticality,boolean active,long actor){lock();name=validName(name,100);if(criticality<1||criticality>3)throw new BusinessException("Criticality must be 1–3.");if(id==null)jdbc.update("INSERT INTO it_services(name,criticality,active) VALUES (?,?,?)",name,criticality,active);else jdbc.update("UPDATE it_services SET name=?,criticality=?,active=? WHERE id=?",name,criticality,active,id);audit.record(actor,"SERVICE_SAVED","SERVICE-"+id,"Catalog configuration updated; historical incidents unchanged");}
 @Transactional public void savePolicy(Long service,String priority,int response,int resolution,long actor){lock();if(!Set.of("P1","P2","P3","P4").contains(priority)||response<1||resolution<response)throw new BusinessException("Invalid SLA targets.");if(service!=null)service(service);var rows=jdbc.queryForList("SELECT id FROM sla_policies WHERE priority=? AND (service_id=? OR (service_id IS NULL AND ?::bigint IS NULL))",priority,service,service);if(rows.isEmpty())jdbc.update("INSERT INTO sla_policies(service_id,priority,response_seconds,resolution_seconds) VALUES (?,?,?,?)",service,priority,response,resolution);else jdbc.update("UPDATE sla_policies SET response_seconds=?,resolution_seconds=? WHERE id=?",response,resolution,rows.getFirst().get("id"));audit.record(actor,"SLA_POLICY_SAVED","SLA-"+priority,"SLA policy updated; new incidents only");}
 public List<Map<String,Object>> policies(){return jdbc.queryForList("SELECT p.*,COALESCE(s.name,'All services') service_name FROM sla_policies p LEFT JOIN it_services s ON s.id=p.service_id ORDER BY p.priority,s.name NULLS FIRST");}
 private String validName(String name,int max){if(name==null||name.isBlank()||name.length()>max)throw new BusinessException("Supply a valid catalog name.");return name.strip();}
 public record Policy(int responseSeconds,int resolutionSeconds){}
}
