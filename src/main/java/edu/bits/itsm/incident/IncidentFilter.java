package edu.bits.itsm.incident;
import org.springframework.format.annotation.DateTimeFormat;import java.time.LocalDate;
public class IncidentFilter {
 private String query="",status="",priority="",sla="";
 private Long category,service,technician;private int page;
 @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate from,to;
 public String getQuery(){return query;}public void setQuery(String v){query=v==null?"":v;}
 public String getStatus(){return status;}public void setStatus(String v){status=v==null?"":v;}
 public String getPriority(){return priority;}public void setPriority(String v){priority=v==null?"":v;}
 public String getSla(){return sla;}public void setSla(String v){sla=v==null?"":v;}
 public Long getCategory(){return category;}public void setCategory(Long v){category=v;}public Long getService(){return service;}public void setService(Long v){service=v;}public Long getTechnician(){return technician;}public void setTechnician(Long v){technician=v;}
 public int getPage(){return page;}public void setPage(int v){page=Math.max(0,Math.min(v,100000));}
 public LocalDate getFrom(){return from;}public void setFrom(LocalDate v){from=v;}public LocalDate getTo(){return to;}public void setTo(LocalDate v){to=v;}
}
