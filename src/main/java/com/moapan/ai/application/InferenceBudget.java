package com.moapan.ai.application;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Called inside the worker claim transaction. Only source/day aggregate counters, never user events. */
public final class InferenceBudget {
    private InferenceBudget() {}
    public static boolean reserve(JdbcClient db,UUID source,String model,int characters,int seconds){
        if(characters<0||characters>16000||seconds<1||seconds>900)throw new IllegalArgumentException("ai_budget_invalid");
        db.sql("DELETE FROM ai_source_budgets WHERE day<current_date-2").update();
        db.sql("INSERT INTO ai_source_budgets(source_id,model,day) VALUES(:source,:model,current_date) ON CONFLICT DO NOTHING").param("source",source).param("model",model).update();
        return db.sql("UPDATE ai_source_budgets SET calls=calls+1,characters=characters+:characters,seconds=seconds+:seconds WHERE source_id=:source AND model=:model AND day=current_date AND calls<500 AND characters+:characters<=300000 AND seconds+:seconds<=7200")
            .param("source",source).param("model",model).param("characters",characters).param("seconds",seconds).update()==1;
    }
    public static void settle(JdbcClient db,UUID source,String model,java.time.LocalDate reservedDay,int reserved,int used){
        if(used<1||used>reserved)throw new IllegalArgumentException("ai_budget_invalid");
        db.sql("UPDATE ai_source_budgets SET seconds=GREATEST(0,seconds-:refund) WHERE source_id=:source AND model=:model AND day=:day").param("refund",reserved-used).param("source",source).param("model",model).param("day",reservedDay).update();
    }
}
