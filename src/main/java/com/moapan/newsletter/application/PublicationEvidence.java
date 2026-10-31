package com.moapan.newsletter.application;
import com.moapan.newsletter.domain.PublicationResearch;
import com.moapan.newsletter.domain.PublicationResearch.Evidence;
import com.moapan.shared.api.ApiError;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.jdbc.core.simple.JdbcClient;
@Service
public class PublicationEvidence {
 public record Search(String search,String weekStart){}
 private final JdbcClient db;private final Clock clock;
 public PublicationEvidence(JdbcClient d,Clock c){db=d;clock=c;}
 public List<Evidence> selected(Collection<UUID> ids,boolean lock){
  if(ids.isEmpty())return List.of();
  if(lock)db.sql("SELECT id FROM sources WHERE id IN (SELECT source_id FROM articles WHERE id IN (:ids)) ORDER BY id FOR SHARE").param("ids",ids).query(UUID.class).list();
  return db.sql("SELECT a.id,a.title,a.canonical_url,a.collected_at,(a.public AND s.allow_metadata AND s.allow_display AND s.rights_status IN ('APPROVED','RESTRICTED')) visible FROM articles a JOIN sources s ON s.id=a.source_id WHERE a.id IN (:ids) ORDER BY a.id"+(lock?" FOR SHARE OF a":"")).param("ids",ids).query((r,n)->new Evidence(r.getObject("id",UUID.class),r.getString("title"),r.getString("canonical_url"),r.getTimestamp("collected_at").toInstant(),r.getBoolean("visible"))).list();
 }
 @PreAuthorize("hasRole('ADMIN')") public Object search(Search input){
  if(input==null||input.search()==null||input.search().length()>100||input.search().codePoints().anyMatch(Character::isISOControl)||input.weekStart()==null)throw new ApiError(400,"invalid_evidence_search");
  LocalDate week;try{week=input.weekStart().isEmpty()?PublicationResearch.week(clock.instant()):LocalDate.parse(input.weekStart());if(week.getDayOfWeek()!=DayOfWeek.MONDAY||week.getYear()<1970||week.isAfter(PublicationResearch.week(clock.instant())))throw new IllegalArgumentException();}catch(RuntimeException e){throw new ApiError(400,"invalid_evidence_period");}
  String pattern="%"+input.search().strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
  var rows=db.sql("SELECT a.id,a.title,a.canonical_url,a.collected_at FROM articles a JOIN sources s ON s.id=a.source_id WHERE a.public AND s.allow_metadata AND s.allow_display AND s.rights_status IN ('APPROVED','RESTRICTED') AND a.collected_at>=:start AND a.collected_at<:end AND a.collected_at<=now() AND lower(a.title) LIKE :term ESCAPE '!' ORDER BY a.collected_at DESC,a.id LIMIT 100").param("start",OffsetDateTime.ofInstant(PublicationResearch.start(week),ZoneOffset.UTC)).param("end",OffsetDateTime.ofInstant(PublicationResearch.start(week.plusDays(7)),ZoneOffset.UTC)).param("term",pattern).query((r,n)->new Evidence(r.getObject("id",UUID.class),r.getString("title"),r.getString("canonical_url"),r.getTimestamp("collected_at").toInstant(),true)).list();
  return Map.of("weekStart",week.toString(),"cutoff",PublicationResearch.cutoff(week,clock.instant()).toString(),"items",rows);
 }
}
