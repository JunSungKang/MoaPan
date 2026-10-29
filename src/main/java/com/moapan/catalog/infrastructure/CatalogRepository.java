package com.moapan.catalog.infrastructure;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class CatalogRepository {
    private final JdbcClient db;
    public JdbcClient db(){return db;}
    public CatalogRepository(JdbcClient db){this.db=db;}
    public Map<String,Object> site(UUID id,boolean lock){return db.sql("SELECT * FROM sites WHERE id=:id"+(lock?" FOR UPDATE":"")).param("id",id).query().listOfRows().stream().findFirst().orElseThrow(()->new com.moapan.shared.api.ApiError(404,"site_not_found"));}
    public Map<String,Object> source(UUID id,boolean lock){return db.sql("SELECT * FROM sources WHERE id=:id"+(lock?" FOR UPDATE":"")).param("id",id).query().listOfRows().stream().findFirst().orElseThrow(()->new com.moapan.shared.api.ApiError(404,"source_not_found"));}
}
