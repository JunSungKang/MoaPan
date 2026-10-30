package com.moapan.newsletter;
import java.util.*;
import java.net.URI;
import java.time.*;
import com.moapan.newsletter.domain.PublicationResearch;
import com.moapan.newsletter.domain.PublicationResearch.*;
import com.moapan.newsletter.application.PublicationEvidence;
import com.moapan.subscription.application.ContentAccess;
import com.moapan.subscription.domain.EntitlementPolicy.Benefit;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.moapan.shared.api.ApiError;
import tools.jackson.databind.json.JsonMapper;
@Service
public class NewsletterService{
 public record Block(String type,String text,String url,String caption){}
 public record Manuscript(String title,String introduction,String topic,List<Block> blocks){}
 public record Input(long version,Manuscript manuscript,Dossier dossier){}
 private final JdbcClient db;private final JsonMapper json=JsonMapper.builder().build();
 private final PublicationEvidence evidence;private final ContentAccess access;private final Clock clock;
 public NewsletterService(JdbcClient db,PublicationEvidence evidence,ContentAccess access,Clock clock){this.db=db;this.evidence=evidence;this.access=access;this.clock=clock;}
 private String validate(Manuscript m){
  if(m==null||m.title()==null||m.title().isBlank()||m.title().length()>180||m.introduction()==null||m.introduction().length()>1000||m.topic()==null||m.topic().length()>100||m.blocks()==null||m.blocks().size()>80)throw new ApiError(400,"invalid_manuscript");
  for(var b:m.blocks()){
   if(b==null||b.type()==null||!Set.of("paragraph","heading","quote","image","reference").contains(b.type())||b.text()==null||b.text().length()>12000||b.caption()==null||b.caption().length()>1000||b.url()==null)throw new ApiError(400,"invalid_block");
   if(b.type().equals("image")){if(!b.url().matches("data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+")||b.url().length()>300000||b.text().isBlank())throw new ApiError(400,"invalid_image");try{var bytes=Base64.getDecoder().decode(b.url().substring(b.url().indexOf(',')+1));try(var stream=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))){var readers=javax.imageio.ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new IllegalArgumentException();var reader=readers.next();try{reader.setInput(stream);if(reader.getWidth(0)>2000||reader.getHeight(0)>2000)throw new IllegalArgumentException();}finally{reader.dispose();}}}catch(Exception e){throw new ApiError(400,"invalid_image");}}
   else if(b.type().equals("reference")){try{var u=URI.create(b.url());if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||b.url().length()>2048)throw new IllegalArgumentException();}catch(Exception e){throw new ApiError(400,"invalid_reference");}}
   else if(!b.url().isEmpty())throw new ApiError(400,"invalid_block_url");
  }
  var encoded=json.writeValueAsString(m);if(encoded.length()>800000)throw new ApiError(400,"manuscript_too_large");return encoded;
 }
 @PreAuthorize("isAuthenticated()") public Object list(){
  access.require(Benefit.READ_NEWSLETTER);
  var rows=db.sql("SELECT id,issue_number,published->>'title' title,published->>'introduction' introduction,published->>'topic' topic,published_at,first_published_at,publication_revision,distribution,published_dossier::text dossier FROM newsletters WHERE published IS NOT NULL ORDER BY published_at DESC,id LIMIT 100").query().listOfRows();
  var ids=new HashSet<UUID>();for(var row:rows){var d=dossier(row.get("dossier"));if(d!=null)ids.addAll(d.articleIds());}
  var visible=new HashSet<UUID>();for(var e:evidence.selected(ids,false))if(e.visible())visible.add(e.id());
  var output=new ArrayList<Map<String,Object>>();for(var row:rows){var d=dossier(row.get("dossier"));if(d!=null&&!visible.containsAll(d.articleIds()))continue;var copy=new LinkedHashMap<>(row);copy.remove("dossier");output.add(utcTimestamps(copy));}return output;
 }
 @PreAuthorize("isAuthenticated()") public Object detail(UUID id){
  access.require(Benefit.READ_NEWSLETTER);
  var row=db.sql("SELECT id,issue_number,published::text content,published_dossier::text dossier,published_at,first_published_at,publication_revision,distribution FROM newsletters WHERE id=:id AND published IS NOT NULL").param("id",id).query().listOfRows().stream().findFirst().orElseThrow(()->new ApiError(404,"newsletter_not_found"));
  Dossier d=dossier(row.get("dossier"));var refs=d==null?List.<Evidence>of():evidence.selected(d.articleIds(),false);
  if(d!=null&&(refs.size()!=d.articleIds().size()||refs.stream().anyMatch(e->!e.visible())))throw new ApiError(404,"newsletter_not_found");
  var out=new LinkedHashMap<String,Object>();out.put("id",id);out.put("issueNumber",row.get("issue_number"));out.put("manuscript",json.readTree(row.get("content").toString()));out.put("publishedAt",utcTimestamp(row.get("published_at")));out.put("firstPublishedAt",utcTimestamp(row.get("first_published_at")));out.put("revision",row.get("publication_revision"));out.put("distribution",row.get("distribution"));out.put("dossier",d);out.put("evidence",refs);out.put("reuseAllowed",d!=null&&d.rightsConfirmed());return out;
 }
 @PreAuthorize("isAuthenticated()") public Object reuse(UUID id){access.require(Benefit.REUSE_NEWSLETTER);var result=detail(id);@SuppressWarnings("unchecked")var data=(Map<String,Object>)result;if(!Boolean.TRUE.equals(data.get("reuseAllowed")))throw new ApiError(409,"reuse_rights_review_required");return data;}
 @PreAuthorize("isAuthenticated()") public Object worksheet(UUID id){access.require(Benefit.RESEARCH_WORKSHEET);return detail(id);}
 @PreAuthorize("hasRole('ADMIN')") public Object drafts(){return db.sql("SELECT id,draft->>'title' title,version,published_at,updated_at FROM newsletters ORDER BY updated_at DESC,id LIMIT 100").query().listOfRows().stream().map(this::utcTimestamps).toList();}
 @PreAuthorize("hasRole('ADMIN')") public Object edit(UUID id){var r=db.sql("SELECT draft::text content,draft_dossier::text dossier,version,publication_revision,first_published_at FROM newsletters WHERE id=:id").param("id",id).query().listOfRows().stream().findFirst().orElseThrow(()->new ApiError(404,"newsletter_not_found"));var result=new LinkedHashMap<String,Object>();var d=dossier(r.get("dossier"));result.put("manuscript",json.readTree(r.get("content").toString()));result.put("version",r.get("version"));result.put("dossier",d);result.put("evidence",d==null?List.of():evidence.selected(d.articleIds(),false));result.put("revision",r.get("publication_revision"));result.put("expectedWeek",expectedWeek(r).toString());return result;}
 @Transactional @PreAuthorize("hasRole('ADMIN')") public Object save(UUID id,Input input,UUID actor){
  if(input==null||input.version()<0)throw new ApiError(400,"invalid_version");String content=validate(input.manuscript());try{PublicationResearch.shape(input.dossier());}catch(IllegalArgumentException e){throw new ApiError(400,"invalid_research");}String research=json.writeValueAsString(input.dossier());
  lock(id);if(input.version()==0){if(db.sql("SELECT EXISTS(SELECT 1 FROM admin_actions WHERE target_id=:id AND action='newsletter_delete')").param("id",id).query(Boolean.class).single())throw new ApiError(409,"newsletter_deleted");db.sql("INSERT INTO newsletters(id,draft) VALUES(:id,CAST(:body AS jsonb)) ON CONFLICT DO NOTHING").param("id",id).param("body",content).update();}
  int n=db.sql("UPDATE newsletters SET draft=CAST(:body AS jsonb),draft_dossier=CAST(:research AS jsonb),version=version+1,updated_at=now() WHERE id=:id AND version=:version").param("id",id).param("body",content).param("research",research).param("version",input.version()).update();if(n!=1)throw new ApiError(409,"version_conflict");audit(actor,id,"newsletter_save");return edit(id);
 }
 @PreAuthorize("hasRole('ADMIN')") public Object review(UUID id){var row=reviewRow(id);var d=dossier(row.get("dossier"));var m=json.readValue(row.get("content").toString(),Manuscript.class);var refs=d==null?List.<Evidence>of():evidence.selected(d.articleIds(),false);return Map.of("version",row.get("version"),"expectedWeek",expectedWeek(row).toString(),"blockers",publicationBlockers(m,d,refs,expectedWeek(row),((Number)row.get("publication_revision")).intValue()>0),"evidence",refs);}
 private List<String> publicationBlockers(Manuscript m,Dossier d,List<Evidence> refs,LocalDate week,boolean revision){var out=new ArrayList<>(PublicationResearch.blockers(d,refs,m.blocks().stream().filter(b->b.type().equals("reference")).map(Block::url).toList(),clock.instant(),week,revision));if(m.introduction().isBlank()||m.topic().isBlank()||m.blocks().stream().noneMatch(b->b.type().equals("paragraph")&&!b.text().isBlank()))out.add("manuscript_incomplete");return out;}
 @Transactional @PreAuthorize("hasRole('ADMIN')") public void publish(UUID id,long version,boolean visible,UUID actor){
  lock(id);var row=reviewRow(id);if(((Number)row.get("version")).longValue()!=version)throw new ApiError(409,"version_conflict");
  if(visible){var d=dossier(row.get("dossier"));var m=json.readValue(row.get("content").toString(),Manuscript.class);var refs=d==null?List.<Evidence>of():evidence.selected(d.articleIds(),true);var blockers=publicationBlockers(m,d,refs,expectedWeek(row),((Number)row.get("publication_revision")).intValue()>0);if(!blockers.isEmpty())throw new ApiError(409,blockers.getFirst());}
  if(db.sql("UPDATE newsletters SET issue_number=CASE WHEN :visible THEN COALESCE(issue_number,nextval('newsletter_issue_seq')) ELSE issue_number END,published=CASE WHEN :visible THEN draft ELSE NULL END,published_dossier=CASE WHEN :visible THEN draft_dossier ELSE NULL END,first_published_at=CASE WHEN :visible THEN COALESCE(first_published_at,now()) ELSE first_published_at END,published_at=CASE WHEN :visible THEN now() ELSE NULL END,publication_revision=publication_revision+CASE WHEN :visible THEN 1 ELSE 0 END,distribution=:distribution,version=version+1,updated_at=now() WHERE id=:id AND version=:version").param("visible",visible).param("distribution",access.distribution()).param("id",id).param("version",version).update()!=1)throw new ApiError(409,"version_conflict");audit(actor,id,visible?"newsletter_publish":"newsletter_withdraw");
 }
 private Map<String,Object> reviewRow(UUID id){return db.sql("SELECT draft::text content,draft_dossier::text dossier,version,first_published_at,publication_revision FROM newsletters WHERE id=:id").param("id",id).query().listOfRows().stream().findFirst().orElseThrow(()->new ApiError(404,"newsletter_not_found"));}
 private LocalDate expectedWeek(Map<String,Object> row){return PublicationResearch.week(row.get("first_published_at")==null?clock.instant():Instant.parse(utcTimestamp(row.get("first_published_at"))));}
 private Dossier dossier(Object value){return value==null||value.toString().equals("null")?null:json.readValue(value.toString(),Dossier.class);}
 @Transactional @PreAuthorize("hasRole('ADMIN')") public void delete(UUID id,long version,UUID actor){
  if(version<1)throw new ApiError(400,"invalid_version");
  lock(id);
  if(db.sql("DELETE FROM newsletters WHERE id=:id AND version=:version").param("id",id).param("version",version).update()!=1){
   boolean exists=db.sql("SELECT EXISTS(SELECT 1 FROM newsletters WHERE id=:id)").param("id",id).query(Boolean.class).single();
   throw new ApiError(exists?409:404,exists?"version_conflict":"newsletter_not_found");
  }
  audit(actor,id,"newsletter_delete");
 }
 private String utcTimestamp(Object value){if(value instanceof java.sql.Timestamp t)return t.toInstant().toString();if(value instanceof java.time.OffsetDateTime t)return t.toInstant().toString();throw new IllegalStateException("Unsupported newsletter timestamp");}
 private Map<String,Object> utcTimestamps(Map<String,Object> row){var result=new LinkedHashMap<String,Object>(row);for(String key:List.of("published_at","first_published_at","updated_at"))if(result.get(key)!=null)result.put(key,utcTimestamp(result.get(key)));return result;}
 private void lock(UUID id){db.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))").param("key","newsletter:"+id).query((row,n)->1).list();}
 private void audit(UUID actor,UUID id,String action){db.sql("INSERT INTO admin_actions VALUES(:id,:actor,:action,:target,now())").param("id",UUID.randomUUID()).param("actor",actor).param("action",action).param("target",id).update();}
}
