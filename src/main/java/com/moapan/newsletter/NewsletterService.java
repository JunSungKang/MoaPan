package com.moapan.newsletter;
import java.util.*;
import java.net.URI;
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
 public record Input(long version,Manuscript manuscript){}
 private final JdbcClient db;private final JsonMapper json=JsonMapper.builder().build();
 public NewsletterService(JdbcClient db){this.db=db;}
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
 @PreAuthorize("isAuthenticated()") public Object list(){return db.sql("SELECT id,issue_number,published->>'title' title,published->>'introduction' introduction,published->>'topic' topic,published_at FROM newsletters WHERE published IS NOT NULL ORDER BY published_at DESC,id LIMIT 100").query().listOfRows();}
 @PreAuthorize("isAuthenticated()") public Object detail(UUID id){var row=db.sql("SELECT id,issue_number,published::text content,published_at FROM newsletters WHERE id=:id AND published IS NOT NULL").param("id",id).query().listOfRows().stream().findFirst().orElseThrow(()->new ApiError(404,"newsletter_not_found"));return Map.of("id",id,"issueNumber",row.get("issue_number"),"manuscript",json.readTree(row.get("content").toString()),"publishedAt",row.get("published_at").toString());}
 @PreAuthorize("hasRole('ADMIN')") public Object drafts(){return db.sql("SELECT id,draft->>'title' title,version,published_at,updated_at FROM newsletters ORDER BY updated_at DESC LIMIT 100").query().listOfRows();}
 @PreAuthorize("hasRole('ADMIN')") public Object edit(UUID id){var r=db.sql("SELECT draft::text content,version FROM newsletters WHERE id=:id").param("id",id).query().listOfRows().stream().findFirst().orElseThrow(()->new ApiError(404,"newsletter_not_found"));return Map.of("manuscript",json.readTree(r.get("content").toString()),"version",r.get("version"));}
 @Transactional @PreAuthorize("hasRole('ADMIN')") public Object save(UUID id,Input input,UUID actor){String content=validate(input.manuscript());if(input.version()<0)throw new ApiError(400,"invalid_version");if(input.version()==0){db.sql("INSERT INTO newsletters(id,draft) VALUES(:id,CAST(:body AS jsonb)) ON CONFLICT DO NOTHING").param("id",id).param("body",content).update();}int n=db.sql("UPDATE newsletters SET draft=CAST(:body AS jsonb),version=version+1,updated_at=now() WHERE id=:id AND version=:version").param("id",id).param("body",content).param("version",input.version()).update();if(n!=1)throw new ApiError(409,"version_conflict");audit(actor,id,"newsletter_save");return edit(id);}
 @Transactional @PreAuthorize("hasRole('ADMIN')") public void publish(UUID id,long version,boolean visible,UUID actor){if(db.sql("UPDATE newsletters SET issue_number=CASE WHEN :visible THEN COALESCE(issue_number,nextval('newsletter_issue_seq')) ELSE issue_number END,published=CASE WHEN :visible THEN draft ELSE NULL END,published_at=CASE WHEN :visible THEN now() ELSE NULL END,version=version+1 WHERE id=:id AND version=:version").param("visible",visible).param("id",id).param("version",version).update()!=1)throw new ApiError(409,"version_conflict");audit(actor,id,visible?"newsletter_publish":"newsletter_withdraw");}
 private void audit(UUID actor,UUID id,String action){db.sql("INSERT INTO admin_actions VALUES(:id,:actor,:action,:target,now())").param("id",UUID.randomUUID()).param("actor",actor).param("action",action).param("target",id).update();}
}
