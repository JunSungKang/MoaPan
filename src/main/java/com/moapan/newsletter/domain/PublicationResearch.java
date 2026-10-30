package com.moapan.newsletter.domain;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
/** Editorial metadata contains no source text and no reader profiles. */
public final class PublicationResearch {
 private PublicationResearch(){}
 public record Forecast(String question,String observation,String outlook,String horizon,String experiment,String successCriteria,String stopCondition,List<UUID> evidenceIds){}
 public record Dossier(String weekStart,String cutoff,List<UUID> articleIds,List<Forecast> forecasts,boolean rightsConfirmed,boolean editorialReviewed,String correctionNote){}
 public record Evidence(UUID id,String title,String url,Instant collectedAt,boolean visible){}
 public static LocalDate week(Instant time){return time.atZone(ZoneId.of("Asia/Seoul")).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));}
 public static Instant start(LocalDate day){return day.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();}
 public static Instant cutoff(LocalDate week,Instant now){Instant end=start(week.plusDays(7));return now.isBefore(end)?now:end.minusNanos(1);}
 public static void shape(Dossier d){
  if(d==null)return;
  if(d.weekStart()==null||d.weekStart().length()>10||d.cutoff()==null||d.cutoff().length()>40||d.articleIds()==null||d.articleIds().size()>40||d.articleIds().stream().anyMatch(Objects::isNull)||new HashSet<>(d.articleIds()).size()!=d.articleIds().size()||d.forecasts()==null||d.forecasts().size()>8||!text(d.correctionNote(),1000))throw new IllegalArgumentException();
  for(var f:d.forecasts())if(f==null||!text(f.question(),1000)||!text(f.observation(),2000)||!text(f.outlook(),2000)||!text(f.horizon(),200)||!text(f.experiment(),2000)||!text(f.successCriteria(),1000)||!text(f.stopCondition(),1000)||f.evidenceIds()==null||f.evidenceIds().size()>40||f.evidenceIds().stream().anyMatch(Objects::isNull)||new HashSet<>(f.evidenceIds()).size()!=f.evidenceIds().size())throw new IllegalArgumentException();
 }
 private static boolean text(String s,int max){return s!=null&&s.length()<=max;}
 public static List<String> blockers(Dossier d,List<Evidence> evidence,List<String> references,Instant now,LocalDate expectedWeek,boolean revision){
  List<String> out=new ArrayList<>();
  if(d==null)return List.of("research_required");
  try{shape(d);}catch(IllegalArgumentException e){return List.of("invalid_research");}
  LocalDate week;Instant cutoff;
  try{week=LocalDate.parse(d.weekStart());cutoff=Instant.parse(d.cutoff());if(!week.equals(expectedWeek)||week.getDayOfWeek()!=DayOfWeek.MONDAY||cutoff.isBefore(start(week))||!cutoff.isBefore(start(week.plusDays(7)))||cutoff.isAfter(now))out.add("invalid_evidence_period");}catch(RuntimeException e){return List.of("invalid_evidence_period");}
  if(d.articleIds().isEmpty())out.add("evidence_required");
  var available=new HashMap<UUID,Evidence>();for(var e:evidence)available.put(e.id(),e);
  for(var id:d.articleIds()){var e=available.get(id);if(e==null||!e.visible())out.add("evidence_unavailable");else if(e.collectedAt().isBefore(start(week))||e.collectedAt().isAfter(cutoff))out.add("evidence_outside_week");}
  var urls=new HashSet<String>();for(var e:evidence)if(d.articleIds().contains(e.id()))urls.add(e.url());
  if(references.stream().anyMatch(url->!urls.contains(url)))out.add("uncollected_reference");
  if(d.forecasts().isEmpty())out.add("forecast_required");
  for(var f:d.forecasts()){
   if(List.of(f.question(),f.observation(),f.outlook(),f.horizon(),f.experiment(),f.successCriteria(),f.stopCondition()).stream().anyMatch(String::isBlank))out.add("forecast_incomplete");
   if(f.evidenceIds().isEmpty()||!d.articleIds().containsAll(f.evidenceIds()))out.add("forecast_evidence_required");
  }
  if(!d.rightsConfirmed())out.add("reuse_rights_review_required");
  if(!d.editorialReviewed())out.add("editorial_review_required");
  if(revision&&d.correctionNote().isBlank())out.add("correction_note_required");
  return out.stream().distinct().toList();
 }
}
