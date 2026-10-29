package com.moapan.collection.infrastructure;
import com.moapan.collection.infrastructure.SafeFetch;
import java.sql.*;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;
/** Shared admission gate across web previews and workers; no credentials or user data. */
@Component
public class ArxivGate {
    private final DataSource data;
    public ArxivGate(DataSource data){this.data=data;}
    public Lease enter(){
        Connection c=null;
        try {c=data.getConnection();c.setAutoCommit(false);
            try(var q=c.prepareStatement("SELECT next_at<=now() FROM source_access_gate WHERE id='arxiv' FOR UPDATE NOWAIT");var r=q.executeQuery()){
                if(!r.next()||!r.getBoolean(1))throw new SafeFetch.FetchFailure(429,"3");
            }
            return new Lease(c);
        }catch(Exception e){if(c!=null)try{c.rollback();c.close();}catch(SQLException ignored){}if(e instanceof SafeFetch.FetchFailure f)throw f;if(e instanceof SQLException sql&&"55P03".equals(sql.getSQLState()))throw new SafeFetch.FetchFailure(429,"3");throw new com.moapan.shared.api.ApiError(503,"source_gate_unavailable");}
    }
    public static final class Lease implements AutoCloseable {
        private final Connection connection;
        Lease(Connection c){connection=c;}
        public void close(){try(connection){try(var q=connection.prepareStatement("UPDATE source_access_gate SET next_at=now()+interval '3 seconds' WHERE id='arxiv'")){q.executeUpdate();}connection.commit();}catch(SQLException e){throw new com.moapan.shared.api.ApiError(503,"source_gate_unavailable");}}
    }
}
