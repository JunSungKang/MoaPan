package com.moapan.subscription.application;

import com.moapan.shared.api.ApiError;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class SubscriptionManagement {
    public record Search(String username,Integer page,Integer size,String sort) {}
    public record GrantInput(long version,Instant startsAt,Instant expiresAt) {}
    public record Version(long version) {}
    public record AccountView(UUID id,String username,String role,boolean active,String subscriptionStatus,
                              Instant startsAt,Instant expiresAt,long version) {}
    public record Page(List<AccountView> items,int page,boolean hasNext) {}
    private final JdbcClient db;
    private final Clock clock;
    public SubscriptionManagement(JdbcClient db,Clock clock){this.db=db;this.clock=clock;}

    public Page search(Search input) {
        if(input==null)throw new ApiError(400,"invalid_subscription_search");
        String name=input.username()==null?"":input.username().toLowerCase(Locale.ROOT);
        int page=input.page()==null?0:input.page(),size=input.size()==null?10:input.size();
        if(name.length()>128||page<0||page>10000||size<1||size>100)throw new ApiError(400,"invalid_subscription_search");
        String sort=input.sort()==null?"RECENT":input.sort();
        String usable="s.status='ACTIVE' AND u.active AND s.starts_at<=:now AND s.expires_at>:now";
        String order=switch(sort){
            case "RECENT" -> "CASE WHEN s.status='ACTIVE' THEN 0 WHEN s.status='REVOKED' THEN 1 ELSE 2 END,s.updated_at DESC NULLS LAST,u.username,u.id";
            case "EXPIRING" -> "CASE WHEN "+usable+" THEN 0 ELSE 1 END,CASE WHEN "+usable+" THEN s.expires_at END ASC NULLS LAST,s.updated_at DESC NULLS LAST,u.username,u.id";
            default -> throw new ApiError(400,"invalid_subscription_search");
        };
        var now=clock.instant();var query=db.sql(SELECT+" WHERE position(:name in u.username)>0 ORDER BY "+order+" LIMIT :limit OFFSET :offset")
                .param("name",name).param("limit",size+1).param("offset",page*size);
        if(sort.equals("EXPIRING"))query.param("now",now.atOffset(ZoneOffset.UTC));
        var rows=query.query((rs,n)->map(rs,now)).list();
        return new Page(List.copyOf(rows.subList(0,Math.min(size,rows.size()))),page,rows.size()>size);
    }

    @Transactional
    public AccountView grant(UUID account,GrantInput input,UUID actor) {
        var now=clock.instant();
        if(input==null||input.version()<0||input.startsAt()==null||input.expiresAt()==null
                ||!input.expiresAt().isAfter(input.startsAt())||!input.expiresAt().isAfter(now)
                ||input.startsAt().isBefore(Instant.parse("0001-01-01T00:00:00Z"))
                ||input.expiresAt().isAfter(Instant.parse("9999-12-31T23:59:59Z")))throw new ApiError(400,"invalid_subscription_period");
        lockAccount(account);checkVersion(account,input.version());
        db.sql("""
                INSERT INTO account_subscriptions(account_id,status,starts_at,expires_at,version)
                VALUES(:id,'ACTIVE',:start,:end,1)
                ON CONFLICT(account_id) DO UPDATE SET status='ACTIVE',starts_at=:start,expires_at=:end,
                version=account_subscriptions.version+1,updated_at=now()
                """).param("id",account).param("start",input.startsAt().atOffset(ZoneOffset.UTC)).param("end",input.expiresAt().atOffset(ZoneOffset.UTC)).update();
        audit(actor,account,"subscription_grant");return read(account);
    }

    @Transactional
    public AccountView revoke(UUID account,Version input,UUID actor) {
        if(input==null||input.version()<1)throw new ApiError(400,"invalid_version");
        lockAccount(account);checkVersion(account,input.version());
        db.sql("UPDATE account_subscriptions SET status='REVOKED',version=version+1,updated_at=now() WHERE account_id=:id")
                .param("id",account).update();audit(actor,account,"subscription_revoke");return read(account);
    }

    private void lockAccount(UUID id){if(db.sql("SELECT id FROM users WHERE id=:id FOR UPDATE").param("id",id).query(UUID.class).optional().isEmpty())throw new ApiError(404,"not_found");}
    private void checkVersion(UUID id,long version){long current=db.sql("SELECT version FROM account_subscriptions WHERE account_id=:id").param("id",id).query(Long.class).optional().orElse(0L);if(current!=version)throw new ApiError(409,"version_conflict");}
    private AccountView read(UUID id){var now=clock.instant();return db.sql(SELECT+" WHERE u.id=:id").param("id",id).query((rs,n)->map(rs,now)).single();}
    private void audit(UUID actor,UUID target,String action){db.sql("INSERT INTO admin_actions(id,admin_id,action,target_id) VALUES(:id,:actor,:action,:target)").param("id",UUID.randomUUID()).param("actor",actor).param("action",action).param("target",target).update();}
    private static final String SELECT="SELECT u.id,u.username,u.role,u.active,s.status,s.starts_at,s.expires_at,COALESCE(s.version,0) version FROM users u LEFT JOIN account_subscriptions s ON s.account_id=u.id";
    private static AccountView map(java.sql.ResultSet rs,Instant now)throws java.sql.SQLException {
        var start=rs.getObject("starts_at",OffsetDateTime.class);var end=rs.getObject("expires_at",OffsetDateTime.class);String state=rs.getString("status");
        String display=state==null?"NONE":state.equals("REVOKED")?"REVOKED":!end.toInstant().isAfter(now)?"EXPIRED":start.toInstant().isAfter(now)?"SCHEDULED":"ACTIVE";
        return new AccountView(rs.getObject("id",UUID.class),rs.getString("username"),rs.getString("role"),rs.getBoolean("active"),display,start==null?null:start.toInstant(),end==null?null:end.toInstant(),rs.getLong("version"));
    }
}
