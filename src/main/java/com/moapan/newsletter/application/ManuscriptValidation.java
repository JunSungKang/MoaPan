package com.moapan.newsletter.application;

import com.moapan.newsletter.domain.Manuscript;
import com.moapan.newsletter.domain.Manuscript.Block;
import com.moapan.shared.api.ApiError;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.*;
import java.util.function.UnaryOperator;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Closed validation strategies; each block type owns its rules, no client expressions. */
@Component
public final class ManuscriptValidation {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final Map<String,UnaryOperator<Block>> STRATEGIES=Map.of(
        "paragraph",ManuscriptValidation::text,
        "heading",ManuscriptValidation::text,
        "quote",ManuscriptValidation::text,
        "reference",ManuscriptValidation::reference,
        "image",ManuscriptValidation::image);

    public String encode(Manuscript manuscript){
        if(manuscript==null||manuscript.title()==null||manuscript.title().isBlank()||manuscript.title().length()>180
                ||manuscript.introduction()==null||manuscript.introduction().length()>1000||manuscript.topic()==null
                ||manuscript.topic().length()>100||manuscript.blocks()==null||manuscript.blocks().size()>80
                ||!Set.of("ko","en","und").contains(manuscript.language()))throw new ApiError(400,"invalid_manuscript");
        var blocks=new ArrayList<Block>();
        for(var block:manuscript.blocks()){
            if(block==null||block.type()==null||block.text()==null||block.text().length()>12000
                    ||block.caption()==null||block.caption().length()>1000||block.url()==null)throw new ApiError(400,"invalid_block");
            var strategy=STRATEGIES.get(block.type());if(strategy==null)throw new ApiError(400,"invalid_block");
            blocks.add(strategy.apply(block));
        }
        String encoded=JSON.writeValueAsString(new Manuscript(manuscript.title(),manuscript.introduction(),manuscript.topic(),blocks,manuscript.language()));
        if(encoded.length()>800000)throw new ApiError(400,"manuscript_too_large");return encoded;
    }
    private static Block text(Block block){
        if(!block.url().isEmpty())throw new ApiError(400,"invalid_block_url");
        return new Block(block.type(),block.text(),block.url(),block.caption());
    }
    private static Block reference(Block block){
        try{
            var uri=URI.create(block.url());
            if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||block.url().length()>2048)throw new IllegalArgumentException();
            return new Block(block.type(),block.text(),block.url(),block.caption());
        }catch(Exception e){throw new ApiError(400,"invalid_reference");}
    }
    private static Block image(Block block){
        if(!block.url().matches("data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+")||block.url().length()>300000||block.text().isBlank())throw new ApiError(400,"invalid_image");
        try{
            var bytes=Base64.getDecoder().decode(block.url().substring(block.url().indexOf(',')+1));
            try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
                var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new IllegalArgumentException();
                var reader=readers.next();
                try{
                    reader.setInput(stream);int width=reader.getWidth(0),height=reader.getHeight(0);
                    if(width>2000||height>2000)throw new IllegalArgumentException();
                    return new Block(block.type(),block.text(),block.url(),block.caption(),width,height);
                }finally{reader.dispose();}
            }
        }catch(Exception e){throw new ApiError(400,"invalid_image");}
    }
}
